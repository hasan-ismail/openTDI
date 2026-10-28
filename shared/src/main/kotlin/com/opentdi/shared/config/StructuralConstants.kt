package com.opentdi.shared.config

/**
 * The structural GUESSED constants of §8.15: fixed in code, not config values. [configRows] logs them as `const.*`
 * rows in w_config.csv / p_config.csv; changing one needs a code change plus a replay re-run (§12.6).
 */
object StructuralConstants {
    // §8.6 minimum counts: a fixed fraction of each window (integer ceil), so every allowed window can be satisfied.
    fun nHrMin(hrMaWindowS: Int): Int = (2 * hrMaWindowS + 2) / 3
    fun nStillMin(stillWindowS: Int): Int = (5 * stillWindowS + 5) / 6
    fun nEdaMin(edaSlopeWindowS: Int): Int = (2 * edaSlopeWindowS + 2) / 3

    const val GATE_MAX_MOVING_S = 2 // §8.6: a jerk of <= 500 ms spans <= 2 s
    const val MAJOR_MOVEMENT_MIN_S = 3 // §8.10: moving seconds ...
    const val MAJOR_MOVEMENT_WINDOW_S = 5 // ... within the last 5 s
    const val HOLD_RESET_GAP_MS = 1500L // §8.3: a longer packet gap resets hold
    const val ACCEL_MIN_SAMPLES_PER_S = 20 // §8.1: of the nominal 25 Hz
    const val GRAVITY_MODE_STILL_S = 10 // §8.1: calm seconds (stillness >= moveStillness)
    const val WITH_GRAVITY_MIN_G = 0.8f
    const val WITH_GRAVITY_MAX_G = 1.2f
    const val LINEAR_ONLY_MAX_G = 0.3f
    const val STILL_LEVEL = 0.8f // §8.1: a "still" second for jerk, limp and the gravity mode
    const val LIMP_REF_CLEAR_BELOW = 0.5f // §8.1
    const val BASELINE_MIN_FRACTION = 0.5f // §8.4: motion-free, HR and EDA frames >= 0.5 W
    const val BASELINE_RMSSD_MIN_N = 30
    const val BASELINE_MAX_EXTENSIONS = 1 // §8.4: once, to 2 W
    const val HR_SD_FLOOR_BPM = 1.0f
    const val EDA_SD_FLOOR_US = 0.005f
    const val PRIOR_HR_MIN_FRACTION = 0.85f // §8.5: a prior below 0.85 μ_live is ignored
    const val HR_MIN_BPM = 30 // §8.2
    const val HR_MAX_BPM = 220
    const val IBI_MIN_MS = 300
    const val IBI_MAX_MS = 2000
    const val IBI_MAX_DEVIATION = 0.20f // §8.2: from the median of the last IBI_MEDIAN_OF accepted IBIs ...
    const val IBI_MEDIAN_OF = 7
    const val IBI_DEVIATION_CHECK_FROM = 3 // ... once 3 IBIs were accepted
    const val RMSSD_MIN_PAIRS = 20
    const val EDA_MIN_US = 0.05f
    const val EDA_SPIKE_FLOOR_US = 0.02f
    const val EDA_SPIKE_SIGMA = 1.5f
    const val CONFIDENCE_W_STILL = 0.30f // §8.9
    const val CONFIDENCE_W_AUTONOMIC = 0.40f
    const val CONFIDENCE_W_HOLD = 0.15f
    const val CONFIDENCE_W_BONUS = 0.15f
    const val CONFIDENCE_BONUS_EACH = 0.5f
    const val COMMAND_RETRIES = 3 // §10.2: 3x at 2 s if no reply in 5 s
    const val COMMAND_RETRY_INTERVAL_MS = 2000L
    const val COMMAND_REPLY_TIMEOUT_MS = 5000L
    val TRIGGER_ATTEMPT_OFFSETS_MS = listOf(0L, 2000L, 4000L, 8000L) // §10.2
    const val TRIGGER_REPLY_TIMEOUT_MS = 2000L
    const val CUE_PEAK_DBFS = -3f // §7.3
    const val PHRASE_SILENCE_DBFS = -50f // §7.3: a TTS phrase's leading and trailing samples below this are trimmed
    const val SPEECH_LEAD_FADE_FRACTION = 0.75f // §7.3: a cue's speech enters this far into the §7.4 fade (−15 dB)

