package com.opentdi.wear.sensing

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.HrDataPoint
import com.opentdi.shared.sensing.Tracker
import kotlin.math.roundToInt

/** The ANDROID_SENSORS fallback (§11.8): SensorManager heart rate (1 Hz) and accelerometer (25 Hz), no IBI or EDA. */
class AndroidSensorSource(
    private val context: Context,
    private val handler: Handler,
    private val session: LoggingSession,
) : SensorPath, SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private var epochOffsetMs = 0L // SensorEvent.timestamp (elapsed-realtime ns) → epoch ms, set at registration

    fun start() {
        epochOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos() / 1_000_000
        val hrPermission = context.checkSelfPermission(heartRatePermission(Build.VERSION.SDK_INT)) ==
            PackageManager.PERMISSION_GRANTED
        val registered = buildSet {
            if (hrPermission && register(Sensor.TYPE_HEART_RATE, 1_000_000)) add(Tracker.HR)
            if (register(Sensor.TYPE_ACCELEROMETER, 40_000)) add(Tracker.ACCEL)
        }
        logEvent("ANDROID_SENSORS", "registered" to registered.joinToString(";"), "hrPermission" to hrPermission)
        if (registered.isNotEmpty()) session.begin(SensorSource.ANDROID_SENSORS, registered)
        when {
            !hrPermission -> session.setStatus(SensorStatus.PERMISSION_MISSING)
            registered.size < 2 -> session.setStatus(SensorStatus.NO_SENSORS)
        }
    }

    // Delivered on the sensing thread through [handler].
    private fun register(type: Int, periodUs: Int): Boolean {
        val sensor = sensorManager.getDefaultSensor(type) ?: return false
        return sensorManager.registerListener(this, sensor, periodUs, handler)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timestampMs = event.timestamp / 1_000_000 + epochOffsetMs
        val arrivalWallMs = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0]
                val point = HrDataPoint(
                    timestampMs = timestampMs,
                    hr = if (bpm.isFinite()) bpm.roundToInt() else null,
                    hrStatus = androidHrStatus(bpm, event.accuracy),
                    ibiList = null,
                    ibiStatusList = null,
                )
                session.onHr(listOf(point), arrivalWallMs)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val (x, y, z) = event.values // m/s², kept as m/s² × 1000 integers like w_accel_raw.csv
                if (x.isFinite() && y.isFinite() && z.isFinite()) {
                    val counts = AccelCounts(
                        timestampMs, (x * 1000).roundToInt(), (y * 1000).roundToInt(), (z * 1000).roundToInt(),
                    )
                    session.onAccel(listOf(counts), arrivalWallMs)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun flush() = Unit

    override fun stop() = sensorManager.unregisterListener(this)
}
