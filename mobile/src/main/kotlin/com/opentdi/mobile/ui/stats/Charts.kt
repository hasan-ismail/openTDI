package com.opentdi.mobile.ui.stats

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.ui.Help
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// The chart colours (dataviz method, owner request 2026-10-28). Checked with the skill's validate_palette.js
// --mode dark against the card (#111017) and the page (#060609): lightness band, chroma floor and 3:1 contrast pass;
// worst adjacent CVD ΔE 17.3 (deutan), normal-vision ΔE 24.6. Slots 7 (violet, beside the app's lavender accent) and
// 3 (aqua) of the skill's reference palette; the only status colour on Stats is the error pink (ΔE 19.2 from violet).
internal val SERIES_1 = Color(0xFF9085E9)
internal val SERIES_2 = Color(0xFF199E70)
// The night timeline's possible-REM strip: dark slot 2 (orange). All three --pairs all on the card pass: worst CVD
// ΔE 9.4 (deutan, against aqua), normal-vision ΔE 24.6. Slot 1 (blue) failed beside violet (ΔE 1.9 protan).
internal val SERIES_3 = Color(0xFFD95926)

// Morning outcomes are ordered, so one hue steps from dark (no dream) to light (lucid): the reference blue ramp's
// steps 600/450/350/250, checked --ordinal on both surfaces (the darkest at 2.33:1 on the card).
internal val OUTCOME_FILL = mapOf(
    Outcome.NO_DREAM to Color(0xFF184F95),
    Outcome.WHITE to Color(0xFF2A78D6),
    Outcome.DREAM to Color(0xFF5598E7),
    Outcome.LUCID to Color(0xFF86B6EF),
)

private val GUTTER = 40.dp // y-axis tick labels
private val TOP = 10.dp
private val X_BAND = 22.dp // x-axis dates
private val BAR_MAX = 16.dp
private val GAP = 2.dp // between touching marks

/** The non-data ink of every chart: hairlines one step off the card, text in the theme's text colours. */
internal class Ink(
    val measurer: TextMeasurer,
    val label: TextStyle,
    val grid: Color,
    val axis: Color,
    val text: Color,
    val surface: Color,
)

@Composable
internal fun rememberInk(): Ink {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium.copy(color = c.onSurfaceVariant, fontFeatureSettings = "tnum")
    return remember(measurer, style, c) {
        Ink(measurer, style, c.outlineVariant, c.outline, c.onSurface, c.surfaceContainer)
    }
}

/** Date words as the phone's language writes them ("Oct 27" or "27 Oct"). */
internal class Dates(locale: Locale) {
    private fun pattern(skeleton: String, locale: Locale) =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private val day = pattern("MMMd", locale)
    private val weekday = pattern("EEEMMMd", locale)
    private val month = pattern("MMM", locale)
    private val monthYear = pattern("MMMMy", locale)

    fun short(d: LocalDate): String = day.format(d)
    fun night(d: LocalDate) = "Night of ${weekday.format(d)}"
    fun axis(b: Bin): String = if (b.unit == BinUnit.MONTH) month.format(b.start) else day.format(b.start)
    fun bin(b: Bin): String = when (b.unit) {
        BinUnit.NIGHT -> night(b.start)
        BinUnit.WEEK -> "Week of ${day.format(b.start)}"
        BinUnit.MONTH -> monthYear.format(b.start)
    }
}

@Composable
internal fun rememberDates(): Dates {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { Dates(locale) }
}

/** A chart's one y-axis: [ticks] low to high, drawn bottom to top, or top to bottom when [down] (a clock). */
internal class YAxis(val ticks: List<Float>, val label: (Float) -> String, val down: Boolean = false) {
    val lo get() = ticks.first()
    val hi get() = ticks.last()

    /** 0 at the top of the plot, 1 at the bottom. */
    fun frac(v: Float): Float {
        val f = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        return if (down) f else 1f - f
    }
}

/** The plot area of a chart of [n] slots, in px. */
internal class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float, val n: Int, val axis: YAxis) {
    val slot get() = (right - left) / n
    fun x(i: Int) = left + slot * (i + 0.5f)
    fun y(v: Float) = top + (bottom - top) * axis.frac(v)
}

