package com.opentdi.shared.stats

import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_MIN_S
import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_WINDOW_S
import kotlin.math.abs
import kotlin.math.roundToInt

// The sleep/wake estimate of docs/references/sleep_stats.md §A2–A3 (owner, 2026-10-28: "nice graphs ... of your sleep
// time"), from wrist movement only: heart rate is shown, never used [5]. [n] are that file's references; every value
// is SOURCED from them or GUESSED. Changing one changes the method's version and SUMMARY_VERSION.
private const val EPOCH_S = 5 // SOURCED [1][2]
private const val EPOCH_MS = EPOCH_S * 1000L
private const val EPOCHS_PER_MIN = 60 / EPOCH_S
private const val MIN_RECORDING_MS = 3 * 3_600_000L // GUESSED: no estimate for a shorter recording ...
private const val MIN_VALID_SHARE = 0.8 // GUESSED: ... or for one with fewer valid epochs
private const val MIN_INPUT_S = 3 // GUESSED: a valid epoch has the method's input in 3 of its 5 seconds ...
private const val MAX_DETACHED_S = 2 // GUESSED: ... and is off the wrist in fewer than 3
private const val SIB_MAX_DEG = 5.0 // SOURCED [1]: an arm angle that changes by no more than 5° ...
private const val SIB_MIN_EPOCHS = 5 * EPOCHS_PER_MIN // SOURCED [1]: ... for 5 min is a sustained inactivity bout
// GUESSED (stillness v1, never validated against PSG): moving below the MIT_STANDARD still second, whatever the
// night's setting; 3 of 5 seconds moving is a major movement as in §8.10, but unlike StillnessGate and LateCues a
// second without data is not moving.
private const val MOVING_BELOW = 0.8f
private const val HDCZA_HALF_EPOCHS = 30 // SOURCED [2]: a centred 5-min rolling median ...
private const val HDCZA_MIN_VALID = 30 // GUESSED: ... of at least half its epochs
private const val HDCZA_PERCENTILE = 10 // SOURCED [2]: threshold = 10th percentile × 15, clamped to 0.13–0.50
private const val HDCZA_FACTOR = 15.0
private const val HDCZA_MIN_THRESHOLD = 0.13
private const val HDCZA_MAX_THRESHOLD = 0.50
private const val HDCZA_BLOCK_EPOCHS = 30 * EPOCHS_PER_MIN // SOURCED [2]: blocks longer than 30 min ...
private const val HDCZA_MERGE_EPOCHS = 60 * EPOCHS_PER_MIN // SOURCED [2]: ... joined across less than 60 min
// SOURCED [14]: onset begins 10 min of sleep with at most 1 min awake; applying it to bouts is GUESSED.
private const val ONSET_EPOCHS = 10 * EPOCHS_PER_MIN
private const val ONSET_MAX_WAKE_EPOCHS = EPOCHS_PER_MIN
private const val LONG_WAKING_EPOCHS = 5 * EPOCHS_PER_MIN // SOURCED [14]; counting only these: GUESSED
private const val GAP_EPOCHS = 10 * EPOCHS_PER_MIN // GUESSED: an invalid run this long is no data, a shorter one wake
private const val RE_SLEEP_EPOCHS = 10 * EPOCHS_PER_MIN // GUESSED: without RE_ONSET, this much sleep ends the WBTB break
private const val HR_MEAN_MIN_S = 30 * 60 // GUESSED: seconds of heart rate while estimated asleep for its mean ...
private const val HR_MINUTE_MIN_S = 30 // GUESSED: ... and in a minute, for the lowest minute mean

/** w_packets.csv on a 1-s grid: index s is the second from [startMs] + 1000 s. NaN where a second has no value. */
internal class Seconds(
    val startMs: Long,
    /** The last packet's ts_ms + 1 s. */
    val endMs: Long,
    val hr: FloatArray,
    val stillness: FloatArray,
    /** Null for a night logged before the angle_z_deg column. */
    val angle: FloatArray?,
    /** Off the wrist: the Samsung SDK's hr_status −3 or eda_status −5. */
    val detached: BooleanArray,
    /** [hr] where Samsung's hr_status is 1 (good), or with no status (Android's sensor): what [remLikely] uses. */
    val hrGood: FloatArray,
    /** Skin conductance in μS where Samsung's eda_status is 0 (normal). */
    val eda: FloatArray,
    /** The watch's rolling RMSSD of filtered beat-to-beat intervals, ms. */
    val rmssd: FloatArray = FloatArray(hr.size) { Float.NaN },
    /** Wrist skin temperature, °C, the last measurement held (about one a minute). */
    val skinTemp: FloatArray = FloatArray(hr.size) { Float.NaN },
    /** The second's largest acceleration away from gravity, g: small twitches show here. */
    val accelPeak: FloatArray = FloatArray(hr.size) { Float.NaN },
) {
    val size: Int get() = hr.size

    /** The seconds of 5-s epoch [k]. */
    fun ofEpoch(k: Int): IntRange = EPOCH_S * k until minOf(EPOCH_S * (k + 1), size)
}

