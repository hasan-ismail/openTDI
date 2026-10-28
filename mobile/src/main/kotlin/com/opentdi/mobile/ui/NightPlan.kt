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
import com.opentdi.shared.model.SessionConfig
import java.util.Locale

/** One part of tonight's plan, in minutes from now. */
data class PlanMark(val label: String, val fromMin: Int, val toMin: Int = fromMin)

/** If a session started at [nowMinOfDay]: when the cues can play, and when the watch alarms ring. */
fun nightPlan(c: SessionConfig, nowMinOfDay: Int): List<PlanMark> {
    fun ahead(at: Int) = Math.floorMod(at - nowMinOfDay, 1440)
    return listOfNotNull(
        PlanMark(CUES, c.timed.firstSleepMinMin, c.sessionMaxMin),
        PlanMark(WBTB, ahead(c.alarms.wbtbAtMin)).takeIf { c.alarms.wbtbEnabled },
        PlanMark(WAKE, ahead(c.alarms.wakeAtMin)).takeIf { c.alarms.wakeEnabled },
    )
}

/** "Cues from about 23:20 until 2:10 at the latest · early wake 4:30 · wake-up 7:00" for [nightPlan]. */
fun nightPlanLine(c: SessionConfig, nowMinOfDay: Int): String {
    fun at(min: Int) = String.format(Locale.ROOT, "%d:%02d", min / 60 % 24, min % 60)
    return nightPlan(c, nowMinOfDay).joinToString(" · ") {
        when (it.label) {
            CUES -> "Cues from about ${at(nowMinOfDay + it.fromMin)} until ${at(nowMinOfDay + it.toMin)} at the latest"
            else -> "${it.label} ${at(nowMinOfDay + it.fromMin)}"
        }
    }
}

/** Tonight's plan as a timeline from now (a hollow dot) to the last alarm, shown while no session runs. */
@Composable
fun NightPlanCard(config: SessionConfig, nowMinOfDay: Int) {
    val marks = nightPlan(config, nowMinOfDay)
    val line = nightPlanLine(config, nowMinOfDay)
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

private const val CUES = "Cues"
private const val WBTB = "early wake"
private const val WAKE = "wake-up"
