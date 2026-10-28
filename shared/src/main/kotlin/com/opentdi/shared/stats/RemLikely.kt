package com.opentdi.shared.stats

import kotlin.math.sqrt

// "REM-likely v2" (owner, 2026-10-28: "combine signals", then "using all the hr and other data to detect rem"), from
// docs/references/sleep_and_dreams.md §2; [n] are that file's references. Shown in Stats only: never used to cue and
// never called REM, since a wrist rule cannot stage sleep (§1). Every comparison is within the night, so no calibration
// night is needed. Every value is SOURCED or GUESSED; changing one changes SUMMARY_VERSION.
private const val AFTER_ONSET_MIN = 60 // GUESSED from [3]: the first REM comes about 96 min after sleep onset
private const val WINDOW_HALF_MIN = 2 // GUESSED: each minute is judged on the 5 min around it ...
private const val WINDOW_MIN_S = 150 // GUESSED: ... with a signal in at least half their seconds
private const val MINUTE_MIN_S = 30 // GUESSED: the same for one minute's value
private const val NEAR_HALF_MIN = 45 // GUESSED: the 90 min around a minute hold about one cycle (median 96 min [8])
private const val NEAR_MIN_MINUTES = 30 // GUESSED: the fewest asleep minutes a running reference needs
private const val BASELINE_QUANTILE = 0.25 // GUESSED: the lower quartile of the minute heart rates nearby is NREM
private const val ABOVE_BASELINE_BPM = 3.0 // GUESSED: REM heart rate returns toward waking levels, NREM's falls [5][20]
private const val TWITCH_FACTOR = 3.0 // GUESSED: a twitch second peaks at 3× the night's median asleep second [6]
private const val MIN_SIGNS = 2 // GUESSED: besides the raised heart rate, 2 of the 4 other signs
private const val MIN_RUN_MIN = 5 // GUESSED from [4]: early-night REM episodes last 5–10 min
private const val EDA_EPOCH_S = 30 // SOURCED [7]: an EDA-peak epoch is 30 s with at least one peak ...
// SOURCED [7], their wrist threshold: ... a peak where the rise passes 0.01 μS/s, here the 1-s difference of the 1-Hz
// trace (GUESSED: they low-passed a 32-Hz trace at 0.4 Hz) ...
private const val EDA_RISE_US = 0.01f
private const val STORM_MIN_EPOCHS = 2 // SOURCED [7]: ... and a storm is consecutive peak epochs over at least 1 min

/**
 * Each minute of [asleep] as REM-likely, null where [asleep] is. A minute is a candidate when it is asleep (a still
 * bout), at least 60 min after [onsetMinute] and outside an EDA storm (most storms are NREM [7]). It counts when the
 * 5 min around it have a heart rate at least 3 bpm above the night's running NREM baseline [5][20], and at least 2 of:
 * a heart rate more variable than the night's median 5 min asleep [5][21]; an RMSSD below the running median (REM's
 * vagal withdrawal [20][21]); more twitches than the night's median 5 min asleep (REM moves more than N2/N3 [6]); a
 * wrist cooler than its running median (warmer in deep sleep than in REM [7]). A sign without data does not count.
 * Runs shorter than 5 min are dropped.
 */
internal fun remLikely(sec: Seconds, asleep: List<Boolean?>, onsetMinute: Int): List<Boolean?> {
    val n = asleep.size
    fun seconds(m: Int) = 60 * m until minOf(60 * (m + 1), sec.size)
    fun values(a: FloatArray, m: Int) = seconds(m).map { a[it] }.filter { !it.isNaN() }
    fun window(a: FloatArray, m: Int) =
        (m - WINDOW_HALF_MIN..m + WINDOW_HALF_MIN).filter { asleep.getOrNull(it) == true }.flatMap { values(a, it) }
    // Per asleep minute: its own value, for the running references.
    fun perMinute(a: FloatArray, of: (List<Float>) -> Double) = DoubleArray(n) { m ->
        values(a, m).takeIf { asleep[m] == true && it.size >= MINUTE_MIN_S }?.let(of) ?: Double.NaN
    }
    // Per asleep minute: the 5 min around it.
    fun perWindow(a: FloatArray, of: (List<Float>) -> Double) = DoubleArray(n) { m ->
        window(a, m).takeIf { asleep[m] == true && it.size >= WINDOW_MIN_S }?.let(of) ?: Double.NaN
    }
    /** The [q] quantile of [minute] over the 90 min around [m]; NaN with too few asleep minutes. */
    fun near(minute: DoubleArray, m: Int, q: Double): Double {
        val v = (m - NEAR_HALF_MIN..m + NEAR_HALF_MIN).mapNotNull { minute.getOrNull(it) }.filter { !it.isNaN() }.sorted()
        return if (v.size < NEAR_MIN_MINUTES) Double.NaN else v[(v.size * q).toInt().coerceAtMost(v.size - 1)]
    }
    fun nightMedian(a: DoubleArray) = a.filter { !it.isNaN() }.sorted().let { if (it.isEmpty()) Double.NaN else it[it.size / 2] }

    val hrMinute = perMinute(sec.hrGood) { it.average() }
    val hrMean = perWindow(sec.hrGood) { it.average() }
    val hrSd = perWindow(sec.hrGood) { v -> v.average().let { mean -> sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size) } }
    val rmssdMinute = perMinute(sec.rmssd, ::median)
    val rmssd = perWindow(sec.rmssd, ::median)
    val tempMinute = perMinute(sec.skinTemp) { it.average() }
    val temp = perWindow(sec.skinTemp) { it.average() }
    val twitchLevel = TWITCH_FACTOR * median((0 until n).filter { asleep[it] == true }.flatMap { values(sec.accelPeak, it) })
    val twitches = perWindow(sec.accelPeak) { v -> v.count { it > twitchLevel }.toDouble() }
    val typicalSd = nightMedian(hrSd)
    val typicalTwitches = nightMedian(twitches)
    val storm = edaStorms(sec, n)

    val likely = BooleanArray(n) { m ->
        if (asleep[m] != true || m < onsetMinute + AFTER_ONSET_MIN || storm[m]) return@BooleanArray false
        // NaN compares false, so a sign without data never counts.
        if (!(hrMean[m] >= near(hrMinute, m, BASELINE_QUANTILE) + ABOVE_BASELINE_BPM)) return@BooleanArray false
        listOf(
            hrSd[m] > typicalSd,
            rmssd[m] < near(rmssdMinute, m, 0.5),
            twitches[m] > typicalTwitches,
            temp[m] < near(tempMinute, m, 0.5),
        ).count { it } >= MIN_SIGNS
    }
    for (r in runs(n) { likely[it] }) if (r.length < MIN_RUN_MIN) r.forEach { likely[it] = false }
    return List(n) { m -> asleep[m]?.let { likely[m] } }
}

private fun median(v: List<Float>): Double = if (v.isEmpty()) Double.NaN else v.sorted().let { s ->
    if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
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
