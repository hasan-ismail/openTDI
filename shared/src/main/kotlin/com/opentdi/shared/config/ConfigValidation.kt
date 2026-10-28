package com.opentdi.shared.config

import com.opentdi.shared.config.StructuralConstants.nEdaMin
import com.opentdi.shared.config.StructuralConstants.nHrMin
import com.opentdi.shared.config.StructuralConstants.nStillMin
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.LateCueMode
import com.opentdi.shared.model.NEURAL_SPEAKERS
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SimulationMode

/** Every range of model/SessionConfig.kt and the cross-rules of §8.14; empty = valid. Each message names its field. */
fun SessionConfig.validate(): List<String> {
    val errors = mutableListOf<String>()
    fun rule(ok: Boolean, message: () -> String) {
        if (!ok) errors += message()
    }
    fun int(field: String, value: Int, range: IntRange, step: Int = 1) {
        rule(value in range) { "$field = $value is outside ${range.first}..${range.last}" }
        rule(value !in range || (value - range.first) % step == 0) {
            "$field = $value is not ${range.first} + a multiple of $step"
        }
    }
    fun float(field: String, value: Float?, range: ClosedFloatingPointRange<Float>) =
        rule(value == null || value in range) { "$field = $value is outside ${range.start}..${range.endInclusive}" }

    rule(theme.length in 1..60) { "theme must be 1..60 characters, is ${theme.length}" }
    int("maxCycles", maxCycles, 1..10)
    int("sessionMaxMin", sessionMaxMin, 15..180)
    if (countdownS == 15) {
        rule(simulation == SimulationMode.FAST_HYPNAGOGIA) {
            "countdownS = 15 is allowed only with simulation = FAST_HYPNAGOGIA"
        }
    } else {
        int("countdownS", countdownS, 60..300, step = 15)
    }
    rule(sessionMaxMin * 60L >= 2L * detection.baselineWindowS + countdownS) {
        "sessionMaxMin = $sessionMaxMin min is shorter than 2 x detection.baselineWindowS + countdownS " +
            "= ${2 * detection.baselineWindowS + countdownS} s"
    }

    with(detection) {
        int("detection.baselineWindowS", baselineWindowS, 60..600, step = 30)
        float("detection.hrDropFraction", hrDropFraction, 0.04f..0.12f)
        float("detection.rmssdRiseFraction", rmssdRiseFraction, 0.05f..0.50f)
        float("detection.stillThreshold", stillThreshold, 0.60f..0.95f)
        holdS?.let { int("detection.holdS", it, 5..30) }
        holdShortS?.let { int("detection.holdShortS", it, 3..30) }
        int("detection.hrMaWindowS", hrMaWindowS, 5..60)
        int("detection.stillWindowS", stillWindowS, 10..120)
        int("detection.edaSlopeWindowS", edaSlopeWindowS, 15..120)
        int("detection.bonusLookbackS", bonusLookbackS, 30..300)
        int("detection.refractoryS", refractoryS, 0..600)
        int("detection.missedCycleGraceS", missedCycleGraceS, 5..60)
        int("detection.cycleStageTimeoutS", cycleStageTimeoutS, 90..600)
        int("detection.hrLostS", hrLostS, 60..900)
        int("detection.accelLostS", accelLostS, 10..300)
        int("detection.hrHoldMaxS", hrHoldMaxS, 1..15)
        int("detection.rmssdWindowS", rmssdWindowS, 30..300)
        float("detection.priorRestingHrBpm", priorRestingHrBpm, 35f..120f)
        float("detection.priorHrWeight", priorHrWeight, 0f..0.5f)

        val resolved = thresholds()
        rule(resolved.holdS <= stillWindowS) {
            "detection.holdS = ${resolved.holdS} (resolved) exceeds detection.stillWindowS = $stillWindowS"
        }
        rule(resolved.holdShortS <= resolved.holdS) {
            "detection.holdShortS = ${resolved.holdShortS} (resolved) exceeds detection.holdS = ${resolved.holdS}"
        }
        rule(nHrMin(hrMaWindowS) <= hrMaWindowS) { "detection.hrMaWindowS = $hrMaWindowS is below its minimum count" }
        rule(nStillMin(stillWindowS) <= stillWindowS) {
            "detection.stillWindowS = $stillWindowS is below its minimum count"
        }
        rule(nEdaMin(edaSlopeWindowS) <= edaSlopeWindowS) {
            "detection.edaSlopeWindowS = $edaSlopeWindowS is below its minimum count"
        }
        val longestCycleMs = recording.maxMs + recording.answerMaxMs + 60_000L
        rule(cycleStageTimeoutS * 1000L >= longestCycleMs) {
            "detection.cycleStageTimeoutS = $cycleStageTimeoutS s is shorter than recording.maxMs + " +
                "recording.answerMaxMs + 60 s = $longestCycleMs ms"
        }
        rule(missedCycleGraceS < cycleStageTimeoutS) {
            "detection.missedCycleGraceS = $missedCycleGraceS is not below " +
                "detection.cycleStageTimeoutS = $cycleStageTimeoutS"
        }
    }

    with(timed) {
        int("timed.firstSleepMinMin", firstSleepMinMin, 1..60)
        int("timed.firstSleepMaxMin", firstSleepMaxMin, 1..90)
        rule(firstSleepMinMin <= firstSleepMaxMin) {
            "timed.firstSleepMaxMin = $firstSleepMaxMin is below timed.firstSleepMinMin = $firstSleepMinMin"
        }
        int("timed.betweenSleepS", betweenSleepS, 60..1200)
        int("timed.phase1ReportWindowS", phase1ReportWindowS, 30..90)
        int("timed.watchLinkTimeoutS", watchLinkTimeoutS, 30..600)
        int("timed.stillForS", stillForS, 60..1800)
    }

    with(audio) {
        float("audio.userGain", userGain, 0f..1f)
        routeCapGain.forEach { (r, cap) -> float("audio.routeCapGain.$r", cap, 0f..1f) }
        rule(route in routeCapGain) { "audio.routeCapGain.$route is missing for the selected audio.route" }
        int("audio.fadeInMs", fadeInMs, 0..5000)
        int("audio.carrierLevelDbfs", carrierLevelDbfs, -90..-50, step = 5)
        int("audio.carrierLeadS", carrierLeadS, 0..60)
        int("audio.endChimeCount", endChimeCount, 1..3)
        float("audio.ttsSpeechRate", ttsSpeechRate, 0.5f..1.2f)
        int("audio.phrasePauseMs", phrasePauseMs, 0..2000)
        cueTexts.forEach { (cue, text) ->
            rule(text.length <= 300) { "audio.cueTexts.$cue must be at most 300 characters, is ${text.length}" }
        }
        rule(neuralSpeaker in NEURAL_SPEAKERS) { "audio.neuralSpeaker = $neuralSpeaker is not a voice of the model" }
    }

    with(recording) {
        float("recording.marginDb", marginDb, 3f..20f)
        int("recording.hangoverMs", hangoverMs, 1000..10_000)
        int("recording.noSpeechTimeoutMs", noSpeechTimeoutMs, 5000..60_000)
        int("recording.maxMs", maxMs, 10_000..180_000)
        int("recording.seedMs", seedMs, 300..500)
    }

    int("link.telemetryFlushS", link.telemetryFlushS, 1..30)
    int("link.sdkFlushS", link.sdkFlushS, 1..30)

    with(sensors) {
        rule(!ppgRawEnabled || ppgChannels.isNotEmpty()) {
            "sensors.ppgChannels must name a channel while sensors.ppgRawEnabled"
        }
        int("sensors.spo2IntervalMin", spo2IntervalMin, 5..120)
        int("sensors.spo2TimeoutS", spo2TimeoutS, 30..120)
    }

    with(alarms) {
        int("alarms.wakeAtMin", wakeAtMin, 0..1439)
        int("alarms.wbtbAtMin", wbtbAtMin, 0..1439)
        int("alarms.wbtbAwakeMin", wbtbAwakeMin, 5..60)
        int("alarms.rampS", rampS, 0..300)
        int("alarms.maxRingMin", maxRingMin, 1..30)
        int("alarms.maxSnoozes", maxSnoozes, 0..5)
        int("alarms.snoozeMin", snoozeMin, 1..30)
        rule(sleeperName.length <= 30) { "alarms.sleeperName is longer than 30 characters" }
        int("alarms.lateCueAfterOnsetMin", lateCueAfterOnsetMin, 10..120)
        float("alarms.lateCueVolume", lateCueVolume, 0.02f..1f)
        rule(lateCues == LateCueMode.OFF || wbtbEnabled) { "alarms.lateCues need the WBTB alarm (alarms.wbtbEnabled)" }
    }
    return errors
}

