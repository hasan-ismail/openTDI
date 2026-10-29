package com.opentdi.wear.alarm

import com.opentdi.shared.model.LateCueMode
import android.content.Context
import androidx.core.content.edit
import com.opentdi.shared.audio.lucidCue
import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_MIN_S
import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_WINDOW_S
import com.opentdi.shared.config.StructuralConstants.RE_ONSET_MS
import com.opentdi.shared.model.AlarmConfig
import kotlin.random.Random

/**
 * Late-night lucidity cues after WBTB (owner, 2026-10-28; docs/references/lucid_induction.md §5). During the WBTB
 * awake time the cue is trained: it plays [TRAINING_CUES] times while the watch says what it means; with the guided
 * back-to-sleep the guide trains it in bed instead ([GuideRoutine]), and [arm] comes at the guide's end, not at the
 * buzz. After the arm the first still stretch of [RE_ONSET_MS] is taken as falling back asleep (RE_ONSET, logged with
 * its latency); from lateCueAfterOnsetMin after it, once the sleeper has had no major movement (§8.10) for
 * [STILL_MS], one soft cue plays every 20–30 s, at most [CUES_PER_BOUT] per still stretch; a movement pauses it until
 * 5 min of stillness again. REM came 42.1 ± 24.7 min after falling back asleep, not after the buzz (sleep_and_dreams.md §1
 * [12]). On a no-cue night of the EXPERIMENT the same moments are logged as WOULD_CUE and nothing plays, so the
 * morning reports can compare the two kinds of night. All timings GUESSED from the studies in §5.
 */
class LateCues(
    private val context: Context,
    private val config: () -> AlarmConfig,
    private val log: (event: String, stillness: Float?, hr: Float?, detail: String?) -> Unit,
) {
    /** Tonight's arm: always cues when ON; EXPERIMENT randomises in pairs of nights at arming, unseen (§5.4). */
    var cueNight = true
        private set
    private val sound = OneShots(context)
    private val cue = lucidCue(SAMPLE_RATE)
    private val recentMoving = ArrayDeque<Boolean>()
    private var stillSinceMs: Long? = null
    private var armedAtMs: Long? = null // the buzz, while waiting for the sleeper to fall back asleep
    private var startAtMs: Long? = null
    private var endAtMs: Long? = null // the cues end by themselves, with or without a wake-up alarm
    private var nextCueAtMs = 0L
    private var boutCues = 0
    private var trainingLeft = 0
    private var nextTrainingAtMs = 0L

    /** The WBTB alarm was stopped: train the cue a few times while the sleeper is up. */
    fun train(nowMs: Long) {
        trainingLeft = TRAINING_CUES
        nextTrainingAtMs = nowMs + TRAINING_FIRST_MS
    }

    /**
     * The back-to-sleep buzz, or the guide's end or stop ([nowMs] the buzz when the guide was offered but never
     * started): cues may start lateCueAfterOnsetMin after the sleeper falls back asleep. In the
     * EXPERIMENT the first night of a pair tosses a coin and the second gets the other arm, so season and slow drift
     * cannot load one side (sleep_and_dreams.md §3 [49]); the pending arm stays in private preferences the sleeper
     * never sees, and lapses after [PAIR_MAX_MS] so a pair cannot span seasons.
     */
    fun arm(nowMs: Long) {
        val c = config()
        var pair = ""
        if (c.lateCues == LateCueMode.EXPERIMENT) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_SECOND_ARM) && nowMs - prefs.getLong(KEY_FIRST_MS, 0) <= PAIR_MAX_MS) {
                cueNight = prefs.getBoolean(KEY_SECOND_ARM, true)
                prefs.edit {
                    remove(KEY_SECOND_ARM)
                    remove(KEY_FIRST_MS)
                }
                pair = ";pair=2/2"
            } else {
                cueNight = Random.nextBoolean()
                prefs.edit {
                    putBoolean(KEY_SECOND_ARM, !cueNight)
                    putLong(KEY_FIRST_MS, nowMs)
                }
                pair = ";pair=1/2"
            }
        } else {
            cueNight = c.lateCues == LateCueMode.ON
        }
        armedAtMs = nowMs
        startAtMs = null
        endAtMs = nowMs + LATE_CUE_WINDOW_MS
        val night = if (cueNight) "CUE" else "NO_CUE"
        log("ARMED", null, null, "night=$night$pair;start_after_onset_min=${c.lateCueAfterOnsetMin}")
    }

    /** The wake-up alarm, or [LATE_CUE_WINDOW_MS] after the arm: no lucidity cue while the sleeper is up. */
    fun disarm() {
        endAtMs = null
        if (startAtMs == null && armedAtMs == null && trainingLeft == 0) return
        armedAtMs = null
        startAtMs = null
        trainingLeft = 0
        log("ENDED", null, null, null)
    }

    fun close() = sound.stop()

    fun tick(nowMs: Long, stillness: Float?, hr: Float?) {
        if (endAtMs?.let { nowMs >= it } == true) disarm()
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
        armedAtMs?.let { armed ->
            val onset = reOnsetAt(armed, stillSinceMs, nowMs) ?: return@let
            armedAtMs = null
            startAtMs = onset + config().lateCueAfterOnsetMin * 60_000L
            // Asleep within 10 min went with more lucid nights (sleep_and_dreams.md §5 [67]): worth keeping.
            log("RE_ONSET", stillness, hr, "latency_min=${(onset - armed) / 60_000}")
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
     * the phone sent), the earbuds paired with the watch.
     */
    fun play(volume: Float, earbuds: Boolean = WatchOutputs.earbudsChosen(context)) =
        sound.play(cue, SAMPLE_RATE, volume, earbuds)

    private companion object {
        const val PREFS = "late_cues"
        const val KEY_SECOND_ARM = "experiment_second_arm" // the pair's second night: true = cue
        const val KEY_FIRST_MS = "experiment_first_ms" // when the pair's first night was armed
        const val PAIR_MAX_MS = 7 * 24 * 3_600_000L // GUESSED: an older pending arm is dropped and a new pair starts
        const val SAMPLE_RATE = 24_000
        const val STILL_THRESHOLD = 0.8f // the StillnessGate's default (MIT_STANDARD) still second
        const val STILL_MS = 5 * 60_000L // home TLR study: cues resume 5 min after movement stops
        const val CUES_PER_BOUT = 10 // the Netherlands lab's cap per REM period
        const val MIN_GAP_MS = 20_000L
        const val MAX_GAP_MS = 30_000L
        const val TRAINING_CUES = 4
        const val TRAINING_FIRST_MS = 20_000L
        const val TRAINING_GAP_MS = 30_000L

        // GUESSED: falling back asleep, the 40 min wait and the late REM periods before a usual wake-up. Ends the cues
        // on a night without a wake-up alarm, or when the sleeper is up for good.
        const val LATE_CUE_WINDOW_MS = 4 * 3_600_000L
    }
}

/** GUESSED: the cue while awake, in training or the guide; louder than the night's lateCueVolume. */
internal const val TRAINING_VOLUME = 0.4f

/**
 * The estimated re-onset once the still stretch that began at [stillSinceMs] (null while moving) has lasted
 * [RE_ONSET_MS] from the buzz at [armedAtMs] or later; stillness before the buzz counts from the buzz. Null until then.
 */
internal fun reOnsetAt(armedAtMs: Long, stillSinceMs: Long?, nowMs: Long): Long? =
    stillSinceMs?.let { maxOf(it, armedAtMs) }?.takeIf { nowMs - it >= RE_ONSET_MS }
