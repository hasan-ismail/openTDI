package com.opentdi.shared.audio

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
        val kept = ByteArrayOutputStream().also {
            WristCue.write(it, FloatArray(0), 24_000, calibrate = false, storeAs = WristCue.WAKE_VOICE)
        }
        WristCue.read(kept.toByteArray().inputStream()).let {
            assertEquals(WristCue.WAKE_VOICE, it.storeAs)
            assertFalse(it.calibrate)
        }
        assertNull(WristCue.read(plain.toByteArray().inputStream()).storeAs) // played, not kept
    }

    @Test
    fun namedClip_roundTrip_andOlderAppsStayCompatible() {
        val clip = ByteArrayOutputStream().also {
            WristCue.write(it, floatArrayOf(0.25f), 24_000, calibrate = false, storeAs = GuideClip.CUE_INTRO.file, earbuds = true)
        }.toByteArray()
        WristCue.read(clip.inputStream()).let {
            assertEquals("cue_intro", it.storeAs)
            assertTrue(it.earbuds && !it.calibrate)
            assertEquals(24_000, it.audio.sampleRate)
            assertArrayEquals(floatArrayOf(0.25f), it.audio.samples, 1e-4f)
        }
        // An older watch reads its rate where the name now starts: far above 48 kHz, so it answers FAILED.
        assertTrue(DataInputStream(clip.inputStream()).apply { skipBytes(8) }.readInt() > 48_000)
        // An older phone's wake-up voice (the store flag, no name) is still kept as the wake-up voice...
        val old = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(0x4F544432)
                writeInt(2)
                writeInt(24_000)
                writeInt(1)
                writeShort(0)
            }
        }.toByteArray()
        assertEquals(WristCue.WAKE_VOICE, WristCue.read(old.inputStream()).storeAs)
        // ...and this app sends it byte for byte the same way, so an older watch keeps it too.
        val wake = ByteArrayOutputStream().also {
            WristCue.write(it, floatArrayOf(0f), 24_000, calibrate = false, storeAs = WristCue.WAKE_VOICE)
        }
        assertArrayEquals(old, wake.toByteArray())
    }

    @Test
    fun clipNames_staySimpleFileNames() {
        for (clip in GuideClip.entries) {
            val bytes = ByteArrayOutputStream().also {
                WristCue.write(it, FloatArray(0), 24_000, calibrate = false, storeAs = clip.file)
            }
            assertEquals(clip.file, WristCue.read(bytes.toByteArray().inputStream()).storeAs)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WristCue.write(ByteArrayOutputStream(), FloatArray(0), 24_000, calibrate = false, storeAs = "../wake")
        }
        // A name that would leave the watch's guide folder is refused before any audio is read.
        val hostile = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(0x4F544432)
                writeInt(8)
                writeUTF("../../shared_prefs/x")
                writeInt(24_000)
                writeInt(0)
            }
        }
        val e = assertThrows(IOException::class.java) { WristCue.read(hostile.toByteArray().inputStream()) }
        assertEquals("bad clip name", e.message)
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
