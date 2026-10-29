package com.opentdi.wear.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAlarmsTest {
    private val hour = 3_600_000L

    @Test
    fun nightStart_keepsTheNightsStartForARestartSoonAfterAStop() {
        val start = 23 * hour
        // Ended at 3:06, started again at 3:10: the night still began at 23:00, so a 4:30 WBTB is not "too early".
        assertEquals(start, WakeAlarms.nightStart(27 * hour + 10 * 60_000, start, 27 * hour + 6 * 60_000))
        // The next evening is a new night.
        assertEquals(46 * hour, WakeAlarms.nightStart(46 * hour, start, 31 * hour))
        // Last night's recording, left running all day and restarted by the phone's Start: a new night all the same.
        assertEquals(46 * hour, WakeAlarms.nightStart(46 * hour, 22 * hour, 46 * hour - 1000))
        // Nothing saved yet, or a stop before the saved start (a clock change): this recording's own start.
        assertEquals(5 * hour, WakeAlarms.nightStart(5 * hour, null, null))
        assertEquals(5 * hour, WakeAlarms.nightStart(5 * hour, 4 * hour, 3 * hour))
    }

    @Test
    fun wbtbTooSoon_skipsAnEarlyWakeUpLessThan4_5HoursIntoTheNight() {
        val wbtb = 28 * hour + 30 * 60_000 // 4:30 AM
        assertTrue(WakeAlarms.wbtbTooSoon(wbtb, 25 * hour)) // to bed at 1 AM: 3.5 h, skipped
        assertFalse(WakeAlarms.wbtbTooSoon(wbtb, 24 * hour)) // to bed at midnight: 4.5 h, rings
    }
}
