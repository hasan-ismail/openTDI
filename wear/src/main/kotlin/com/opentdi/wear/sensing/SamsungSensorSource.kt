package com.opentdi.wear.sensing

import android.app.Activity
import android.content.Context
import android.os.Handler
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.EdaDataPoint
import com.opentdi.shared.sensing.HrDataPoint
import com.opentdi.shared.sensing.Tracker
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * The Samsung Health Sensor SDK path (§11.2, §11.6). Created only when BuildConfig.SAMSUNG_SDK_BUNDLED, so stub
 * builds never load an SDK class; it implements no SDK interface itself for the same reason. Runs on the sensing
 * thread: every SDK callback is converted to `:shared` types where it arrives, then posted to [handler].
 */
class SamsungSensorSource(
    private val context: Context,
    private val handler: Handler,
    private val session: LoggingSession,
) : SensorPath {
    private var service: HealthTrackingService? = null
    private val trackers = LinkedHashMap<Tracker, HealthTracker>()
    private var generation = 0 // callbacks of an earlier connection are ignored
    private var failures = 0
    private var stopped = false
    private var capabilityLogged = false
    private val flushRefused = HashSet<Tracker>() // trackers whose last flush() returned false or threw
    private val reconnect = Runnable { connect() }

    fun start() = connect()

    override fun flush() {
        for ((tracker, healthTracker) in trackers) {
            val accepted = runCatching { healthTracker.flush() }.getOrDefault(false)
            // Logged only when the result changes, so a refused flush shows up without a line every sdkFlushS.
            val changed = if (accepted) flushRefused.remove(tracker) else flushRefused.add(tracker)
            if (changed) logEvent(if (accepted) "FLUSH_ACCEPTED" else "FLUSH_REFUSED", "tracker" to tracker)
        }
    }

    override fun stop() {
        stopped = true
        handler.removeCallbacks(reconnect)
        release()
    }

    private fun release() {
        generation++
        trackers.values.forEach { runCatching { it.unsetEventListener() } }
        trackers.clear()
        service?.let { runCatching { it.disconnectService() } }
        service = null
    }

    private fun connect() {
        if (stopped) return
        release()
        val gen = generation
        val listener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                handler.post { if (gen == generation) onConnected() }
            }

            override fun onConnectionEnded() {
                handler.post { if (gen == generation) retry("connection ended", null) }
            }

            override fun onConnectionFailed(e: HealthTrackerException?) {
                handler.post { if (gen == generation) onFailed(e) }
            }
        }
        try {
            service = HealthTrackingService(listener, context.applicationContext).also { it.connectService() }
        } catch (e: RuntimeException) { // vendor code: whatever it throws, retry like a failed connection
            retry(e.toString(), null)
        }
    }

    private fun onConnected() {
        val svc = service ?: return
        if (trackers.isNotEmpty()) return // a repeated onConnectionSuccess: already registered
        val supported: List<HealthTrackerType> = try {
            val capability = svc.trackingCapability
            capability.supportHealthTrackerTypes.orEmpty().also { types ->
                if (!capabilityLogged) {
                    capabilityLogged = true
                    session.configRow("samsungSdkVersion", capability.version)
                    session.configRow("supportedTrackers", types)
                    logEvent("SDK_VERSION", "version" to capability.version)
                    logEvent("TRACKERS", "supported" to types.joinToString(";"))
                }
            }
        } catch (e: RuntimeException) { // vendor code; IllegalStateException = not connected after all
            return retry(e.toString(), null)
        }
        for ((tracker, type) in WANTED) {
            val healthTracker = try {
                if (type in supported) svc.getHealthTracker(type) else null
            } catch (e: UnsupportedOperationException) {
                null
            } catch (e: RuntimeException) {
                return retry(e.toString(), null)
            }
            if (healthTracker != null) {
                trackers[tracker] = healthTracker
            } else if (tracker == Tracker.EDA) {
                session.setStatus(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.EDA) // continue without E
            } else {
                session.setStatus(SensorStatus.TRACKER_UNSUPPORTED, unsupported = tracker)
                return session.startAndroidSensors(SensorStatus.TRACKER_UNSUPPORTED, tracker) // stops this source
            }
        }
        session.begin(SensorSource.SAMSUNG_SDK, trackers.keys.toSet())
        try {
            trackers.forEach { (tracker, healthTracker) -> healthTracker.setEventListener(listener(tracker)) }
        } catch (e: RuntimeException) {
            retry(e.toString(), null)
        }
    }

    private fun onFailed(e: HealthTrackerException?) {
        val code = e?.errorCode
        val status = connectionFailureStatus(code)
        if (status == SensorStatus.CONNECTION_FAILED) return retry(e?.message ?: "error code $code", code)
        val resolvable = e?.takeIf { runCatching { it.hasResolution() }.getOrDefault(false) }
        logEvent("CONNECTION_FAILED", "status" to status, "code" to code, "resolvable" to (resolvable != null))
        session.setStatus(status, resolve = resolvable?.let { ex -> { activity: Activity -> resolve(ex, activity) } })
        session.startAndroidSensors(status) // basic watch sensors until resolved; stops this source
    }

    private fun retry(message: String?, code: Int?) {
        release()
        val delayMs = reconnectDelayMs(failures++)
        logEvent("CONNECTION_FAILED", "code" to code, "message" to message, "retry_ms" to delayMs)
        session.setStatus(SensorStatus.CONNECTION_FAILED, message = message)
        handler.postDelayed(reconnect, delayMs)
    }

    private fun onTrackerError(tracker: Tracker, error: HealthTracker.TrackerError?) {
        logEvent("TRACKER_ERROR", "tracker" to tracker, "error" to error)
        if (error == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
            stop() // no silent fallback: the watch offers basic sensors instead (§11.6)
            session.setStatus(SensorStatus.SDK_POLICY_ERROR)
        } else {
            trackers.remove(tracker)?.let { runCatching { it.unsetEventListener() } }
            session.setStatus(SensorStatus.PERMISSION_MISSING)
        }
    }

    private fun listener(tracker: Tracker): HealthTracker.TrackerEventListener {
        val gen = generation
        fun deliver(action: () -> Unit) {
            handler.post { if (gen == generation) action() }
        }
        return object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) {
                val arrival = System.currentTimeMillis()
                if (dataPoints.isEmpty()) return
                val toSession: () -> Unit = when (tracker) {
                    Tracker.HR -> dataPoints.map { toHr(it) }.let { { session.onHr(it, arrival) } }
                    Tracker.EDA -> dataPoints.map { toEda(it) }.let { { session.onEda(it, arrival) } }
                    Tracker.ACCEL -> dataPoints.mapNotNull { toCounts(it) }.let { { session.onAccel(it, arrival) } }
                }
                deliver {
                    failures = 0 // data flows, so the next failure backs off from 1 s again
                    toSession()
                }
            }

            override fun onFlushCompleted() = Unit

            override fun onError(error: HealthTracker.TrackerError?) {
                deliver { onTrackerError(tracker, error) }
            }
        }
    }

    private companion object {
        val WANTED = listOf(
            Tracker.HR to HealthTrackerType.HEART_RATE_CONTINUOUS,
            Tracker.EDA to HealthTrackerType.EDA_CONTINUOUS,
            Tracker.ACCEL to HealthTrackerType.ACCELEROMETER_CONTINUOUS,
        )

        // Every getValue result is a nullable platform type (§11.2).
        fun toHr(p: DataPoint) = HrDataPoint(
            timestampMs = p.timestamp,
            hr = p.getValue(ValueKey.HeartRateSet.HEART_RATE),
            hrStatus = p.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS),
            ibiList = p.getValue(ValueKey.HeartRateSet.IBI_LIST)?.map { it ?: 0 }, // 0 is an invalid IBI
            ibiStatusList = p.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)?.map { it ?: -1 }, // -1 = error
        )

        fun toEda(p: DataPoint) = EdaDataPoint(
            timestampMs = p.timestamp,
            skinConductance = p.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE),
            status = p.getValue(ValueKey.EdaSet.STATUS),
        )

        fun toCounts(p: DataPoint): AccelCounts? {
            val x: Int = p.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X) ?: return null
            val y: Int = p.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y) ?: return null
            val z: Int = p.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z) ?: return null
            return AccelCounts(p.timestamp, x, y, z)
        }

        fun resolve(e: HealthTrackerException, activity: Activity): Boolean = try {
            e.resolve(activity) // opens Galaxy Store to install or update Health Sensor Service
            true
        } catch (ex: RuntimeException) { // vendor code on the main thread: whatever it throws, e.g. no Store
            logEvent("RESOLVE_FAILED", "message" to ex.message)
            false
        }
    }
}
