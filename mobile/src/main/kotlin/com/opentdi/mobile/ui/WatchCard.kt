package com.opentdi.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.link.WatchTelemetry
import com.opentdi.shared.model.BiosignalPacket
import com.opentdi.shared.model.TelemetryFrame
import java.util.Locale
import kotlinx.coroutines.delay

private const val STALE_MS = 10_000L // no batch for longer: the watch is not streaming
private const val SPARK_WINDOW_MS = 180_000L
private const val SPARK_MAX_GAP_MS = 1500L // packets come once a second; a longer gap is missing data

/** "Watch (live)": the newest values from the watch and 3-minute sparklines, redrawn when a batch arrives. */
@Composable
fun WatchCard() {
    val live by WatchTelemetry.live.collectAsStateWithLifecycle()
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Watch (live)", style = MaterialTheme.typography.titleLarge)
            StreamAge(live.receivedAtMs)
            val p = live.frames.lastOrNull()?.packet
            if (p != null) {
                Text("Sensors ${live.sensorStatus ?: "—"}")
                Text("HR ${format("%.0f", p.hr)} bpm · RMSSD ${format("%.0f", p.rmssd)} ms")
                Text("EDA ${format("%.3f", p.skinConductance)} µS, status ${p.edaStatus ?: "—"}")
                Text("Stillness ${format("%.2f", p.stillnessScore)} · accel mean ${format("%.3f", p.accelMean)} g")
                Text("Jerks ${live.jerkEvents} · limp events ${live.limpEvents}")
                SparkRow("HR, 3 min", "%.0f–%.0f bpm", live.frames) { it.hr }
                SparkRow("EDA, 3 min", "%.3f–%.3f µS", live.frames) { it.skinConductance }
                SparkRow("Stillness, 3 min", "%.0f–%.0f", live.frames, fixedRange = 0f..1f) { it.stillnessScore }
            }
        }
    }
}

// The only part that changes every second, so the clock never redraws the sparklines.
@Composable
private fun StreamAge(receivedAtMs: Long?) {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val ageMs = receivedAtMs?.let { (now - it).coerceAtLeast(0) }
    if (ageMs == null || ageMs > STALE_MS) {
        Text("Watch not streaming — start a logging session on the watch", color = MaterialTheme.colorScheme.tertiary)
    }
    if (ageMs != null) Text("Updated ${ageMs / 1000} s ago", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SparkRow(
    label: String,
    rangePattern: String,
    frames: List<TelemetryFrame>,
    fixedRange: ClosedFloatingPointRange<Float>? = null,
    value: (BiosignalPacket) -> Float?,
) {
    val spark = remember(frames) {
        spark(frames.map { it.packet.timestamp to value(it.packet) }, SPARK_WINDOW_MS, fixedRange)
    }
    val range = spark.range?.let { String.format(Locale.ROOT, rangePattern, it.start, it.endInclusive) } ?: "—"
    Text("$label: $range", style = MaterialTheme.typography.bodyMedium)
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        val stroke = 1.5.dp.toPx()
        for (line in spark.lines) {
            val points = line.map { (x, y) -> Offset(x * size.width, (1 - y) * size.height) }
            if (points.size == 1) {
                drawCircle(color, stroke, points[0])
            } else {
                drawPoints(points, PointMode.Polygon, color, stroke)
            }
        }
    }
}

private fun format(pattern: String, value: Float?) = value?.let { String.format(Locale.ROOT, pattern, it) } ?: "—"

/** A sparkline in unit coordinates: [lines] of (x, y) points, x and y in 0..1 (the newest at x = 1, y up). */
class Spark(val lines: List<List<Pair<Float, Float>>>, val range: ClosedFloatingPointRange<Float>?)

/**
 * The (timestamp ms, value) [samples] of the [windowMs] up to the newest one as a [Spark], y scaled to [fixedRange]
 * or else to the span of the values. A null value or a gap over 1.5 s starts a new line, so missing data never draws
 * as a slope.
 */
fun spark(
    samples: List<Pair<Long, Float?>>,
    windowMs: Long,
    fixedRange: ClosedFloatingPointRange<Float>? = null,
): Spark {
    val end = samples.lastOrNull()?.first ?: return Spark(emptyList(), fixedRange)
    val shown = samples.filter { it.first > end - windowMs }
    val values = shown.mapNotNull { it.second }
    val range = fixedRange ?: values.minOrNull()?.let { it..values.max() } ?: return Spark(emptyList(), null)
    val span = range.endInclusive - range.start
    val lines = mutableListOf<MutableList<Pair<Float, Float>>>()
    var previous: Long? = null
    for ((t, v) in shown) {
        if (v == null) {
            previous = null
            continue
        }
        if (previous == null || t - previous > SPARK_MAX_GAP_MS) lines += mutableListOf<Pair<Float, Float>>()
        val y = if (span > 0f) ((v - range.start) / span).coerceIn(0f, 1f) else 0.5f
        lines.last() += 1f - (end - t).toFloat() / windowMs to y
        previous = t
    }
    return Spark(lines, range)
}
