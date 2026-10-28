package com.opentdi.shared.sensing

import com.opentdi.shared.model.AccelConfig
import com.opentdi.shared.model.GravityMode
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/** One accelerometer sample in g, timed by `DataPoint.getTimestamp()` (ms). */
data class AccelSample(val timestampMs: Long, val xG: Double, val yG: Double, val zG: Double) {
    companion object {
        const val STANDARD_GRAVITY_MS2 = 9.80665

        /** Samsung `AccelerometerSet.ACCELEROMETER_X/Y/Z` raw counts. */
        fun fromSamsungCounts(timestampMs: Long, x: Int, y: Int, z: Int) = AccelSample(
            timestampMs,
            samsungCountsToG(x.toDouble()),
            samsungCountsToG(y.toDouble()),
            samsungCountsToG(z.toDouble()),
        )

        /** Android `Sensor.TYPE_ACCELEROMETER` values in m/s² (the `ANDROID_SENSORS` fallback, §11.8). */
        fun fromAndroidMs2(timestampMs: Long, x: Float, y: Float, z: Float) =
            AccelSample(timestampMs, x / STANDARD_GRAVITY_MS2, y / STANDARD_GRAVITY_MS2, z / STANDARD_GRAVITY_MS2)
    }
}

/** g = counts × 4.0 / 16383.75: Samsung's m/s² formula divided by 9.81 (§8.1). */
internal fun samsungCountsToG(counts: Double): Double = counts * 4.0 / 16383.75

/**
 * §8.1 result for the second [startMs, startMs + 1000). The accel fields are null with fewer than 20 samples.
 * [tiltDeg] and [sampleCount] go to `w_packets.csv` only, never over the wire.
 */
data class AccelSecond(
    val startMs: Long,
    val sampleCount: Int,
    val accelMean: Float?, // g, mean |a|
    val accelRms: Float?, // g, RMS of the dynamic magnitude d
    val accelPeak: Float?, // g, max d
    val stillnessScore: Float?, // 0..1, 1 = dead still
    val tiltDeg: Float?, // θ_s against the limp reference; null without one
    val jerkEvent: Boolean,
    val limpEvent: Boolean,
)

/** `w_accel_events.csv` event names (§12.2), with what [AccelEvent.value1] / [AccelEvent.value2] / detail hold. */
enum class AccelEventType {
    JERK_BURST_OPEN, // d of the opening sample (g)
    JERK_CONFIRMED, // burst ms, peak d (g)
    JERK_REJECTED_LONG, // burst ms, peak d (g)
    JERK_REJECTED_POST_MOVE, // burst ms, peak d (g)
    LIMP_REF_SET,
    LIMP_REF_CLEARED, // detail: MOVEMENT, LIMP or AGE
    LIMP_CONFIRMED, // tilt (°)
    GRAVITY_MODE, // median accelMean of the still seconds (g); detail: the mode
    LIMP_UNAVAILABLE, // detail: the mode
}

/**
 * One `w_accel_events.csv` row. [sampleTsMs] is the sample that caused the event or, for decisions taken when a
 * second closes (jerk post-still check, limp, gravity mode), the start of that second.
 */
data class AccelEvent(
    val sampleTsMs: Long,
    val event: AccelEventType,
    val value1: Float? = null,
    val value2: Float? = null,
    val detail: String? = null,
)

/** What one [AccelProcessor.add] call finished: the closed seconds in order, and the events. */
data class AccelOutput(val seconds: List<AccelSecond>, val events: List<AccelEvent>)

/**
 * Watch-side 25 Hz accelerometer processing (§8.1): gravity low-pass with the jerk-burst freeze, per-second
 * stillness, gravity-mode detection, and the hypnic-jerk and limp-wrist detectors. Time comes only from sample
 * timestamps, so live data, tests and the AccelConfig replay (§12.6) give the same results. Not thread-safe.
 *
 * A second is closed by the first sample of a later second. Seconds without samples are emitted too (null fields),
 * so the output is contiguous and a closed second means the tracker delivered a sample past its end.
 */
class AccelProcessor(private val config: AccelConfig) {
    /** Decided once, after [GRAVITY_MODE_STILL_SECONDS] still seconds; goes to `w_config.csv`. */
    var gravityMode: GravityMode = GravityMode.UNKNOWN
        private set

    /** Samples dropped because they were older than the open second. */
    var lateSamples: Long = 0
        private set

    private val alpha = config.gravityAlpha.toDouble()
    private val jerkThresholdG = config.jerkThresholdG.toDouble()

    private var gravity: Vec3? = null // g_lp; starts at the first sample
    private var frozen = false
    private var burst: Burst? = null
    private var pendingJerk: PendingJerk? = null

    private var openSecond: Long? = null
    private var count = 0
    private var sumMagnitude = 0.0
    private var sumD2 = 0.0
    private var peakD = 0.0
    private var sumA = Vec3.ZERO

