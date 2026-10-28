package com.opentdi.shared.sensing

import com.opentdi.shared.model.AccelConfig
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.sensing.AccelEventType.GRAVITY_MODE
import com.opentdi.shared.sensing.AccelEventType.JERK_BURST_OPEN
import com.opentdi.shared.sensing.AccelEventType.JERK_CONFIRMED
import com.opentdi.shared.sensing.AccelEventType.JERK_REJECTED_LONG
import com.opentdi.shared.sensing.AccelEventType.JERK_REJECTED_POST_MOVE
import com.opentdi.shared.sensing.AccelEventType.LIMP_CONFIRMED
import com.opentdi.shared.sensing.AccelEventType.LIMP_REF_CLEARED
import com.opentdi.shared.sensing.AccelEventType.LIMP_REF_SET
import com.opentdi.shared.sensing.AccelEventType.LIMP_UNAVAILABLE
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val START = 1_761_000_000_000L // a second boundary

class AccelProcessorTest {
    private val config = AccelConfig()

    /** Raw `a` at 25 Hz (40 ms apart) from [t]; still means (0, 0, 1 g). */
    private class Stream(var t: Long = START) {
        val samples = ArrayList<AccelSample>()

        fun add(count: Int, x: Double, y: Double, z: Double) = repeat(count) {
            samples += AccelSample(t, x, y, z)
            t += 40
        }

        fun still(seconds: Int) = add(seconds * 25, 0.0, 0.0, 1.0)

        /** Alternating ±0.1 g on x: stillness 0, but d stays below J_G. */
        fun moving(seconds: Int) = repeat(seconds * 25) { add(1, if (it % 2 == 0) 0.1 else -0.1, 0.0, 1.0) }

        /** Still, with gravity rotated by [deg] about y. */
        fun tilted(deg: Double) = Math.toRadians(deg).let { add(1, sin(it), 0.0, cos(it)) }
    }

    private fun run(stream: Stream) = AccelProcessor(config).add(stream.samples)

    private fun isMoving(second: AccelSecond) = second.stillnessScore.let { it != null && it < config.moveStillness }

    /** §8.10 majorMovement (≥ 3 moving seconds in the last 5 s), evaluated at every second. */
    private fun majorMovementEver(out: AccelOutput) =
        out.seconds.windowed(5, partialWindows = true).any { w -> w.count(::isMoving) >= 3 }

    private fun jerks(events: List<AccelEvent>) = events.filter { it.event.name.startsWith("JERK_") }

    private fun assertJerkConfirmedIn(out: AccelOutput, secondMs: Long, burstMs: Float) {
        assertEquals(listOf(secondMs), out.seconds.filter { it.jerkEvent }.map { it.startMs })
        assertEquals(listOf(JERK_BURST_OPEN, JERK_CONFIRMED), jerks(out.events).map { it.event })
        assertEquals(AccelEvent(secondMs, JERK_CONFIRMED, burstMs, 0.5f), jerks(out.events)[1])
        assertTrue(out.seconds.count(::isMoving) <= 2)
        assertFalse(majorMovementEver(out))
    }

    @Test
    fun countsToG_4096CountsIsOneG() {
        assertEquals(1.0, samsungCountsToG(4095.9375), 0.0)
        val a = AccelSample.fromSamsungCounts(START, 4096, 0, -4096)
        assertEquals(1.0, a.xG, 1e-4)
        assertEquals(-1.0, a.zG, 1e-4)
        assertEquals(1.0, AccelSample.fromAndroidMs2(START, 9.80665f, 0f, 0f).xG, 1e-6)
    }

    @Test
    fun stillness_mapping() {
        assertEquals(1.0, stillnessScore(0.003, config), 1e-6)
        assertEquals(0.5, stillnessScore(0.0265, config), 1e-6)
        assertEquals(0.0, stillnessScore(0.05, config), 1e-6)
    }

