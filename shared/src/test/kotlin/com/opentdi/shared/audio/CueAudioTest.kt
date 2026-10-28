package com.opentdi.shared.audio

import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.AudioRouteMode
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueAudioTest {
    @Test
    fun targetGain_isTheMinimumAndNeverAboveUnity() {
        assertEquals(0.3f, targetGain(userGain = 0.5f, routeCapGain = 0.3f), 0f)
        assertEquals(0.2f, targetGain(userGain = 0.2f, routeCapGain = 0.6f), 0f)
        assertEquals(HARD_MAX_GAIN, targetGain(userGain = 1.5f, routeCapGain = 2f), 0f)
        assertEquals(0f, targetGain(userGain = -0.5f, routeCapGain = 0.6f), 0f)
        assertEquals(0.5f, AudioConfig().targetGain(), 0f) // speaker cap 0.6
        assertEquals(0f, AudioConfig(route = AudioRouteMode.USB_WIRED_DAC).targetGain(), 0f) // no calibrated cap
    }

    @Test
    fun volumeRaised_scalesByTheDbDifference() {
        assertEquals(10.0.pow(-6.0 / 20).toFloat(), volumeRaisedCorrection(dbNow = -20f, dbCal = -26f), 1e-6f)
        assertEquals(1f, volumeRaisedCorrection(dbNow = -26f, dbCal = -26f), 0f)
        assertEquals(1f, volumeRaisedCorrection(dbNow = -30f, dbCal = -26f), 0f)
    }

    @Test
    fun normalisePeak_minus3Dbfs() {
        val input = floatArrayOf(0.1f, -0.5f, 0.25f)
        val out = normalisePeak(input)
        val target = 10.0.pow(-3.0 / 20).toFloat()
        assertEquals(target, out.maxOf { abs(it) }, 1e-6f)
        assertEquals(-target, out[1], 1e-6f)
        assertEquals(out[0] * 2.5f, out[2], 1e-6f)
        assertArrayEquals(floatArrayOf(0.1f, -0.5f, 0.25f), input, 0f) // input untouched
        assertArrayEquals(FloatArray(4), normalisePeak(FloatArray(4)), 0f)
    }

    @Test
    fun endChime_softDeterministicAndClickFree() {
        val chime = renderEndChime(48_000, count = 3)
        assertEquals(3 * 48_000, chime.size)
        val peak = chime.maxOf { abs(it) }
        assertTrue("peak $peak", peak <= 10.0.pow(-12.0 / 20).toFloat() + 1e-6f && peak > 0.25f)
        for (strike in 0 until 3) {
            assertEquals(0f, chime[strike * 48_000], 0f)
            assertTrue(abs(chime[strike * 48_000 + 47_999]) < 1e-3f)
        }
        assertArrayEquals(chime, renderEndChime(48_000, count = 3), 0f)
        assertEquals(22_050, renderEndChime(22_050).size)
    }
}
