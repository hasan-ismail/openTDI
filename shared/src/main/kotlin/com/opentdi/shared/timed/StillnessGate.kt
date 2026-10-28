package com.opentdi.shared.timed

import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_MIN_S
import com.opentdi.shared.config.StructuralConstants.MAJOR_MOVEMENT_WINDOW_S
import com.opentdi.shared.config.thresholds
import com.opentdi.shared.model.SessionConfig

/**
 * Wait for stillness (owner, 2026-10-28: "no cues while I toss and turn"). The phone feeds it the watch's 1 Hz
 * packets; a loop may start once the sleeper has been still for [com.opentdi.shared.model.TimedCueConfig.stillForS]
 * and the heart rate has dropped below the session's own baseline, or has been still for twice that long (a baseline
 * taken while already calm may never show the drop). Movement is the §8.10 major movement (3 of the last 5 seconds
 * not still), so a single twitch, like a hypnic jerk at sleep onset, does not count. Times are the phone's elapsed ms
 * when a packet arrived; batches arrive every 2 s, which is fine at this scale. Pure and not thread-safe.
 */
class StillnessGate(private val config: SessionConfig) {
    private val thresholds = config.detection.thresholds()
    private var firstAtMs: Long? = null
    private var lastAtMs: Long? = null
    private var stillSinceMs: Long? = null
    private val recentMoving = ArrayDeque<Boolean>()
    private var baselineSum = 0.0
    private var baselineCount = 0
    private val recentHr = ArrayDeque<Pair<Long, Float>>()

    /** One watch second: [stillness] 0..1 (null without enough accel samples counts as movement), [hr] in bpm. */
    fun add(atMs: Long, stillness: Float?, hr: Float?) {
        val first = firstAtMs ?: atMs.also { firstAtMs = it }
        lastAtMs = atMs
        recentMoving.addLast(stillness == null || stillness < thresholds.stillThreshold)
        if (recentMoving.size > MAJOR_MOVEMENT_WINDOW_S) recentMoving.removeFirst()
        if (recentMoving.count { it } >= MAJOR_MOVEMENT_MIN_S) {
            stillSinceMs = null
        } else if (stillSinceMs == null) {
            stillSinceMs = atMs
        }
        if (hr == null) return
        if (atMs - first < config.detection.baselineWindowS * 1000L) {
            baselineSum += hr
            baselineCount++
        }
        recentHr.addLast(atMs to hr)
        while (recentHr.first().first < atMs - config.detection.hrMaWindowS * 1000L) recentHr.removeFirst()
    }

    /** False before the watch's first packet and once it has been silent for watchLinkTimeoutS. */
    fun fresh(nowMs: Long): Boolean = lastAtMs?.let { nowMs - it <= config.timed.watchLinkTimeoutS * 1000L } == true

    fun stillForMs(nowMs: Long): Long = if (fresh(nowMs)) stillSinceMs?.let { nowMs - it } ?: 0 else 0

    /** True while the watch is streaming and shows a major movement: a countdown is cut (§8.10). */
    fun moving(nowMs: Long): Boolean = fresh(nowMs) && stillSinceMs == null

    /** The moving average against the baseline (§8.8); null until both exist. */
    fun hrDropped(): Boolean? {
        if (baselineCount == 0 || recentHr.isEmpty()) return null
        val baseline = baselineSum / baselineCount
        return recentHr.sumOf { it.second.toDouble() } / recentHr.size <= baseline * (1 - thresholds.dropFraction)
    }

    fun settled(nowMs: Long): Boolean {
        val still = stillForMs(nowMs)
        val needed = config.timed.stillForS * 1000L
        return still >= needed && (hrDropped() != false || still >= 2 * needed)
    }
}