    @Test
    fun fewerThan20Samples_nullFields() {
        val s = Stream()
        s.still(1)
        s.add(19, 0.0, 0.0, 1.0)
        s.t = START + 2000
        s.add(20, 0.0, 0.0, 1.0)
        s.t = START + 3000
        s.add(1, 0.0, 0.0, 1.0)
        val seconds = run(s).seconds
        assertEquals(listOf(25, 19, 20), seconds.map { it.sampleCount })
        with(seconds[1]) {
            assertNull(accelMean)
            assertNull(accelRms)
            assertNull(accelPeak)
            assertNull(stillnessScore)
        }
        assertEquals(1f, seconds[2].stillnessScore)
    }

    @Test
    fun lateSamples_sortedPerBatchThenDropped() {
        val p = AccelProcessor(config)
        p.add(Stream().apply { still(2) }.samples) // second 1 is open
        val out = p.add(listOf(3000L, 500L, 2000L).map { AccelSample(START + it, 0.0, 0.0, 1.0) })
        assertEquals(1L, p.lateSamples) // only the sample of the already closed second 0
        assertEquals(listOf(25, 1), out.seconds.map { it.sampleCount }) // unsorted, 2000 would have been dropped too
    }

    @Test
    fun gravityMode_detected() {
        val s = Stream().apply {
            still(30)
            add(1, 0.0, 0.0, 1.0)
        }
        val withGravity = AccelProcessor(config)
        withGravity.add(s.samples.subList(0, 9 * 25 + 1)) // closes 9 calm seconds
        assertEquals(GravityMode.UNKNOWN, withGravity.gravityMode)
        assertEquals(9, withGravity.gravityProgress)
        val events = withGravity.add(s.samples.subList(9 * 25 + 1, 10 * 25 + 1)).events // closes the 10th
        assertEquals(GravityMode.WITH_GRAVITY, withGravity.gravityMode)
        assertEquals(AccelEvent(START + 9_000, GRAVITY_MODE, 1f, detail = "WITH_GRAVITY"), events[0])
        assertEquals(10, withGravity.gravityProgress)
        // the limp detector starts exactly once, as soon as it has its still reference
        val later = withGravity.add(s.samples.subList(10 * 25 + 1, s.samples.size)).events
        assertEquals(1, (events + later).count { it.event == LIMP_REF_SET })

        val linear = AccelProcessor(config)
        val linearEvents = linear.add(Stream().apply { add(10 * 25 + 1, 0.02, 0.0, 0.0) }.samples).events
        assertEquals(GravityMode.LINEAR_ONLY, linear.gravityMode)
        assertEquals(listOf(GRAVITY_MODE, LIMP_UNAVAILABLE), linearEvents.map { it.event })
    }

    @Test
    fun angleZ_ofTheMeanVector_onlyWithGravity() {
        val s = Stream().apply {
            still(10) // second 9 decides WITH_GRAVITY
            repeat(25) { tilted(30.0) } // second 10: gravity 30° off z, an arm angle of 60°
            add(19, 0.0, 0.0, 1.0) // second 11: too few samples
            t = START + 12_000
            add(1, 0.0, 0.0, 1.0) // closes second 11
        }
        val angles = run(s).seconds.map { it.angleZDeg }
        assertEquals(List(9) { null }, angles.take(9)) // gravity not yet decided
        assertEquals(90f, angles[9]!!, 1e-4f)
        assertEquals(60f, angles[10]!!, 1e-4f)
        assertNull(angles[11])

        val linear = AccelProcessor(config).add(Stream().apply { add(10 * 25 + 1, 0.02, 0.0, 0.0) }.samples)
        assertTrue(linear.seconds.all { it.angleZDeg == null }) // the mean vector is no gravity: no angle
    }

    @Test
    fun jerk_300msAfter10sStill_confirmedInThirdSecond() {
        val s = Stream().apply {
            still(10)
            add(8, 0.5, 0.0, 1.0) // 320 ms burst at the start of second 10
            add(17, 0.0, 0.0, 1.0)
            still(3)
            add(1, 0.0, 0.0, 1.0) // closes the 3rd post-burst second
        }
        val out = run(s)
        assertEquals(listOf(0f, 1f, 1f, 1f), out.seconds.drop(10).map { it.stillnessScore })
        assertJerkConfirmedIn(out, START + 13_000, burstMs = 320f)
    }

