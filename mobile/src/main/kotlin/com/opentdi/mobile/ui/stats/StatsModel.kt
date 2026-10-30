package com.opentdi.mobile.ui.stats

import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.stats.PhoneNight
import com.opentdi.mobile.stats.SyncResult
import com.opentdi.shared.stats.DreamReport
import com.opentdi.shared.stats.Epochs
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.stats.WbtbOutcome
import com.opentdi.shared.time.NBSP
import com.opentdi.shared.time.clock12
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The series maths of the Stats tab (owner, 2026-10-28: "make nice graphs for everything like all your stats and
 * everything like proper visualizations of your sleep time and dream state over months and weeks"). Pure functions,
 * so the screens only draw. Every sleep figure is the watch's estimate from movement and heart rate, never a sleep
 * stage (docs/references/sleep_and_dreams.md §1–2).
 */

/** Insight (0–5) from which a morning counts as a lucid moment. GUESSED: the LuCiD scale ([64]) has no cut-off. */
const val LUCID_INSIGHT = 3

/** SOURCED: adults should regularly sleep at least 7 h (AASM/SRS, sleep_and_dreams.md §1 [19]). */
const val SLEEP_GUIDE_H = 7f

/** Pairs before shares show. GUESSED (task brief, 2026-10-28): with fewer, one night moves a share over 20 points. */
const val MIN_PAIRS = 5

/** SOURCED: the usual two-sided 5 % level, for the exact sign test on paired yes/no outcomes (McNemar). */
const val CLEAR_P = 0.05

/** GUESSED: the spread of bedtimes over fewer nights says little. */
const val MIN_SPREAD_NIGHTS = 3

/** The range filter at the top of Stats: the last [days] nights up to tonight, or every night. */
enum class StatsRange(val label: String, val days: Int?) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    QUARTER("3 months", 91),
    ALL("All", null),
}

/** The first night [range] covers: [StatsRange.days] back from [today], or the oldest of [rows]. */
fun rangeStart(range: StatsRange, today: LocalDate, rows: List<NightRow>): LocalDate =
    range.days?.let { today.minusDays(it - 1L) } ?: rows.minOfOrNull { it.night }?.coerceAtMost(today) ?: today

/** The nights of [rows] from [from] to [today], oldest first. */
fun inRange(rows: List<NightRow>, from: LocalDate, today: LocalDate): List<NightRow> =
    rows.filter { it.night in from..today }.sortedBy { it.night }

enum class BinUnit { NIGHT, WEEK, MONTH }

/** One slot of a chart's time axis: one night, or the nights of the week or month that starts on [start]. */
data class Bin(val unit: BinUnit, val start: LocalDate, val nights: List<NightRow>)

/**
 * The slots from [from] to [to]: night by night up to 31 nights, then weeks starting on [firstDay] up to 26 weeks,
 * then months, so a bar on a phone never gets thinner than a few dp (GUESSED cut-offs). Empty slots stay: a gap is
 * a night without data.
 */
fun bins(nights: List<NightRow>, from: LocalDate, to: LocalDate, firstDay: DayOfWeek): List<Bin> {
    val days = ChronoUnit.DAYS.between(from, to) + 1
    val unit = when {
        days <= 31 -> BinUnit.NIGHT
        days <= 26 * 7 -> BinUnit.WEEK
        else -> BinUnit.MONTH
    }
    fun startOf(d: LocalDate): LocalDate = when (unit) {
        BinUnit.NIGHT -> d
        BinUnit.WEEK -> d.with(TemporalAdjusters.previousOrSame(firstDay))
        BinUnit.MONTH -> d.withDayOfMonth(1)
    }
    val byStart = nights.groupBy { startOf(it.night) }
    return generateSequence(startOf(from)) {
        when (unit) {
            BinUnit.NIGHT -> it.plusDays(1)
            BinUnit.WEEK -> it.plusWeeks(1)
            BinUnit.MONTH -> it.plusMonths(1)
        }
    }.takeWhile { it <= to }.map { Bin(unit, it, byStart[it].orEmpty()) }.toList()
}

