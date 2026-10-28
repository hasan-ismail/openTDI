package com.opentdi.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.link.WatchTelemetry
import com.opentdi.shared.model.BiosignalPacket
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.model.TelemetryFrame
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

const val STALE_MS = 10_000L // no batch for longer: the watch is not streaming
private const val SPARK_WINDOW_MS = 180_000L
private const val SPARK_MAX_GAP_MS = 1500L // packets come once a second; a longer gap is missing data

/** The watch's live data: status, big tiles, 3-minute sparklines and the details, redrawn when a batch arrives. */
@Composable
fun WatchScreen() {
    val live by WatchTelemetry.live.collectAsStateWithLifecycle()
    val p = live.frames.lastOrNull()?.packet
    Page("Watch") {
        StreamStatus(live.receivedAtMs, live.sensorStatus)
        if (p != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Heart rate", format("%.0f", p.hr), "bpm", Modifier.weight(1f))
                Tile("Skin response", format("%.2f", p.skinConductance), "µS", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(
                    "Stillness",
                    p.stillnessScore?.let { "${(it * 100).roundToInt()}" } ?: "—",
                    "% still",
                    Modifier.weight(1f),
                )
                Tile("Skin temp", format("%.1f", p.skinTemperature), "°C", Modifier.weight(1f))
            }
            Tile(
                "Blood oxygen",
                p.spo2?.toString() ?: "—",
                if (p.spo2 == null) "no check yet" else "% · checked ${time(live.spo2AtMs)}",
                Modifier.fillMaxWidth(),
            )
            Section("Last 3 minutes") {
                SparkRow("Heart rate", "%.0f–%.0f bpm", live.frames) { it.hr }
                SparkRow("Skin response", "%.3f–%.3f µS", live.frames) { it.skinConductance }
                SparkRow("Stillness", "%.0f–%.0f", live.frames, fixedRange = 0f..1f) { it.stillnessScore }
            }
            Section(null) {
                Fold("Technical details") {
                    Help("RMSSD ${format("%.0f", p.rmssd)} ms · EDA status ${p.edaStatus ?: "—"}")
                    Help("Stillness ${format("%.2f", p.stillnessScore)} · accel mean ${format("%.3f", p.accelMean)} g")
                    Help("Jerks ${live.jerkEvents} · limp events ${live.limpEvents} · gravity ${live.gravityMode}")
                    Help("Sensors ${live.sensorStatus ?: "—"}")
                }
            }
        }
        WatchSetupCard()
        SamsungKitCard()
    }
}

// The only part that changes every second, so the clock never redraws the sparklines.
@Composable
private fun StreamStatus(receivedAtMs: Long?, status: SensorStatus?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(SystemClock.elapsedRealtime()) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { // no ticking while the screen is off
            while (true) {
                value = SystemClock.elapsedRealtime()
                delay(1000)
            }
        }
    }
    val ageMs = receivedAtMs?.let { (now - it).coerceAtLeast(0) }
    Section(null) {
        if (ageMs == null || ageMs > STALE_MS) {
            Text(
                "No live data from the watch",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Help("It starts when you tap Start session, or Start recording on the watch.")
        } else {
            Text(sensorWords(status), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (ageMs != null) Help("Updated ${ageMs / 1000} s ago")
    }
}

@Composable
private fun Tile(label: String, value: String, unit: String, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Help(unit)
        }
    }
}

/** The watch's sensor status in plain words. */
private fun sensorWords(status: SensorStatus?) = when (status) {
    null -> "Waiting for the watch"
    SensorStatus.CONNECTED_SAMSUNG -> "Samsung sensors connected"
    SensorStatus.FALLBACK_ANDROID_SENSORS -> "Basic watch sensors: heart rate and motion, no EDA"
    SensorStatus.SIMULATION -> "Simulation — not real sensor data"
    SensorStatus.AAR_NOT_BUNDLED -> "This watch build has no Samsung sensor SDK: basic sensors only"
    SensorStatus.HSS_NOT_INSTALLED -> "Health Sensor Service is missing on the watch: see the watch"
    SensorStatus.HSS_TOO_OLD -> "Health Sensor Service on the watch is too old: see the watch"
    SensorStatus.SDK_POLICY_ERROR -> "Samsung blocked sensor access: see the watch"
    SensorStatus.PERMISSION_MISSING -> "The watch app needs a permission: see the watch"
    SensorStatus.TRACKER_UNSUPPORTED -> "A sensor is not supported on this watch"
    SensorStatus.CONNECTION_FAILED -> "The watch cannot reach its sensors yet: retrying"
    SensorStatus.NO_SENSORS -> "No usable sensors on the watch"
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
    Help("$label: $range")
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
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

// %tR: the local 24-hour time of an epoch-ms timestamp (the watch clock).
private fun time(epochMs: Long?) = epochMs?.let { String.format(Locale.ROOT, "%tR", it) } ?: "—"

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