/** The slot under [x] in a chart [width] px wide. */
private fun slotAt(x: Float, width: Int, gutter: Float, n: Int) =
    ((x - gutter) / ((width - gutter) / n)).toInt().coerceIn(0, n - 1)

/** One measure per night; a week or month shows the mean of its nights. */
internal class Series(val name: String, val color: Color, val of: (NightRow) -> Float?)

/**
 * A chart of [bins] on the night axis: its title, legend, the plot ([marks] over the y-axis [axis] and the dates),
 * a tooltip card for the slot tapped or dragged over (hit target: the whole slot), and on request a table instead,
 * whose night rows open the night. [cells] gives slot i's values for [columns], null when it has none.
 */
@Composable
internal fun BinChart(
    title: String,
    takeaway: String,
    bins: List<Bin>,
    axis: YAxis,
    columns: List<String>,
    cells: (Int) -> List<String>?,
    dates: Dates,
    onOpen: (NightRow) -> Unit,
    legend: List<Key> = emptyList(),
    note: String? = null,
    height: Dp = 170.dp,
    marks: DrawScope.(Plot, Ink, Int?) -> Unit,
) {
    val ink = rememberInk()
    var table by rememberSaveable(title) { mutableStateOf(false) }
    var selected by remember(bins) { mutableStateOf<Int?>(null) }
    val gutter = with(LocalDensity.current) { GUTTER.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (legend.isNotEmpty()) Legend(legend)
        Crossfade(table, animationSpec = tween(250), label = "table") { asTable ->
            if (asTable) {
                val unit = when (bins.firstOrNull()?.unit) {
                    BinUnit.WEEK -> "Week"
                    BinUnit.MONTH -> "Month"
                    else -> "Night"
                }
                Table(
                    listOf(unit) + columns,
                    bins.indices.mapNotNull { i ->
                        val b = bins[i]
                        val date = if (b.unit == BinUnit.MONTH) dates.axis(b) else dates.short(b.start)
                        cells(i)?.let { (listOf(date) + it) to b.only }
                    },
                    onOpen,
                )
            } else {
                var widthPx by remember { mutableIntStateOf(0) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .onSizeChanged { widthPx = it.width }
                        .semantics { contentDescription = takeaway }
                        .pointerInput(bins) {
                            detectTapGestures { o ->
                                val i = slotAt(o.x, size.width, gutter, bins.size)
                                selected = if (selected == i) null else i
                            }
                        }
                        .pointerInput(bins) {
                            detectHorizontalDragGestures(
                                onDragStart = { selected = slotAt(it.x, size.width, gutter, bins.size) },
                            ) { change, _ -> selected = slotAt(change.position.x, size.width, gutter, bins.size) }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val bottom = size.height - X_BAND.toPx()
                        val p = Plot(gutter, TOP.toPx(), size.width - 4.dp.toPx(), bottom, bins.size, axis)
                        drawGrid(p, ink)
                        selected?.let { i ->
                            val wash = ink.text.copy(alpha = 0.06f) // the tapped slot, even when it has no mark
                            drawRect(wash, Offset(p.x(i) - p.slot / 2, p.top), Size(p.slot, p.bottom - p.top))
                        }
                        marks(p, ink, selected)
                        drawDates(p, ink, bins, dates)
                    }
                    selected?.let { i ->
                        val slot = (widthPx - gutter) / bins.size
                        Floating(gutter + slot * (i + 0.5f)) {
                            Readout(dates.bin(bins[i]), cells(i)?.let { columns.zip(it) }, bins[i].only, onOpen)
                        }
                    }
                }
            }
        }
        note?.let { Help(it) }
        TextButton(onClick = { table = !table }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(if (table) "Show as chart" else "Show as table")
        }
    }
}

/** The one night of a night slot, which a tap can open. */
internal val Bin.only: NightRow? get() = nights.singleOrNull()?.takeIf { unit == BinUnit.NIGHT }

/**
 * [series] per slot as bars (one series, rounded at the value end) or as lines with dots (two), on one y-axis from
 * zero or fitted ([fromZero]) or [fixed]; [reference] draws a labelled line. Values read in [unit].
 */
