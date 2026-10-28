package com.opentdi.shared.sensing

import com.opentdi.shared.config.StructuralConstants
import com.opentdi.shared.model.BiosignalPacket
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SessionConfig
import kotlin.math.abs
import kotlin.math.sqrt

/** One `HeartRateSet` data point (§11.2); values are nullable like the SDK's platform types. */
data class HrDataPoint(
    val timestampMs: Long,
    val hr: Int?, // bpm
    val hrStatus: Int?, // HEART_RATE_STATUS, 1 = ok
    val ibiList: List<Int>?, // ms; in a screen-off batch only the first point carries them
    val ibiStatusList: List<Int>?, // 0 = normal
)

/** One `EdaSet` data point (§11.2). */
data class EdaDataPoint(
    val timestampMs: Long,
    val skinConductance: Float?, // µS
    val status: Int?, // 0 = normal
)

/** One `SkinTemperatureSet` data point. */
data class SkinTempDataPoint(
    val timestampMs: Long,
    val objectTemp: Float?, // °C, the skin under the watch
    val ambientTemp: Float?, // °C, around the watch
    val status: Int?, // 0 = normal, -1 = error
)

/** The sensor streams a bin can wait for. */
enum class Tracker { HR, EDA, ACCEL }

/** An emitted packet plus the `w_packets.csv` columns that never go over the wire (§12.2). */
data class EmittedPacket(
    val packet: BiosignalPacket,
    val tiltDeg: Float?,
    val angleZDeg: Float?,
    val accelSampleCount: Int,
    val emittedWallMs: Long,
)

/**
 * 1 Hz packet aggregation (§8.2): bins HR, IBI, EDA and [AccelSecond]s by sample timestamp and emits one
 * [BiosignalPacket] per second, in order. [poll] emits a bin once every tracker in [activeTrackers] has delivered
 * data at or past the bin's end, or once the caller's clock passes the arrival deadline (2 × `sdkFlushS` after the
 * bin's end); missing channels are then null.
 *
 * The base of `DataPoint.getTimestamp()` is UNVERIFIED (§11.2), so sample time is never compared with the caller's
 * clock directly: the deadline maps it through the smallest recent (arrival − sample time). Pure: the caller passes
 * every clock reading, and calls [poll] after each add and on a timer. Not thread-safe.
 */
