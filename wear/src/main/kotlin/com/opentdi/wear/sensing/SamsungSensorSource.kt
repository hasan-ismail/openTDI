package com.opentdi.wear.sensing

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import com.opentdi.shared.model.PpgChannel
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.EdaDataPoint
import com.opentdi.shared.sensing.HrDataPoint
import com.opentdi.shared.sensing.SkinTempDataPoint
import com.opentdi.shared.sensing.Tracker
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * The Samsung Health Sensor SDK path (§11.2, §11.6). Only [SamsungKit] creates it, by reflection, when an SDK is
 * there: built in, or the user's own kit, whose classes it then loads through a loader of its own. It implements no SDK
 * interface itself, so the app never loads an SDK class without it. Runs on the sensing
 * thread: every SDK callback is converted to `:shared` types where it arrives, then posted to [handler].
 */
class SamsungSensorSource(
    private val context: Context,
    private val handler: Handler,
    private val session: LoggingSession,
) : SamsungPath {
    private var service: HealthTrackingService? = null
    private val trackers = LinkedHashMap<Tracker, HealthTracker>()
    private val extras = LinkedHashMap<ExtraTracker, HealthTracker>() // logging only; SpO2 listens per spot check
    private var generation = 0 // callbacks of an earlier connection are ignored
    private var failures = 0
    private var stopped = false
    private var capabilityLogged = false
    private val flushRefused = HashSet<Tracker>() // trackers whose last flush() returned false or threw
    private val reconnect = Runnable { connect() }

    override fun start() = connect()

    override fun flush() {
        for ((tracker, healthTracker) in trackers) {
            val accepted = runCatching { healthTracker.flush() }.getOrDefault(false)
            // Logged only when the result changes, so a refused flush shows up without a line every sdkFlushS.
            val changed = if (accepted) flushRefused.remove(tracker) else flushRefused.add(tracker)
            if (changed) logEvent(if (accepted) "FLUSH_ACCEPTED" else "FLUSH_REFUSED", "tracker" to tracker)
        }
        // flush() is for continuous trackers only; SPO2_ON_DEMAND would just return false.
        extras.filterKeys { it != ExtraTracker.SPO2 }.values.forEach { runCatching { it.flush() } }
    }

    override fun stop() {
        stopped = true
        handler.removeCallbacks(reconnect)
        release()
    }

    /** Starts one SpO2 measurement; false while SpO2 is off. The session ends it with [stopSpo2]. */
    override fun startSpo2(): Boolean {
        val tracker = extras[ExtraTracker.SPO2] ?: return false
        return try {
            tracker.setEventListener(listener(ExtraTracker.SPO2))
            true
        } catch (e: RuntimeException) { // vendor code
            stopExtra(ExtraTracker.SPO2)
            session.setExtraOff(ExtraTracker.SPO2, "failed to start")
            logEvent("EXTRA_FAILED", "tracker" to ExtraTracker.SPO2, "error" to e)
            false
        }
    }

    override fun stopSpo2() {
        extras[ExtraTracker.SPO2]?.let { runCatching { it.unsetEventListener() } }
    }

    /** Stops one logging-only tracker (its error, or STORAGE_LOW for PPG); the session runs on. */
    override fun stopExtra(extra: ExtraTracker) {
        extras.remove(extra)?.let { runCatching { it.unsetEventListener() } }
    }

    private fun release() {
        generation++
        (trackers.values + extras.values).forEach { runCatching { it.unsetEventListener() } }
        trackers.clear()
        extras.clear()
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
            return retry(e.toString(), null)
        }
        for ((extra, type) in EXTRAS) if (session.extraWanted(extra)) startExtra(svc, extra, type, type in supported)
    }

    /** Whatever goes wrong here only turns [extra] off, never the HR/EDA/motion session. */
    private fun startExtra(
        svc: HealthTrackingService,
        extra: ExtraTracker,
        type: HealthTrackerType,
        supported: Boolean,
    ) {
        val permission = extraPermission(extra, Build.VERSION.SDK_INT)
        val why = try {
            when {
                !supported -> "not supported by this watch"
                context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED -> "permission missing"
                else -> {
                    val tracker = if (extra == ExtraTracker.PPG) {
                        svc.getHealthTracker(type, session.config.sensors.ppgChannels.map(::ppgType).toSet())
                    } else {
                        svc.getHealthTracker(type)
                    }
                    if (extra != ExtraTracker.SPO2) tracker.setEventListener(listener(extra))
                    extras[extra] = tracker
                    null
                }
            }
        } catch (e: UnsupportedOperationException) {
            "not supported by this watch"
        } catch (e: RuntimeException) { // vendor code
            logEvent("EXTRA_FAILED", "tracker" to extra, "error" to e)
            "failed to start"
        }
        session.setExtraOff(extra, why)
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
        session.endSpo2Check("SPO2_ERROR") // its listener went with the connection
        val delayMs = reconnectDelayMs(failures++)
        logEvent("CONNECTION_FAILED", "code" to code, "message" to message, "retry_ms" to delayMs)
        session.setStatus(SensorStatus.CONNECTION_FAILED, message = message)
        handler.postDelayed(reconnect, delayMs)
    }

    private fun onTrackerError(tracker: Tracker, error: HealthTracker.TrackerError?) {
        logEvent("TRACKER_ERROR", "tracker" to tracker, "error" to error)
        if (error == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
            stop() // no silent fallback: the watch offers basic sensors instead (§11.6)
            session.extrasStopped("refused ($error)")
            session.setStatus(SensorStatus.SDK_POLICY_ERROR)
        } else {
            trackers.remove(tracker)?.let { runCatching { it.unsetEventListener() } }
            session.setStatus(SensorStatus.PERMISSION_MISSING)
        }
    }

    private fun onExtraError(extra: ExtraTracker, error: HealthTracker.TrackerError?) {
        logEvent("TRACKER_ERROR", "tracker" to extra, "error" to error)
        stopExtra(extra)
        val why = if (error == HealthTracker.TrackerError.PERMISSION_ERROR) "permission missing" else "refused ($error)"
        session.setExtraOff(extra, why)
    }

    private fun listener(tracker: Tracker) = listener(
        convert = { points, arrival ->
            when (tracker) {
                Tracker.HR -> points.map { toHr(it) }.let { { session.onHr(it, arrival) } }
                Tracker.EDA -> points.map { toEda(it) }.let { { session.onEda(it, arrival) } }
                Tracker.ACCEL -> points.mapNotNull { toCounts(it) }.let { { session.onAccel(it, arrival) } }
            }
        },
        onError = { onTrackerError(tracker, it) },
    )

    private fun listener(extra: ExtraTracker): HealthTracker.TrackerEventListener {
        val channels = session.config.sensors.ppgChannels
        return listener(
            convert = { points, arrival ->
                when (extra) {
                    ExtraTracker.SKIN_TEMP -> points.map { toSkinTemp(it) }.let { { session.onSkinTemp(it, arrival) } }
                    ExtraTracker.PPG -> points.map { toPpg(it, channels) }.let { { session.onPpg(it) } }
                    ExtraTracker.SPO2 -> points.map { toSpo2(it) }.let { { session.onSpo2(it, arrival) } }
                }
            },
            onError = { onExtraError(extra, it) },
        )
    }

    /** [convert] runs on the SDK's thread and returns what the sensing thread then does with the data. */
    private fun listener(
        convert: (List<DataPoint>, Long) -> () -> Unit,
        onError: (HealthTracker.TrackerError?) -> Unit,
    ): HealthTracker.TrackerEventListener {
        val gen = generation
        fun deliver(action: () -> Unit) {
            handler.post { if (gen == generation) action() }
        }
        return object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) {
                val arrival = System.currentTimeMillis()
                if (dataPoints.isEmpty()) return
                val toSession = convert(dataPoints, arrival)
                deliver {
                    failures = 0 // data flows, so the next failure backs off from 1 s again
                    toSession()
                }
            }

            override fun onFlushCompleted() = Unit

            override fun onError(error: HealthTracker.TrackerError?) {
                deliver { onError(error) }
            }
        }
    }

    private companion object {
        val WANTED = listOf(
            Tracker.HR to HealthTrackerType.HEART_RATE_CONTINUOUS,
            Tracker.EDA to HealthTrackerType.EDA_CONTINUOUS,
            Tracker.ACCEL to HealthTrackerType.ACCELEROMETER_CONTINUOUS,
        )

        val EXTRAS = listOf(
            ExtraTracker.SKIN_TEMP to HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
            ExtraTracker.PPG to HealthTrackerType.PPG_CONTINUOUS,
            ExtraTracker.SPO2 to HealthTrackerType.SPO2_ON_DEMAND,
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

        fun toSkinTemp(p: DataPoint) = SkinTempDataPoint(
            timestampMs = p.timestamp,
            objectTemp = p.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE),
            ambientTemp = p.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE),
            status = p.getValue(ValueKey.SkinTemperatureSet.STATUS),
        )

        // Only the requested channels are read: what the SDK returns for the others is not documented.
        fun toPpg(p: DataPoint, channels: Set<PpgChannel>): PpgSample {
            fun read(channel: PpgChannel, key: ValueKey<Int>): Int? = if (channel in channels) p.getValue(key) else null
            return PpgSample(
                timestampMs = p.timestamp,
                green = read(PpgChannel.GREEN, ValueKey.PpgSet.PPG_GREEN),
                greenStatus = read(PpgChannel.GREEN, ValueKey.PpgSet.GREEN_STATUS),
                ir = read(PpgChannel.IR, ValueKey.PpgSet.PPG_IR),
                irStatus = read(PpgChannel.IR, ValueKey.PpgSet.IR_STATUS),
                red = read(PpgChannel.RED, ValueKey.PpgSet.PPG_RED),
                redStatus = read(PpgChannel.RED, ValueKey.PpgSet.RED_STATUS),
            )
        }

        fun toSpo2(p: DataPoint) = Spo2DataPoint(
            timestampMs = p.timestamp,
            spo2 = p.getValue(ValueKey.SpO2Set.SPO2),
            hr = p.getValue(ValueKey.SpO2Set.HEART_RATE),
            status = p.getValue(ValueKey.SpO2Set.STATUS),
        )

        fun ppgType(channel: PpgChannel): PpgType = when (channel) {
            PpgChannel.GREEN -> PpgType.GREEN
            PpgChannel.IR -> PpgType.IR
            PpgChannel.RED -> PpgType.RED
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
