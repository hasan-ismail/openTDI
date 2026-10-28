package com.opentdi.shared.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Mono samples in [−1, 1] and their sample rate. */
class WavAudio(val samples: FloatArray, val sampleRate: Int)

/** RIFF/WAVE reading and PCM16 mono writing (§7.3, [U] §8.1). */
object WavIo {
    /**
     * Reads PCM 8/16 bit, IEEE float (tag 3), or tag 1 with 32 bits as float when [ttsFloat] says the TTS engine
     * reported ENCODING_PCM_FLOAT (FileSynthesisCallback writes tag 1 for every encoding). Channels are averaged to
     * mono; unknown chunks are skipped with their pad byte; a data size of 0 or 0xFFFFFFFF reads to the end.
     */
    fun read(bytes: ByteArray, ttsFloat: Boolean = false): WavAudio {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && fourCc(bytes, 0) == "RIFF" && fourCc(bytes, 8) == "WAVE") { "not a RIFF/WAVE file" }
        var format = -1
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var pos = 12L
        while (pos + 8 <= bytes.size) {
            val at = pos.toInt()
            val size = b.getInt(at + 4).toLong() and 0xFFFFFFFFL
            val body = at + 8
            when (fourCc(bytes, at)) {
                "fmt " -> {
                    require(size >= 16 && body + 16 <= bytes.size) { "fmt chunk too short" }
                    format = b.getShort(body).toInt() and 0xFFFF
                    channels = b.getShort(body + 2).toInt() and 0xFFFF
                    sampleRate = b.getInt(body + 4)
                    bits = b.getShort(body + 14).toInt() and 0xFFFF
                }
                "data" -> {
                    require(format != -1) { "data chunk before fmt chunk" }
                    require(channels >= 1 && sampleRate > 0) { "bad fmt: $channels channels at $sampleRate Hz" }
                    val unpatched = size == 0L || size == 0xFFFFFFFFL // a crash, or a streaming writer
                    val end = if (unpatched || body + size > bytes.size) bytes.size else (body + size).toInt()
                    return WavAudio(decode(b, body, end, format, channels, bits, ttsFloat), sampleRate)
                }
            }
            pos = body + size + (size and 1L)
        }
        throw IllegalArgumentException("no data chunk")
    }

    /** A PCM16 mono WAV with the 44-byte header; samples are clamped to [−1, 1]. */
    fun writePcm16Mono(samples: FloatArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * 2
        val b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes).put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        b.putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        for (s in samples) b.putShort((clampSample(s) * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        return b.array()
    }

    private fun decode(
        b: ByteBuffer,
        start: Int,
        end: Int,
        format: Int,
        channels: Int,
        bits: Int,
        ttsFloat: Boolean,
    ): FloatArray {
        val sample: (Int) -> Float = when {
            format == 1 && bits == 16 -> { i -> b.getShort(i) / 32768f }
            format == 1 && bits == 8 -> { i -> ((b.get(i).toInt() and 0xFF) - 128) / 128f } // 8-bit PCM is unsigned
            (format == 3 || (format == 1 && ttsFloat)) && bits == 32 -> { i -> b.getFloat(i) }
            else -> throw IllegalArgumentException("unsupported WAV format tag $format with $bits bits per sample")
        }
        val bytesPerSample = bits / 8
        val frameBytes = channels * bytesPerSample
        return FloatArray((end - start) / frameBytes) { f ->
            val frame = start + f * frameBytes
            var sum = 0f
            for (c in 0 until channels) sum += sample(frame + c * bytesPerSample)
            clampSample(sum / channels)
        }
    }

    private fun fourCc(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}