/** A time the app knows the sleeper was awake, [fromMs, toMs) (sleep_stats.md §A1). */
internal class Span(val fromMs: Long, val toMs: Long)

internal class Estimate(val sleep: SleepEstimate?, val epochs: Epochs)

private enum class Kind { SLEEP, WAKE, GAP }

/** A method's valid epochs and its own wake ([change]: ends a bout); the arm angle's changes feed the window. */
private class Method(val name: String, val valid: BooleanArray, val change: BooleanArray, val angleChange: DoubleArray?)

/**
 * The night's sleep estimate and minute timeline: `arm-angle v1`, sustained inactivity bouts [1] inside the HDCZA
 * sleep-period window [2], where the arm angle covers the night, else `stillness v1`. Epochs that overlap [knownWake]
 * or the WBTB break of [wbtb] are wake, since lying still while awake is the main error [1][9][10]; the break and the
 * no-data gaps stay out of the time awake and the long wakings.
 */
internal fun estimate(sec: Seconds, knownWake: List<Span>, wbtb: WbtbOutcome?): Estimate {
    val n = (sec.size + EPOCH_S - 1) / EPOCH_S
    val major = majorMovement(sec.stillness)
    val method = chooseMethod(sec, n, major) ?: return Estimate(null, timeline(sec, major) { null })
    val known = BooleanArray(n).apply { knownWake.forEach { mark(sec.startMs, it) } }
    fun kindsWith(extra: Span?) = kinds(method, known.copyOf().apply { extra?.let { mark(sec.startMs, it) } })
    val wbtbBreak = wbtbBreak(wbtb, sec.startMs, ::kindsWith)
    val inBreak = BooleanArray(n).apply { wbtbBreak?.let { mark(sec.startMs, it) } }
    val kind = kindsWith(wbtbBreak)
    val angleChange = method.angleChange
    val window = if (angleChange == null) {
        0 until n // stillness v1: the whole recording
    } else {
        hdczaWindow(angleChange, BooleanArray(n) { inBreak[it] || kind[it] == Kind.GAP })
    }
    val (sleep, night) = window?.let { measure(sec, method.name, kind, inBreak, it) }
        ?: return Estimate(null, timeline(sec, major) { null })
    val minutes = timeline(sec, major) { minute ->
        val epochs = EPOCHS_PER_MIN * minute until minOf(EPOCHS_PER_MIN * (minute + 1), n)
        // §B9: half the minute's epochs valid, and asleep when half are sleep between onset and final wake.
        if (epochs.count { method.valid[it] } < EPOCHS_PER_MIN / 2) {
            null
        } else {
            epochs.count { it in night && kind[it] == Kind.SLEEP } >= EPOCHS_PER_MIN / 2
        }
    }
    val onsetMinute = ((sleep.onsetMs - sec.startMs) / 60_000).toInt()
    return Estimate(sleep, minutes.copy(remLikely = remLikely(sec, minutes.asleep, onsetMinute)))
}

/** `arm-angle v1` where enough epochs have the angle, else `stillness v1` where enough have stillness, else null. */
private fun chooseMethod(sec: Seconds, n: Int, major: BooleanArray): Method? {
    if (sec.endMs - sec.startMs < MIN_RECORDING_MS) return null
    fun validWith(input: FloatArray) = BooleanArray(n) { k ->
        val s = sec.ofEpoch(k)
        s.count { !input[it].isNaN() } >= MIN_INPUT_S && s.count { sec.detached[it] } <= MAX_DETACHED_S
    }
    fun enough(valid: BooleanArray) = valid.count { it } >= MIN_VALID_SHARE * n
    val angle = sec.angle
    if (angle != null) {
        val valid = validWith(angle)
        if (enough(valid)) {
            // Each second's angle is that of its mean vector; their epoch mean stands in for the 5-s per-axis medians
            // of [1] (GUESSED), with Samsung's axes taken as Android's, z through the screen and skin (GUESSED).
            val a = DoubleArray(n) { k ->
                if (valid[k]) sec.ofEpoch(k).map { angle[it] }.filter { !it.isNaN() }.average() else Double.NaN
            }
            val d = DoubleArray(n) { k -> if (k == 0) Double.NaN else abs(a[k] - a[k - 1]) }
            // An epoch without a valid change ends a bout, so missing data never makes sleep (GUESSED).
            return Method("arm-angle v1", valid, BooleanArray(n) { d[it].isNaN() || d[it] > SIB_MAX_DEG }, d)
        }
    }
    val valid = validWith(sec.stillness)
    if (!enough(valid)) return null
    return Method("stillness v1", valid, BooleanArray(n) { k -> !valid[k] || sec.ofEpoch(k).any { major[it] } }, null)
}