/**
 * Non-fatal issues. USER_GAIN_CLAMPED: userGain above the route's cap is clamped at playback (§7.7 step 3).
 * WAKE_SOON_AFTER_WBTB: [wakeTooSoonAfterWbtb].
 */
fun SessionConfig.warnings(): List<String> = buildList {
    val cap = audio.routeCapGain[audio.route]
    if (cap != null && audio.userGain > cap) {
        add(
            "USER_GAIN_CLAMPED: audio.userGain = ${audio.userGain} exceeds audio.routeCapGain.${audio.route} = $cap; " +
                "cues play at $cap",
        )
    }
    if (alarms.wakeTooSoonAfterWbtb()) {
        add(
            "WAKE_SOON_AFTER_WBTB: alarms.wakeAtMin = ${alarms.wakeAtMin} is less than " +
                "$MIN_WAKE_AFTER_BACK_TO_BED_MIN min after alarms.wbtbAtMin = ${alarms.wbtbAtMin} + " +
                "alarms.wbtbAwakeMin = ${alarms.wbtbAwakeMin}",
        )
    }
}

/**
 * The least time from going back to bed after WBTB (wbtbAtMin + wbtbAwakeMin) to the wake-up (owner, 2026-10-28: "do
 * earlier wbtb"). GUESSED from sleep_and_dreams.md: after a WBTB break REM came back about 75 min after going back to
 * bed (31.5 min to fall asleep + 42.1 min, §1 [12]), and late-night REM episodes run up to 30–40 min (§1 [4]).
 */
const val MIN_WAKE_AFTER_BACK_TO_BED_MIN = 120

/**
 * Both alarms on, and the wake-up comes less than wbtbAwakeMin + [MIN_WAKE_AFTER_BACK_TO_BED_MIN] after the WBTB alarm
 * (minutes of day, across midnight). A wake-up inside the time awake counts too.
 */
fun AlarmConfig.wakeTooSoonAfterWbtb(): Boolean = wakeEnabled && wbtbEnabled &&
    Math.floorMod(wakeAtMin - wbtbAtMin, MINUTES_PER_DAY) < wbtbAwakeMin + MIN_WAKE_AFTER_BACK_TO_BED_MIN

/** The WBTB alarm time that leaves exactly [MIN_WAKE_AFTER_BACK_TO_BED_MIN] from back to bed to the wake-up. */
fun AlarmConfig.wbtbAtMinBeforeWake(): Int =
    Math.floorMod(wakeAtMin - wbtbAwakeMin - MIN_WAKE_AFTER_BACK_TO_BED_MIN, MINUTES_PER_DAY)

private const val MINUTES_PER_DAY = 24 * 60
