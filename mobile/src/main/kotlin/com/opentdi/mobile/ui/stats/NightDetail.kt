package com.opentdi.mobile.ui.stats

import android.media.MediaPlayer
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.stats.PhoneNight
import com.opentdi.mobile.ui.Help
import com.opentdi.mobile.ui.Page
import com.opentdi.mobile.ui.Section
import com.opentdi.shared.stats.DreamReport
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.time.NBSP
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One night (tap a night anywhere in Stats; owner, 2026-10-28): estimated sleep, the timeline, the morning reports,
 * the dream recordings, and the alarms and cues. Sleep figures are estimates, never stages.
 */
@Composable
internal fun NightDetail(row: NightRow, today: LocalDate, dates: Dates, onBack: () -> Unit) {
    val s = row.watch
    val zone = s?.let(::zoneOf) ?: ZoneId.systemDefault()
    fun at(ms: Long) = clockLabel(clockMin(ms, row.night, zone))
    Page(dates.night(row.night)) {
        TextButton(onClick = onBack, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text("Back to all nights")
        }
        Section("Estimated sleep") {
            val sleep = s?.sleep
            when {
                s == null -> Help("The watch didn't track your sleep this night.")
                sleep == null -> Help("Too little watch data this night to estimate sleep.")
                else -> {
                    Text(hm(sleep.sleepMin), style = MaterialTheme.typography.headlineMedium)
                    Help("Estimated from movement and heart rate on the watch.")
                    Text("Asleep about ${at(sleep.onsetMs)}, up for the day about ${at(sleep.finalWakeMs)} (estimated)")
                    Text("About ${hm(sleep.latencyMin)} to fall asleep (estimated)")
                    Text("About ${hm(sleep.wasoMin)} awake in between, ${plural(sleep.awakenings, "time")} (estimated)")
                    sleep.hrSleepMeanBpm?.let { mean ->
                        val low = sleep.hrSleepMinBpm?.let { ", lowest ${it.roundToInt()}" }.orEmpty()
                        Text("Heart rate while asleep (estimated): average ${mean.roundToInt()} bpm$low")
                    }
                    if (s.epochs.remLikely.isNotEmpty()) {
                        val rem = s.epochs.remLikely.count { it == true } * s.epochs.stepS / 60
                        Text("Possibly REM: about ${hm(rem)} (a rough guess, not a measured sleep stage)")
                    }
                }
            }
        }
        if (s != null && s.epochs.movement.isNotEmpty() && s.epochs.stepS > 0) {
            Section("The night") { Timeline(s, row.phone, row.night, zone) }
        }
        s?.reports?.forEach { r ->
            Section("${questionsLabel(r.after)}, ${at(r.atMs)}") {
                answers(r).forEach { (question, answer) ->
                    // A long answer goes under its question instead of squeezing it to a word a line.
                    FlowRow(Modifier.fillMaxWidth()) {
                        Text(
                            question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(answer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // A dream saved in the night asks no questions (the watch's shownQuestions): it is only the recording.
                when {
                    r.after == "NIGHT" -> if (r.recording != null) Help("Its recording is under Dream recordings.")
                    answers(r).isEmpty() -> Help("Every question was skipped.")
                }
            }
        }
        if (row.recordings.isNotEmpty()) {
            Section("Dream recordings") { Recordings(row.recordings, s?.reports.orEmpty()) }
        }
        Section("Alarms and sounds") {
            val lines = buildList {
                s?.wake?.let { a ->
                    val stopped = a.stoppedAtMs?.let { "\"I'm awake\" at ${at(it)}" } ?: "it rang out"
                    val took = a.latencyS?.let { ", ${minutes(it / 60f)} after the first ring" }.orEmpty()
                    val snoozes = if (a.snoozes > 0) ", ${plural(a.snoozes, "snooze")}" else ""
                    add("Wake-up alarm ${at(a.ringAtMs)}: $stopped$took$snoozes")
                }
                s?.wbtb?.let { w ->
                    if (w.skipped != null) {
                        add(
                            "Early wake-up skipped" + when (w.skipped) {
                                "MIN_SLEEP_BEFORE_WBTB" -> ": the watch had been tracking your sleep for less than 4½ hours"
                                "PASSED" -> ": its time had already passed (the watch app was off, or the setting changed)"
                                else -> ""
                            },
                        )
                    } else {
                        val steps = listOfNotNull(
                            w.ringAtMs?.let { ring ->
                                // On set-time nights too: how long after falling asleep the set time came.
                                val since = s.liveOnsetMs?.takeIf { it < ring }?.let {
                                    ", ${hm(((ring - it) / 60_000).toInt())} after the watch saw you fall asleep"
                                }.orEmpty()
                                val why = when (w.timedFrom.takeUnless { w.byUser }) {
                                    "NO_ONSET" -> " (your set time: the watch didn't see you fall asleep)"
                                    "LATE" -> " (your set time: you fell asleep too late to ring earlier)"
                                    "EARLY_START" -> " (your set time: the night started over 12 hours before it)"
                                    else -> ""
                                }
                                val what = if (w.byUser) "you got up on your own at" else "alarm"
                                "$what ${at(ring)}$since$why"
                            },
                            w.upAtMs?.takeUnless { w.byUser }?.let { "up ${at(it)}" },
                            w.backToSleepAtMs?.let { "back to bed ${at(it)}" },
                            w.reOnsetMs?.let { o ->
                                val later = w.reOnsetLatencyMin?.let { ", $it min later" }.orEmpty()
                                "asleep again about ${at(o)}$later (estimated)"
                            },
                        )
                        if (steps.isNotEmpty()) add("Early wake-up: ${steps.joinToString(", ")}")
                    }
                }
                s?.lateCues?.takeIf { armShown(row, today) }?.let { c ->
                    add(
                        when {
                            c.mode != "EXPERIMENT" -> "Soft sounds: ${plural(c.cuesPlayed, "sound")} played"
                            c.cueNight -> "Test night with soft sounds: ${plural(c.cuesPlayed, "sound")} played"
                            else -> "Test night without soft sounds: ${plural(c.wouldCue, "sound")} held back"
                        },
                    )
                }
                row.phone?.let { add("The voice on the phone: ${phoneLine(it, row.night, zone)}") }
            }
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (lines.isEmpty()) Help("No alarms or sounds this night.")
        }
    }
}

/** A report's answered questions in the watch's words; skipped ones are left out. */
private fun answers(r: DreamReport): List<Pair<String, String>> {
    fun yes(b: Boolean?) = b?.let { if (it) "Yes" else "No" }
    return listOf(
        "Do you remember a dream?" to when (r.recalled) {
            "YES" -> "Yes"
            "WHITE" -> "I dreamed, but can't recall it"
            "NO" -> "No"
            else -> null
        },
        "Did you know you were dreaming?" to r.insight?.let { "$it of 5" },
        "Could you steer it?" to r.control?.let { "$it of 5" },
        "Nightmare?" to yes(r.nightmare),
        "Did you dream that you woke up?" to yes(r.falseAwakening),
        "Did you hear the voice or a sound in your dream?" to yes(r.cueInDream),
        "Did the voice or a sound wake you?" to yes(r.cueWoke),
        "How refreshed?" to r.refreshed?.let { "$it of 5" },
        "Sleep paralysis?" to yes(r.sleepParalysis),
        "Unusual night?" to yes(r.unusualNight),
        // The watch's labels (DreamReportScreen LABELS), repeated: :mobile cannot read :wear.
        "Fell asleep during the guide?" to when (r.guideSleep) {
            "YES" -> "Yes"
            "NO" -> "No"
            "UNSURE" -> "Not sure"
            else -> null
        },
        "What was odd?" to when (r.odd) {
            "FORM" -> "How something looked"
            "ACTION" -> "What happened"
            "CONTEXT" -> "Where you were"
            "NONE" -> "Nothing odd"
            else -> null
        },
        "Did one of your signs show up?" to yes(r.signSeen),
    ).mapNotNull { (q, a) -> a?.let { q to it } }
}

internal fun minutes(m: Float) = String.format(Locale.ROOT, "%.1f${NBSP}min", m)

/** Plays one recording at a time; the page releases it when it closes. */
private class OnePlayer {
    private var player: MediaPlayer? = null
    var playing by mutableStateOf<File?>(null)
        private set
    var failed by mutableStateOf<File?>(null)
        private set

    fun toggle(file: File) {
        val same = playing == file
        stop()
        if (same) return
        val p = MediaPlayer()
        player = p
        try {
            p.setDataSource(file.path)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            playing = file
            failed = null
        } catch (e: Exception) { // IOException or an illegal state from a damaged file: say so, never crash
            stop()
            failed = file
        }
    }

    fun stop() {
        player?.release()
        player = null
        playing = null
    }
}

@Composable
private fun Recordings(files: List<File>, reports: List<DreamReport>) {
    val player = remember { OnePlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    for (f in files) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recordingLabel(f.name, reports), style = MaterialTheme.typography.bodyMedium)
                if (player.failed == f) Text("Can't play this recording.", color = MaterialTheme.colorScheme.error)
            }
            FilledTonalButton(onClick = { player.toggle(f) }) { Text(if (player.playing == f) "Stop" else "Play") }
        }
    }
}

// The text bands at the normal font size; larger text makes them taller (Timeline).
private val LANE = 52.dp // three rows of event names
private val BAND_TITLE = 18.dp
private val MOVE_H = 56.dp
private val BAND_GAP = 12.dp
private val HR_H = 110.dp
private val X_BAND = 22.dp

/**
 * The night's timeline: movement as thin bars and heart rate as a line, two charts with their own y-axes sharing one
 * time axis, the estimated asleep time shaded behind both, the possible REM as a strip between them, and the alarms,
 * WBTB steps, reports and the phone's cues marked above. Tap or drag for the values at a moment; the table lists half
 * hours.
 */
@Composable
private fun Timeline(s: NightSummary, phone: PhoneNight?, night: LocalDate, zone: ZoneId) {
    val e = s.epochs
    val stepMs = e.stepS * 1000L
    val t0 = e.startMs
    val t1 = e.startMs + e.movement.size * stepMs
    val per = ((e.movement.size + 119) / 120).coerceAtLeast(1) // at most about 120 bars
    val bs = remember(e) { buckets(e, per) }
    val runs = remember(e) { asleepRuns(e.asleep) }
    val remRuns = remember(e) { asleepRuns(e.remLikely) }
    val hasRem = e.remLikely.isNotEmpty()
    val events = remember(s) { marks(s).filter { it.atMs in t0..t1 } }
    val movePct = (bs.mapNotNull { it.movement }.maxOrNull() ?: 0f) * 100
    val moveAxis = YAxis(niceTicks(0f, maxOf(movePct, 1f), 2), { "${it.roundToInt()}%" })
    val hrs = bs.mapNotNull { it.hr }
    val hrAxis = YAxis(niceTicks(hrs.minOrNull() ?: 50f, hrs.maxOrNull() ?: 70f, 3), { "${it.roundToInt()}" })
    val ink = rememberInk()
    val colors = MaterialTheme.colorScheme
    val gutter = LocalDensity.current.axisGutter(ink, moveAxis, hrAxis)
    val right = with(LocalDensity.current) { 4.dp.toPx() }
    val line = with(LocalDensity.current) { ink.lineH.toDp() }
    val lane = maxOf(LANE, line * 3 + 4.dp)
    val bandTitle = maxOf(BAND_TITLE, line + 2.dp)
    val xBand = maxOf(X_BAND, line + 6.dp)
    var selected by remember(s) { mutableStateOf<Long?>(null) }
    var table by rememberSaveable { mutableStateOf(false) }
    fun clock(ms: Long) = clockLabel(clockMin(ms, night, zone))
    fun timeAt(x: Float, width: Int) = t0 + (((x - gutter) / (width - gutter - right)).coerceIn(0f, 1f) * (t1 - t0)).toLong()
    val takeaway = buildString {
        append("Night timeline from ${clock(t0)} to ${clock(t1)}.")
        s.sleep?.let { append(" Estimated asleep ${hm(it.sleepMin)}.") }
        if (hasRem) append(" Possibly REM about ${hm(e.remLikely.count { it == true } * e.stepS / 60)}, a rough guess.")
        if (events.isNotEmpty()) append(" " + events.joinToString(", ") { "${it.label} ${clock(it.atMs)}" } + ".")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Legend(
            listOfNotNull(
                Key("Asleep (estimated)", colors.onSurface.copy(alpha = 0.16f), KeyShape.BAR), // the wash, a little stronger to show
                Key("Movement", SERIES_2, KeyShape.BAR),
                Key("Heart rate", SERIES_1, KeyShape.LINE),
                Key("Possible REM (rough guess)", SERIES_3, KeyShape.BAR).takeIf { hasRem },
            ),
        )
        Crossfade(table, animationSpec = tween(250), label = "table") { asTable ->
            if (asTable) {
                Table(
                    listOf("Time", "Movement", "Heart rate", "Asleep (est.)", "REM (guess)"),
                    buckets(e, (30 * 60 / e.stepS).coerceAtLeast(1)).map { b ->
                        listOf(
                            clock(b.startMs),
                            b.movement?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                            b.hr?.let { "${it.roundToInt()} bpm" } ?: "—",
                            "${b.asleepMin} min",
                            if (hasRem) "${b.remMin} min" else "—",
                        ) to null
                    },
                    onOpen = {},
                )
            } else {
                var widthPx by remember { mutableIntStateOf(0) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(lane + bandTitle + MOVE_H + BAND_GAP + bandTitle + HR_H + xBand)
                        .onSizeChanged { widthPx = it.width }
                        .semantics { contentDescription = takeaway }
                        .pointerInput(s) {
                            detectTapGestures { o ->
                                val t = timeAt(o.x, size.width)
                                selected = if (selected?.let { abs(it - t) < 5 * 60_000L } == true) null else t
                            }
                        }
                        .pointerInput(s) {
                            detectHorizontalDragGestures(
                                onDragStart = { selected = timeAt(it.x, size.width) },
                            ) { change, _ -> selected = timeAt(change.position.x, size.width) }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val left = gutter
                        val end = size.width - right
                        fun x(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * (end - left)
                        val moveTop = (lane + bandTitle).toPx()
                        val moveBottom = moveTop + MOVE_H.toPx()
                        val hrTop = moveBottom + (BAND_GAP + bandTitle).toPx()
                        val hrBottom = hrTop + HR_H.toPx()
                        for (r in runs) { // the estimated asleep time, a wash behind both charts
                            val a = x(t0 + r.first * stepMs)
                            val width = x(t0 + (r.last + 1) * stepMs) - a
                            drawRect(colors.onSurface.copy(alpha = 0.07f), Offset(a, moveTop), Size(width, hrBottom - moveTop))
                        }
                        val move = Plot(left, moveTop, end, moveBottom, 1, moveAxis)
                        val heart = Plot(left, hrTop, end, hrBottom, 1, hrAxis)
                        drawGrid(move, ink)
                        drawGrid(heart, ink)
                        for ((title, top) in listOf("Movement, share of the minute" to moveTop, "Heart rate, bpm" to hrTop)) {
                            // One line, cut with "…" when large text makes it wider than the chart; the legend says it too.
                            val l = ink.measurer.measure(
                                title, ink.label, TextOverflow.Ellipsis, maxLines = 1,
                                constraints = Constraints(maxWidth = (end - left).toInt().coerceAtLeast(0)),
                            )
                            drawText(l, topLeft = Offset(left, top - bandTitle.toPx()))
                        }
                        val slot = (end - left) * per * stepMs / (t1 - t0)
                        val w = (slot - 1.dp.toPx()).coerceAtLeast(1.dp.toPx())
                        for (b in bs) {
                            val top = move.y((b.movement ?: continue) * 100)
                            if (moveBottom - top >= 0.5f) {
                                drawRect(SERIES_2, Offset(x(b.startMs), top), Size(w, moveBottom - top))
                            }
                        }
                        val remY = moveBottom + BAND_GAP.toPx() / 2 // its own lane in the gap between the charts
                        for (r in remRuns) {
                            val a = x(t0 + r.first * stepMs)
                            val to = Offset(maxOf(x(t0 + (r.last + 1) * stepMs), a + 2.dp.toPx()), remY)
                            drawLine(SERIES_3, Offset(a, remY), to, 4.dp.toPx(), StrokeCap.Round)
                        }
                        var previous: Offset? = null
                        for (b in bs) {
                            val point = b.hr?.let { Offset(x(b.startMs) + slot / 2, heart.y(it)) }
                            if (point != null && previous != null) {
                                drawLine(SERIES_1, previous, point, 2.dp.toPx(), StrokeCap.Round)
                            }
                            previous = point
                        }
                        // Event names in up to three rows; a name with no room is left to the list and the readout.
                        val rowEnds = FloatArray(3) { Float.NEGATIVE_INFINITY }
                        fun label(text: String, at: Float) {
                            val l = ink.measurer.measure(text, ink.label)
                            val lx = (at - l.size.width / 2f).coerceIn(0f, maxOf(0f, size.width - l.size.width))
                            val row = rowEnds.indexOfFirst { it + 4.dp.toPx() <= lx }
                            if (row < 0) return
                            drawText(l, topLeft = Offset(lx, row * maxOf(16.dp.toPx(), ink.lineH.toFloat())))
                            rowEnds[row] = lx + l.size.width
                        }
                        phone?.let { p ->
                            val a = x(p.startMs.coerceIn(t0, t1))
                            val b = x(p.endMs.coerceIn(t0, t1))
                            val y = lane.toPx() - 4.dp.toPx()
                            val to = Offset(maxOf(b, a + 2.dp.toPx()), y)
                            drawLine(colors.onSurfaceVariant, Offset(a, y), to, 4.dp.toPx(), StrokeCap.Round)
                            label("Voice", a + 12.dp.toPx())
                        }
                        for (m in events) {
                            val mx = x(m.atMs)
                            val hairline = colors.onSurfaceVariant.copy(alpha = 0.5f)
                            drawLine(hairline, Offset(mx, lane.toPx() - 2.dp.toPx()), Offset(mx, hrBottom), 1.dp.toPx())
                            label(m.short, mx)
                        }
                        val hours = when {
                            t1 - t0 <= 4 * 3_600_000L -> 1
                            t1 - t0 <= 10 * 3_600_000L -> 2
                            else -> 3
                        }
                        var h = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t0), zone).truncatedTo(ChronoUnit.HOURS)
                            .plusHours(1)
                        while (h.toInstant().toEpochMilli() < t1) {
                            val ms = h.toInstant().toEpochMilli()
                            if (h.hour % hours == 0) {
                                val l = ink.measurer.measure(clock(ms), ink.label)
                                val lx = (x(ms) - l.size.width / 2f).coerceIn(left, maxOf(left, end - l.size.width))
                                drawText(l, topLeft = Offset(lx, hrBottom + 4.dp.toPx()))
                            }
                            h = h.plusHours(1)
                        }
                        selected?.let { t ->
                            val crosshair = colors.onSurface.copy(alpha = 0.6f)
                            drawLine(crosshair, Offset(x(t), moveTop), Offset(x(t), hrBottom), 1.dp.toPx())
                        }
                    }
                    selected?.let { t ->
                        val b = bs[((t - t0) / (per * stepMs)).toInt().coerceIn(0, bs.size - 1)]
                        val asleep = e.asleep.getOrNull(((t - t0) / stepMs).toInt())
                        val rem = e.remLikely.getOrNull(((t - t0) / stepMs).toInt()) == true
                        val values = listOfNotNull(
                            b.hr?.let { "heart rate" to "${it.roundToInt()} bpm" },
                            b.movement?.let { "movement" to "${(it * 100).roundToInt()}%" },
                            asleep?.let {
                                "" to when {
                                    !it -> "Awake (estimated)"
                                    rem -> "Asleep, possibly REM (rough guess)"
                                    else -> "Asleep (estimated)"
                                }
                            },
                        ) + events.filter { abs(it.atMs - t) <= 10 * 60_000L }.map { it.label to clock(it.atMs) }
                        Floating(gutter + (t - t0).toFloat() / (t1 - t0) * (widthPx - gutter - right)) {
                            Readout(clock(t), values.ifEmpty { null }, null) {}
                        }
                    }
                }
            }
        }
        if (events.isNotEmpty()) Help(events.joinToString(" · ") { "${clock(it.atMs)} ${it.label}" })
        TextButton(onClick = { table = !table }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(if (table) "Show as chart" else "Show as table")
        }
    }
}