/** [date]'s week, counted from the week holding [from] (weeks start on [firstDay]), and its day in that week. */
fun calendarSlot(date: LocalDate, from: LocalDate, firstDay: DayOfWeek): Pair<Int, Int> {
    val days = ChronoUnit.DAYS.between(from.with(TemporalAdjusters.previousOrSame(firstDay)), date).toInt()
    return Math.floorDiv(days, 7) to Math.floorMod(days, 7)
}

/** A slot's value: the mean over its [nights] that have one. */
data class Mean(val value: Float, val nights: Int)

/** The mean of [of] over the nights that have a value; null when none has. */
fun List<NightRow>.mean(of: (NightRow) -> Float?): Mean? =
    mapNotNull(of).takeIf { it.isNotEmpty() }?.let { Mean(it.average().toFloat(), it.size) }

val NightRow.reports: List<DreamReport> get() = watch?.reports.orEmpty()
val NightRow.recorded: Boolean get() = watch != null || phone != null
val NightRow.sleepMin: Int? get() = watch?.sleep?.sleepMin
val NightRow.insight: Int? get() = reports.mapNotNull { it.insight }.maxOrNull()
val NightRow.control: Int? get() = reports.mapNotNull { it.control }.maxOrNull()
val NightRow.nightmare: Boolean get() = reports.any { it.nightmare == true }

/** A night's morning, in order: the dream calendar's colour steps. */
enum class Outcome(val label: String) {
    NO_REPORT("Not answered"),
    NO_DREAM("No dream"),
    WHITE("Dreamed, can't recall it"),
    DREAM("Dream"),
    LUCID("Lucid moment"),
}

/** The night's best morning answer: a lucid moment, then a dream, a dream without content, no dream. */
fun outcome(row: NightRow): Outcome {
    val r = row.reports
    return when {
        r.any { (it.insight ?: 0) >= LUCID_INSIGHT } -> Outcome.LUCID
        r.any { it.recalled == "YES" } -> Outcome.DREAM
        r.any { it.recalled == "WHITE" } -> Outcome.WHITE
        r.any { it.recalled == "NO" } -> Outcome.NO_DREAM
        else -> Outcome.NO_REPORT
    }
}

/** Mornings that remembered a dream with content, and mornings that answered the question. */
fun recall(nights: List<NightRow>): Pair<Int, Int> {
    val answered = nights.filter { n -> n.reports.any { it.recalled != null } }
    return answered.count { n -> n.reports.any { it.recalled == "YES" } } to answered.size
}

/**
 * Mornings with insight of [LUCID_INSIGHT] or more, and mornings that answered recall or insight. Since 2026-10-29 the
 * watch asks insight only about a remembered dream, so a morning without one counts as not lucid, not as unrated.
 */
fun lucid(nights: List<NightRow>): Pair<Int, Int> {
    val answered = nights.filter { n -> n.reports.any { it.recalled != null || it.insight != null } }
    return answered.count { (it.insight ?: 0) >= LUCID_INSIGHT } to answered.size
}

/** The night's own time zone, or the phone's when the stored one is unreadable. */
fun zoneOf(summary: NightSummary): ZoneId = try {
    ZoneId.of(summary.zone)
} catch (e: DateTimeException) {
    ZoneId.systemDefault()
}

/**
 * Minutes from noon of [night] to [epochMs] on the wall clock of [zone]: 660 is 23:00, 810 is 1:30 the next
 * morning. Wall-clock minutes, so a daylight-saving night does not shift the clock axis.
 */
fun clockMin(epochMs: Long, night: LocalDate, zone: ZoneId): Int =
    Duration.between(night.atTime(12, 0), Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime())
        .toMinutes().toInt()

/** "11 PM" for 660 minutes from noon. */
fun clockLabel(minFromNoon: Int): String = clock12(minFromNoon + 12 * 60)

/** The sleep-times axis for [values] (minutes from noon): whole hours with an hour to spare, inside noon to noon. */
fun clockAxis(values: List<Int>): IntRange {
    val lo = values.minOrNull() ?: (22 * 60 - 12 * 60)
    val hi = values.maxOrNull() ?: (31 * 60 - 12 * 60)
    return (Math.floorDiv(lo - 60, 60) * 60).coerceAtLeast(0)..(Math.floorDiv(hi + 119, 60) * 60).coerceAtMost(24 * 60)
}

