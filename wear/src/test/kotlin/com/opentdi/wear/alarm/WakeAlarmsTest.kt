package com.opentdi.wear.alarm

import com.opentdi.shared.config.MIN_SLEEP_BEFORE_WBTB_MIN
import com.opentdi.shared.model.AlarmConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
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
    fun wbtbAhead_onlyTonightsStillToRing() {
        val utc = ZoneOffset.UTC
        val wbtb = 4 * 60 + 30 // 4:30 AM
        val start = 23 * hour
        val tonight = 28 * hour + 30 * 60_000
        assertEquals(tonight, WakeAlarms.wbtbAhead(WakeAlarms.nextWbtbAt(wbtb, start, null, utc), start, start))
        // Up at 2:00, skipped or started now: the next 4:30 is the next night's, so none is ahead tonight.
        val up = 26 * hour
        assertNull(WakeAlarms.wbtbAhead(WakeAlarms.nextWbtbAt(wbtb, up, up, utc), up, start))
        // Moved to 5 AM at 3:00, or a restart at 3:00 with the saved done time: still none tonight.
        assertNull(WakeAlarms.wbtbAhead(WakeAlarms.nextWbtbAt(5 * 60, 27 * hour, up, utc), 27 * hour, start))
        // Too soon after the night began to ring, or more than 12 h away: none either.
        assertNull(WakeAlarms.wbtbAhead(tonight, 25 * hour, 25 * hour))
        assertNull(WakeAlarms.wbtbAhead(tonight, 14 * hour, 14 * hour))
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

    private val min = 60_000L
    private val setAt = 28 * hour + 30 * min // the set time, 4:30 AM
    private val cfg = AlarmConfig(wakeAtMin = 420, wbtbEnabled = true, wbtbAtMin = 270, wbtbAfterSleepMin = 360)

    @Test
    fun wbtbTime_sleepTimedOnlyWhenItComesBeforeTheSetTime() {
        val start = 21 * hour + 30 * min
        assertEquals(28 * hour to "SLEEP", WakeAlarms.wbtbTime(cfg, start, 22 * hour, setAt, 22 * hour + 10 * min))
        // Asleep at 22:30: 6 h later is the set time itself, not before it.
        assertEquals(setAt to "LATE", WakeAlarms.wbtbTime(cfg, start, 22 * hour + 30 * min, setAt, 23 * hour))
        assertEquals(setAt to "LATE", WakeAlarms.wbtbTime(cfg, start, 22 * hour + 31 * min, setAt, 23 * hour))
    }

    @Test
    fun wbtbTime_decidedAtBedtime_whenEvenFallingAsleepAtStartIsTooLate() {
        // No wait: the skip line shows at bedtime.
        assertEquals(setAt to "LATE", WakeAlarms.wbtbTime(cfg, 23 * hour, null, setAt, 23 * hour))
        val late = 22 * hour + 30 * min
        assertEquals(setAt to "LATE", WakeAlarms.wbtbTime(cfg, late, null, setAt, late))
        val start = 22 * hour + 29 * min
        assertNull(WakeAlarms.wbtbTime(cfg, start, null, setAt, start)) // it waits to see you fall asleep
    }

    @Test
    fun wbtbTime_waitsForTheOnset_untilTheDeadline() {
        val start = 21 * hour
        assertNull(WakeAlarms.wbtbTime(cfg, start, null, setAt, 24 * hour - 1000))
        assertEquals(setAt to "NO_ONSET", WakeAlarms.wbtbTime(cfg, start, null, setAt, 24 * hour))
        assertEquals(28 * hour to "SLEEP", WakeAlarms.wbtbTime(cfg, start, 22 * hour, setAt, 22 * hour + 10 * min))
        // Waiting never delays a ring: the deadline ends before the earliest one.
        assertTrue(WakeAlarms.ONSET_DEADLINE_MIN < MIN_SLEEP_BEFORE_WBTB_MIN)
    }

    @Test
    fun wbtbTime_aStartOver12HoursBeforeIsTheSetTime_exceptOnATestNight() {
        assertEquals(setAt to "EARLY_START", WakeAlarms.wbtbTime(cfg, 16 * hour + 29 * min, null, setAt, 17 * hour))
        val start = 16 * hour + 30 * min // exactly 12 h before
        assertNull(WakeAlarms.wbtbTime(cfg, start, null, setAt, start))
        val onset = 15 * hour + 20_000
        val test = WakeAlarms.wbtbTime(cfg.copy(wbtbAfterSleepMin = 270), 15 * hour, onset, setAt, onset, test = true)
        assertEquals(onset + 270_000 to "SLEEP", test) // seconds for minutes
    }

    @Test
    fun wbtbTime_aWakeUpBeforeTheSetTime_ringsFirst_soTheEarlyWakeUpIsNotSleepTimed() {
        // Wake-up at 6:00, early wake-up no later than 6:30: Settings says the early wake-up won't ring.
        val c = cfg.copy(wakeAtMin = 360, wbtbAtMin = 390)
        val sixThirty = 30 * hour + 30 * min
        val onset = 23 * hour + 45 * min // 6 h later would be 5:45, before the wake-up
        assertEquals(sixThirty to "CLOCK", WakeAlarms.wbtbTime(c, 23 * hour, onset, sixThirty, onset + min))
    }

    @Test
    fun wbtbTime_neverUnder4_5HoursAfterTheOnset() {
        val tooSoon = cfg.copy(wbtbAfterSleepMin = 60) // a value no phone should send
        val timed = WakeAlarms.wbtbTime(tooSoon, 21 * hour, 22 * hour, setAt, 23 * hour)
        assertEquals(26 * hour + 30 * min to "SLEEP", timed) // 4.5 h after the onset at 22:00
    }

    @Test
    fun nextWbtbAt_afterTonightsRang_onlyTomorrows() {
        val ny = ZoneId.of("America/New_York")
        fun ms(text: String) = LocalDateTime.parse(text).atZone(ny).toInstant().toEpochMilli()
        assertEquals(ms("2026-10-29T04:30"), WakeAlarms.nextWbtbAt(270, ms("2026-10-28T23:00"), null, ny))
        // Sleep-timed at 3:40: tonight's set time never rings as a second one.
        val rang = ms("2026-10-29T03:40")
        assertEquals(ms("2026-10-30T04:30"), WakeAlarms.nextWbtbAt(270, ms("2026-10-29T03:50"), rang, ny))
        // A crash restart planned from the night's start, after tonight's rang at 4:30.
        val rangAtSet = ms("2026-10-29T04:30")
        assertEquals(ms("2026-10-30T04:30"), WakeAlarms.nextWbtbAt(270, ms("2026-10-28T23:00"), rangAtSet, ny))
        // The recording ran on into the evening: the next night still rings.
        assertEquals(ms("2026-10-30T04:30"), WakeAlarms.nextWbtbAt(270, ms("2026-10-29T20:00"), rang, ny))
    }

    @Test
    fun takeTestNight_onceAndOnlyWithin10Min() {
        WakeAlarms.armTestNight(hour)
        assertTrue(WakeAlarms.takeTestNight(hour + 9 * min))
        assertFalse(WakeAlarms.takeTestNight(hour + 9 * min)) // the next night is a real one
        WakeAlarms.armTestNight(hour)
        assertFalse(WakeAlarms.takeTestNight(hour + 11 * min))
    }

    private val bed = 23 * hour

    private fun over(
        afterMs: Long, morningDone: Boolean = false, reportDue: Boolean = false, onScreen: Boolean = false,
        left: Boolean = false,
    ) = WakeAlarms.nightOver(bed + afterMs, bed, morningDone, reportDue, onScreen, left)

    @Test
    fun nightOver_afterTheMorningAlarm_onceItsQuestionsAreSaved() {
        assertTrue(over(8 * hour, morningDone = true))
        assertFalse(over(8 * hour, morningDone = true, reportDue = true))
        assertFalse(over(8 * hour)) // the morning not over yet
    }

    @Test
    fun nightOver_waitsForUnsavedQuestions_until12HoursAfterTheStart() {
        assertFalse(over(12 * hour - min, morningDone = true, reportDue = true))
        assertTrue(over(12 * hour, morningDone = true, reportDue = true))
    }

    @Test
    fun nightOver_neverWithAnythingLeftTonight() {
        assertFalse(over(20 * hour, morningDone = true, left = true))
    }

    @Test
    fun nightOver_neverWhileTheQuestionsAreOnScreen() {
        assertFalse(over(20 * hour, morningDone = true, onScreen = true))
    }

    @Test
    fun nightOver_withoutAnyAlarm_after12Hours() {
        assertFalse(over(12 * hour - 1000))
        assertTrue(over(12 * hour))
    }

    @Test
    fun somethingLeftTonight_coversRingSnoozeWakeUpEarlyWakeUpBuzzAndGuide() {
        val now = 30 * hour
        fun left(
            ringing: Boolean = false, snoozed: Boolean = false, wakeAt: Long? = null, wbtbAt: Long? = null,
            buzzAt: Long? = null, guide: Boolean = false,
        ) = WakeAlarms.somethingLeftTonight(now, ringing, snoozed, wakeAt, wbtbAt, buzzAt, guide)
        assertTrue(left(wakeAt = now + hour))
        assertFalse(left(wakeAt = now + 23 * hour + 55 * min)) // tomorrow's, once today's rang
        assertFalse(left())
        assertTrue(left(ringing = true))
        assertTrue(left(snoozed = true))
        assertTrue(left(wbtbAt = now + hour))
        assertTrue(left(buzzAt = now + 10 * min))
        assertTrue(left(guide = true))
    }
}
