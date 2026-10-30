package com.opentdi.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.opentdi.shared.audio.nextAlarmAt
import com.opentdi.shared.audio.wbtbTooSoon
import com.opentdi.shared.model.AudioRouteMode
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
    // The watch skips an early wake-up too soon after Start, in milliseconds, so not by the rounded minutes.
    // shortcut: a night restarted soon after a stopped one keeps its earlier start on the watch (WakeAlarms), so the
    // plan can wrongly leave the early wake-up out then; upgrade if the phone ever learns the watch's night start.
    val wbtbRings = !wbtbTooSoon(nextAlarmAt(c.alarms.wbtbAtMin, nowMs, now.zone), nowMs)
    return listOfNotNull(
        PlanMark(CUES, minOf(c.timed.firstSleepMinMin, c.sessionMaxMin), c.sessionMaxMin),
        // Timed from falling asleep, it rings at the set time or before (WakeAlarms.wbtbTime).
        PlanMark(if (c.alarms.wbtbAfterSleepMin != null) WBTB_BY else WBTB, ahead(c.alarms.wbtbAtMin))
            .takeIf { c.alarms.wbtbEnabled && wbtbRings },
        PlanMark(WAKE, ahead(c.alarms.wakeAtMin)).takeIf { c.alarms.wakeEnabled },
    )
}

/** "Voice now, then from about 11:20 PM until 2:10 AM at the latest · early wake-up 4:30 AM · wake-up 7 AM". */
fun nightPlanLine(c: SessionConfig, now: ZonedDateTime): String {
    val marks = nightPlan(c, now)
    // The voice speaks once at Start (TimedCueScheduler.start), then in rounds.
    val line = marks.joinToString(" · ") {
        when (it.label) {
            CUES -> "Voice now, then from about ${at(now, it.fromMin)} until ${at(now, it.toMin)} at the latest"
            else -> "${it.label} ${at(now, it.fromMin)}"
        }
    }
    return if (wbtbSkipped(c, marks)) "$line · no early wake-up tonight (less than 4½ hours after Start)" else line
}

/**
 * The bedtime card's sentence: where the voice plays and the watch's alarms if the night started [now], by the same
 * rules as [nightPlan], e.g. "The voice plays from your watch. Alarms on the watch: early wake-up 4:30 AM, wake-up 7 AM."
 */
fun tonightSummary(c: SessionConfig, now: ZonedDateTime): String {
    val device = if (c.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER) "watch" else "phone"
    val marks = nightPlan(c, now)
    val alarms = marks.filter { it.label != CUES }.map { "${it.label} ${at(now, it.fromMin)}" }
    val skipped = wbtbSkipped(c, marks)
    return "The voice plays from your $device." + when (alarms.size) {
        0 -> if (skipped) "" else " No alarms tonight."
        1 -> " Alarm on the watch: ${alarms[0]}."
        else -> " Alarms on the watch: ${alarms.joinToString(", ")}."
    } + if (skipped) " No early wake-up tonight: it's less than 4½ hours away." else ""
}

/** After the voice, while the watch tracks: the alarms still [ahead] ([alarmsAhead]'s words) and how the night ends. */
fun nightOnText(ahead: String?, wakeEnabled: Boolean): String {
    val end = if (wakeEnabled) "after your morning alarm" else "in the morning"
    return if (ahead == null) {
        "The voice is done. The watch tracks your sleep. The night ends by itself in the morning."
    } else {
        "The voice is done. Your alarms are on the watch: $ahead. The night ends by itself $end."
    }
}

private fun at(now: ZonedDateTime, min: Int) = now.plusMinutes(min.toLong()).let { clock12(it.hour * 60 + it.minute) }

/** The early wake-up is on, but too soon after a start now for the watch to ring it. */
private fun wbtbSkipped(c: SessionConfig, marks: List<PlanMark>) =
    c.alarms.wbtbEnabled && marks.none { it.label == WBTB || it.label == WBTB_BY }

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
                WBTB, WBTB_BY -> drawCircle(colors.primary, r, Offset(x(m.fromMin), y))
                else -> drawCircle(colors.tertiary, r, Offset(x(m.fromMin), y))
            }
            drawCircle(colors.background, r, Offset(x(0), y))
            drawCircle(colors.onSurface, r, Offset(x(0), y), style = Stroke(1.5.dp.toPx()))
        }
        Help(line)
    }
}

@Composable
internal fun InstructionsCard(config: SessionConfig) {
    Section("What to expect") {
        Text(whatToExpect(config), style = MaterialTheme.typography.bodyMedium)
    }
}

private const val CUES = "Voice"
private const val WBTB = "early wake-up"
private const val WBTB_BY = "early wake-up by"
private const val WAKE = "wake-up"
