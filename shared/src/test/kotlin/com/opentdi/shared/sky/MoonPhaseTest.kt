package com.opentdi.shared.sky

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonPhaseTest {
    @Test
    fun knownNewAndFullMoons() {
        val new = moonPhase(Instant.parse("2024-01-11T11:57:00Z").toEpochMilli())
        assertEquals(0.0, minOf(new, 1 - new), 0.02) // new moon, 2024-01-11 11:57 UTC
        assertEquals(0.5, moonPhase(Instant.parse("2024-01-25T17:54:00Z").toEpochMilli()), 0.02) // full moon
        assertEquals("Full moon", moonPhaseName(0.5))
        assertEquals("Waxing crescent", moonPhaseName(0.1))
        assertEquals("New moon", moonPhaseName(0.99))
    }
}