@Composable
internal fun SeriesChart(
    title: String,
    bins: List<Bin>,
    series: List<Series>,
    format: (Float) -> String,
    unit: String,
    dates: Dates,
    onOpen: (NightRow) -> Unit,
    reveal: Animatable<Float, AnimationVector1D>,
    takeaway: String = takeaway(title, bins.flatMap { it.nights }.mapNotNull(series.first().of), format),
    lines: Boolean = false,
    fromZero: Boolean = true,
    fixed: ClosedFloatingPointRange<Float>? = null,
    minStep: Float = 0f,
    reference: Pair<Float, String>? = null,
    note: String? = null,
) {
    val means = remember(bins, series) { series.map { s -> bins.map { b -> b.nights.mean(s.of) } } }
    val axis = remember(means, fixed, reference) {
        val values = means.flatten().mapNotNull { it?.value }
        val lo = fixed?.start ?: if (fromZero) 0f else values.minOrNull() ?: 0f
        val hi = fixed?.endInclusive ?: maxOf(values.maxOrNull() ?: lo + 1f, reference?.first ?: lo)
        val ticks = niceTicks(lo, hi, if (fromZero) 5 else 4, minStep)
        val decimals = ticks.zipWithNext().any { (a, b) -> b - a < 1f }
        YAxis(ticks, { v -> String.format(Locale.ROOT, if (decimals) "%.1f" else "%.0f", v) + " $unit".trimEnd() })
    }
    BinChart(
        title = title,
        takeaway = takeaway,
        bins = bins,
        axis = axis,
        columns = series.map { it.name },
        cells = { i ->
            val ms = means.map { it[i] }
            if (ms.all { it == null }) null else ms.map { m ->
                when {
                    m == null -> "—"
                    bins[i].unit == BinUnit.NIGHT -> format(m.value)
                    else -> "${format(m.value)} · ${plural(m.nights, "night")}"
                }
            }
        },
        dates = dates,
        onOpen = onOpen,
        legend = if (series.size < 2) emptyList() else {
            series.map { Key(it.name, it.color, if (lines) KeyShape.LINE else KeyShape.BAR) }
        },
        note = note,
    ) { p, ink, selected ->
        reference?.let { (v, label) -> drawReference(p, ink, v, label) }
        if (lines) {
            drawLines(p, ink, means, series, selected, reveal.value)
        } else {
            drawBars(p, means[0], series[0].color, selected, reveal.value)
        }
    }
}

/** [s] in the axis style, its anchor [alignX] and [alignY] of the way across and down placed at ([x], [y]). */
private fun DrawScope.text(ink: Ink, s: String, x: Float, y: Float, alignX: Float, alignY: Float) {
    val l = ink.measurer.measure(s, ink.label)
    drawText(l, topLeft = Offset(x - l.size.width * alignX, y - l.size.height * alignY))
}

/** Hairline gridlines at the ticks (the baseline one step stronger) and their labels in the gutter. */
internal fun DrawScope.drawGrid(p: Plot, ink: Ink) {
    for (t in p.axis.ticks) {
        val y = p.y(t)
        val base = !p.axis.down && t == p.axis.lo
        drawLine(if (base) ink.axis else ink.grid, Offset(p.left, y), Offset(p.right, y), 1.dp.toPx())
        text(ink, p.axis.label(t), p.left - 6.dp.toPx(), y, alignX = 1f, alignY = 0.5f)
    }
}

/** About four date labels under the slots, the newest always labelled. */
private fun DrawScope.drawDates(p: Plot, ink: Ink, bins: List<Bin>, dates: Dates) {
    val every = ceil(bins.size / 4f).toInt().coerceAtLeast(1)
    for (i in bins.indices) {
        if ((bins.size - 1 - i) % every != 0) continue
        val l = ink.measurer.measure(dates.axis(bins[i]), ink.label)
        val x = (p.x(i) - l.size.width / 2f).coerceIn(p.left, (p.right - l.size.width).coerceAtLeast(p.left))
        drawText(l, topLeft = Offset(x, p.bottom + 4.dp.toPx()))
    }
}

