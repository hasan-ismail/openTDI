package com.opentdi.wear.alarm

import com.opentdi.shared.model.LateCueMode
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.opentdi.shared.audio.lucidCue
import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_MIN_S
import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_WINDOW_S
import com.opentdi.shared.model.AlarmConfig
import kotlin.random.Random

/**
 * Late-night lucidity cues after WBTB (owner, 2026-10-28; docs/references/lucid_induction.md §5). During the WBTB
 * awake time the cue is trained: it plays [TRAINING_CUES] times while the watch says what it means. From
 * lateCueDelayMin after the back-to-sleep buzz, once the sleeper has had no major movement (§8.10) for [STILL_MS], one
 * soft cue plays every 20–30 s, at most [CUES_PER_BOUT] per still stretch; a movement pauses it until 5 min of
 * stillness again. On a no-cue night of the EXPERIMENT the same moments are logged as WOULD_CUE and nothing plays,
 * so the morning reports can compare the two kinds of night. All timings GUESSED from the studies in §5.
 */
class LateCues(
    private val context: Context,
    private val config: () -> AlarmConfig,
    private val log: (event: String, stillness: Float?, hr: Float?, detail: String?) -> Unit,
) {
    /** Tonight's arm: always cues when ON; EXPERIMENT tosses a coin at arming, unseen (§5.4). */
    var cueNight = true
        private set
    private val playing = mutableSetOf<AudioTrack>() // held until done: an unreferenced track may be collected
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val cue = lucidCue(SAMPLE_RATE)
    private val recentMoving = ArrayDeque<Boolean>()
    private var stillSinceMs: Long? = null
    private var startAtMs: Long? = null
    private var nextCueAtMs = 0L
    private var boutCues = 0
    private var trainingLeft = 0
    private var nextTrainingAtMs = 0L

    /** The WBTB alarm was stopped: train the cue a few times while the sleeper is up. */
    fun train(nowMs: Long) {
        trainingLeft = TRAINING_CUES
        nextTrainingAtMs = nowMs + TRAINING_FIRST_MS
    }

    /** The back-to-sleep buzz: cues may start lateCueDelayMin from now. */
    fun arm(nowMs: Long) {
        val c = config()
        cueNight = c.lateCues == LateCueMode.ON || (c.lateCues == LateCueMode.EXPERIMENT && Random.nextBoolean())
        startAtMs = nowMs + c.lateCueDelayMin * 60_000L
        log("ARMED", null, null, "night=${if (cueNight) "CUE" else "NO_CUE"};start_in_min=${c.lateCueDelayMin}")
    }

    /** The wake-up alarm: no lucidity cue during the ring, the dream replay or the report. */
    fun disarm() {
        if (startAtMs == null && trainingLeft == 0) return
        startAtMs = null
        trainingLeft = 0
        log("ENDED", null, null, null)
    }

    fun close() {
        playing.forEach { it.release() }
        playing.clear()
    }

    fun tick(nowMs: Long, stillness: Float?, hr: Float?) {
        if (trainingLeft > 0 && nowMs >= nextTrainingAtMs) {
            play(TRAINING_VOLUME)
            trainingLeft--
            nextTrainingAtMs = nowMs + TRAINING_GAP_MS
            log("TRAINING_CUE", stillness, hr, null)
        }
        recentMoving.addLast(stillness == null || stillness < STILL_THRESHOLD)
        if (recentMoving.size > MAJOR_MOVEMENT_WINDOW_S) recentMoving.removeFirst()
        if (recentMoving.count { it } >= MAJOR_MOVEMENT_MIN_S) {
            if (stillSinceMs != null && boutCues > 0) log("PAUSE_MOVEMENT", stillness, hr, "bout_cues=$boutCues")
            stillSinceMs = null
            boutCues = 0
        } else if (stillSinceMs == null) {
            stillSinceMs = nowMs
        }
        val start = startAtMs ?: return
        val still = stillSinceMs ?: return
        if (nowMs < start || nowMs - still < STILL_MS || boutCues >= CUES_PER_BOUT || nowMs < nextCueAtMs) return
        boutCues++
        nextCueAtMs = nowMs + Random.nextLong(MIN_GAP_MS, MAX_GAP_MS + 1)
        if (cueNight) play(config().lateCueVolume)
        log(if (cueNight) "CUE" else "WOULD_CUE", stillness, hr, "bout_cue=$boutCues")
    }

    /**
     * One cue at [volume] of the media stream, on the watch speaker or, when chosen ([earbuds], by default the choice
     * the phone sent), the earbuds paired with the watch; the static track frees itself when done.
     */
    fun play(volume: Float, earbuds: Boolean = WatchOutputs.earbudsChosen(context)) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(cue.size * Float.SIZE_BYTES)
            .build()
        track.write(cue, 0, cue.size, AudioTrack.WRITE_BLOCKING)
        WatchOutputs.pick(audioManager, earbuds)?.let { track.setPreferredDevice(it) }
        track.setVolume(volume)
        track.setNotificationMarkerPosition(cue.size)
        playing += track
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) {
                t.release()
                playing -= t
            }

            override fun onPeriodicNotification(t: AudioTrack) = Unit
        })
        track.play()
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val STILL_THRESHOLD = 0.8f // the StillnessGate's default (MIT_STANDARD) still second
        const val STILL_MS = 5 * 60_000L // home TLR study: cues resume 5 min after movement stops
        const val CUES_PER_BOUT = 10 // the Netherlands lab's cap per REM period
        const val MIN_GAP_MS = 20_000L
        const val MAX_GAP_MS = 30_000L
        const val TRAINING_CUES = 4
        const val TRAINING_FIRST_MS = 20_000L
        const val TRAINING_GAP_MS = 30_000L
        const val TRAINING_VOLUME = 0.4f
    }
}
