package com.opentdi.mobile.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.ui.Help
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

private const val BAND_WEEKS = 26 // GUESSED: about 10 dp cells on a phone

/**
 * The nights from [from] to [to] as cells coloured by their morning (one hue, dark for no dream to light for a lucid
 * moment), a ring for a recorded night without a report, faint for a night not recorded, a dot for a nightmare. Up
 * to six weeks it reads as a calendar, a row per week; longer, each column is a week, [BAND_WEEKS] to a band.
 */
@Composable
internal fun DreamCalendar(
    nights: List<NightRow>,
    from: LocalDate,
    to: LocalDate,
    firstDay: DayOfWeek,
    dates: Dates,
    onOpen: (NightRow) -> Unit,
    reveal: Animatable<Float, AnimationVector1D>,
) {
    val colors = MaterialTheme.colorScheme
    val byDate = remember(nights) { nights.associateBy { it.night } }
    val counts = remember(nights) { nights.filter { it.recorded }.groupingBy(::outcome).eachCount() }
    val weeks = calendarSlot(to, from, firstDay).first + 1
    val banded = weeks > 6
    val cols = if (banded) minOf(weeks, BAND_WEEKS) else 7
    val bands = if (banded) (weeks + cols - 1) / cols else 1
    val rows = if (banded) bands * 8 - 1 else weeks // a spare row between bands
    val weekStart = from.with(TemporalAdjusters.previousOrSame(firstDay))
    var selected by remember(from, to) { mutableStateOf<LocalDate?>(null) }
    var table by rememberSaveable { mutableStateOf(false) }

    /** The cell (column, row) of [d]. */
    fun cellOf(d: LocalDate): Pair<Int, Int> {
        val (week, day) = calendarSlot(d, from, firstDay)
        return if (banded) week % cols to week / cols * 8 + day else day to week
    }

    /** The night in cell ([col], [row]), when the cell holds one in range. */
    fun dateAt(col: Int, row: Int): LocalDate? {
        val (week, day) = if (banded) (row / 8 * cols + col) to row % 8 else row to col
        if (day > 6) return null
        return weekStart.plusDays(week * 7L + day).takeIf { it in from..to }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Morning dream questions", style = MaterialTheme.typography.titleMedium)
        Legend(
            listOf(Outcome.LUCID, Outcome.DREAM, Outcome.WHITE, Outcome.NO_DREAM).map {
                Key("${it.label} ${counts[it] ?: 0}", OUTCOME_FILL.getValue(it), KeyShape.BAR)
            } + listOf(
                Key("${Outcome.NO_REPORT.label} ${counts[Outcome.NO_REPORT] ?: 0}", colors.outline, KeyShape.RING),
                Key("Not tracked", colors.outlineVariant.copy(alpha = 0.45f), KeyShape.BAR),
                Key("Nightmare", colors.onSurface, KeyShape.DOT),
            ),
        )
        Crossfade(table, animationSpec = tween(250), label = "table") { asTable ->
            if (asTable) {
                Table(
                    listOf("Night", "Morning", "Knew", "Steered", "Nightmare"),
                    nights.filter { it.recorded }.map { n ->
                        listOf(
                            dates.short(n.night),
                            outcome(n).label,
                            n.insight?.toString() ?: "—",
                            n.control?.toString() ?: "—",
                            if (n.nightmare) "Yes" else "—",
                        ) to n
                    },
                    onOpen,
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 2.dp
                    val cell = if (banded) (maxWidth - gap * (cols - 1)) / cols else minOf(36.dp, (maxWidth - gap * 6) / 7)
                    val step = with(LocalDensity.current) { (cell + gap).toPx() }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!banded) WeekdayHeader(firstDay, cell, gap)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = outcomeTakeaway(counts) }
                                .pointerInput(from, to, step) {
                                    detectTapGestures { o ->
                                        val d = dateAt((o.x / step).toInt(), (o.y / step).toInt())
                                        selected = if (d == null || d == selected) null else d
                                    }
                                },
                        ) {
                            // Its own height, not the box's: the readout is taller than a week or two of cells.
                            Canvas(Modifier.fillMaxWidth().height(cell * rows + gap * (rows - 1))) {
                                val c = cell.toPx()
                                val corner = CornerRadius(minOf(4.dp.toPx(), c / 4))
                                var d = from
                                while (d <= to) {
                                    val (col, row) = cellOf(d)
                                    val at = Offset(col * step, row * step)
                                    val night = byDate[d]
                                    when {
                                        night == null || !night.recorded ->
                                            drawRoundRect(colors.outlineVariant, at, Size(c, c), corner, alpha = 0.45f)
                                        outcome(night) == Outcome.NO_REPORT -> {
                                            val inset = 0.75.dp.toPx()
                                            val box = Size(c - 2 * inset, c - 2 * inset)
                                            val ring = Stroke(1.5.dp.toPx())
                                            drawRoundRect(colors.outline, at + Offset(inset, inset), box, corner, ring)
                                        }
                                        else -> {
                                            val fill = OUTCOME_FILL.getValue(outcome(night))
                                            drawRoundRect(fill, at, Size(c, c), corner, alpha = reveal.value)
                                        }
                                    }
                                    if (night?.nightmare == true) {
                                        val r = maxOf(2.dp.toPx(), c * 0.12f)
                                        val dot = at + Offset(c - r - 1.dp.toPx(), r + 1.dp.toPx())
                                        drawCircle(colors.surfaceContainer, r + 1.5.dp.toPx(), dot)
                                        drawCircle(colors.onSurface, r, dot)
                                    }
                                    if (d == selected) {
                                        val o = 2.dp.toPx()
                                        val ring = Size(c + 2 * o, c + 2 * o)
                                        drawRoundRect(colors.onSurface, at - Offset(o, o), ring, corner, Stroke(1.5.dp.toPx()))
                                    }
                                    d = d.plusDays(1)
                                }
                            }
                            selected?.let { d ->
                                val night = byDate[d]?.takeIf { it.recorded }
                                Floating(cellOf(d).first * step + step / 2) {
                                    Readout(dates.night(d), night?.let(::morningValues), night, onOpen)
                                }
                            }
                        }
                        if (banded) Help("Each column is a week, ${dayName(firstDay)} at the top.")
                    }
                }
            }
        }
        val nightmares = nights.filter { it.nightmare }
        if (nightmares.isNotEmpty()) {
            Help("Nightmares: ${nightmares.size}, on ${nightmares.joinToString(", ") { dates.short(it.night) }}.")
        }
        TextButton(onClick = { table = !table }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(if (table) "Show as chart" else "Show as table")
        }
    }
}

/** A night's morning answers for the tooltip card. */
private fun morningValues(n: NightRow): List<Pair<String, String>> = listOfNotNull(
    "" to outcome(n).label,
    n.insight?.let { "knew you were dreaming" to "$it of 5" },
    n.control?.let { "could steer it" to "$it of 5" },
    ("" to "Nightmare").takeIf { n.nightmare },
)

@Composable
private fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])

@Composable
private fun WeekdayHeader(firstDay: DayOfWeek, cell: Dp, gap: Dp) {
    val locale = LocalConfiguration.current.locales[0]
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 0L..6L) {
            Text(
                firstDay.plus(i).getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(cell),
            )
        }
    }
}
