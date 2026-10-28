package com.opentdi.wear.sensing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import com.opentdi.shared.sensing.Tracker
import com.opentdi.wear.BuildConfig
import com.opentdi.wear.link.TelemetrySender
import java.io.File
import java.io.IOException
import kotlin.random.Random

/** One raw accelerometer sample as w_accel_raw.csv logs it: Samsung counts, or m/s² × 1000 for ANDROID_SENSORS. */
data class AccelCounts(val timestampMs: Long, val x: Int, val y: Int, val z: Int)

/** The sensor path a session runs: the Samsung SDK or the SensorManager fallback. Called on the sensing thread. */
interface SensorPath {
    fun flush()
    fun stop()
}

/**
 * One watch logging session (§12): owns the session directory, the CSV writers, the wake lock, the AccelProcessor,
 * the PacketAggregator and the active [SensorPath], and hands its packets to [telemetry] as TelemetryBatches. Every
 * method runs on the sensing thread ([handler]).
 */
class LoggingSession(
    private val context: Context,
    private val handler: Handler,
    private val config: SessionConfig,
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
    private var batches = 0L

    fun start() {
        SensingStateHolder.update {
            SensingState(
                running = true,
                sessionId = sessionId,
                startedElapsedMs = SystemClock.elapsedRealtime(),
                logRawAccel = config.accel.logRawAccel,
            )
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
            writeConfig()
        }
        logEvent("SESSION_START", "sessionId" to sessionId, "aar" to BuildConfig.SAMSUNG_SDK_AAR.ifEmpty { null })

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
            !BuildConfig.SAMSUNG_SDK_BUNDLED -> {
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
                path = SamsungSensorSource(context, handler, this).also { it.start() }
            }
        }
        flushFiles()
    }

    /**
     * Called every second by the service: packet deadline, tracker flush every sdkFlushS, telemetry every
     * telemetryFlushS, files every 5 s.
     */
    fun tick(seconds: Long) {
        poll()
        if (seconds % config.link.sdkFlushS == 0L) path?.flush()
        if (seconds % config.link.telemetryFlushS == 0L) sendTelemetry()
        if (seconds % FILE_FLUSH_S == 0L) flushFiles()
    }

    /** Bins still waiting for data at stop (at most 2 × sdkFlushS) are not written. */
    fun stop() {
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
            // No engine runs yet: every frame is IDLE, without a score. Its seq is the packet's w_packets.csv seq.
            frames += TelemetryFrame(packets, p, TdiState.IDLE, stateReason = null, completedCycles = 0, score = null)
            io {
                packetsCsv?.row(
                    p.timestamp, packets++, p.source, p.hr, p.hrStatus, p.ibiList, p.rmssd, p.skinConductance,
                    p.edaStatus, p.accelMean, p.accelRms, p.accelPeak, p.stillnessScore, p.jerkEvent, p.limpEvent,
                    e.tiltDeg, e.accelSampleCount, e.emittedWallMs,
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

    private fun writers() = listOfNotNull(configCsv, packetsCsv, accelEventsCsv, sensorRawCsv, accelRawCsv)

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
    }
}

const val LOG_TAG = "OTDI.Sensors"

/** Logcat line "EVENT key=value ..." under OTDI.Sensors. Never pass per-sample biosignal values. */
fun logEvent(event: String, vararg fields: Pair<String, Any?>) {
    Log.i(LOG_TAG, (listOf(event) + fields.map { (key, value) -> "$key=$value" }).joinToString(" "))
}