/** A solid reference line with its label on a small card-coloured patch, so bars behind it never hide it. */
private fun DrawScope.drawReference(p: Plot, ink: Ink, value: Float, label: String) {
    val y = p.y(value)
    drawLine(ink.text.copy(alpha = 0.55f), Offset(p.left, y), Offset(p.right, y), 1.dp.toPx())
    val l = ink.measurer.measure(label, ink.label)
    val at = Offset(p.left + 4.dp.toPx(), y - l.size.height - 2.dp.toPx())
    val patch = Size(l.size.width + 6.dp.toPx(), l.size.height.toFloat())
    drawRoundRect(ink.surface.copy(alpha = 0.85f), at - Offset(3.dp.toPx(), 0f), patch, CornerRadius(4.dp.toPx()))
    drawText(l, topLeft = at)
}

/** Columns from the baseline, at most 16 dp wide with a 2 dp gap, rounded 4 dp at the value end; [grow] 0–1. */
private fun DrawScope.drawBars(p: Plot, means: List<Mean?>, color: Color, selected: Int?, grow: Float) {
    val w = minOf(BAR_MAX.toPx(), p.slot - GAP.toPx()).coerceAtLeast(1.dp.toPx())
    val base = p.y(p.axis.lo)
    means.forEachIndexed { i, m ->
        if (m == null) return@forEachIndexed
        val top = base - (base - p.y(m.value)) * grow
        if (base - top < 0.5f) return@forEachIndexed
        val r = CornerRadius(minOf(4.dp.toPx(), w / 2, base - top))
        val bar = RoundRect(p.x(i) - w / 2, top, p.x(i) + w / 2, base, r, r, CornerRadius.Zero, CornerRadius.Zero)
        drawPath(Path().apply { addRoundRect(bar) }, if (i == selected) lerp(color, Color.White, 0.25f) else color)
    }
}

/**
 * 2 dp lines between neighbouring slots that both have a value (a gap breaks the line), dots with a 2 dp ring in the
 * card colour, and each series' name at its last dot when the names do not collide (the legend always says it too).
 * Two series sit 2.5 dp either side of the slot's centre, so equal values (insight 0, control 0) both stay visible.
 */
private fun DrawScope.drawLines(
    p: Plot,
    ink: Ink,
    means: List<List<Mean?>>,
    series: List<Series>,
    selected: Int?,
    alpha: Float,
) {
    val r = 4.dp.toPx()
    fun x(k: Int, i: Int) = p.x(i) + (k - (means.size - 1) / 2f) * 5.dp.toPx()
    means.forEachIndexed { k, ms ->
        for (i in 0 until ms.size - 1) {
            val a = ms[i] ?: continue
            val b = ms[i + 1] ?: continue
            val from = Offset(x(k, i), p.y(a.value))
            drawLine(series[k].color, from, Offset(x(k, i + 1), p.y(b.value)), 2.dp.toPx(), StrokeCap.Round, alpha = alpha)
        }
    }
    means.forEachIndexed { k, ms ->
        ms.forEachIndexed { i, m ->
            if (m == null) return@forEachIndexed
            val c = Offset(x(k, i), p.y(m.value))
            val dot = if (i == selected) r * 1.5f else r
            drawCircle(ink.surface, dot + 2.dp.toPx(), c, alpha = alpha)
            drawCircle(series[k].color, dot, c, alpha = alpha)
        }
    }
    val labels = means.mapIndexedNotNull { k, ms ->
        val i = ms.indexOfLast { it != null }
        val m = ms.getOrNull(i) ?: return@mapIndexedNotNull null
        val l = ink.measurer.measure(series[k].name, ink.label)
        val x = (p.x(i) - l.size.width / 2f).coerceIn(p.left, (p.right - l.size.width).coerceAtLeast(p.left))
        val y = (p.y(m.value) - r - 4.dp.toPx() - l.size.height).coerceAtLeast(0f)
        Rect(Offset(x, y), Size(l.size.width.toFloat(), l.size.height.toFloat())) to l
    }
    if (labels.size > 1 && labels[0].first.overlaps(labels[1].first)) return
    for ((rect, l) in labels) drawText(l, topLeft = rect.topLeft, alpha = alpha)
}