    private var stillRun = 0 // consecutive still seconds up to the last closed one
    private val stillMeans = ArrayList<Double>() // accelMean of still seconds, until the gravity mode is decided
    private val recentStill = ArrayDeque<Vec3>() // u_s of the latest consecutive still seconds
    private var limpRef: Vec3? = null
    private var limpRefSetMs = 0L
    private var limpRefIntact = false // every second since the reference was set had stillness >= STILL_LEVEL

    fun add(batch: List<AccelSample>): AccelOutput {
        val seconds = ArrayList<AccelSecond>()
        val events = ArrayList<AccelEvent>()
        for (sample in batch.sortedBy { it.timestampMs }) {
            val second = binStartMs(sample.timestampMs)
            val open = openSecond
            if (open != null && second < open) {
                lateSamples++
                continue
            }
            if (open != null) {
                var s = open
                while (s < second) {
                    seconds += closeSecond(s, events)
                    s += 1000
                }
            }
            openSecond = second
            addSample(sample, events)
        }
        return AccelOutput(seconds, events)
    }

    private fun addSample(sample: AccelSample, events: MutableList<AccelEvent>) {
        val t = sample.timestampMs
        val a = Vec3(sample.xG, sample.yG, sample.zG)
        val g = gravity ?: a
        val d = (a - g).norm() // against g_lp from before this sample
        val b = burst
        var closedBurst: Burst? = null
        when {
            b == null -> if (d > jerkThresholdG && stillRun >= config.jerkPreStillS) {
                burst = Burst(t, d)
                frozen = true
                events += AccelEvent(t, AccelEventType.JERK_BURST_OPEN, d.toFloat())
            }
            d < jerkThresholdG -> if (++b.belowRun == JERK_CLOSE_SAMPLES) closedBurst = b
            else -> {
                b.belowRun = 0
                b.lastAboveMs = t
                b.peakG = max(b.peakG, d)
                if (b.lengthMs > config.jerkMaxBurstMs) frozen = false // voluntary movement: follow the new posture
            }
        }
        if (!frozen) gravity = g + (a - g) * alpha
        if (closedBurst != null) {
            frozen = false
            burst = null
            endBurst(closedBurst, t)?.let { events += it }
        }
        count++
        sumMagnitude += a.norm()
        sumD2 += d * d
        peakD = max(peakD, d)
        sumA += a
    }

    /** Returns the rejection event, or null when the burst now waits for its post-still seconds. */
    private fun endBurst(b: Burst, closeMs: Long): AccelEvent? {
        val lastSecond = binStartMs(b.lastAboveMs)
        val type = when {
            b.lengthMs > config.jerkMaxBurstMs -> AccelEventType.JERK_REJECTED_LONG
            // The first post-burst second closed while the burst was open, so it had too few samples to be still.
            binStartMs(closeMs) > lastSecond + 1000 -> AccelEventType.JERK_REJECTED_POST_MOVE
            else -> {
                pendingJerk = PendingJerk(lastSecond, b.lengthMs.toFloat(), b.peakG.toFloat())
                return null
            }
        }
        return AccelEvent(closeMs, type, b.lengthMs.toFloat(), b.peakG.toFloat())
    }

    private fun closeSecond(start: Long, events: MutableList<AccelEvent>): AccelSecond {
        val n = count
        val valid = n >= MIN_SAMPLES_PER_SECOND
        val mean = sumMagnitude / n
        val rms = sqrt(sumD2 / n)
        val peak = peakD
        val orientation = if (valid) (sumA / n.toDouble()).normalized() else null
        count = 0
        sumMagnitude = 0.0
        sumD2 = 0.0
        peakD = 0.0
        sumA = Vec3.ZERO

        val stillness = if (valid) stillnessScore(rms, config) else null
        val still = stillness != null && stillness >= STILL_LEVEL
        stillRun = if (still) stillRun + 1 else 0
        if (still && gravityMode == GravityMode.UNKNOWN) decideGravityMode(start, mean, events)
        val jerk = checkPendingJerk(start, still, events)
        val (tilt, limp) = updateLimp(start, stillness, orientation, events)
        return if (valid) {
            AccelSecond(
                start, n, mean.toFloat(), rms.toFloat(), peak.toFloat(), stillness?.toFloat(), tilt?.toFloat(),
                jerk, limp,
            )
        } else {
            AccelSecond(start, n, null, null, null, null, null, jerk, limp)
        }
    }

    private fun decideGravityMode(start: Long, accelMean: Double, events: MutableList<AccelEvent>) {
        stillMeans += accelMean
        if (stillMeans.size < GRAVITY_MODE_STILL_SECONDS) return
        val m = median(stillMeans)
        stillMeans.clear()
        gravityMode = when {
            m in GRAVITY_MIN_G..GRAVITY_MAX_G -> GravityMode.WITH_GRAVITY
            m < LINEAR_ONLY_MAX_G -> GravityMode.LINEAR_ONLY
            else -> GravityMode.AMBIGUOUS
        }
        events += AccelEvent(start, AccelEventType.GRAVITY_MODE, m.toFloat(), detail = gravityMode.name)
        if (gravityMode != GravityMode.WITH_GRAVITY) {
            events += AccelEvent(start, AccelEventType.LIMP_UNAVAILABLE, detail = gravityMode.name)
        }
    }

