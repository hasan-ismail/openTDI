package com.opentdi.mobile.ui.stats

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.ui.Help
import com.opentdi.mobile.ui.Page
import com.opentdi.mobile.ui.Section
import com.opentdi.shared.stats.nightDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Stats tab (owner, 2026-10-28: "make nice graphs for everything like all your stats and everything like proper
 * visualizations of your sleep time and dream state over months and weeks"): a range filter, the copy-from-watch
 * line, headline figures, then Sleep, Dreams, Practice, Waking and Body; a tapped night opens its own page. Charts
 * follow the dataviz method (the form first, validated colours, thin marks, one y-axis each, a tooltip and a table).
 * Wellness only (sleep_and_dreams.md §1–2, §6): every sleep figure is an estimate; no stages, scores or red nights.
 */
@Composable
fun StatsScreen(stats: StatsViewModel) {
    val context = LocalContext.current
    val rows by stats.rows.collectAsStateWithLifecycle()
    val sync by stats.sync.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var today by remember { mutableStateOf(nightDate(System.currentTimeMillis(), zone)) }
    LifecycleResumeEffect(stats) {
        today = nightDate(System.currentTimeMillis(), zone) // a screen kept open past noon moves to the new night
        stats.open() // every return to the app copies from the watch, not only a tab switch
        onPauseOrDispose {}
    }
    val debuggable = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    var sample by rememberSaveable { mutableStateOf(false) }
    val data = if (sample) remember(today) { sampleNights(today, zone) } else rows
    var open by rememberSaveable { mutableStateOf<String?>(null) } // the night on show, as yyyy-MM-dd
    val night = open?.let { iso -> data?.firstOrNull { it.night.toString() == iso } }
    val dates = rememberDates()
    BackHandler(enabled = night != null) { open = null }
    val overview = rememberSaveableStateHolder() // keeps the scroll position while a night is open
    AnimatedContent(
        night,
        contentKey = { it?.night },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "night",
    ) { shown ->
        if (shown != null) {
            NightDetail(shown, today, dates) { open = null }
        } else {
            overview.SaveableStateProvider("overview") {
                Overview(stats, data, sync, sample, debuggable, today, dates, { sample = it }) {
                    open = it.night.toString()
                }
            }
        }
    }
}

@Composable
private fun Overview(
    stats: StatsViewModel,
    data: List<NightRow>?,
    sync: Sync,
    sample: Boolean,
    debuggable: Boolean,
    today: LocalDate,
    dates: Dates,
    onSample: (Boolean) -> Unit,
    onOpen: (NightRow) -> Unit,
) {
    val range = stats.range
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    Page("Stats") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in StatsRange.entries) {
                FilterChip(selected = r == range, onClick = { stats.range = r }, label = { Text(r.label) })
            }
        }
        syncWords(sync.copying, sync.result)?.let {
            val failed = !sync.copying && sync.result?.error != null
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failed) TextButton(onClick = { stats.open() }) { Text("Try again") }
        }
        if (sample) {
            Section(null) {
                Text("Sample nights", style = MaterialTheme.typography.titleMedium)
                Help("Made up to check the charts: not your data.")
            }
        }
        when {
            data == null -> Help("Reading your nights…")
            data.isEmpty() -> Section("Your nights will show here") {
                Text(
                    "After your first night with the watch, this tab charts your estimated sleep, alarms and " +
                        "dream questions over weeks and months.",
                )
                Help(
                    "Wear the watch to bed with OpenTDI open on it. In the morning, open this tab: the phone copies " +
                        "the night from the watch.",
                )
                Help("The dream charts start once you answer the dream questions on the watch.")
            }
            else -> {
                val from = rangeStart(range, today, data)
                val nights = remember(data, from, today) { inRange(data, from, today) }
                if (nights.isEmpty()) {
                    Section(null) {
                        Text("No nights in this range")
                        Help("Pick a longer range to see older nights.")
                    }
                } else {
                    val bins = remember(nights, from, today, firstDay) { bins(nights, from, today, firstDay) }
                    val reveal = remember(range, sample) { Animatable(0f) }
                    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
                    val days = ChronoUnit.DAYS.between(from, today).toInt() + 1
                    Figures(nights, days)
                    SleepSection(nights, bins, dates, onOpen, reveal)
                    DreamsSection(nights, bins, from, today, firstDay, dates, onOpen, reveal)
                    PracticeSection(nights, bins, today, dates, onOpen, reveal)
                    Section("Waking") {
                        if (nights.none { it.watch?.wake?.latencyS != null }) {
                            Help("How long waking takes shows here once the wake-up alarm has rung on the watch.")
                        } else {
                            SeriesChart(
                                "Minutes from the first ring to \"I'm awake\"", bins,
                                listOf(Series("Minutes", SERIES_1) { it.watch?.wake?.latencyS?.div(60f) }),
                                ::minutes, "", dates, onOpen, reveal,
                                note = "Snoozes count in. An alarm that rang out has no time.",
                            )
                        }
                    }
                    Section("Body") {
                        if (nights.none { it.watch?.sleep?.hrSleepMeanBpm != null }) {
                            Help("Heart rate while asleep shows here once the watch has estimated a night's sleep.")
                        } else {
                            val bpm = { v: Float -> "${v.roundToInt()} bpm" }
                            val averages = nights.mapNotNull { it.watch?.sleep?.hrSleepMeanBpm }
                            val lows = nights.mapNotNull { it.watch?.sleep?.hrSleepMinBpm }
                            SeriesChart(
                                "Heart rate while asleep (estimated)", bins,
                                listOf(
                                    Series("Average", SERIES_1) { it.watch?.sleep?.hrSleepMeanBpm },
                                    Series("Lowest", SERIES_2) { it.watch?.sleep?.hrSleepMinBpm },
                                ),
                                bpm, "", dates, onOpen, reveal,
                                takeaway = takeaway("Heart rate while asleep", averages, bpm) + " " +
                                    takeaway("The lowest each night", lows, bpm),
                                lines = true,
                                fromZero = false,
                                minStep = 1f,
                                note = "Beats per minute. Compare with your own nights: heart rate differs a lot " +
                                    "between people.",
                            )
                        }
                    }
                }
            }
        }
        if (debuggable) {
            Section(null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = sample, role = Role.Switch, onValueChange = onSample),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show sample nights", modifier = Modifier.weight(1f))
                    Switch(checked = sample, onCheckedChange = null)
                }
                Help("Debug builds only: made-up nights fill every chart.")
            }
        }
    }
}

