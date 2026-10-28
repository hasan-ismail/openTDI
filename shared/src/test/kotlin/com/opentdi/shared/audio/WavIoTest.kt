package com.opentdi.shared.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WavIoTest {
    private fun le(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    private fun chunk(id: String, body: ByteArray): ByteArray {
        val pad = if (body.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        return id.toByteArray() + le(4) { putInt(body.size) } + body + pad
    }

    private fun fmt(tag: Int, channels: Int, rate: Int, bits: Int) = chunk(
        "fmt ",
        le(16) {
            putShort(tag.toShort()).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * bits / 8)
            putShort((channels * bits / 8).toShort()).putShort(bits.toShort())
        },
    )

    private fun riff(vararg chunks: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { chunks.forEach { write(it) } }.toByteArray()
        return "RIFF".toByteArray() + le(4) { putInt(4 + body.size) } + "WAVE".toByteArray() + body
    }

    private fun pcm16(vararg samples: Int) = le(2 * samples.size) { samples.forEach { putShort(it.toShort()) } }

    private fun float32(vararg samples: Float) = le(4 * samples.size) { samples.forEach { putFloat(it) } }

    @Test
    fun pcm16_roundTrip() {
        val samples = FloatArray(200) { (it * 327 - 32768) / 32768f } + floatArrayOf(-1f, 0f, 32767 / 32768f)
        val bytes = WavIo.writePcm16Mono(samples, 16_000)
        assertEquals(44 + 2 * samples.size, bytes.size)
        val back = WavIo.read(bytes)
        assertEquals(16_000, back.sampleRate)
        assertArrayEquals(samples, back.samples, 0f)
    }

    @Test
    fun write_clampsAndHeaderIsStandard() {
        val bytes = WavIo.writePcm16Mono(floatArrayOf(1.5f, -2f, Float.NaN), 22_050)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(36 + 6, b.getInt(4))
        assertEquals("WAVEfmt ", String(bytes, 8, 8))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(22_050, b.getInt(24))
        assertEquals(44_100, b.getInt(28))
        assertEquals(2, b.getShort(32).toInt())
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(6, b.getInt(40))
        assertEquals(listOf<Short>(32767, -32768, 0), listOf(b.getShort(44), b.getShort(46), b.getShort(48)))
    }

    @Test
    fun oddSizedListChunks_skippedWithPadByte() {
        val info = chunk("LIST", "INFOabc".toByteArray()) // 7 bytes + 1 pad
        val odd = chunk("LIST", byteArrayOf(1, 2, 3))
        val bytes = riff(info, fmt(1, 1, 8000, 16), odd, chunk("data", pcm16(100, -200, 300)))
        assertArrayEquals(floatArrayOf(100 / 32768f, -200 / 32768f, 300 / 32768f), WavIo.read(bytes).samples, 0f)
    }

    // A data chunk header with a raw size field and no pad, as a crashed or streaming writer leaves it.
    private fun rawData(size: Int, body: ByteArray) =
        riff(fmt(1, 1, 8000, 16)) + "data".toByteArray() + le(4) { putInt(size) } + body

    @Test
    fun dataSizeZeroOrMax_readsToEof() {
        for (size in listOf(0, 0xFFFFFFFF.toInt())) {
            val samples = WavIo.read(rawData(size, pcm16(1, 2, 3, 4))).samples
            assertArrayEquals(floatArrayOf(1 / 32768f, 2 / 32768f, 3 / 32768f, 4 / 32768f), samples, 0f)
        }
        // A size past the end of the file (truncated) reads what is there and drops the partial frame.
        val truncated = rawData(1000, pcm16(7, 8) + byteArrayOf(9))
        assertArrayEquals(floatArrayOf(7 / 32768f, 8 / 32768f), WavIo.read(truncated).samples, 0f)
    }

    @Test
    fun ieeeFloat_tag3() {
        val bytes = riff(fmt(3, 1, 24_000, 32), chunk("data", float32(0.5f, -0.25f, 1.5f)))
        val audio = WavIo.read(bytes)
        assertEquals(24_000, audio.sampleRate)
        assertArrayEquals(floatArrayOf(0.5f, -0.25f, 1f), audio.samples, 0f) // clamped to [-1, 1]
    }

    @Test
    fun tag1With32Bits_isFloatOnlyWhenTtsSaysSo() {
        val bytes = riff(fmt(1, 1, 22_050, 32), chunk("data", float32(0.125f, -0.75f)))
        assertArrayEquals(floatArrayOf(0.125f, -0.75f), WavIo.read(bytes, ttsFloat = true).samples, 0f)
        assertThrows(IllegalArgumentException::class.java) { WavIo.read(bytes) }
    }

    @Test
    fun stereo_downmixedToMono() {
        val bytes = riff(fmt(1, 2, 44_100, 16), chunk("data", pcm16(1000, 3000, -2000, -4000)))
        assertArrayEquals(floatArrayOf(2000 / 32768f, -3000 / 32768f), WavIo.read(bytes).samples, 0f)
        val floatStereo = riff(fmt(3, 2, 48_000, 32), chunk("data", float32(1f, 0f, -0.5f, 0.5f)))
        assertArrayEquals(floatArrayOf(0.5f, 0f), WavIo.read(floatStereo).samples, 0f)
    }

    @Test
    fun pcm8_isUnsigned() {
        val bytes = riff(fmt(1, 1, 8000, 8), chunk("data", byteArrayOf(128.toByte(), 0, 255.toByte())))
        assertArrayEquals(floatArrayOf(0f, -1f, 127 / 128f), WavIo.read(bytes).samples, 0f)
    }

    @Test
    fun malformed_rejected() {
        assertThrows(IllegalArgumentException::class.java) { WavIo.read("RIFX....WAVE".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { WavIo.read(riff(fmt(1, 1, 8000, 16))) } // no data
        val dataFirst = riff(chunk("data", pcm16(1)), fmt(1, 1, 8000, 16))
        assertThrows(IllegalArgumentException::class.java) { WavIo.read(dataFirst) }
        val pcm24 = riff(fmt(1, 1, 8000, 24), chunk("data", ByteArray(6)))
        assertThrows(IllegalArgumentException::class.java) { WavIo.read(pcm24) }
    }
}
