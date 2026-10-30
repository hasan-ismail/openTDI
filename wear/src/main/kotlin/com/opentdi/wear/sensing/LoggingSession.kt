package com.opentdi.wear.sensing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.opentdi.shared.config.StructuralConstants
import com.opentdi.shared.config.thresholds
import com.opentdi.shared.config.toConfigRows
import com.opentdi.shared.csv.CsvFile
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.TelemetryBatch
import com.opentdi.shared.model.TelemetryFrame
import com.opentdi.shared.model.newSessionId
import com.opentdi.shared.sensing.AccelProcessor
import com.opentdi.shared.sensing.AccelSample
import com.opentdi.shared.sensing.EdaDataPoint
import com.opentdi.shared.sensing.HrDataPoint
import com.opentdi.shared.sensing.PacketAggregator
import com.opentdi.shared.sensing.SkinTempDataPoint
import com.opentdi.shared.sensing.Tracker
import com.opentdi.wear.BuildConfig
import com.opentdi.wear.link.TelemetrySender
import java.io.File
import java.io.IOException
import java.util.EnumMap
import kotlin.random.Random

/** One raw accelerometer sample as w_accel_raw.csv logs it: Samsung counts, or m/s² × 1000 for ANDROID_SENSORS. */
data class AccelCounts(val timestampMs: Long, val x: Int, val y: Int, val z: Int)

/** One PPG_CONTINUOUS sample (PpgSet) as w_ppg_raw.csv logs it; channels that were not requested are null. */
data class PpgSample(
    val timestampMs: Long,
    val green: Int?,
    val greenStatus: Int?,
    val ir: Int?,
    val irStatus: Int?,
    val red: Int?,
    val redStatus: Int?,
)

/** One SPO2_ON_DEMAND data point (SpO2Set). */
data class Spo2DataPoint(val timestampMs: Long, val spo2: Int?, val hr: Int?, val status: Int?)

/** The sensor path a session runs: the Samsung SDK or the SensorManager fallback. Called on the sensing thread. */
interface SensorPath {
    fun flush()
    fun stop()
}

/** The Samsung SDK path, made by [SamsungKit] so that its SDK classes may come from the user's own kit. */
interface SamsungPath : SensorPath {
    fun start()
    fun startSpo2(): Boolean
    fun stopSpo2()
    fun stopExtra(extra: ExtraTracker)
}

/**
 * One watch logging session (§12): owns the session directory, the CSV writers, the wake lock, the AccelProcessor,
 * the PacketAggregator and the active [SensorPath], and hands its packets to [telemetry] as TelemetryBatches. Every
 * method runs on the sensing thread ([handler]).
 */