/** The four headline figures, each with the nights behind it. */
@Composable
private fun Figures(nights: List<NightRow>, days: Int) {
    val sleep = nights.mapNotNull { it.sleepMin }
    val (dreams, mornings) = recall(nights)
    val (lucidMornings, rated) = lucid(nights)
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            "Estimated sleep",
            if (sleep.isEmpty()) "—" else hm(sleep.average().roundToInt()),
            if (sleep.isEmpty()) "No estimate yet" else "Average of ${plural(sleep.size, "night")}",
            Modifier.weight(1f),
        )
        Tile("Nights tracked", "${nights.count { it.recorded }}", "Of $days in this range", Modifier.weight(1f))
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            "Dream recall",
            if (mornings == 0) "—" else "${(100f * dreams / mornings).roundToInt()}%",
            if (mornings == 0) "After your first dream questions" else
                "$dreams of ${plural(mornings, "morning")} remembered a dream",
            Modifier.weight(1f),
        )
        Tile(
            "Lucid moments",
            if (rated == 0) "—" else "$lucidMornings",
            if (rated == 0) "After your first dream questions" else
                "Of ${plural(rated, "reported morning")} (knew you were dreaming: $LUCID_INSIGHT of 5 or more)",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Tile(label: String, value: String, behind: String, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        // Like Section: slightly see-through, so the night sky shows faintly behind.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)),
        modifier = modifier.fillMaxHeight(),
    ) { Figure(label, value, behind, Modifier.padding(16.dp)) }
}

