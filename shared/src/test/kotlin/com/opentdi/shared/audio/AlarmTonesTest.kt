package com.opentdi.shared.audio

import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.AlarmTone
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTonesTest {
    @Test
    fun everyToneLoopsForOneToFourSeconds_atAPeakOf09_andVibrationHasNone() {
        for (tone in AlarmTone.entries - AlarmTone.VIBRATE_ONLY - AlarmTone.SPOKEN) {
            val loop = alarmLoop(tone, 24_000)
            assertTrue("$tone length", loop.size in 24_000..96_000)
            assertEquals("$tone peak", 0.9f, loop.maxOf { abs(it) }, 1e-4f)
        }
        assertEquals(0, alarmLoop(AlarmTone.VIBRATE_ONLY, 24_000).size)
        val cue = lucidCue(24_000)
        assertEquals(15_600, cue.size) // 650 ms
        assertTrue(cue.maxOf { abs(it) } <= 0.9f)
    }

    @Test
    fun nextAlarm_isTodayWhenAhead_tomorrowWhenPassed_andSurvivesDst() {
        val zone = ZoneId.of("America/New_York")
        fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        assertEquals(ms("2026-10-28T07:00"), nextAlarmAt(420, ms("2026-10-27T23:30"), zone)) // past midnight
        assertEquals(ms("2026-10-28T07:00"), nextAlarmAt(420, ms("2026-10-28T06:59"), zone))
        assertEquals(ms("2026-10-29T07:00"), nextAlarmAt(420, ms("2026-10-28T07:00"), zone)) // exactly now: tomorrow
        // DST ends 2026-11-01 at 02:00 in New York: 07:00 is still 07:00 on the wall clock.
        assertEquals(ms("2026-11-01T07:00"), nextAlarmAt(420, ms("2026-10-31T23:00"), zone))
    }

    @Test
    fun alarmsAheadNamesOnlyTheOnesStillToRingTonight() {
        val zone = ZoneId.of("America/New_York")
        fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        val both = AlarmConfig(wakeEnabled = true, wakeAtMin = 420, wbtbEnabled = true, wbtbAtMin = 270)
        assertEquals(
            "the WBTB alarm at 4:30 AM and the wake-up alarm at 7 AM",
            alarmsAhead(both, ms("2026-10-29T03:06"), zone),
        )
        assertEquals("the wake-up alarm at 7 AM", alarmsAhead(both, ms("2026-10-29T05:00"), zone)) // WBTB has rung
        assertNull(alarmsAhead(both, ms("2026-10-29T07:30"), zone)) // the morning: both have rung
        assertNull(alarmsAhead(both.copy(wakeEnabled = false, wbtbEnabled = false), ms("2026-10-29T03:06"), zone))
    }
}
