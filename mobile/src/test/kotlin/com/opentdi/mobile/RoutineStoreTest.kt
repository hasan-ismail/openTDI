package com.opentdi.mobile

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineStoreTest {
    @Test
    fun anEveningAndTheNightAfterShareOneChecklist() {
        val zone = ZoneId.of("America/New_York")
        fun night(text: String) = nightOf(LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli(), zone)
        assertEquals("2026-10-28", night("2026-10-28T19:00")) // the shower
        assertEquals("2026-10-28", night("2026-10-29T02:00")) // after midnight, the same night
        assertEquals("2026-10-28", night("2026-10-29T09:00")) // the morning still belongs to it
        assertEquals("2026-10-29", night("2026-10-29T12:30")) // a new day's checklist from noon
    }

    @Test
    fun routineStepsHaveUniqueIds() {
        assertEquals(ROUTINE.size, ROUTINE.map { it.id }.toSet().size)
        assertTrue(ROUTINE.all { it.text.isNotBlank() && it.why.isNotBlank() })
    }
}