class PacketAggregator(
    config: SessionConfig,
    private val source: SensorSource,
    private val activeTrackers: Set<Tracker>,
) {
    init {
        require(activeTrackers.isNotEmpty()) { "activeTrackers must not be empty" }
    }

    private val hrHoldMaxMs = config.detection.hrHoldMaxS * 1000L
    private val rmssdWindowMs = config.detection.rmssdWindowS * 1000L
    private val deadlineMs = DEADLINE_SDK_FLUSHES * config.link.sdkFlushS * 1000L
    private val spo2HoldMs = (config.sensors.spo2IntervalMin * 60L + config.sensors.spo2TimeoutS) * 1000L
    private val skinTemps = HeldValues<Float>()
    private val spo2s = HeldValues<Int>()

    private val bins = HashMap<Long, Bin>() // not yet emitted, by start
    private var nextBin: Long? = null
    private var started = false // a bin was emitted, so nextBin only moves forward
    private val watermarks = HashMap<Tracker, Long>() // latest sample time delivered per tracker
    private val clockOffsets = ArrayDeque<Long>() // arrival wall time − latest sample time, recent arrivals

    private var heldHr: Int? = null
    private var heldHrBin = 0L
    private val lastAccepted = ArrayDeque<Int>() // last IBI_MEDIAN_COUNT accepted IBIs
    private var previousAcceptedAtMs: Long? = null // time of the previous IBI in the stream, if it was accepted
    private val beatDiffs = ArrayDeque<BeatDiff>()

    /**
     * Adds one `onDataReceived` batch of the HR tracker. Returns, per point, whether it was late (its bin was
     * already emitted): `late` in `w_sensor_raw.csv`. Late data never changes a packet.
     */
    fun addHrBatch(points: List<HrDataPoint>, arrivalWallMs: Long): List<Boolean> {
        if (points.isEmpty()) return emptyList()
        noteArrival(Tracker.HR, points.maxOf { it.timestampMs }, arrivalWallMs)
        val late = points.map { p ->
            val bin = binAt(p.timestampMs) ?: return@map true
            bin.hrStatus = p.hrStatus
            if (p.hrStatus == HR_STATUS_OK && p.hr != null && p.hr in HR_MIN_BPM..HR_MAX_BPM) bin.validHr = p.hr
            false
        }
        addIbis(points)
        return late
    }

    /** Adds one batch of the EDA tracker; returns the late flags as [addHrBatch] does. */
    fun addEdaBatch(points: List<EdaDataPoint>, arrivalWallMs: Long): List<Boolean> {
        if (points.isEmpty()) return emptyList()
        noteArrival(Tracker.EDA, points.maxOf { it.timestampMs }, arrivalWallMs)
        return points.map { p ->
            val bin = binAt(p.timestampMs) ?: return@map true
            bin.edaStatus = p.status
            val value = p.skinConductance
            if (p.status == EDA_STATUS_OK && value != null && value >= EDA_MIN_US) bin.validEda = value
            false
        }
    }

    /** Adds the seconds an [AccelProcessor.add] call closed. A closed second means a sample past its end arrived. */
    fun addAccelSeconds(seconds: List<AccelSecond>, arrivalWallMs: Long) {
        if (seconds.isEmpty()) return
        // The open second's samples are not visible here, so the closed second's end stands in for the latest sample.
        noteArrival(Tracker.ACCEL, seconds.maxOf { it.startMs } + 1000, arrivalWallMs)
        for (second in seconds) binAt(second.startMs)?.accel = second
    }

    /**
     * Adds one batch of the skin-temperature tracker. It never holds a bin back (it is not an active tracker), and a
     * point that arrives after its bin went out still sets the held value of the bins after it.
     */
    fun addSkinTempBatch(points: List<SkinTempDataPoint>) {
        for (p in points) {
            val celsius = p.objectTemp
            if (p.status == SKIN_TEMP_STATUS_OK && celsius != null) skinTemps.add(p.timestampMs, celsius)
        }
    }

    /** Adds a completed SpO2 spot check, held until the next check could have completed (interval + timeout). */
    fun addSpo2(timestampMs: Long, spo2: Int) = spo2s.add(timestampMs, spo2)

    /** Emits, in order, every bin that is complete or past its deadline at [nowWallMs]. */
    fun poll(nowWallMs: Long): List<EmittedPacket> {
        val clockOffset = clockOffsets.minOrNull() ?: 0L
        val out = ArrayList<EmittedPacket>()
        while (true) {
            val start = nextBin ?: break
            val end = start + 1000
            val complete = activeTrackers.all { (watermarks[it] ?: Long.MIN_VALUE) >= end }
            if (!complete && nowWallMs < end + clockOffset + deadlineMs) break
            out += emit(start, bins.remove(start), nowWallMs)
            nextBin = end
            started = true
        }
        return out
    }

    private fun noteArrival(tracker: Tracker, latestSampleMs: Long, arrivalWallMs: Long) {
        watermarks[tracker] = maxOf(watermarks[tracker] ?: Long.MIN_VALUE, latestSampleMs)
        clockOffsets.addLast(arrivalWallMs - latestSampleMs)
        if (clockOffsets.size > CLOCK_OFFSET_ARRIVALS) clockOffsets.removeFirst()
    }

    /** The pending bin that holds [timestampMs], or null when that bin was already emitted (late data). */
    private fun binAt(timestampMs: Long): Bin? {
        val start = binStartMs(timestampMs)
        val next = nextBin
        if (next == null || (!started && start < next)) {
            nextBin = start // until the first emission the stream starts at the earliest data
        } else if (start < next) {
            return null
        }
        return bins.getOrPut(start) { Bin() }
    }

    /** Times the batch's IBIs back from its last point, t_j = t_end − Σ_{k>j} IBI_k, and accepts or rejects each. */
    private fun addIbis(points: List<HrDataPoint>) {
        val ibis = points.flatMap { p ->
            p.ibiList.orEmpty().mapIndexed { j, ibi -> ibi to p.ibiStatusList?.getOrNull(j) }
        }
        var t = points.last().timestampMs - ibis.sumOf { it.first.toLong() }
        for ((ibi, status) in ibis) {
            t += ibi
            val valid = status == IBI_STATUS_OK && ibi in IBI_MIN_MS..IBI_MAX_MS && nearMedian(ibi)
            val bin = if (valid) binAt(t) else null
            if (bin == null) {
                previousAcceptedAtMs = null // rejected or late: no successive difference across it
                continue
            }
            bin.ibis += ibi
            // A gap above IBI_MAX_MS means undelivered beats (e.g. a reconnect); the backward timing errs by ~1 s only.
            val previous = previousAcceptedAtMs
            if (previous != null && t - ibi - previous <= IBI_MAX_MS) {
                beatDiffs.addLast(BeatDiff(t, ibi - lastAccepted.last()))
            }
            lastAccepted.addLast(ibi)
            if (lastAccepted.size > IBI_MEDIAN_COUNT) lastAccepted.removeFirst()
            previousAcceptedAtMs = t
        }
    }

    private fun nearMedian(ibi: Int): Boolean {
        if (lastAccepted.size < IBI_MEDIAN_MIN_ACCEPTED) return true
        val m = median(lastAccepted.map { it.toDouble() })
        return abs(ibi - m) <= IBI_MAX_DEVIATION * m
    }

    private fun emit(start: Long, bin: Bin?, nowWallMs: Long): EmittedPacket {
        bin?.validHr?.let {
            heldHr = it
            heldHrBin = start
        }
        val hr = heldHr?.takeIf { start - heldHrBin <= hrHoldMaxMs }

        val end = start + 1000
        beatDiffs.removeAll { it.timeMs < end - rmssdWindowMs }
        val pairs = beatDiffs.filter { it.timeMs < end }
        val rmssd = if (pairs.size >= RMSSD_MIN_PAIRS) {
            sqrt(pairs.sumOf { it.diffMs.toDouble() * it.diffMs } / pairs.size)
        } else {
            null
        }

        val accel = bin?.accel
        val packet = BiosignalPacket(
            timestamp = start,
            hr = hr?.toFloat(),
            ibiList = bin?.ibis.orEmpty(),
            rmssd = rmssd?.toFloat(),
            skinConductance = bin?.validEda,
            accelRms = accel?.accelRms,
            edaStatus = bin?.edaStatus,
            hrStatus = bin?.hrStatus,
            accelMean = accel?.accelMean,
            accelPeak = accel?.accelPeak,
            jerkEvent = accel?.jerkEvent == true,
            limpEvent = accel?.limpEvent == true,
            stillnessScore = accel?.stillnessScore,
            source = source,
            skinTemperature = skinTemps.at(start, SKIN_TEMP_HOLD_MS),
            spo2 = spo2s.at(start, spo2HoldMs),
        )
        return EmittedPacket(packet, accel?.tiltDeg, accel?.angleZDeg, accel?.sampleCount ?: 0, nowWallMs)
    }

    companion object {
        // Structural constants (§8.15), logged as const.* in w_config.csv.
        const val HR_MIN_BPM = 30
        const val HR_MAX_BPM = 220
        const val IBI_MIN_MS = 300
        const val IBI_MAX_MS = 2000
        const val IBI_MEDIAN_COUNT = 7
        const val IBI_MEDIAN_MIN_ACCEPTED = 3
        const val IBI_MAX_DEVIATION = 0.20
        const val RMSSD_MIN_PAIRS = 20
        const val EDA_MIN_US = 0.05f
        const val DEADLINE_SDK_FLUSHES = 2
        const val SKIN_TEMP_HOLD_MS = StructuralConstants.SKIN_TEMP_HOLD_S * 1000L

        private const val HR_STATUS_OK = 1
        private const val IBI_STATUS_OK = 0
        private const val EDA_STATUS_OK = 0
        private const val SKIN_TEMP_STATUS_OK = 0
        private const val CLOCK_OFFSET_ARRIVALS = 64
    }
}

/**
 * Values of one slow stream in sample-time order (Samsung keeps each tracker's order). [at] gives the newest value
 * sampled before a bin's end, if it was sampled at most maxAgeMs before the bin; ask for bins in increasing order.
 */
private class HeldValues<T : Any> {
    private val values = ArrayDeque<Pair<Long, T>>()

    fun add(timeMs: Long, value: T) = values.addLast(timeMs to value)

    fun at(binStart: Long, maxAgeMs: Long): T? {
        val end = binStart + 1000
        while (values.size > 1 && values[1].first < end) values.removeFirst()
        val (timeMs, value) = values.firstOrNull()?.takeIf { it.first < end } ?: return null
        return value.takeIf { binStart - binStartMs(timeMs) <= maxAgeMs }
    }
}

private class Bin {
    var hrStatus: Int? = null // of the last HR point
    var validHr: Int? = null // of the last valid HR point
    var edaStatus: Int? = null // of the last EDA point
    var validEda: Float? = null // of the last valid EDA point
    val ibis = ArrayList<Int>() // accepted
    var accel: AccelSecond? = null
}

/** IBI_k − IBI_{k−1} of two consecutive accepted beats, timed at the later beat. */
private class BeatDiff(val timeMs: Long, val diffMs: Int)
