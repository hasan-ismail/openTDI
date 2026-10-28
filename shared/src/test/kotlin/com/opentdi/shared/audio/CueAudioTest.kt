package com.opentdi.shared.audio

import com.opentdi.shared.config.StructuralConstants
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
    fun trimSilence_cutsOnlyTheEdgesBelowMinus50Dbfs() {
        val quiet = 0.003f // −50.5 dBFS
        val edge = 0.0032f // −49.9 dBFS
        val phrase = floatArrayOf(0f, quiet, -quiet, edge, 0.5f, 0f, -0.2f, quiet, 0f)
        assertArrayEquals(floatArrayOf(edge, 0.5f, 0f, -0.2f), trimSilence(phrase), 0f)
        assertArrayEquals(floatArrayOf(0.5f), trimSilence(floatArrayOf(0.5f)), 0f)
        assertEquals(0, trimSilence(floatArrayOf(0f, quiet, -quiet)).size)
        assertEquals(0, trimSilence(FloatArray(0)).size)
    }

    @Test
    fun joinWithPauses_exactSilenceBetweenPhrasesOnly() {
        val joined = joinWithPauses(listOf(floatArrayOf(0.1f, 0.2f), floatArrayOf(0.3f), floatArrayOf(-0.4f)), 1000, 3)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0f, 0f, 0f, 0.3f, 0f, 0f, 0f, -0.4f), joined, 0f)
        assertEquals(2 * 16_800 + 3, joinWithPauses(List(3) { floatArrayOf(0.5f) }, 24_000, 700).size)
        assertArrayEquals(floatArrayOf(0.5f), joinWithPauses(listOf(floatArrayOf(0.5f)), 24_000, 700), 0f)
        assertEquals(0, joinWithPauses(emptyList(), 24_000, 700).size)
        assertEquals(2, joinWithPauses(List(2) { floatArrayOf(0.5f) }, 24_000, 0).size)
    }

    @Test
    fun speechLead_silenceBeforeTheFirstPhraseOnly_speechEntersTheFadeAtMinus15Db() {
        val led = joinWithPauses(listOf(floatArrayOf(0.5f), floatArrayOf(-0.4f)), 1000, 3, leadMs = 2)
        assertArrayEquals(floatArrayOf(0f, 0f, 0.5f, 0f, 0f, 0f, -0.4f), led, 0f)
        val leadMs = (2000 * StructuralConstants.SPEECH_LEAD_FADE_FRACTION).toInt()
        val cue = joinWithPauses(listOf(floatArrayOf(0.5f, 0.3f)), 24_000, 700, leadMs)
        val speechAt = cue.indexOfFirst { abs(it) >= 10f.pow(StructuralConstants.PHRASE_SILENCE_DBFS / 20f) }
        assertEquals(1500 * 24, speechAt)
        assertEquals(10.0.pow(-15.0 / 20), FadeEnvelope(24_000, 2000).gain(speechAt), 1e-9)
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
