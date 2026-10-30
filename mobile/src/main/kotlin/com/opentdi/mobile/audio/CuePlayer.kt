package com.opentdi.mobile.audio

import com.opentdi.shared.model.WatchOutput
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.opentdi.mobile.logLine
import com.opentdi.shared.audio.FadeEnvelope
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.WristCue
import com.opentdi.shared.audio.targetGain
import com.opentdi.shared.audio.volumeRaisedCorrection
import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.FocusMode
import com.opentdi.shared.model.PromptCue
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One p_audio.csv row (§12.3) without wall_ms, elapsed_ms, cycle and route, which the logger adds. */
data class AudioEvent(
    val event: String,
    val cue: PromptCue?,
    val routedType: Int? = null,
    val gain: Float? = null,
    val fadeMs: Int? = null,
    val focus: FocusMode? = null,
    val dndFilter: Int? = null,
    val dndCategories: Int? = null,
    val streamIndex: Int? = null,
    val detail: String? = null,
)

/** The OTDI.Audio logcat message, in the §13.5 order (`CUE_START cue=… route=… gain=0.50 fadeMs=2000 …`). */
fun AudioEvent.toLogLine(route: AudioRouteMode, cycle: Int?): String = logLine(
    event,
    "cue" to cue,
    "route" to route,
    "gain" to gain?.let { "%.2f".format(Locale.ROOT, it) },
    "fadeMs" to fadeMs,
    "routedType" to routedType,
    "focus" to focus,
    "dndFilter" to dndFilter,
    "dndCategories" to dndCategories,
    "streamIndex" to streamIndex,
    "cycle" to cycle,
    "detail" to detail,
)

/**
 * Plays rendered cues on the phone's built-in speaker (§7.4, §7.6, §7.7), or sends them to the watch on the
 * wrist-speaker route (playOnWatch). On the phone: a float
 * MODE_STREAM AudioTrack with the fade-in and G_target in the samples (the track volume stays 1.0), audio focus per
 * cue, a routed-device check, the DND and zero-volume rules and the volume-raised correction. It never changes a
 * stream volume.
 */
