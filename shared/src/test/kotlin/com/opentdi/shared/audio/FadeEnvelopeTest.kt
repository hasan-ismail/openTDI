package com.opentdi.shared.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeEnvelopeTest {
    private val fs = 48_000
    private val env = FadeEnvelope(fs, 2000)

    private fun at(ms: Int) = env.gain(ms * fs / 1000)

    @Test
    fun gainCurve_section7_4() {
        assertTrue("G(0) must be exactly 0", env.gain(0) == 0.0)
        assertEquals(0.00100, at(1), 1e-4)
        assertEquals(0.00562, at(500), 1e-4)
        assertEquals(0.0316, at(1000), 1e-4)
        // §16 prints 0.178; the formula gives 10^(-15/20) = 0.177828, which is 1.7e-4 from 0.178.
        assertEquals(0.17783, at(1500), 1e-4)
        assertEquals(1.0, at(2000), 0.0)
        assertEquals(1.0, at(3000), 0.0)
        assertTrue(env.gain(2 * fs - 1) < 1.0)
    }

    @Test
    fun gainCurve_monotonic() {
        var previous = env.gain(0)
        for (n in 1..(2 * fs + 100)) {
            val g = env.gain(n)
            assertTrue("n = $n", g >= previous)
            previous = g
        }
    }

    @Test
    fun zeroLength_disablesTheFade() {
        val off = FadeEnvelope(fs, 0)
        assertEquals(1.0, off.gain(0), 0.0)
        assertEquals(1.0, off.gain(1), 0.0)
        assertEquals(1.0, off.gain(10 * fs), 0.0)
    }

    @Test
    fun applyTo_multipliesTargetGainAndClamps() {
        val out = env.applyTo(FloatArray(3 * fs) { 0.8f }, targetGain = 0.5f)
        assertEquals(0f, out[0], 0f)
        assertEquals((0.8 * env.gain(fs) * 0.5).toFloat(), out[fs], 1e-7f)
        assertEquals(0.4f, out[2 * fs], 1e-7f)
        val loud = FadeEnvelope(fs, 0).applyTo(floatArrayOf(3f, -3f, Float.NaN, 0.25f), targetGain = 1f)
        assertEquals(listOf(1f, -1f, 0f, 0.25f), loud.toList())
    }
}