/** A night's estimated sleep on the clock axis (minutes from noon): the WBTB time awake cut out, and its alarms. */
data class Window(val start: Int, val end: Int, val awake: IntRange? = null, val alarms: List<Int> = emptyList())

/** [row]'s window: estimated onset to final wake, without the WBTB ring to estimated falling back asleep. */
fun window(row: NightRow): Window? {
    val s = row.watch ?: return null
    val sleep = s.sleep ?: return null
    val zone = zoneOf(s)
    fun at(ms: Long) = clockMin(ms, row.night, zone)
    val w = s.wbtb
    val awakeFrom = w?.ringAtMs
    val awakeTo = w?.reOnsetMs ?: w?.backToSleepAtMs
    val awake = if (awakeFrom != null && awakeTo != null && awakeTo > awakeFrom) at(awakeFrom)..at(awakeTo) else null
    val alarms = listOfNotNull(w?.takeUnless { it.byUser }?.ringAtMs, s.wake?.ringAtMs).map(::at)
    return Window(at(sleep.onsetMs), at(sleep.finalWakeMs), awake, alarms)
}

/**
 * "I'm up now" on the watch: no alarm rang, the sleeper started the early wake-up. WakeAlarms logs its silent RING and
 * the STOPPED in one millisecond, which no answered ring can match.
 */
val WbtbOutcome.byUser: Boolean get() = ringAtMs != null && upAtMs == ringAtMs

/** A slot's window: the night's own, or for a week or month the mean start and end (no cut-out, no alarms). */
fun window(bin: Bin): Window? {
    val all = bin.nights.mapNotNull(::window)
    if (bin.unit == BinUnit.NIGHT || all.isEmpty()) return all.firstOrNull()
    return Window(all.map { it.start }.average().roundToInt(), all.map { it.end }.average().roundToInt())
}

/** The mean of [values] and their standard deviation, both rounded; null below [MIN_SPREAD_NIGHTS] values. */
fun spread(values: List<Int>): Pair<Int, Int>? {
    if (values.size < MIN_SPREAD_NIGHTS) return null
    val mean = values.average()
    return mean.roundToInt() to sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size).roundToInt()
}

/**
 * False only while a night's experiment arm could still colour its morning report: an EXPERIMENT night with no
 * morning (WAKE) report yet, before noon. NightSummary.LateCueOutcome: the arm is shown only next to that report.
 */
fun armShown(row: NightRow, today: LocalDate): Boolean =
    row.watch?.lateCues?.mode != "EXPERIMENT" || row.reports.any { it.after == "WAKE" } || row.night < today

/**
 * The late-cue experiment in [nights]: pairs of one cue and one no-cue night (the watch's "1/2" then "2/2") whose
 * morning reports both rate insight or say no dream was remembered (the watch asks insight only about a remembered
 * dream, so that morning is not lucid). Only the WAKE report counts, as the cues play after WBTB.
 */
data class Experiment(
    val pairs: Int,
    val cueLucid: Int,
    val noCueLucid: Int,
    /** Pairs where only the cue night, or only the no-cue night, had a lucid moment. */
    val onlyCue: Int,
    val onlyNoCue: Int,
    /** EXPERIMENT nights not in such a pair yet: waiting for the other night or for a report. */
    val waiting: Int,
) {
    val p: Double get() = signTestP(onlyCue, onlyNoCue)
    val clear: Boolean get() = pairs >= MIN_PAIRS && p < CLEAR_P
}

fun experiment(nights: List<NightRow>, today: LocalDate): Experiment {
    // A remembered dream whose insight was skipped stays unrated.
    fun morningInsight(n: NightRow) = n.reports.filter { it.after == "WAKE" }
        .mapNotNull { it.insight ?: if (it.recalled == "NO" || it.recalled == "WHITE") 0 else null }.maxOrNull()
    val shown = nights.filter { it.watch?.lateCues?.mode == "EXPERIMENT" && armShown(it, today) }.sortedBy { it.night }
    val pairs = mutableListOf<Pair<Int, Int>>() // (cue night insight, no-cue night insight)
    var first: NightRow? = null
    for (n in shown) {
        val arm = n.watch?.lateCues ?: continue
        when (arm.pair) {
            "1/2" -> first = n
            "2/2" -> {
                val a = first
                first = null
                val aArm = a?.watch?.lateCues ?: continue
                val ai = morningInsight(a)
                val ni = morningInsight(n)
                if (aArm.cueNight == arm.cueNight || ai == null || ni == null) continue
                pairs += if (arm.cueNight) ni to ai else ai to ni
            }
        }
    }
    fun isLucid(insight: Int) = insight >= LUCID_INSIGHT
    return Experiment(
        pairs = pairs.size,
        cueLucid = pairs.count { isLucid(it.first) },
        noCueLucid = pairs.count { isLucid(it.second) },
        onlyCue = pairs.count { isLucid(it.first) && !isLucid(it.second) },
        onlyNoCue = pairs.count { !isLucid(it.first) && isLucid(it.second) },
        waiting = shown.size - 2 * pairs.size,
    )
}

