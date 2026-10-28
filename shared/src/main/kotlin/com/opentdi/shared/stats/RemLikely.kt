package com.opentdi.shared.stats

import kotlin.math.sqrt

// "REM-likely v1" (owner, 2026-10-28: "combine signals"), the bottom line of docs/references/sleep_and_dreams.md §2;
// [n] are that file's references. Shown in Stats only: never used to cue and never called REM, since a wrist rule cannot
// stage sleep (§1). Every value is SOURCED or GUESSED; changing one changes SUMMARY_VERSION.
private const val AFTER_ONSET_MIN = 60 // GUESSED from [3]: the first REM comes about 96 min after sleep onset
private const val WINDOW_HALF_MIN = 2 // GUESSED: each minute is judged on the 5 min around it ...
private const val WINDOW_MIN_S = 150 // GUESSED: ... with good heart rate in at least half their seconds
private const val MINUTE_MIN_S = 30 // GUESSED: the same for one minute's mean
private const val BASELINE_HALF_MIN = 45 // GUESSED: the 90 min around a minute hold about one cycle (median 96 min [8]) ...
private const val BASELINE_QUANTILE = 0.25 // GUESSED: ... and the lower quartile of their minute means is NREM
private const val BASELINE_MIN_MINUTES = 30 // GUESSED
private const val ABOVE_BASELINE_BPM = 3.0 // GUESSED: REM heart rate returns toward waking levels, NREM's falls [5][20]
private const val MIN_RUN_MIN = 5 // GUESSED from [4]: early-night REM episodes last 5–10 min
private const val EDA_EPOCH_S = 30 // SOURCED [7]: an EDA-peak epoch is 30 s with at least one peak ...
// SOURCED [7], their wrist threshold: ... a peak where the rise passes 0.01 μS/s, here the 1-s difference of the 1-Hz
// trace (GUESSED: they low-passed a 32-Hz trace at 0.4 Hz) ...
private const val EDA_RISE_US = 0.01f
private const val STORM_MIN_EPOCHS = 2 // SOURCED [7]: ... and a storm is consecutive peak epochs over at least 1 min

/**
 * Each minute of [asleep] as REM-likely, null where [asleep] is: asleep (a still bout, twitches allowed), at least
 * 60 min after [onsetMinute], outside an EDA storm (most storms are NREM [7]), and the 5 min around it with a heart
 * rate at least 3 bpm above the night's running NREM baseline and more variable than the night's median 5 min asleep
 * [5][20][21]. Runs shorter than 5 min are dropped.
 */
internal fun remLikely(sec: Seconds, asleep: List<Boolean?>, onsetMinute: Int): List<Boolean?> {
    val n = asleep.size
    fun hr(m: Int) = (60 * m until minOf(60 * (m + 1), sec.size)).map { sec.hrGood[it] }.filter { !it.isNaN() }
    val minuteMean = DoubleArray(n) { m ->
        hr(m).takeIf { asleep[m] == true && it.size >= MINUTE_MIN_S }?.average() ?: Double.NaN
    }
    val mean = DoubleArray(n) { Double.NaN }
    val sd = DoubleArray(n) { Double.NaN }
    for (m in 0 until n) {
        if (asleep[m] != true) continue
        val v = (m - WINDOW_HALF_MIN..m + WINDOW_HALF_MIN).filter { asleep.getOrNull(it) == true }.flatMap(::hr)
        if (v.size < WINDOW_MIN_S) continue
        mean[m] = v.average()
        sd[m] = sqrt(v.sumOf { (it - mean[m]) * (it - mean[m]) } / v.size)
    }
    val sds = sd.filter { !it.isNaN() }.sorted()
    val typicalSd = if (sds.isEmpty()) Double.POSITIVE_INFINITY else sds[sds.size / 2]
    val storm = edaStorms(sec, n)
    val likely = BooleanArray(n) { m ->
        if (m < onsetMinute + AFTER_ONSET_MIN || storm[m] || !(sd[m] > typicalSd)) return@BooleanArray false // NaN fails
        val near = (m - BASELINE_HALF_MIN..m + BASELINE_HALF_MIN).mapNotNull { minuteMean.getOrNull(it) }
            .filter { !it.isNaN() }.sorted()
        near.size >= BASELINE_MIN_MINUTES && mean[m] >= near[(near.size * BASELINE_QUANTILE).toInt()] + ABOVE_BASELINE_BPM
    }
    for (r in runs(n) { likely[it] }) if (r.length < MIN_RUN_MIN) r.forEach { likely[it] = false }
    return List(n) { m -> asleep[m]?.let { likely[m] } }
}

/** The minutes that overlap an EDA storm [7]: two or more 30-s epochs in a row, each with a peak. */
private fun edaStorms(sec: Seconds, minutes: Int): BooleanArray {
    val rise = BooleanArray(sec.size) { s -> s > 0 && sec.eda[s] - sec.eda[s - 1] > EDA_RISE_US } // NaN never passes
    // A peak begins where a rise begins, so one long rise is one peak, as [7] joins peaks under 1 s apart.
    val epochs = (sec.size + EDA_EPOCH_S - 1) / EDA_EPOCH_S
    val peaked = BooleanArray(epochs) { k ->
        (EDA_EPOCH_S * k until minOf(EDA_EPOCH_S * (k + 1), sec.size)).any { rise[it] && (it == 0 || !rise[it - 1]) }
    }
    val storm = BooleanArray(minutes)
    for (r in runs(epochs) { peaked[it] }) {
        if (r.length < STORM_MIN_EPOCHS) continue
        for (m in r.first * EDA_EPOCH_S / 60..((r.last + 1) * EDA_EPOCH_S - 1) / 60) if (m < minutes) storm[m] = true
    }
    return storm
}
