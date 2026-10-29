package com.opentdi.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.opentdi.shared.audio.nextAlarmAt
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.time.clock12
import java.time.ZonedDateTime

/** One part of tonight's plan, in minutes from now. */
data class PlanMark(val label: String, val fromMin: Int, val toMin: Int = fromMin)

/** If a session started [now]: when the cues can play, and when the watch alarms ring, in real minutes (DST too). */
fun nightPlan(c: SessionConfig, now: ZonedDateTime): List<PlanMark> {
    val nowMs = now.toInstant().toEpochMilli()
    // As the watch sets them; rounded up, so an alarm keeps its minute when now has seconds.
    fun ahead(at: Int) = ((nextAlarmAt(at, nowMs, now.zone) - nowMs + 59_999) / 60_000).toInt()
    return listOfNotNull(
        PlanMark(CUES, minOf(c.timed.firstSleepMinMin, c.sessionMaxMin), c.sessionMaxMin),
        PlanMark(WBTB, ahead(c.alarms.wbtbAtMin)).takeIf { c.alarms.wbtbEnabled },
        PlanMark(WAKE, ahead(c.alarms.wakeAtMin)).takeIf { c.alarms.wakeEnabled },
    )
}

/** "Voice from about 11:20 PM until 2:10 AM at the latest · early wake-up 4:30 AM · wake-up 7 AM" for [nightPlan]. */
fun nightPlanLine(c: SessionConfig, now: ZonedDateTime): String {
    fun at(min: Int) = now.plusMinutes(min.toLong()).let { clock12(it.hour * 60 + it.minute) }
    return nightPlan(c, now).joinToString(" · ") {
        when (it.label) {
            CUES -> "Voice from about ${at(it.fromMin)} until ${at(it.toMin)} at the latest"
            else -> "${it.label} ${at(it.fromMin)}"
        }
    }
}

/** Tonight's plan as a timeline from now (a hollow dot) to the last alarm, shown while no session runs. */
@Composable
fun NightPlanCard(config: SessionConfig, now: ZonedDateTime) {
    val marks = nightPlan(config, now)
    val line = nightPlanLine(config, now)
    val colors = MaterialTheme.colorScheme
    Section("Tonight's plan") {
        Help("If you start now")
        Canvas(Modifier.fillMaxWidth().height(40.dp)) { // the line under it says the same, for screen readers too
            val r = 5.dp.toPx()
            val scale = marks.maxOf { it.toMin } + 15f
            fun x(min: Int) = r + (size.width - 2 * r) * min / scale
            val y = size.height / 2
            drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            for (m in marks) when (m.label) {
                CUES -> drawRoundRect(
                    colors.primary.copy(alpha = 0.45f),
                    Offset(x(m.fromMin), y - 4.dp.toPx()),
                    Size(x(m.toMin) - x(m.fromMin), 8.dp.toPx()),
                    CornerRadius(4.dp.toPx()),
                )
                WBTB -> drawCircle(colors.primary, r, Offset(x(m.fromMin), y))
                else -> drawCircle(colors.tertiary, r, Offset(x(m.fromMin), y))
            }
            drawCircle(colors.background, r, Offset(x(0), y))
            drawCircle(colors.onSurface, r, Offset(x(0), y), style = Stroke(1.5.dp.toPx()))
        }
        Help(line)
    }
}

private const val CUES = "Voice"
private const val WBTB = "early wake-up"
private const val WAKE = "wake-up"
