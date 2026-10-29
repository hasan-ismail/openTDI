package com.opentdi.wear.alarm

import org.junit.Assert.assertEquals
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
        // Nothing saved yet, or a stop before the saved start (a clock change): this recording's own start.
        assertEquals(5 * hour, WakeAlarms.nightStart(5 * hour, null, null))
        assertEquals(5 * hour, WakeAlarms.nightStart(5 * hour, 4 * hour, 3 * hour))
    }
}