    fun configRows(): List<Pair<String, String>> = listOf(
        "const.N_HR_MIN_FRACTION" to "2/3",
        "const.N_STILL_MIN_FRACTION" to "5/6",
        "const.N_EDA_MIN_FRACTION" to "2/3",
        "const.GATE_MAX_MOVING_S" to "$GATE_MAX_MOVING_S",
        "const.MAJOR_MOVEMENT_MIN_S" to "$MAJOR_MOVEMENT_MIN_S",
        "const.MAJOR_MOVEMENT_WINDOW_S" to "$MAJOR_MOVEMENT_WINDOW_S",
        "const.HOLD_RESET_GAP_MS" to "$HOLD_RESET_GAP_MS",
        "const.ACCEL_MIN_SAMPLES_PER_S" to "$ACCEL_MIN_SAMPLES_PER_S",
        "const.GRAVITY_MODE_STILL_S" to "$GRAVITY_MODE_STILL_S",
        "const.WITH_GRAVITY_MIN_G" to "$WITH_GRAVITY_MIN_G",
        "const.WITH_GRAVITY_MAX_G" to "$WITH_GRAVITY_MAX_G",
        "const.LINEAR_ONLY_MAX_G" to "$LINEAR_ONLY_MAX_G",
        "const.STILL_LEVEL" to "$STILL_LEVEL",
        "const.LIMP_REF_CLEAR_BELOW" to "$LIMP_REF_CLEAR_BELOW",
        "const.BASELINE_MIN_FRACTION" to "$BASELINE_MIN_FRACTION",
        "const.BASELINE_RMSSD_MIN_N" to "$BASELINE_RMSSD_MIN_N",
        "const.BASELINE_MAX_EXTENSIONS" to "$BASELINE_MAX_EXTENSIONS",
        "const.HR_SD_FLOOR_BPM" to "$HR_SD_FLOOR_BPM",
        "const.EDA_SD_FLOOR_US" to "$EDA_SD_FLOOR_US",
        "const.PRIOR_HR_MIN_FRACTION" to "$PRIOR_HR_MIN_FRACTION",
        "const.HR_MIN_BPM" to "$HR_MIN_BPM",
        "const.HR_MAX_BPM" to "$HR_MAX_BPM",
        "const.IBI_MIN_MS" to "$IBI_MIN_MS",
        "const.IBI_MAX_MS" to "$IBI_MAX_MS",
        "const.IBI_MAX_DEVIATION" to "$IBI_MAX_DEVIATION",
        "const.IBI_MEDIAN_OF" to "$IBI_MEDIAN_OF",
        "const.IBI_DEVIATION_CHECK_FROM" to "$IBI_DEVIATION_CHECK_FROM",
        "const.RMSSD_MIN_PAIRS" to "$RMSSD_MIN_PAIRS",
        "const.EDA_MIN_US" to "$EDA_MIN_US",
        "const.EDA_SPIKE_FLOOR_US" to "$EDA_SPIKE_FLOOR_US",
        "const.EDA_SPIKE_SIGMA" to "$EDA_SPIKE_SIGMA",
        "const.CONFIDENCE_W_STILL" to "$CONFIDENCE_W_STILL",
        "const.CONFIDENCE_W_AUTONOMIC" to "$CONFIDENCE_W_AUTONOMIC",
        "const.CONFIDENCE_W_HOLD" to "$CONFIDENCE_W_HOLD",
        "const.CONFIDENCE_W_BONUS" to "$CONFIDENCE_W_BONUS",
        "const.CONFIDENCE_BONUS_EACH" to "$CONFIDENCE_BONUS_EACH",
        "const.COMMAND_RETRIES" to "$COMMAND_RETRIES",
        "const.COMMAND_RETRY_INTERVAL_MS" to "$COMMAND_RETRY_INTERVAL_MS",
        "const.COMMAND_REPLY_TIMEOUT_MS" to "$COMMAND_REPLY_TIMEOUT_MS",
        "const.TRIGGER_ATTEMPT_OFFSETS_MS" to TRIGGER_ATTEMPT_OFFSETS_MS.joinToString(";"),
        "const.TRIGGER_REPLY_TIMEOUT_MS" to "$TRIGGER_REPLY_TIMEOUT_MS",
        "const.CUE_PEAK_DBFS" to "$CUE_PEAK_DBFS",
        "const.PHRASE_SILENCE_DBFS" to "$PHRASE_SILENCE_DBFS",
        "const.SPEECH_LEAD_FADE_FRACTION" to "$SPEECH_LEAD_FADE_FRACTION",
    )
}
