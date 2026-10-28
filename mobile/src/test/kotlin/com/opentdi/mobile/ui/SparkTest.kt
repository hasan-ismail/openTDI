package com.opentdi.mobile.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SparkTest {
    private fun assertLines(expected: List<List<Pair<Float, Float>>>, spark: Spark) {
        assertEquals(expected.map { it.size }, spark.lines.map { it.size })
        val flat = { lines: List<List<Pair<Float, Float>>> -> lines.flatten().flatMap { it.toList() }.toFloatArray() }
        assertArrayEquals(flat(expected), flat(spark.lines), 1e-6f)
    }

    @Test
    fun timeAndValuesScale_toTheUnitSquare_andNullsAndGapsBreakTheLine() {
        val spark = spark(listOf(0L to 1f, 1000L to 2f, 2000L to null, 3000L to 4f, 4000L to 5f, 8000L to 3f), 10_000)
        assertEquals(1f..5f, spark.range)
        assertLines(listOf(listOf(0.2f to 0f, 0.3f to 0.25f), listOf(0.5f to 0.75f, 0.6f to 1f), listOf(1f to 0.5f)), spark)
    }

    @Test
    fun window_flatValues_fixedRange_andNoData() {
        val windowed = spark(listOf(0L to 9f, 5000L to 2f, 6000L to 3f), windowMs = 3000)
        assertEquals(2f..3f, windowed.range) // 9 is older than the window
        assertLines(listOf(listOf(2f / 3 to 0f, 1f to 1f)), windowed)
        assertLines(listOf(listOf(0.9f to 0.5f, 1f to 0.5f)), spark(listOf(0L to 7f, 1000L to 7f), 10_000))
        val fixed = spark(listOf(0L to 1.5f, 1000L to -1f), 10_000, fixedRange = 0f..1f)
        assertEquals(0f..1f, fixed.range)
        assertLines(listOf(listOf(0.9f to 1f, 1f to 0f)), fixed)
        assertNull(spark(emptyList(), 10_000).range)
        assertNull(spark(listOf(0L to null), 10_000).range)
        assertEquals(0, spark(listOf(0L to null), 10_000).lines.size)
    }
}
