package com.opentdi.wear.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LateCuesTest {
    private val min = 60_000L
    private val buzz = 100 * min

    @Test
    fun reOnset_isTheStartOfTheFirstTenMinuteStillStretchFromTheBuzz() {
        assertNull(reOnsetAt(buzz, stillSinceMs = null, nowMs = buzz + 30 * min)) // still moving
        assertNull(reOnsetAt(buzz, buzz + 5 * min, buzz + 14 * min)) // still for 9 min only
        assertEquals(buzz + 5 * min, reOnsetAt(buzz, buzz + 5 * min, buzz + 15 * min)) // latency 5 min
    }

    @Test
    fun reOnset_stillnessBeforeTheBuzzCountsFromTheBuzz() {
        assertNull(reOnsetAt(buzz, buzz - 20 * min, buzz + 9 * min))
        assertEquals(buzz, reOnsetAt(buzz, buzz - 20 * min, buzz + RE_ONSET_MS)) // latency 0
    }
}
