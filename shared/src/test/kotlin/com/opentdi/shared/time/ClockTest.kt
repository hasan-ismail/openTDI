package com.opentdi.shared.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test
    fun twelveHourTimes() {
        assertEquals("12\u00A0AM", clock12(0))
        assertEquals("12:05\u00A0AM", clock12(5))
        assertEquals("4:30\u00A0AM", clock12(270))
        assertEquals("12\u00A0PM", clock12(720))
        assertEquals("11\u00A0PM", clock12(1380))
        assertEquals("1\u00A0AM", clock12(1500)) // wraps past midnight
        assertEquals("11:30\u00A0PM", clock12(-30))
    }
}
