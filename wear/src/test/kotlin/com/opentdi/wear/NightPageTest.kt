package com.opentdi.wear

import com.opentdi.wear.sensing.SensingState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightPageTest {
    @Test
    fun iWokeUp_showsOnlyWhenNothingElseIsDue() {
        val night = SensingState(running = true)
        assertTrue(showIWokeUp(night))
        assertFalse(showIWokeUp(SensingState())) // not tracking
        assertFalse(showIWokeUp(night.copy(alarmRinging = "WAKE")))
        assertFalse(showIWokeUp(night.copy(wakeAgainAtMs = 1L))) // snoozed
        assertFalse(showIWokeUp(night.copy(reportAfter = "WBTB"))) // its own Save my dream is up
        assertTrue(showIWokeUp(night.copy(reportAfter = "NIGHT"))) // a dream left unsaved keeps WBTB near
        assertFalse(showIWokeUp(night.copy(wbtbBackToSleepAtMs = 1L))) // the stay-up time
        assertFalse(showIWokeUp(night.copy(guideOffered = true)))
        assertFalse(showIWokeUp(night.copy(guideRunning = true)))
    }

    @Test
    fun wbtbNowAt_onlyOnceTheEarlyWakeUpCouldRing() {
        val ahead = SensingState(running = true, wbtbAheadAtMs = 9_000L, wbtbNowFromMs = 5_000L)
        assertNull(wbtbNowAt(ahead, nowMs = 4_999L)) // too soon: straight to the dream
        assertEquals(9_000L, wbtbNowAt(ahead, nowMs = 5_000L))
        assertNull(wbtbNowAt(ahead.copy(wbtbAheadAtMs = null), nowMs = 6_000L)) // none, done or skipped
    }

    @Test
    fun nightEndLine_afterTheWakeUp_orByATime() {
        val start = LocalDate.of(2026, 10, 29).atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("The night ends by itself after your wake-up alarm.", nightEndLine(true, start, null))
        assertEquals("The night ends by itself by 11\u00A0AM.", nightEndLine(false, start, null))
        // Late cues armed late hold the night past the 12 h; ending sooner, they change nothing.
        val cuesEnd = start + 12 * 3_600_000L + 20 * 60_000L
        assertEquals("The night ends by itself by 11:20\u00A0AM.", nightEndLine(false, start, cuesEnd))
        assertEquals("The night ends by itself by 11\u00A0AM.", nightEndLine(false, start, start + 10 * 3_600_000L))
    }
}