    @Test
    fun jerk_unidirectionalStraddling_confirmed() {
        val s = Stream().apply {
            still(10)
            add(21, 0.0, 0.0, 1.0)
            add(8, 0.5, 0.0, 1.0) // 4 samples in second 10, 4 in second 11
            add(21, 0.0, 0.0, 1.0)
            still(3)
            add(1, 0.0, 0.0, 1.0)
        }
        val out = run(s)
        assertEquals(listOf(0f, 0f, 1f, 1f), out.seconds.drop(10).take(4).map { it.stillnessScore })
        assertJerkConfirmedIn(out, START + 14_000, burstMs = 320f)
    }

    @Test
    fun jerk_zeroMean_confirmed() {
        val s = Stream().apply {
            still(10)
            repeat(4) {
                add(1, 0.5, 0.0, 1.0)
                add(1, -0.5, 0.0, 1.0)
            }
            add(17, 0.0, 0.0, 1.0)
            still(3)
            add(1, 0.0, 0.0, 1.0)
        }
        val out = run(s)
        assertEquals(listOf(0f, 1f, 1f, 1f), out.seconds.drop(10).map { it.stillnessScore })
        assertJerkConfirmedIn(out, START + 13_000, burstMs = 320f)
    }

    @Test
    fun gravityFreeze_releasedAfter500ms() {
        val s = Stream().apply {
            still(10)
            add(30, 0.5, 0.0, 1.0) // sustained offset: a new posture
        }
        val events = run(s).events
        // Frozen for samples 0..11; sample 12 makes the burst 520 ms long, so from there g_lp follows the offset,
        // d falls below J_G at sample 25 and the burst closes at sample 27 with L = 1000 ms. Kept frozen, d would
        // stay at 0.5 g and the burst would still be open.
        assertEquals(listOf(JERK_BURST_OPEN, JERK_REJECTED_LONG), jerks(events).map { it.event })
        assertEquals(AccelEvent(START + 10_000 + 27 * 40, JERK_REJECTED_LONG, 1000f, 0.5f), jerks(events)[1])
    }

    @Test
    fun jerk_700msBurst_rejected() {
        val s = Stream().apply {
            still(10)
            add(18, 0.5, 0.0, 1.0)
            still(5)
        }
        val out = run(s)
        assertEquals(listOf(JERK_BURST_OPEN, JERK_REJECTED_LONG), jerks(out.events).map { it.event })
        assertEquals(720f, jerks(out.events)[1].value1)
        assertTrue(out.seconds.none { it.jerkEvent })
    }

    @Test
    fun jerk_followedByMovement_rejected() {
        val s = Stream().apply {
            still(10)
            add(8, 0.5, 0.0, 1.0)
            add(17, 0.0, 0.0, 1.0)
            still(1)
            moving(1) // second 12, the 2nd post-burst second
            still(3)
        }
        val out = run(s)
        assertEquals(listOf(JERK_BURST_OPEN, JERK_REJECTED_POST_MOVE), jerks(out.events).map { it.event })
        assertEquals(AccelEvent(START + 12_000, JERK_REJECTED_POST_MOVE, 320f, 0.5f), jerks(out.events)[1])
        assertTrue(out.seconds.none { it.jerkEvent })
    }

    @Test
    fun jerk_withoutPreStill_ignored() {
        val s = Stream().apply {
            still(10)
            moving(1)
            still(5)
            add(8, 0.5, 0.0, 1.0) // 5 s after the movement
            add(17, 0.0, 0.0, 1.0)
            still(4)
        }
        val out = run(s)
        assertEquals(emptyList<AccelEvent>(), jerks(out.events))
        assertTrue(out.seconds.none { it.jerkEvent })
    }

