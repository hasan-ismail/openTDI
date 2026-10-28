package com.opentdi.shared.audio

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WristCueTest {
    @Test
    fun cue_roundTrip_keepsTheRateAndFlagAndClampsToPcm16() {
        val bytes = ByteArrayOutputStream().also {
            WristCue.write(it, floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 2f), 24_000, calibrate = true)
        }
        val back = WristCue.read(bytes.toByteArray().inputStream())
        assertTrue(back.calibrate)
        assertEquals(24_000, back.audio.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 1f), back.audio.samples, 1e-4f)
        val plain = ByteArrayOutputStream().also { WristCue.write(it, FloatArray(0), 22_050, calibrate = false) }
        assertFalse(WristCue.read(plain.toByteArray().inputStream()).calibrate)
    }

    @Test(expected = IOException::class)
    fun read_refusesAnotherAppVersion() {
        WristCue.read(ByteArray(16) { 0x7f }.inputStream())
    }

    @Test
    fun read_refusesMoreThanTwoMinutesOfAudio_beforeReadingSamples() { // bounds what the watch allocates
        val header = ByteArrayOutputStream().also { WristCue.write(it, FloatArray(0), 48_000, calibrate = false) }
            .toByteArray()
        header[12] = 0x01 // the sample count becomes 2^24, far past the limit
        val e = assertThrows(IOException::class.java) { WristCue.read(header.inputStream()) }
        assertTrue(e.message.orEmpty().startsWith("bad wrist cue header")) // not a later EOF
    }

    @Test
    fun reply_reportsStartedThenTheResult() {
        val out = ByteArrayOutputStream()
        WristCue.writeStarted(out)
        WristCue.writeReply(out, WristCue.Reply(WristCue.PLAYED, 7, 2, 0.5f))
        var started = 0
        assertEquals(WristCue.Reply(WristCue.PLAYED, 7, 2, 0.5f), WristCue.readReply(out.toByteArray().inputStream()) { started++ })
        assertEquals(1, started)

        val skipped = ByteArrayOutputStream().also { WristCue.writeReply(it, WristCue.Reply(WristCue.VOLUME_ZERO, 0)) }
        assertEquals(WristCue.Reply(WristCue.VOLUME_ZERO, 0), WristCue.readReply(skipped.toByteArray().inputStream()) { started++ })
        assertEquals(1, started) // no sound, no started marker

        assertNull(WristCue.readReply(ByteArray(0).inputStream()) {}) // the channel closed before any answer
    }
}
