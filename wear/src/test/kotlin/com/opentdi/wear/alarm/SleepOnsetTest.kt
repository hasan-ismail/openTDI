package com.opentdi.wear.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepOnsetTest {
    private val still = 0.95f
    private val moving = 0.3f

    /** One tick a second at t * 1000; the first onset found and the tick it came on, or null. */
    private fun SleepOnset.firstOnset(
        ticks: Int,
        worn: (Int) -> Boolean = { true },
        stillness: (Int) -> Float?,
    ): Pair<Long, Int>? {
        for (t in 0 until ticks) add(t * 1000L, listOf(stillness(t)), worn(t))?.let { return it to t }
        return null
    }

    @Test
    fun tenStillMinutesOnTheWrist_isTheOnset_datedToTheStretchStart() {
        // The 3-of-5 window clears 2 s after the movement.
        assertEquals(302_000L to 902, SleepOnset(0).firstOnset(3600) { if (it < 300) moving else still })
    }

    @Test
    fun majorMovementRestartsTheStretch_aTwitchDoesNot() {
        val rolled = SleepOnset(0).firstOnset(3600) { if (it == 200 || it in 480..482) moving else still }
        assertEquals(485_000L to 1085, rolled)
        assertEquals(0L to 600, SleepOnset(0).firstOnset(3600) { if (it in 300..301) moving else still })
    }

    @Test
    fun offTheWristNeverFires() {
        assertNull(SleepOnset(0).firstOnset(3600, worn = { false }) { 1f })
    }

    @Test
    fun noStillnessCountsAsMoving() {
        assertNull(SleepOnset(0).firstOnset(3600) { null })
    }

    @Test
    fun wornAgain_countsFromThen() {
        assertEquals(600_000L to 1200, SleepOnset(0).firstOnset(3600, worn = { it >= 600 }) { 1f })
    }

    @Test
    fun armedLater_countsFromTheArm_andATestNightTakes10s() {
        assertEquals(120_000L to 720, SleepOnset(120_000).firstOnset(3600) { still })
        assertEquals(7_000L to 17, SleepOnset(0, 10_000).firstOnset(60) { if (it < 5) moving else still })
    }

    @Test
    fun aScreenOffBurst_countsEverySecond() {
        // Ten seconds at once, the newest three still: the moving ones among them break the stretch all the same.
        val onset = SleepOnset(0)
        assertNull(onset.add(0, List(5) { still }, true))
        assertNull(onset.add(10_000, List(7) { moving } + List(3) { still }, true))
        assertNull(onset.add(609_000, emptyList(), true)) // 10 min after the first stillness, but it was broken
        assertEquals(10_000L, onset.add(610_000, emptyList(), true))
    }
}