    @Test
    fun limp_driftAfterStillRef_emits() {
        val s = Stream().apply {
            still(30) // second 9 decides WITH_GRAVITY and sets the reference from seconds 0..9
            for (k in 0 until 750) tilted(3.5 * k / 750) // 3.5° over 30 s, still throughout
            repeat(20 * 25 + 1) { tilted(3.5) }
        }
        val out = run(s)
        // Second 56 is the first whose mean tilt (3.5° × 662 / 750 = 3.089°) reaches 3.0°; the reference is 47 s old.
        assertEquals(listOf(START + 56_000), out.seconds.filter { it.limpEvent }.map { it.startMs })
        val limp = out.events.filter { it.event.name.startsWith("LIMP_") }
        assertEquals(
            listOf(LIMP_REF_SET to 9_000L, LIMP_CONFIRMED to 56_000L, LIMP_REF_CLEARED to 56_000L),
            limp.take(3).map { it.event to it.sampleTsMs - START },
        )
        assertEquals(3.0893f, limp[1].value1!!, 1e-3f)
        assertEquals(listOf(LIMP_REF_SET), limp.drop(3).map { it.event }) // fresh reference, no second event
    }

    @Test
    fun limp_refOlderThan60s_reacquiredNoEvent() {
        val s = Stream().apply {
            still(10) // reference set when second 9 closes
            for (k in 0..62 * 25) tilted(0.002 * k) // still; second 10 + j has a mean tilt of 0.002° × (25j + 12)
        }
        val out = run(s)
        assertEquals(2.974f, out.seconds[69].tiltDeg!!, 1e-3f) // second 70 would be the first at >= 3°, 61 s old
        assertTrue(out.seconds.none { it.limpEvent })
        val limp = out.events.filter { it.event.name.startsWith("LIMP_") }
        assertEquals(
            listOf(
                AccelEvent(START + 9_000, LIMP_REF_SET),
                AccelEvent(START + 70_000, LIMP_REF_CLEARED, detail = "AGE"),
                AccelEvent(START + 70_000, LIMP_REF_SET),
            ),
            limp,
        )
    }

    @Test
    fun limp_secondBelowStillSinceRef_noEvent() {
        val s = Stream().apply {
            still(30) // reference set when second 9 closes
            repeat(25) { add(1, if (it % 2 == 0) 0.02 else -0.02, 0.0, 1.0) } // second 30: not still, no reset
            for (k in 0 until 500) tilted(4.0 * k / 500) // a slow, still drift past 3° within 20 s
            repeat(5 * 25 + 1) { tilted(4.0) }
        }
        val out = run(s)
        assertTrue(out.seconds[30].stillnessScore!! in 0.5f..0.79f)
        assertTrue(out.seconds.any { (it.tiltDeg ?: 0f) >= 3f })
        assertTrue(out.seconds.none { it.limpEvent })
        assertEquals(
            listOf(AccelEvent(START + 9_000, LIMP_REF_SET)),
            out.events.filter { it.event.name.startsWith("LIMP_") },
        )
    }

    @Test
    fun limp_linearOnly_never() {
        val p = AccelProcessor(config)
        val out = p.add(
            Stream().apply {
                add(40 * 25, 0.02, 0.0, 0.0)
                add(40 * 25 + 1, 0.0, 0.02, 0.0) // a 90° turn of the vector: a limp if gravity were in the data
            }.samples,
        )
        assertEquals(GravityMode.LINEAR_ONLY, p.gravityMode)
        assertEquals(listOf(GRAVITY_MODE, LIMP_UNAVAILABLE), out.events.map { it.event })
        assertTrue(out.seconds.none { it.limpEvent || it.tiltDeg != null })
    }

    @Test
    fun limp_movementResetsRef() {
        val s = Stream().apply {
            still(30) // reference set when second 9 closes
            moving(1) // second 30
            repeat(20 * 25 + 1) { tilted(4.0) } // still again, 4° away from the old reference
        }
        val out = run(s)
        val limp = out.events.filter { it.event.name.startsWith("LIMP_") }
        assertEquals(AccelEvent(START + 9_000, LIMP_REF_SET), limp[0])
        assertEquals(AccelEvent(START + 30_000, LIMP_REF_CLEARED, detail = "MOVEMENT"), limp[1])
        assertEquals(listOf(LIMP_REF_SET), limp.drop(2).map { it.event }) // a new reference at the new posture
        assertTrue(out.seconds.none { it.limpEvent })
    }
}