/** Each epoch as sleep, wake or no data (§B7–B8): a long enough bout with no change and no known [wake] is sleep. */
private fun kinds(method: Method, wake: BooleanArray): Array<Kind> {
    val n = wake.size
    val kind = Array(n) { Kind.WAKE }
    for (r in runs(n) { !method.valid[it] }) if (r.length >= GAP_EPOCHS) r.forEach { kind[it] = Kind.GAP }
    for (r in runs(n) { !method.change[it] && !wake[it] }) {
        if (r.length >= SIB_MIN_EPOCHS) r.forEach { kind[it] = Kind.SLEEP }
    }
    return kind
}

/**
 * The WBTB break (§B7 F3), only when someone stopped the alarm: from the ring to the logged re-onset or, without one,
 * to the first 10 min of sleep after the back-to-sleep buzz, failing that to the buzz. Without a logged buzz (the
 * recording or the watch app stopped first), getting up stands in for it (GUESSED).
 */
private fun wbtbBreak(wbtb: WbtbOutcome?, startMs: Long, kindsWith: (Span) -> Array<Kind>): Span? {
    val ring = wbtb?.ringAtMs ?: return null
    val buzz = wbtb.backToSleepAtMs ?: wbtb.upAtMs ?: return null
    val end = wbtb.reOnsetMs ?: run {
        val kind = kindsWith(Span(ring, buzz))
        val after = (buzz - startMs + EPOCH_MS - 1).floorDiv(EPOCH_MS)
        runs(kind.size) { kind[it] == Kind.SLEEP }.firstOrNull { it.first >= after && it.length >= RE_SLEEP_EPOCHS }
            ?.let { startMs + it.first * EPOCH_MS } ?: buzz
    }
    return Span(ring, maxOf(ring, end))
}

/**
 * The sleep-period window of van Hees 2018 [2] over the epochs' arm-angle changes [angleChange] (NaN = none), as epoch
 * indices: the centred 5-min rolling median below 15 × its 10th percentile (clamped to 0.13–0.50) for over 30 min,
 * blocks joined across less than 60 min, the longest (the earliest of equals). The percentile comes from the
 * recording alone, not 24 h (GUESSED). [skip], the WBTB break and no-data gaps, does not count toward the 60 min
 * (GUESSED: else a long WBTB break splits the night). Null without a block.
 */
internal fun hdczaWindow(angleChange: DoubleArray, skip: BooleanArray): IntRange? {
    val n = angleChange.size
    val median = DoubleArray(n) { k ->
        val w = (maxOf(0, k - HDCZA_HALF_EPOCHS) until minOf(n, k + HDCZA_HALF_EPOCHS))
            .map { angleChange[it] }.filter { !it.isNaN() }.sorted()
        when {
            w.size < HDCZA_MIN_VALID -> Double.NaN
            w.size % 2 == 1 -> w[w.size / 2]
            else -> (w[w.size / 2 - 1] + w[w.size / 2]) / 2
        }
    }
    val sorted = median.filter { !it.isNaN() }.sorted()
    if (sorted.isEmpty()) return null
    val p10 = sorted[(sorted.size * HDCZA_PERCENTILE + 99) / 100 - 1] // nearest rank
    val threshold = (p10 * HDCZA_FACTOR).coerceIn(HDCZA_MIN_THRESHOLD, HDCZA_MAX_THRESHOLD)
    val blocks = ArrayList<IntRange>()
    for (b in runs(n) { median[it] < threshold }) { // NaN is never below
        if (b.length <= HDCZA_BLOCK_EPOCHS) continue
        val last = blocks.lastOrNull()
        if (last != null && (last.last + 1 until b.first).count { !skip[it] } < HDCZA_MERGE_EPOCHS) {
            blocks[blocks.lastIndex] = last.first..b.last
        } else {
            blocks += b
        }
    }
    return blocks.maxByOrNull { it.length }
}