/** An arm's tile: a count until [MIN_PAIRS] pairs, then the share with the count behind it. */
fun armValue(lucid: Int, pairs: Int): String =
    if (pairs < MIN_PAIRS) "$lucid of $pairs" else "${(100f * lucid / pairs).roundToInt()}% ($lucid of $pairs)"

/** What the experiment shows so far, never more than the numbers carry; it never says the cues work. */
fun verdict(e: Experiment): String = when {
    e.pairs < MIN_PAIRS -> "${e.pairs} of $MIN_PAIRS pairs so far: results show after $MIN_PAIRS pairs."
    !e.clear -> "No clear difference yet."
    else -> "More lucid moments on nights ${if (e.onlyCue > e.onlyNoCue) "with" else "without"} soft sounds so " +
        "far. Small numbers can mislead: keep going."
}

/** The dream calendar's takeaway for screen readers. */
fun outcomeTakeaway(counts: Map<Outcome, Int>): String {
    val parts = Outcome.entries.reversed().mapNotNull { o ->
        val n = counts[o]?.takeIf { it > 0 } ?: return@mapNotNull null
        when (o) {
            Outcome.LUCID -> plural(n, "lucid moment")
            Outcome.DREAM -> plural(n, "dream")
            Outcome.WHITE -> "$n dreamed but can't recall it"
            Outcome.NO_DREAM -> "$n with no dream"
            Outcome.NO_REPORT -> "$n not answered"
        }
    }
    return if (parts.isEmpty()) "No recorded nights in this range yet." else "Mornings: ${parts.joinToString(", ")}."
}

/** Two-sided exact sign test: the chance of a split as uneven as [a] to [b] if both ways were equally likely. */
fun signTestP(a: Int, b: Int): Double {
    val n = a + b
    if (n == 0) return 1.0
    var term = 0.5.pow(n) // C(n, 0) / 2^n
    var tail = 0.0
    for (i in 0..minOf(a, b)) {
        tail += term
        term = term * (n - i) / (i + 1)
    }
    return minOf(1.0, 2 * tail)
}

/** The copy-from-watch line under the range filter. */
fun syncWords(copying: Boolean, result: SyncResult?): String? = when {
    copying -> "Copying from the watch…"
    result == null -> null
    result.error != null -> result.error
    result.files == 0 -> "Up to date with the watch"
    else -> "Copied ${plural(result.files, "new file")} from the watch"
}

/** "7 h 5 min", "45 min", "8 h". */
fun hm(min: Int): String = when {
    min < 60 -> "$min${NBSP}min"
    min % 60 == 0 -> "${min / 60}${NBSP}h"
    else -> "${min / 60}${NBSP}h ${min % 60}${NBSP}min"
}

fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

/** About [target] round steps (1, 2 or 5 times a power of ten, at least [minStep]) covering [lo] to [hi]. */
fun niceTicks(lo: Float, hi: Float, target: Int = 5, minStep: Float = 0f): List<Float> {
    val raw = ((hi - lo).takeIf { it > 0f } ?: 1f) / target
    val mag = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val step = maxOf(minStep, listOf(1f, 2f, 5f, 10f).map { it * mag }.first { it >= raw * 0.999f })
    val from = floor(lo / step) * step
    val n = ceil((hi - from) / step - 1e-4f).toInt().coerceAtLeast(1)
    return (0..n).map { from + it * step }
}