class LoggingSession(
    private val context: Context,
    private val handler: Handler,
    val config: SessionConfig,
    private val androidSensorsOnly: Boolean,
    private val telemetry: TelemetrySender,
) {
    val sessionId = newSessionId(System.currentTimeMillis(), Random.Default)
    private val dir = File(context.filesDir, "sessions/$sessionId")
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTDI:sensing")

    private var configCsv: CsvWriter? = null
    private var packetsCsv: CsvWriter? = null
    private var accelEventsCsv: CsvWriter? = null
    private var sensorRawCsv: CsvWriter? = null
    private var accelRawCsv: CsvWriter? = null
    private var csvFailed = false
    private var skinTempCsv: CsvWriter? = null
    private var spo2Csv: CsvWriter? = null
    private var ppgRawCsv: CsvWriter? = null

    private var path: SensorPath? = null
    private var source: SensorSource? = null
    private var accel: AccelProcessor? = null
    private var aggregator: PacketAggregator? = null
    private var requested = emptySet<Tracker>()
    private val delivered = HashSet<Tracker>()
    private var status: Pair<SensorStatus, Tracker?>? = null // the last logged status
    private var gravityMode = GravityMode.UNKNOWN
    private var packets = 0L
    private var ibis = 0L
    private var storageLow = false
    private var accelCallbacks = 0L
    private var lastAccelBatchSize = -1
    private val frames = ArrayList<TelemetryFrame>() // emitted since the last telemetry batch
    private val stillSeconds = ArrayList<Float?>() // each packet's stillness since the last drainStillness()
    private var batches = 0L
    private val extraStates = EnumMap<ExtraTracker, String>(ExtraTracker::class.java) // "on", or why it is off
    private var spo2Check: Spo2Check? = null
    private var ppgSamples = 0L
    private var ppgSamplesAtRate = 0L

    /** [keepNight]: a sensor restart during the recording, whose alarms, guide and dream-report prompt carry on. */
    fun start(keepNight: Boolean = false) {
        SensingStateHolder.update {
            val fresh = SensingState(
                running = true,
                sessionId = sessionId,
                startedElapsedMs = SystemClock.elapsedRealtime(),
                logRawAccel = config.accel.logRawAccel,
            )
            // Only the night's fields: the old session's status, source and fallback cause must not survive.
            if (!keepNight) {
                fresh
            } else {
                fresh.copy(
                    startedElapsedMs = it.startedElapsedMs,
                    alarmRinging = it.alarmRinging,
                    wakeAgainAtMs = it.wakeAgainAtMs,
                    wbtbSkipTonight = it.wbtbSkipTonight,
                    wbtbAheadAtMs = it.wbtbAheadAtMs,
                    wbtbSkippedTonight = it.wbtbSkippedTonight,
                    wbtbBackToSleepAtMs = it.wbtbBackToSleepAtMs,
                    lastWakeText = it.lastWakeText,
                    snoozesLeft = it.snoozesLeft,
                    lateCuesTraining = it.lateCuesTraining,
                    guideOffered = it.guideOffered,
                    guideRunning = it.guideRunning,
                    reportAfter = it.reportAfter,
                )
            }
        }
        if (config.link.watchWakeLock) acquireWakeLock()
        dir.mkdirs()
        deleteOldSessions()
        io {
            configCsv = open(CsvFiles.W_CONFIG)
            packetsCsv = open(CsvFiles.W_PACKETS)
            accelEventsCsv = open(CsvFiles.W_ACCEL_EVENTS)
            sensorRawCsv = open(CsvFiles.W_SENSOR_RAW)
            if (config.accel.logRawAccel) accelRawCsv = open(CsvFiles.W_ACCEL_RAW)
            if (config.sensors.skinTempEnabled) skinTempCsv = open(CsvFiles.W_SKIN_TEMP)
            if (config.sensors.spo2SpotChecks) spo2Csv = open(CsvFiles.W_SPO2)
            if (config.sensors.ppgRawEnabled) ppgRawCsv = open(CsvFiles.W_PPG_RAW)
            writeConfig()
        }
        logEvent(
            "SESSION_START", "sessionId" to sessionId, "aar" to BuildConfig.SAMSUNG_SDK_AAR.ifEmpty { null },
            "samsungKit" to SamsungKit.source(context),
        )

        val hss = hssVersionName()
        configRow("hssVersionName", hss)
        logEvent(
            "HSS_VERSION",
            "package" to HSS_PACKAGE,
            "versionName" to hss,
            "min" to HSS_MIN_VERSION,
            "supported" to hss?.takeIf { it.isNotEmpty() }?.let(::hssVersionSupported),
        )
        when {
            androidSensorsOnly -> startAndroidSensors(cause = null)
            SamsungKit.source(context) == null -> {
                setStatus(SensorStatus.AAR_NOT_BUNDLED)
                startAndroidSensors(SensorStatus.AAR_NOT_BUNDLED)
            }
            else -> {
                // Early verdict from the version; the SDK's onConnectionFailed confirms it and keeps the resolution.
                if (hss == null) {
                    setStatus(SensorStatus.HSS_NOT_INSTALLED)
                } else if (hss.isNotEmpty() && !hssVersionSupported(hss)) {
                    setStatus(SensorStatus.HSS_TOO_OLD)
                }
                val samsung = SamsungKit.create(context, handler, this)
                if (samsung == null) { // a kit that does not load: the wrong file or an SDK this code does not fit
                    logEvent("SAMSUNG_KIT_UNUSABLE", "samsungKit" to SamsungKit.source(context))
                    setStatus(SensorStatus.AAR_NOT_BUNDLED)
                    startAndroidSensors(SensorStatus.AAR_NOT_BUNDLED)
                } else {
                    path = samsung
                    samsung.start()
                }
            }
        }
        flushFiles()
    }

    /**
     * Called every second by the service: packet deadline, tracker flush every sdkFlushS, telemetry every
     * telemetryFlushS, SpO2 spot checks, the PPG rate every 10 s, files every 5 s.
     */
    fun tick(seconds: Long) {
        poll()
        if (seconds % config.link.sdkFlushS == 0L) path?.flush()
        if (seconds % config.link.telemetryFlushS == 0L) sendTelemetry()
        spo2Check?.let { if (seconds - it.startS >= config.sensors.spo2TimeoutS) endSpo2Check("SPO2_TIMEOUT") }
        if (spo2CheckDue(seconds, config.sensors.spo2IntervalMin)) startSpo2Check(seconds)
        if (seconds % PPG_RATE_WINDOW_S == 0L && ppgSamples > 0) {
            val rate = (ppgSamples - ppgSamplesAtRate).toFloat() / PPG_RATE_WINDOW_S
            ppgSamplesAtRate = ppgSamples
            SensingStateHolder.update { it.copy(ppgRate = rate) }
        }
        if (seconds % FILE_FLUSH_S == 0L) flushFiles()
    }

    /** The stillness of each packet since the last call, oldest first: with the screen off they come in bursts. */
    fun drainStillness(): List<Float?> = stillSeconds.toList().also { stillSeconds.clear() }

    /** Logging-only trackers run while their CSV is open: enabled in the config, and PPG not stopped by STORAGE_LOW. */
    fun extraWanted(extra: ExtraTracker): Boolean = when (extra) {
        ExtraTracker.SKIN_TEMP -> skinTempCsv != null
        ExtraTracker.PPG -> ppgRawCsv != null
        ExtraTracker.SPO2 -> spo2Csv != null
    }

    /** Shows, logs and records whether a logging-only tracker runs: [why] it is off, or null when it runs. */
    fun setExtraOff(extra: ExtraTracker, why: String?) {
        val state = why ?: "on"
        if (extraStates.put(extra, state) == state) return
        configRow("extra.$extra", state)
        logEvent("EXTRA", "tracker" to extra, "state" to state)
        val off = extraStates.filterValues { it != "on" }
        SensingStateHolder.update { it.copy(extrasOff = off) }
        if (extra == ExtraTracker.SPO2 && why != null) endSpo2Check("SPO2_ERROR")
    }

    /** The Samsung path stopped for the rest of the session: every logging-only tracker still on is now off ([why]). */
    fun extrasStopped(why: String) {
        for (extra in extraStates.filterValues { it == "on" }.keys) setExtraOff(extra, why)
    }

    /** Bins still waiting for data at stop (at most 2 × sdkFlushS) are not written. */
    fun stop() {
        endSpo2Check("SPO2_ABORTED")
        path?.stop()
        path = null
        poll()
        val lateAccelSamples = accel?.lateSamples
        aggregator = null // sensor events still queued on the handler are dropped
        accel = null
        io { writers().forEach { it.close() } }
        if (wakeLock.isHeld) wakeLock.release()
        logEvent("SESSION_STOP", "sessionId" to sessionId, "packets" to packets, "lateAccelSamples" to lateAccelSamples)
        SensingStateHolder.update { it.copy(running = false) }
    }

    /** Switches to the SensorManager fallback; [cause] is shown while it runs (null when the user chose it). */
    fun startAndroidSensors(cause: SensorStatus?, unsupported: Tracker? = null) {
        path?.stop()
        extrasStopped("using basic sensors")
        if (cause != null) {
            SensingStateHolder.update { it.copy(fallbackCause = cause, unsupported = unsupported ?: it.unsupported) }
        }
        path = AndroidSensorSource(context, handler, this).also { it.start() }
    }

    /** The streams of [source] are registered; their data starts now. */
    fun begin(source: SensorSource, trackers: Set<Tracker>) {
        delivered.clear()
        if (aggregator == null || this.source != source) {
            // A source switch mid-session drops the old source's pending bins. A stream that stops within one source
            // (a tracker error, or a reconnect without EDA) keeps its aggregator: its bins go out at the deadline.
            this.source = source
            accel = AccelProcessor(config.accel)
            aggregator = PacketAggregator(config, source, trackers)
        }
        requested = trackers
        SensingStateHolder.update { it.copy(source = source, edaAvailable = Tracker.EDA in trackers) }
        logEvent("SOURCE", "source" to source, "trackers" to trackers.joinToString(";"))
    }

    fun onHr(points: List<HrDataPoint>, arrivalWallMs: Long) {
        val late = aggregator?.addHrBatch(points, arrivalWallMs) ?: return
        spo2Check?.let { check -> check.hrOkPoints += points.count { it.hrStatus == 1 } } // HEART_RATE_STATUS ok
        io {
            sensorRawCsv?.let { csv ->
                points.forEachIndexed { i, p ->
                    csv.row(
                        arrivalWallMs, p.timestampMs, Tracker.HR, p.hr, p.hrStatus, p.ibiList, p.ibiStatusList,
                        null, null, points.size, late[i],
                    )
                }
            }
        }
        onDelivered(Tracker.HR)
    }

    fun onEda(points: List<EdaDataPoint>, arrivalWallMs: Long) {
        val late = aggregator?.addEdaBatch(points, arrivalWallMs) ?: return
        io {
            sensorRawCsv?.let { csv ->
                points.forEachIndexed { i, p ->
                    csv.row(
                        arrivalWallMs, p.timestampMs, Tracker.EDA, null, null, null, null,
                        p.skinConductance, p.status, points.size, late[i],
                    )
                }
            }
        }
        onDelivered(Tracker.EDA)
    }

    fun onAccel(samples: List<AccelCounts>, arrivalWallMs: Long) {
        val processor = accel ?: return
        val agg = aggregator ?: return
        // §15 #6 measures the callback batch sizes: the first callbacks, then each change of size.
        if (accelCallbacks++ < LOGGED_ACCEL_CALLBACKS || samples.size != lastAccelBatchSize) {
            logEvent(
                "ACCEL_BATCH", "size" to samples.size, "arrival" to arrivalWallMs,
                "firstTs" to samples.firstOrNull()?.timestampMs, "lastTs" to samples.lastOrNull()?.timestampMs,
            )
        }
        lastAccelBatchSize = samples.size
        val out = processor.add(samples.map(::toG))
        io {
            accelRawCsv?.let { csv -> samples.forEach { csv.row(it.timestampMs, it.x, it.y, it.z) } }
            out.events.forEach { accelEventsCsv?.row(it.sampleTsMs, it.event, it.value1, it.value2, it.detail) }
        }
        agg.addAccelSeconds(out.seconds, arrivalWallMs)
        if (processor.gravityMode != gravityMode) {
            gravityMode = processor.gravityMode
            configRow("gravityMode", gravityMode)
            logEvent("GRAVITY_MODE", "mode" to gravityMode)
            SensingStateHolder.update { it.copy(gravityMode = gravityMode) }
        }
        if (processor.gravityProgress != SensingStateHolder.state.value.gravityProgress) {
            SensingStateHolder.update { it.copy(gravityProgress = processor.gravityProgress) }
        }
        onDelivered(Tracker.ACCEL)
    }

    fun onSkinTemp(points: List<SkinTempDataPoint>, arrivalWallMs: Long) {
        val agg = aggregator ?: return
        agg.addSkinTempBatch(points)
        io {
            skinTempCsv?.let { csv ->
                points.forEach {
                    csv.row(arrivalWallMs, it.timestampMs, it.objectTemp, it.ambientTemp, it.status, points.size)
                }
            }
        }
    }

    fun onPpg(samples: List<PpgSample>) {
        if (aggregator == null) return // stopped: the CSVs are closed
        val csv = ppgRawCsv ?: return
        io { samples.forEach { csv.row(it.timestampMs, it.green, it.greenStatus, it.ir, it.irStatus, it.red, it.redStatus) } }
        ppgSamples += samples.size
    }

    fun onSpo2(points: List<Spo2DataPoint>, arrivalWallMs: Long) {
        if (aggregator == null) return
        val check = spo2Check ?: return // delivered after the check ended
        io { points.forEach { spo2Csv?.row(arrivalWallMs, it.timestampMs, it.spo2, it.hr, it.status, "DATA", null, null) } }
        val finished = points.lastOrNull { spo2Finished(it.status) }
        check.last = finished ?: points.last()
        if (finished != null) {
            endSpo2Check(if (finished.status == SPO2_STATUS_COMPLETED) "SPO2_RESULT" else "SPO2_TIMEOUT")
        }
    }

    /** One spot check at a time; none while SpO2 is off (unsupported, no permission, disabled or failed). */
    private fun startSpo2Check(seconds: Long) {
        if (spo2Check != null || (path as? SamsungPath)?.startSpo2() != true) return
        spo2Check = Spo2Check(seconds, System.currentTimeMillis())
        logEvent("SPO2_START")
    }

    /** Stops the measurement and writes its summary row; SPO2_RESULT also updates the packets and the watch face. */
    fun endSpo2Check(event: String) {
        val check = spo2Check ?: return
        spo2Check = null
        (path as? SamsungPath)?.stopSpo2()
        val now = System.currentTimeMillis()
        val last = check.last
        val spo2 = last?.spo2?.takeIf { event == "SPO2_RESULT" }
        if (last != null && spo2 != null) {
            aggregator?.addSpo2(last.timestampMs, spo2)
            SensingStateHolder.update { it.copy(lastSpo2 = spo2, lastSpo2AtMs = now) }
        }
        val windowMs = now - check.startWallMs
        io { spo2Csv?.row(now, last?.timestampMs, spo2, last?.hr, last?.status, event, check.hrOkPoints, windowMs) }
        logEvent(event, "status" to last?.status, "hrOkPoints" to check.hrOkPoints, "windowMs" to windowMs)
    }

    fun setStatus(
        status: SensorStatus,
        unsupported: Tracker? = null,
        message: String? = null,
        resolve: ((Activity) -> Boolean)? = null,
    ) {
        SensingStateHolder.update {
            it.copy(
                status = status,
                unsupported = unsupported ?: it.unsupported,
                statusMessage = message,
                resolve = resolve ?: it.resolve,
            )
        }
        if ((status to unsupported) == this.status) return // e.g. CONNECTION_FAILED again on every retry
        this.status = status to unsupported
        configRow("sensorStatus", status)
        if (unsupported != null) configRow("unsupportedTracker", unsupported)
        logEvent("STATUS", "status" to status, "source" to source, "tracker" to unsupported, "message" to message)
        flushFiles()
    }

    /** Appends one w_config.csv row (values that become known or change during the session). */
    fun configRow(key: String, value: Any?) = io { configCsv?.row(key, value) }

    private fun onDelivered(tracker: Tracker) {
        poll()
        val src = source ?: return
        if (delivered.add(tracker)) deliveringStatus(src, requested, delivered, status?.first)?.let { setStatus(it) }
    }

    private fun poll() {
        val emitted = aggregator?.poll(System.currentTimeMillis()).orEmpty()
        if (emitted.isEmpty()) return
        for (e in emitted) {
            val p = e.packet
            val seq = packets++ // outside io(): after a CSV failure the phone still gets rising seqs
            // No engine runs yet: every frame is IDLE, without a score. Its seq is the packet's w_packets.csv seq.
            frames += TelemetryFrame(seq, p, TdiState.IDLE, stateReason = null, completedCycles = 0, score = null)
            stillSeconds += p.stillnessScore
            io {
                packetsCsv?.row(
                    p.timestamp, seq, p.source, p.hr, p.hrStatus, p.ibiList, p.rmssd, p.skinConductance,
                    p.edaStatus, p.accelMean, p.accelRms, p.accelPeak, p.stillnessScore, p.jerkEvent, p.limpEvent,
                    e.tiltDeg, e.accelSampleCount, e.emittedWallMs, p.skinTemperature, p.spo2, e.angleZDeg,
                )
            }
            ibis += p.ibiList.size
        }
        SensingStateHolder.update {
            it.copy(lastPacket = emitted.last(), packetsWritten = packets, ibisAccepted = ibis)
        }
    }

    /** The frames since the last batch, as one TelemetryBatch (§9.3); the sender drops it if the phone is away. */
    private fun sendTelemetry() {
        val src = source
        if (frames.isEmpty() || src == null) return
        // Packets flow before every tracker has delivered, when there is no status yet: the source's own stands in.
        val sensorStatus = status?.first ?: if (src == SensorSource.SAMSUNG_SDK) {
            SensorStatus.CONNECTED_SAMSUNG
        } else {
            SensorStatus.FALLBACK_ANDROID_SENSORS
        }
        telemetry.send(
            TelemetryBatch(
                sessionId = sessionId,
                batchSeq = batches++,
                sensorStatus = sensorStatus,
                gravityMode = gravityMode,
                frames = frames.toList(),
                sentAtWallMs = System.currentTimeMillis(),
                watchBatteryPct = context.getSystemService(BatteryManager::class.java)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            ),
        )
        frames.clear()
    }

    // The live path uses the logged integers, so the AccelConfig replay of w_accel_raw.csv is bit-exact (§12.6).
    private fun toG(s: AccelCounts): AccelSample = if (source == SensorSource.SAMSUNG_SDK) {
        AccelSample.fromSamsungCounts(s.timestampMs, s.x, s.y, s.z)
    } else {
        AccelSample.fromAndroidMs2(s.timestampMs, s.x / 1000f, s.y / 1000f, s.z / 1000f)
    }

    private fun flushFiles() {
        val free = dir.usableSpace
        if (ppgRawCsv != null && free < MIN_FREE_BYTES_PPG) { // the biggest file stops first, with its tracker
            io { ppgRawCsv?.close() }
            ppgRawCsv = null
            (path as? SamsungPath)?.stopExtra(ExtraTracker.PPG)
            setExtraOff(ExtraTracker.PPG, "storage low")
            logEvent("STORAGE_LOW", "free_mb" to free / (1024 * 1024), "stopped" to CsvFiles.W_PPG_RAW.name)
        }
        if (!storageLow && free < MIN_FREE_BYTES) {
            storageLow = true
            io { listOfNotNull(sensorRawCsv, accelRawCsv).forEach { it.close() } }
            sensorRawCsv = null
            accelRawCsv = null
            configRow("storageStatus", "STORAGE_LOW")
            logEvent("STORAGE_LOW", "free_mb" to free / (1024 * 1024))
        }
        io { writers().forEach { it.flush() } }
    }

    /** Runs CSV writes; the first IOException closes every file and ends the logging, never the sensing. */
    private inline fun io(block: () -> Unit) {
        if (csvFailed) return
        try {
            block()
        } catch (e: IOException) {
            csvFailed = true
            logEvent("CSV_FAILED", "dir" to sessionId, "error" to e.message)
            writers().forEach { runCatching { it.close() } }
        }
    }

    private fun writers() = listOfNotNull(
        configCsv, packetsCsv, accelEventsCsv, sensorRawCsv, accelRawCsv, skinTempCsv, spo2Csv, ppgRawCsv,
    )

    private fun open(file: CsvFile) = CsvWriter(File(dir, file.name).bufferedWriter(), file.columns)

    private fun writeConfig() {
        val rows = listOf(
            "appVersion" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE.toString(),
            "applicationId" to context.packageName,
            "deviceModel" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "androidSdkInt" to Build.VERSION.SDK_INT.toString(),
            "samsungSdkAar" to BuildConfig.SAMSUNG_SDK_AAR,
            "samsungKit" to SamsungKit.source(context).orEmpty(),
        ) + config.toConfigRows() + config.detection.thresholds().toConfigRows() + StructuralConstants.configRows()
        rows.forEach { (key, value) -> configRow(key, value) }
        configRow("gravityMode", gravityMode)
    }

    /** versionName of Health Sensor Service ("" if it has none), or null if it is not installed (§11.4). */
    @Suppress("DEPRECATION") // getPackageInfo(String, PackageInfoFlags) needs API 33; minSdk is 30
    private fun hssVersionName(): String? = try {
        context.packageManager.getPackageInfo(HSS_PACKAGE, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    // Held for the whole session (§14.4) and released in stop(); process death releases it too.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() = wakeLock.acquire()

    private fun deleteOldSessions() {
        val root = dir.parentFile ?: return
        val names = root.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        for (name in staleSessionDirs(names, KEEP_SESSIONS) - sessionId) File(root, name).deleteRecursively()
    }

    private companion object {
        const val FILE_FLUSH_S = 5L // §12.1
        const val KEEP_SESSIONS = 30 // §12.1
        const val MIN_FREE_BYTES = 50L * 1024 * 1024 // §12.1: below this, w_sensor_raw.csv stops
        const val LOGGED_ACCEL_CALLBACKS = 10 // ACCEL_BATCH lines before only size changes are logged
        const val MIN_FREE_BYTES_PPG = 2 * MIN_FREE_BYTES // GUESSED: w_ppg_raw.csv stops before every other file
        const val PPG_RATE_WINDOW_S = 10L // two default flush periods, so screen-off batches average out
    }
}

/** A running SpO2 spot check: its start, the HR points with status 1 that arrived meanwhile, its latest data point. */
private class Spo2Check(val startS: Long, val startWallMs: Long) {
    var hrOkPoints = 0
    var last: Spo2DataPoint? = null
}

const val LOG_TAG = "OTDI.Sensors"

/** Logcat line "EVENT key=value ..." under OTDI.Sensors. Never pass per-sample biosignal values. */
fun logEvent(event: String, vararg fields: Pair<String, Any?>) {
    Log.i(LOG_TAG, (listOf(event) + fields.map { (key, value) -> "$key=$value" }).joinToString(" "))
}