/** The §A3.2 measures inside [window] (§B8.4), and the epochs from onset to final wake; null without an onset. */
private fun measure(
    sec: Seconds,
    method: String,
    kind: Array<Kind>,
    inBreak: BooleanArray,
    window: IntRange,
): Pair<SleepEstimate, IntRange>? {
    val n = kind.size
    // SOURCED [14]: the first epoch asleep that begins 10 min with no data gap and at most 1 min awake.
    val onset = window.firstOrNull { k ->
        val next = k until k + ONSET_EPOCHS
        kind[k] == Kind.SLEEP && next.last < n && next.none { kind[it] == Kind.GAP } &&
            next.count { kind[it] == Kind.WAKE } <= ONSET_MAX_WAKE_EPOCHS
    } ?: return null
    // SOURCED [14]: the end of the last epoch asleep, here within the window (GUESSED); never before the onset.
    val end = window.last { kind[it] == Kind.SLEEP } + 1
    val night = onset until end
    // The planned WBTB break is wake but not waking in the night (§A3.2 row 6, inference).
    fun awake(k: Int) = kind[k] == Kind.WAKE && !inBreak[k]
    val asleep = night.filter { kind[it] == Kind.SLEEP }
    // GUESSED: recording start to final wake without no-data gaps, neither of [14]'s denominators. From epochs rather
    // than rounded minutes, so it never passes 100.
    val span = end - (0 until end).count { kind[it] == Kind.GAP }
    fun hr(epochs: Iterable<Int>) = epochs.flatMap(sec::ofEpoch).map { sec.hr[it] }.filter { !it.isNaN() }
    val minuteMeans = (0 until (n + EPOCHS_PER_MIN - 1) / EPOCHS_PER_MIN).mapNotNull { m ->
        val epochs = EPOCHS_PER_MIN * m until EPOCHS_PER_MIN * (m + 1)
        if (epochs.all { it in night && kind[it] == Kind.SLEEP }) {
            hr(epochs).takeIf { it.size >= HR_MINUTE_MIN_S }?.average()
        } else {
            null
        }
    }
    return SleepEstimate(
        onsetMs = sec.startMs + onset * EPOCH_MS,
        finalWakeMs = sec.startMs + end * EPOCH_MS,
        sleepMin = minutes(asleep.size),
        latencyMin = minutes(onset), // GUESSED proxy: from the recording's start, which is not lights out
        wasoMin = minutes(night.count(::awake)),
        awakenings = runs(end) { it >= onset && awake(it) }.count { it.length >= LONG_WAKING_EPOCHS },
        efficiencyPct = if (span <= 0) 0 else (100.0 * asleep.size / span).roundToInt(),
        hrSleepMeanBpm = hr(asleep).takeIf { it.size >= HR_MEAN_MIN_S }?.average()?.toFloat(),
        hrSleepMinBpm = minuteMeans.minOrNull()?.toFloat(),
        method = method,
    ) to night
}

/** The night minute by minute from the first packet (§B9); [asleep] gives each minute's estimate. */
private fun timeline(sec: Seconds, major: BooleanArray, asleep: (minute: Int) -> Boolean?): Epochs {
    val minutes = ((sec.endMs - sec.startMs + 59_999) / 60_000).toInt()
    fun seconds(m: Int) = 60 * m until minOf(60 * (m + 1), sec.size)
    return Epochs(
        startMs = sec.startMs,
        movement = List(minutes) { m ->
            val known = seconds(m).filter { !sec.stillness[it].isNaN() }
            if (known.isEmpty()) null else known.count { major[it] }.toFloat() / known.size
        },
        hrBpm = List(minutes) { m ->
            seconds(m).filter { !sec.detached[it] }.map { sec.hr[it] }.filter { !it.isNaN() }
                .takeIf { it.isNotEmpty() }?.average()?.toFloat()
        },
        asleep = List(minutes, asleep),
    )
}

/** The §8.10 major movement at each second: at least 3 of the last 5 seconds moving (see [MOVING_BELOW]). */
private fun majorMovement(stillness: FloatArray): BooleanArray {
    val moving = BooleanArray(stillness.size) { stillness[it] < MOVING_BELOW } // NaN is not moving
    return BooleanArray(stillness.size) { s ->
        (maxOf(0, s - MAJOR_MOVEMENT_WINDOW_S + 1)..s).count { moving[it] } >= MAJOR_MOVEMENT_MIN_S
    }
}

/** Marks the 5-s epochs that overlap [span]: an epoch that overlaps known wake is wake (§B7). */
private fun BooleanArray.mark(startMs: Long, span: Span) {
    if (span.toMs <= span.fromMs) return
    val from = (span.fromMs - startMs).floorDiv(EPOCH_MS).coerceAtLeast(0)
    val to = (span.toMs - startMs + EPOCH_MS - 1).floorDiv(EPOCH_MS).coerceAtMost(size.toLong())
    for (k in from until to) this[k.toInt()] = true
}

/** The maximal runs of indices in 0 until [n] where [inRun] holds. */
internal inline fun runs(n: Int, inRun: (Int) -> Boolean): List<IntRange> {
    val out = ArrayList<IntRange>()
    var k = 0
    while (k < n) {
        if (!inRun(k)) {
            k++
            continue
        }
        val start = k
        while (k < n && inRun(k)) k++
        out += start until k
    }
    return out
}

internal val IntRange.length: Int get() = last - first + 1

private fun minutes(epochs: Int) = (epochs * EPOCH_S / 60.0).roundToInt()
