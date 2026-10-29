package com.opentdi.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineStoreTest {
    @Test
    fun routineStepsSayWhatAndWhy() {
        assertTrue(ROUTINE.all { it.text.isNotBlank() && it.why.isNotBlank() })
    }
}
