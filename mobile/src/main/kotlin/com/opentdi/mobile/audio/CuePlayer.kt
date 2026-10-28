package com.opentdi.mobile.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import com.opentdi.mobile.logLine
import com.opentdi.shared.audio.FadeEnvelope
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.targetGain
import com.opentdi.shared.audio.volumeRaisedCorrection
import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.model.FocusMode
import com.opentdi.shared.model.PromptCue
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
 * Plays rendered cues on the phone's built-in speaker, the only Phase 1 route (§7.4, §7.6, §7.7): a float
 * MODE_STREAM AudioTrack with the fade-in and G_target in the samples (the track volume stays 1.0), audio focus per
 * cue, a routed-device check, the DND rule and the volume-raised correction. It never changes a stream volume.
 */
class CuePlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * Plays [audio] once as [cue]; true when it played to the end, false when it was skipped or cut (already logged
     * to [log]). [calibratedIndex] is STREAM_MUSIC's index at session start (§7.7 step 5).
     */
    suspend fun play(
        cue: PromptCue,
        audio: WavAudio,
        config: AudioConfig,
        calibratedIndex: Int,
        log: (AudioEvent) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
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
