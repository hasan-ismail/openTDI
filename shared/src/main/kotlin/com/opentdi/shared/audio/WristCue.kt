package com.opentdi.shared.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * The wrist-speaker channel (DataLayerPaths.WRIST_AUDIO_CHANNEL): the phone sends one cue, already faded in and at its
 * target gain, as mono PCM16 behind a sample-rate and sample-count header; the watch plays it and answers with one
 * status byte.
 */
object WristCue {
    const val PLAYED = 1
    const val VOLUME_ZERO = 2
    const val FAILED = 3
    private const val MAX_SAMPLES = 48_000 * 120 // two minutes at 48 kHz bounds what the watch allocates

    fun write(out: OutputStream, samples: FloatArray, sampleRate: Int) {
        val data = DataOutputStream(out.buffered())
        data.writeInt(sampleRate)
        data.writeInt(samples.size)
        for (s in samples) data.writeShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt())
        data.flush()
    }

    fun read(input: InputStream): WavAudio {
        val data = DataInputStream(input.buffered())
        val sampleRate = data.readInt()
        val count = data.readInt()
        if (sampleRate !in 8_000..48_000 || count !in 0..MAX_SAMPLES) {
            throw IOException("bad wrist cue header: rate=$sampleRate count=$count")
        }
        return WavAudio(FloatArray(count) { data.readShort() / Short.MAX_VALUE.toFloat() }, sampleRate)
    }
}