class CuePlayer(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * Plays [audio] once as [cue]; true when it played to the end, false when it was skipped or cut (already logged
     * to [log]). [calibratedIndex] is STREAM_MUSIC's index at session start (§7.7 step 5); [testCue] marks the Test
     * cue tap, which on the wrist-speaker route becomes the watch's calibration.
     */
    suspend fun play(
        cue: PromptCue,
        audio: WavAudio,
        config: AudioConfig,
        calibratedIndex: Int,
        testCue: Boolean = false,
        log: (AudioEvent) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        if (config.route == AudioRouteMode.WATCH_WRIST_SPEAKER) {
            return@withContext playOnWatch(cue, audio, config, testCue, log)
        }
        val index = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val (filter, categories) = notificationManager.dndState()
        log(AudioEvent("DND_STATE", cue, dndFilter = filter, dndCategories = categories, streamIndex = index))
        if (dndEffect(filter, categories) != DndEffect.PLAYS) {
            log(AudioEvent("CUE_SKIPPED", cue, streamIndex = index, detail = "dnd_muted"))
            return@withContext false
        }
        if (!streamOnSpeaker()) {
            log(AudioEvent("CUE_SKIPPED", cue, streamIndex = index, detail = "stream_device_not_speaker"))
            return@withContext false
        }
        if (index == 0) { // media volume off: the cue would "play" in silence, as on the owner's first night
            log(AudioEvent("VOLUME_ZERO", cue, streamIndex = index))
            return@withContext false
        }
        var gain = config.targetGain()
        if (index > calibratedIndex) {
            val factor = try {
                volumeRaisedCorrection(speakerDb(index), speakerDb(calibratedIndex))
            } catch (e: IllegalArgumentException) {
                log(AudioEvent("CUE_SKIPPED", cue, streamIndex = index, detail = "volume_db_unavailable"))
                return@withContext false
            }
            gain *= factor
            log(
                AudioEvent(
                    "VOLUME_RAISED", cue, gain = gain, streamIndex = index,
                    detail = "calibrated_index=$calibratedIndex;factor=$factor",
                ),
            )
        }
        val speaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker == null) {
            log(AudioEvent("CUE_SKIPPED", cue, gain = gain, streamIndex = index, detail = "no_builtin_speaker"))
            return@withContext false
        }
        // §7.7 table: the MAY_DUCK setting covers every cue but WAKE_PROMPT, which always takes GAIN_TRANSIENT.
        val focus = if (cue == PromptCue.WAKE_PROMPT) FocusMode.GAIN_TRANSIENT else config.focusMode
        val focusChange = AtomicInteger(0)
        val focusRequest = AudioFocusRequest.Builder(
            if (focus == FocusMode.GAIN_TRANSIENT_MAY_DUCK) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            },
        )
            .setAudioAttributes(ATTRIBUTES)
            .setOnAudioFocusChangeListener { change ->
                // §7.7: CAN_DUCK (a notification sound) is no loss: speech is never ducked, so the cue plays on.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    focusChange.compareAndSet(0, change)
                }
            }
            .build()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            log(AudioEvent("FOCUS_DENIED", cue, gain = gain, focus = focus, streamIndex = index))
            return@withContext false
        }
        try {
            Playback(cue, gain, config.fadeInMs, focus, index, speaker, focusChange, log).run(audio)
        } finally {
            audioManager.abandonAudioFocusRequest(focusRequest) // the same request object (§7.7)
        }
    }

    /**
     * The wrist speaker: the cue goes to the watch faded in and at G_target, and the watch's media volume sets the
     * loudness, never above the watch's last test cue. The phone's DND, volume and focus rules are about the phone
     * speaker, so they do not apply here.
     */
    private suspend fun playOnWatch(
        cue: PromptCue,
        audio: WavAudio,
        config: AudioConfig,
        testCue: Boolean,
        log: (AudioEvent) -> Unit,
    ): Boolean {
        val gain = config.targetGain()
        // gain is what played, as on the phone route: the watch scales a cue down when its volume was raised.
        fun event(name: String, detail: String, reply: WristCue.Reply? = null) = AudioEvent(
            name, cue, routedType = reply?.routedType?.takeIf { it >= 0 }, gain = gain * (reply?.factor ?: 1f),
            fadeMs = config.fadeInMs, streamIndex = reply?.streamIndex?.takeIf { it >= 0 }, detail = detail,
        )
        // A call (ringing, cellular, VoIP, screening) on the phone: the cue must not talk over it, as on the phone
        // route. No focus request: that would pause the user's own sleep sounds on the phone for a cue played elsewhere.
        if (audioManager.mode != AudioManager.MODE_NORMAL) {
            log(event("CUE_SKIPPED", "phone_in_call"))
            return false
        }
        val channels = Wearable.getChannelClient(context)
        val channel = try {
            openWatchChannel(channels)
        } catch (e: CancellationException) {
            log(event("CUE_END", "stopped"))
            throw e
        }
        if (channel == null) {
            log(event("CUE_SKIPPED", "watch_unreachable"))
            return false
        }
        val pcm = FadeEnvelope(audio.sampleRate, config.fadeInMs).applyTo(audio.samples, gain)
        val reply = try {
            coroutineScope {
                // A blocked stream call returns only once the channel closes, so the deadline (transfer plus play) and
                // a Stop both close it. A stopped cue still plays to its end on the watch.
                val watchdog = launch {
                    try {
                        delay(2 * pcm.size * 1000L / audio.sampleRate + WATCH_MARGIN_MS)
                    } finally {
                        channels.close(channel)
                    }
                }
                try {
                    WristCue.write(
                        channels.getOutputStream(channel).await(), pcm, audio.sampleRate, testCue,
                        earbuds = config.watchOutput == WatchOutput.EARBUDS,
                    )
                    WristCue.readReply(channels.getInputStream(channel).await()) { log(event("CUE_START", "watch")) }
                } finally {
                    watchdog.cancel()
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: ApiException) {
            null
        } catch (e: CancellationException) {
            log(event("CUE_END", "stopped"))
            throw e
        } finally {
            channels.close(channel)
        }
        val context = currentCoroutineContext()
        if (!context.isActive) { // the Stop's close ended the blocked call with an IOException
            log(event("CUE_END", "stopped"))
            context.ensureActive()
        }
        if (reply != null && reply.factor < 1f) log(event("VOLUME_RAISED", "watch;factor=${reply.factor}", reply))
        return when (reply?.status) {
            WristCue.PLAYED -> true.also { log(event("CUE_END", "watch", reply)) }
            WristCue.VOLUME_ZERO -> false.also { log(event("VOLUME_ZERO", "watch_media_volume_off", reply)) }
            WristCue.NOT_ON_SPEAKER -> false.also { log(event("CUE_SKIPPED", "watch_media_not_on_speaker", reply)) }
            null -> false.also { log(event("CUE_END", "watch_no_reply")) }
            else -> false.also { log(event("CUE_END", "watch_failed", reply)) }
        }
    }

    /**
     * Sends [audio] for the watch to keep under [name], not play: the spoken wake-up alarm, or a clip of the guided
     * back-to-sleep (GuideClip). True when the watch stored it.
     */
    suspend fun storeOnWatch(
        audio: WavAudio,
        name: String = WristCue.WAKE_VOICE,
    ): Boolean = withContext(Dispatchers.IO) {
        val channels = Wearable.getChannelClient(context)
        val channel = openWatchChannel(channels) ?: return@withContext false
        try {
            coroutineScope {
                val watchdog = launch {
                    try {
                        delay(WATCH_STORE_TIMEOUT_MS)
                    } finally {
                        channels.close(channel)
                    }
                }
                try {
                    val out = channels.getOutputStream(channel).await()
                    WristCue.write(out, audio.samples, audio.sampleRate, calibrate = false, storeAs = name)
                    WristCue.readReply(channels.getInputStream(channel).await()) {}?.status == WristCue.PLAYED
                } finally {
                    watchdog.cancel()
                }
            }
        } catch (e: IOException) {
            false
        } catch (e: ApiException) {
            false
        } finally {
            channels.close(channel)
        }
    }

    /**
     * The cue channel to the watch, retried for [WATCH_CONNECT_MS]: a Bluetooth gap of a few seconds should not cost
     * a cue (a lost WAKE_PROMPT aborts its cycle). Null when the watch stayed out of reach.
     */
    private suspend fun openWatchChannel(channels: ChannelClient): ChannelClient.Channel? {
        // Outside the timeout block: a channel that opens just as the timeout fires is still used (or closed on Stop).
        var opened: ChannelClient.Channel? = null
        try {
            withTimeoutOrNull(WATCH_CONNECT_MS) {
                while (opened == null) {
                    opened = watchNode()?.let { openChannel(channels, it.id) }
                    if (opened == null) delay(WATCH_RETRY_MS)
                }
            }
        } catch (e: CancellationException) {
            opened?.let { channels.close(it) }
            throw e
        }
        return opened
    }

    private suspend fun watchNode(): Node? = try {
        Wearable.getCapabilityClient(context)
            .getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes
            .let { nodes -> nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() }
    } catch (e: ApiException) {
        null
    }

    /** A channel that only arrives after a Stop or the timeout is closed, so the watch is never left waiting on it. */
    private suspend fun openChannel(channels: ChannelClient, nodeId: String): ChannelClient.Channel? {
        val task = channels.openChannel(nodeId, DataLayerPaths.WRIST_AUDIO_CHANNEL)
        return try {
            task.await()
        } catch (e: ApiException) {
            null
        } catch (e: CancellationException) {
            task.addOnSuccessListener { late -> late?.let { channels.close(it) } }
            throw e
        }
    }

    /**
     * True when media audio goes to the built-in speaker now. getStreamVolume(STREAM_MUSIC) returns the index of that
     * device, so only then is it the speaker's index that the §7.7 volume guard compares.
     */
    fun streamOnSpeaker(): Boolean = audioManager.getAudioDevicesForAttributes(ATTRIBUTES)
        .any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    private fun speakerDb(index: Int) =
        audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)

    /** One cue on one track, after the DND, volume and focus checks passed. */
    private class Playback(
        val cue: PromptCue,
        val gain: Float,
        val fadeInMs: Int,
        val focus: FocusMode,
        val index: Int,
        val speaker: AudioDeviceInfo,
        val focusChange: AtomicInteger,
        val log: (AudioEvent) -> Unit,
    ) {
        private var routeVerified = false
        private var startedAtMs = 0L

        suspend fun run(audio: WavAudio): Boolean {
            val track = try {
                buildTrack(audio.sampleRate)
            } catch (e: RuntimeException) { // the builder's UnsupportedOperationException or IllegalArgumentException
                log(event("CUE_SKIPPED", detail = "track_unavailable"))
                return false
            }
            try {
                track.setPreferredDevice(speaker)
                val pcm = FadeEnvelope(audio.sampleRate, fadeInMs).applyTo(audio.samples, gain)
                // Trailing silence carries the last cue samples through the mixer, so the head position reaches them.
                val out = pcm.copyOf(pcm.size + maxOf(track.bufferSizeInFrames, audio.sampleRate / 4))
                track.play()
                startedAtMs = SystemClock.elapsedRealtime()
                // Non-blocking writes keep the cue cancellable and bounded: the scheduler always hears back by then.
                val deadline = startedAtMs + out.size * 1000L / audio.sampleRate + DEADLINE_MARGIN_MS
                var written = 0
                while (written < out.size) {
                    if (interrupted(track)) return false
                    val n = track.write(out, written, out.size - written, AudioTrack.WRITE_NON_BLOCKING)
                    if (n < 0) {
                        log(event("CUE_END", detail = "write_error=$n"))
                        return false
                    }
                    written += n
                    if (written < out.size) {
                        if (SystemClock.elapsedRealtime() > deadline) {
                            log(event("CUE_END", detail = "write_timeout"))
                            return false
                        }
                        delay(POLL_MS)
                    }
                }
                var detail: String? = null
                while (track.playbackHeadPosition < pcm.size) {
                    if (interrupted(track)) return false
                    if (SystemClock.elapsedRealtime() > deadline) {
                        detail = "drain_timeout"
                        break
                    }
                    delay(POLL_MS)
                }
                if (!routeVerified) {
                    log(event("ROUTE_MISMATCH", detail = "not_routed"))
                    return false
                }
                log(event("CUE_END", routedType = speaker.type, detail = detail))
                return true
            } catch (e: CancellationException) { // before IllegalStateException, its superclass
                log(event("CUE_END", detail = "stopped"))
                throw e
            } catch (e: IllegalStateException) {
                log(event("CUE_END", detail = "track_error"))
                return false
            } finally {
                track.release() // stops at once; after a full cue only the silent tail is cut
            }
        }

        /** True when focus was lost or the track is not (or no longer) on the speaker; the track is then silenced. */
        private fun interrupted(track: AudioTrack): Boolean {
            val loss = focusChange.get()
            if (loss != 0) {
                track.pause()
                track.flush()
                log(event("FOCUS_LOSS", focus = focus, detail = "focus_change=$loss"))
                return true
            }
            // §7.6: setPreferredDevice is only a hint, so the routed device is checked while the cue plays.
            val routed = track.routedDevice
            if (routed != null && routed.id == speaker.id) {
                if (!routeVerified) {
                    routeVerified = true
                    log(event("CUE_START", routedType = routed.type, focus = focus))
                }
                return false
            }
            if (routed == null && (routeVerified || SystemClock.elapsedRealtime() - startedAtMs < ROUTE_CHECK_MS)) {
                return false
            }
            track.pause()
            track.flush()
            log(
                event(
                    "ROUTE_MISMATCH",
                    routedType = routed?.type,
                    detail = if (routed == null) "not_routed" else "routed_id=${routed.id};speaker_id=${speaker.id}",
                ),
            )
            return true
        }

        private fun event(name: String, routedType: Int? = null, focus: FocusMode? = null, detail: String? = null) =
            AudioEvent(
                name, cue, routedType = routedType, gain = gain, fadeMs = fadeInMs, focus = focus,
                streamIndex = index, detail = detail,
            )
    }

    private companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        const val ROUTE_CHECK_MS = 1000L // how long after play() the routed device may still be unknown
        const val DEADLINE_MARGIN_MS = 2000L
        const val POLL_MS = 20L
        const val WATCH_MARGIN_MS = 20_000L
        const val WATCH_CONNECT_MS = 30_000L
        const val WATCH_RETRY_MS = 3_000L
        const val WATCH_STORE_TIMEOUT_MS = 60_000L

        // A 250 ms buffer leaves the 20 ms polling writer a wide margin against underruns.
        fun buildTrack(sampleRate: Int): AudioTrack = AudioTrack.Builder()
            .setAudioAttributes(ATTRIBUTES)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
            .setBufferSizeInBytes(
                maxOf(
                    AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
                    sampleRate / 4 * Float.SIZE_BYTES,
                ),
            )
            .build()
    }
}
