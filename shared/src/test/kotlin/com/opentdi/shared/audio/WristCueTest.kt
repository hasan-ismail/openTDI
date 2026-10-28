package com.opentdi.shared.audio

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WristCueTest {
    @Test
    fun roundTrip_keepsTheRateAndClampsToPcm16() {
        val bytes = ByteArrayOutputStream().also { WristCue.write(it, floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 2f), 24_000) }
        val back = WristCue.read(bytes.toByteArray().inputStream())
        assertEquals(24_000, back.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 1f), back.samples, 1e-4f)
    }

    @Test(expected = IOException::class)
    fun read_refusesABadHeader() {
        WristCue.read(ByteArray(8) { 0x7f }.inputStream())
    }
}