/** Floating bars on the clock axis from estimated sleep to waking, the WBTB time awake cut out, alarms as ticks. */
internal fun DrawScope.drawWindows(p: Plot, ink: Ink, windows: List<Window?>, selected: Int?, alpha: Float) {
    val w = minOf(BAR_MAX.toPx(), p.slot - GAP.toPx()).coerceAtLeast(1.dp.toPx())
    windows.forEachIndexed { i, win ->
        if (win == null) return@forEachIndexed
        val color = if (i == selected) lerp(SERIES_1, Color.White, 0.25f) else SERIES_1
        val parts = win.awake?.let { listOf(win.start to it.first, it.last to win.end) } ?: listOf(win.start to win.end)
        for ((a, b) in parts) {
            val top = p.y(a.coerceAtLeast(win.start).toFloat())
            val bottom = p.y(b.coerceAtMost(win.end).toFloat())
            if (bottom - top < 1f) continue
            val corner = CornerRadius(minOf(4.dp.toPx(), w / 2)) // both ends are values
            drawRoundRect(color, Offset(p.x(i) - w / 2, top), Size(w, bottom - top), corner, alpha = alpha)
        }
        for (alarm in win.alarms) {
            val y = p.y(alarm.toFloat())
            val half = w / 2 + 4.dp.toPx()
            drawLine(ink.text, Offset(p.x(i) - half, y), Offset(p.x(i) + half, y), 2.dp.toPx(), alpha = 0.8f * alpha)
        }
    }
}

enum class KeyShape { BAR, LINE, RING, DOT }

/** A legend entry: a small mark like the chart's, then the label in text colour. */
data class Key(val label: String, val color: Color, val shape: KeyShape)

@Composable
internal fun Legend(keys: List<Key>) {
    val ring = MaterialTheme.colorScheme.outline
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in keys) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp)) {
                    val r = CornerRadius(3.dp.toPx())
                    when (k.shape) {
                        KeyShape.BAR -> drawRoundRect(k.color, cornerRadius = r)
                        KeyShape.LINE ->
                            drawLine(k.color, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx(), StrokeCap.Round)
                        KeyShape.RING -> {
                            val inset = 1.dp.toPx()
                            val box = Size(size.width - 2 * inset, size.height - 2 * inset)
                            drawRoundRect(ring, Offset(inset, inset), box, r, Stroke(1.5.dp.toPx()))
                        }
                        KeyShape.DOT -> drawCircle(k.color, 3.dp.toPx())
                    }
                }
                Spacer(Modifier.width(6.dp))
                Help(k.label)
            }
        }
    }
}

/** Places [content] at the top of its parent beside [x] px, on the roomier side, so the touched mark stays in view. */
@Composable
internal fun Floating(x: Float, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val left = if (x < constraints.maxWidth / 2f) x + 12.dp.toPx() else x - 12.dp.toPx() - p.width
        layout(constraints.maxWidth, p.height) {
            p.place(left.roundToInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0)), 0)
        }
    }
}

/** The tooltip card: values first and strong, their names after, then the date; a night can be opened. */
@Composable
internal fun Readout(title: String, values: List<Pair<String, String>>?, night: NightRow?, onOpen: (NightRow) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .widthIn(max = 260.dp)
            .background(colors.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (values == null) Text("No data", style = MaterialTheme.typography.bodyMedium)
        values?.forEach { (name, value) ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Text(title, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (night != null) {
            TextButton(onClick = { onOpen(night) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("Open night")
            }
        }
    }
}

/** A chart's table twin: a header, then one row per slot with data; a night's row opens the night. */
@Composable
internal fun Table(header: List<String>, rows: List<Pair<List<String>, NightRow?>>, onOpen: (NightRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TableRow(header, header = true)
        for ((cells, night) in rows) {
            val open = night?.let { n -> Modifier.clickable(onClickLabel = "Open night", role = Role.Button) { onOpen(n) } }
            TableRow(cells, modifier = open ?: Modifier)
        }
        if (rows.isEmpty()) Help("Nothing in this range yet.")
    }
}

@Composable
private fun TableRow(cells: List<String>, modifier: Modifier = Modifier, header: Boolean = false) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().heightIn(min = 32.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                style = style,
                color = color,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (i == 0) 1.3f else 1f),
            )
        }
    }
}
