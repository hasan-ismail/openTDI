package com.opentdi.shared.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test
    fun twelveHourTimes() {
        assertEquals("12 AM", clock12(0))
        assertEquals("12:05 AM", clock12(5))
        assertEquals("4:30 AM", clock12(270))
        assertEquals("12 PM", clock12(720))
        assertEquals("11 PM", clock12(1380))
        assertEquals("1 AM", clock12(1500)) // wraps past midnight
        assertEquals("11:30 PM", clock12(-30))
    }
}