    /** Post-still check of a closed burst; true when this second carries the confirmed jerk. */
    private fun checkPendingJerk(start: Long, still: Boolean, events: MutableList<AccelEvent>): Boolean {
        val p = pendingJerk ?: return false
        if (start <= p.lastSecond) return false // the burst's own last second
        val type = when {
            !still -> AccelEventType.JERK_REJECTED_POST_MOVE
            start - p.lastSecond < config.jerkPostStillS * 1000L -> return false
            else -> AccelEventType.JERK_CONFIRMED
        }
        pendingJerk = null
        events += AccelEvent(start, type, p.lengthMs, p.peakG)
        return type == AccelEventType.JERK_CONFIRMED
    }

    /** Limp-wrist detector; returns (θ_s, limpEvent). */
    private fun updateLimp(
        start: Long,
        stillness: Double?,
        orientation: Vec3?,
        events: MutableList<AccelEvent>,
    ): Pair<Double?, Boolean> {
        val still = stillness != null && stillness >= STILL_LEVEL && orientation != null
        if (still) {
            recentStill.addLast(orientation)
            if (recentStill.size > config.limpRefStillS) recentStill.removeFirst()
        } else {
            recentStill.clear()
        }
        if (gravityMode != GravityMode.WITH_GRAVITY) return null to false

        var tilt: Double? = null
        var limp = false
        val ref = limpRef
        if (ref != null) {
            if (stillness != null && stillness < LIMP_CLEAR_LEVEL) {
                clearLimpRef(start, "MOVEMENT", events)
            } else if (start - limpRefSetMs > config.limpRefMaxAgeS * 1000L) {
                clearLimpRef(start, "AGE", events) // re-acquired below from the latest still seconds
            } else {
                if (!still) limpRefIntact = false
                tilt = orientation?.let { angleDeg(it, ref) }
                if (tilt != null && tilt >= config.limpDriftDeg && limpRefIntact) {
                    limp = true
                    events += AccelEvent(start, AccelEventType.LIMP_CONFIRMED, tilt.toFloat())
                    clearLimpRef(start, "LIMP", events)
                    recentStill.clear() // the next reference comes from still seconds after the drift
                }
            }
        }
        if (limpRef == null && recentStill.size == config.limpRefStillS) {
            limpRef = (recentStill.reduce(Vec3::plus) / recentStill.size.toDouble()).normalized()
            limpRefSetMs = start
            limpRefIntact = true
            events += AccelEvent(start, AccelEventType.LIMP_REF_SET)
        }
        return tilt to limp
    }

    private fun clearLimpRef(start: Long, reason: String, events: MutableList<AccelEvent>) {
        limpRef = null
        events += AccelEvent(start, AccelEventType.LIMP_REF_CLEARED, detail = reason)
    }

    companion object {
        // Structural constants (§8.15), logged as const.* in w_config.csv.
        const val MIN_SAMPLES_PER_SECOND = 20
        const val STILL_LEVEL = 0.8
        const val LIMP_CLEAR_LEVEL = 0.5
        const val GRAVITY_MODE_STILL_SECONDS = 30
        const val GRAVITY_MIN_G = 0.8
        const val GRAVITY_MAX_G = 1.2
        const val LINEAR_ONLY_MAX_G = 0.3
        const val JERK_CLOSE_SAMPLES = 3
        const val SAMPLE_PERIOD_MS = 40L
    }
}

/** clamp(1 − (accelRms − f) / (M − f), 0, 1) (§8.1). */
internal fun stillnessScore(accelRmsG: Double, config: AccelConfig): Double {
    val f = config.noiseFloorG.toDouble()
    return (1 - (accelRmsG - f) / (config.moveRmsG - f)).coerceIn(0.0, 1.0)
}

private class Burst(val openMs: Long, var peakG: Double) {
    var lastAboveMs = openMs
    var belowRun = 0

    /** L = t(last sample above J_G) − t(open) + one sample period. */
    val lengthMs get() = lastAboveMs - openMs + AccelProcessor.SAMPLE_PERIOD_MS
}

private class PendingJerk(val lastSecond: Long, val lengthMs: Float, val peakG: Float)

private data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    operator fun div(k: Double) = Vec3(x / k, y / k, z / k)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun norm() = sqrt(dot(this))
    fun normalized(): Vec3? = norm().let { if (it > 0) this / it else null }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

private fun angleDeg(u: Vec3, v: Vec3) = Math.toDegrees(acos(u.dot(v).coerceIn(-1.0, 1.0)))