/** The one-line takeaway of a chart of [values], for screen readers. */
fun takeaway(what: String, values: List<Float>, format: (Float) -> String): String {
    if (values.isEmpty()) return "$what: nothing in this range yet."
    return "$what averaged ${format(values.average().toFloat())} over ${plural(values.size, "night")}, from " +
        "${format(values.min())} to ${format(values.max())}."
}

/** A stretch of the night's [Epochs]: mean movement share and heart rate (gaps skipped), minutes asleep and REM-likely. */
data class Bucket(val startMs: Long, val movement: Float?, val hr: Float?, val asleepMin: Int, val remMin: Int = 0)

/** [e] in buckets of [per] epochs; lists of unequal length are read as far as each goes. */
fun buckets(e: Epochs, per: Int): List<Bucket> = e.movement.indices.chunked(per.coerceAtLeast(1)).map { idx ->
    fun avg(list: List<Float?>) = idx.mapNotNull { list.getOrNull(it) }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    Bucket(
        e.startMs + idx.first() * e.stepS * 1000L,
        avg(e.movement),
        avg(e.hrBpm),
        idx.count { e.asleep.getOrNull(it) == true } * e.stepS / 60,
        idx.count { e.remLikely.getOrNull(it) == true } * e.stepS / 60,
    )
}

/** The runs of epochs estimated asleep, as epoch index ranges. */
fun asleepRuns(asleep: List<Boolean?>): List<IntRange> = buildList {
    var from = -1
    for (i in 0..asleep.size) {
        val on = asleep.getOrNull(i) == true
        if (on && from < 0) from = i
        if (!on && from >= 0) {
            add(from until i)
            from = -1
        }
    }
}

/** A moment of the night on its timeline: [label] in the list, [short] on the chart. */
data class Mark(val atMs: Long, val label: String, val short: String)

/** The night's alarms, WBTB steps and reports, in time order. */
fun marks(s: NightSummary): List<Mark> = buildList {
    s.wbtb?.let { w ->
        w.ringAtMs?.takeUnless { w.byUser }?.let { add(Mark(it, "Early wake-up", "Early")) }
        val up = if (w.byUser) "Up on your own for the early wake-up" else "Up for the early wake-up"
        w.upAtMs?.let { add(Mark(it, up, "Up")) }
        w.backToSleepAtMs?.let { add(Mark(it, "Back to bed", "Bed")) }
        w.reOnsetMs?.let { add(Mark(it, "Asleep again (estimated)", "Asleep")) }
    }
    s.wake?.let { a ->
        add(Mark(a.ringAtMs, "Wake-up alarm", "Alarm"))
        a.stoppedAtMs?.let { add(Mark(it, "\"I'm awake\"", "Awake")) }
    }
    for (r in s.reports) {
        add(Mark(r.atMs, questionsLabel(r.after), "Questions"))
    }
}.sortedBy { it.atMs }

/** What a dream report is called, by what it came [after]: LATER, from last night by hand, is no morning's. */
fun questionsLabel(after: String): String = when (after) {
    "WBTB" -> "Early wake-up questions"
    "LATER" -> "Dream questions"
    "NIGHT" -> "Dream saved in the night"
    else -> "Morning questions"
}

/** "Recorded 6:45 AM, after the wake-up alarm" from the watch's w_dream_HHmmss.m4a name and the report that made it. */
fun recordingLabel(name: String, reports: List<DreamReport>): String {
    val time = Regex("""w_dream_(\d{2})(\d{2})\d{2}\.m4a""").matchEntire(name)
        ?.let { "Recorded ${clock12(it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt())}" }
    val after = when (reports.firstOrNull { it.recording == name }?.after) {
        "WAKE" -> "after the wake-up alarm"
        "WBTB" -> "at the early wake-up"
        "NIGHT" -> "in the night"
        else -> null
    }
    return listOfNotNull(time ?: name, after).joinToString(", ")
}

/** The phone's sleep-onset cues of the night in one line. */
fun phoneLine(p: PhoneNight, night: LocalDate, zone: ZoneId): String =
    "${plural(p.loopsCompleted, "round")}, ${plural(p.cuesPlayed, "line")} spoken, " +
        "${clockLabel(clockMin(p.startMs, night, zone))}–${clockLabel(clockMin(p.endMs, night, zone))}"