/** A number with its label above and what it rests on below; the value in text colour, never a series colour. */
@Composable
private fun Figure(label: String, value: String, behind: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Help(behind)
    }
}

@Composable
private fun SleepSection(
    nights: List<NightRow>,
    bins: List<Bin>,
    dates: Dates,
    onOpen: (NightRow) -> Unit,
    reveal: Animatable<Float, AnimationVector1D>,
) {
    Section("Sleep") {
        Help("Estimated from movement and heart rate on the watch.")
        val mins = nights.mapNotNull { it.sleepMin }
        if (mins.isEmpty()) {
            Help("Estimated sleep shows here once the watch has tracked your sleep for a whole night.")
            return@Section
        }
        val reached = mins.count { it >= SLEEP_GUIDE_H * 60 }
        SeriesChart(
            "Estimated sleep per night", bins,
            listOf(Series("Estimated sleep", SERIES_1) { it.sleepMin?.div(60f) }),
            { hm((it * 60).roundToInt()) }, "h", dates, onOpen, reveal,
            takeaway = "Estimated sleep averaged ${hm(mins.average().roundToInt())} over " +
                "${plural(mins.size, "night")}; $reached of them reached 7 hours.",
            reference = SLEEP_GUIDE_H to "7 h",
            note = "Line: 7 hours, the least adults should regularly sleep (AASM and Sleep Research Society).",
        )
        val windows = remember(bins) { bins.map(::window) }
        val span = clockAxis(windows.filterNotNull().flatMap { listOf(it.start, it.end) + it.alarms })
        val step = if (span.last - span.first > 12 * 60) 180 else 120
        val ticks = generateSequence(span.first) { it + step }.takeWhile { it - step < span.last }
            .map { it.toFloat() }.toList()
        val own = nights.mapNotNull(::window)
        val starts = spread(own.map { it.start })
        val ends = spread(own.map { it.end })
        BinChart(
            title = "Estimated sleep times",
            takeaway = if (starts == null || ends == null) "Estimated sleep times of ${plural(own.size, "night")}." else
                "Usually asleep around ${clockLabel(starts.first)} and up around ${clockLabel(ends.first)}, estimated.",
            bins = bins,
            axis = YAxis(ticks, { clockLabel(it.roundToInt()) }, down = true),
            columns = listOf("Asleep (est.)", "Up (est.)", "Awake at WBTB"),
            cells = { i ->
                windows[i]?.let { w ->
                    val awake = w.awake?.let { "${clockLabel(it.first)}–${clockLabel(it.last)}" } ?: "—"
                    listOf(clockLabel(w.start), clockLabel(w.end), awake)
                }
            },
            dates = dates,
            onOpen = onOpen,
            legend = listOf(
                Key("Asleep (estimated)", SERIES_1, KeyShape.BAR),
                Key("Alarm", MaterialTheme.colorScheme.onSurface, KeyShape.LINE),
            ),
            note = "Evening at the top. A gap is the time awake at WBTB. Weeks and months show average " +
                "times.",
            height = 220.dp,
        ) { p, ink, selected -> drawWindows(p, ink, windows, selected, reveal.value) }
        Text("Regular times", style = MaterialTheme.typography.titleMedium)
        Text(
            if (starts == null || ends == null) {
                "Your usual times show after $MIN_SPREAD_NIGHTS nights with an estimate."
            } else {
                "Usually asleep around ${clockLabel(starts.first)}, give or take ${starts.second} min, and up " +
                    "around ${clockLabel(ends.first)}, give or take ${ends.second} min (estimated, " +
                    "${plural(own.size, "night")})."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DreamsSection(
    nights: List<NightRow>,
    bins: List<Bin>,
    from: LocalDate,
    today: LocalDate,
    firstDay: DayOfWeek,
    dates: Dates,
    onOpen: (NightRow) -> Unit,
    reveal: Animatable<Float, AnimationVector1D>,
) {
    Section("Dreams") {
        if (nights.none { it.reports.isNotEmpty() }) {
            Help("Your answers to the dream questions on the watch show here: answer them after the wake-up alarm.")
        }
        DreamCalendar(nights, from, today, firstDay, dates, onOpen, reveal)
        if (nights.any { it.insight != null || it.control != null }) {
            SeriesChart(
                "Knew you were dreaming / could steer it", bins,
                listOf(
                    Series("Knew", SERIES_1) { it.insight?.toFloat() },
                    Series("Steered", SERIES_2) { it.control?.toFloat() },
                ),
                { "${num(it)} of 5" }, "", dates, onOpen, reveal,
                takeaway = takeaway("Knew", nights.mapNotNull { it.insight?.toFloat() }) { "${num(it)} of 5" } +
                    " " + takeaway("Steered", nights.mapNotNull { it.control?.toFloat() }) { "${num(it)} of 5" },
                lines = true,
                fixed = 0f..5f,
                minStep = 1f,
                note = "0 to 5, from your answers on the watch. A lucid moment is $LUCID_INSIGHT or more on Knew.",
            )
        }
    }
}

@Composable
private fun PracticeSection(
    nights: List<NightRow>,
    bins: List<Bin>,
    today: LocalDate,
    dates: Dates,
    onOpen: (NightRow) -> Unit,
    reveal: Animatable<Float, AnimationVector1D>,
) {
    Section("Practice") {
        val wbtb = nights.mapNotNull { it.watch?.wbtb }
        if (wbtb.isEmpty()) {
            Help("WBTB nights show here once it is on in Settings.")
            return@Section
        }
        val rang = wbtb.count { it.ringAtMs != null }
        val skipped = wbtb.count { it.skipped != null }
        Text(
            "WBTB on ${plural(rang, "night")}" + if (skipped > 0) ", skipped on $skipped" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (wbtb.any { it.reOnsetLatencyMin != null }) {
            SeriesChart(
                "Minutes to fall back asleep after WBTB", bins,
                listOf(Series("Estimated", SERIES_1) { it.watch?.wbtb?.reOnsetLatencyMin?.toFloat() }),
                { "${it.roundToInt()} min" }, "", dates, onOpen, reveal,
                note = "Estimated: the watch counts your first 10 still minutes after the back-to-bed buzz as sleep.",
            )
        }
        if (nights.any { it.watch?.lateCues != null && armShown(it, today) }) {
            SeriesChart(
                "Soft sounds played", bins,
                listOf(
                    Series("Sounds", SERIES_1) { n ->
                        n.watch?.lateCues?.takeIf { armShown(n, today) }?.cuesPlayed?.toFloat()
                    },
                ),
                ::num, "", dates, onOpen, reveal,
                minStep = 1f,
                note = "Soft sounds after WBTB, once you were still again.",
            )
        }
        if (nights.any { it.watch?.lateCues?.mode == "EXPERIMENT" }) {
            val e = experiment(nights, today)
            Text("Do the soft sounds help?", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("With sounds", armValue(e.cueLucid, e.pairs), "nights with a lucid moment", Modifier.weight(1f))
                Figure("Without", armValue(e.noCueLucid, e.pairs), "nights with a lucid moment", Modifier.weight(1f))
            }
            Text(verdict(e), style = MaterialTheme.typography.bodyMedium)
            Help(
                "Each pair is one night with soft sounds and one without, picked at random by the watch. A night " +
                    "counts once its morning dream questions say whether you remember a dream and, if you do, " +
                    "answer \"Did you know you were dreaming?\"; a lucid moment is $LUCID_INSIGHT or more." +
                    if (e.waiting > 0) " ${plural(e.waiting, "night")} waiting for its pair or answers." else "",
            )
        }
    }
}

/** "3" or "2.5". */
internal fun num(v: Float): String =
    if (v == v.roundToInt().toFloat()) "${v.roundToInt()}" else String.format(Locale.ROOT, "%.1f", v)
