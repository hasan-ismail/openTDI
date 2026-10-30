package com.opentdi.wear.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
        // The app died at 1:00, its last alive minute, and was started again at 1:30: still the 23:00 night.
        assertEquals(start, WakeAlarms.nightStart(25 * hour + 30 * 60_000, start, 25 * hour))
    }

    @Test
    fun nextWbtbAt_ringsOnceANight() {
        val utc = ZoneOffset.UTC
        val wbtb = 4 * 60 + 30 // 4:30 AM
        // Nothing rang yet: tonight's 4:30.
        assertEquals(28 * hour + 30 * 60_000, WakeAlarms.nextWbtbAt(wbtb, 23 * hour, null, utc))
        // Rang at 4:30, then moved to 5:00 at 4:35: the next night's 5:00, not again this morning.
        val rang = 28 * hour + 30 * 60_000
        assertEquals(53 * hour, WakeAlarms.nextWbtbAt(5 * 60, rang + 5 * 60_000, rang, utc))
        // The wake-up rang at 4:15, before it: the next night's 4:30.
        val woke = 28 * hour + 15 * 60_000
        assertEquals(52 * hour + 30 * 60_000, WakeAlarms.nextWbtbAt(wbtb, woke, woke, utc))
    }

    @Test
    fun sameClockTime_keepsTheAlarmsClockTimeInTheNewZone() {
        val ny = ZoneId.of("America/New_York")
        val chicago = ZoneId.of("America/Chicago")
        val seven = ZonedDateTime.of(2026, 1, 15, 7, 0, 0, 0, ny)
        val moved = WakeAlarms.sameClockTime(seven.toInstant().toEpochMilli(), ny, chicago)
        assertEquals(seven.withZoneSameLocal(chicago).toInstant().toEpochMilli(), moved)
        assertEquals(hour, moved - seven.toInstant().toEpochMilli()) // 7 AM in Chicago is an hour later
    }

    @Test
    fun wbtbTooSoon_skipsAnEarlyWakeUpLessThan4_5HoursIntoTheNight() {
        val wbtb = 28 * hour + 30 * 60_000 // 4:30 AM
        assertTrue(WakeAlarms.wbtbTooSoon(wbtb, 25 * hour)) // to bed at 1 AM: 3.5 h, skipped
        assertFalse(WakeAlarms.wbtbTooSoon(wbtb, 24 * hour)) // to bed at midnight: 4.5 h, rings
    }
}
