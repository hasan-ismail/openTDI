package com.opentdi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineStoreTest {
    @Test
    fun routineStepsHaveUniqueIds() {
        assertEquals(ROUTINE.size, ROUTINE.map { it.id }.toSet().size)
        assertTrue(ROUTINE.all { it.text.isNotBlank() && it.why.isNotBlank() })
    }
}
