package com.opentdi.shared.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private const val CHIME_PEAK_DBFS = -12f

/**
 * OpenTDI's own SESSION_END chime (§7.1): [count] soft 1 s strikes of two decaying partials a fifth apart
 * (660 + 990 Hz), peak −12 dBFS. A 10 ms attack and a 100 ms end taper make each strike start and end at 0 (no click).
 */
fun renderEndChime(sampleRate: Int, count: Int = 1): FloatArray {
    val strike = FloatArray(sampleRate) { i ->
        val t = i.toDouble() / sampleRate
        val shape = min(1.0, t / 0.010) * min(1.0, (1.0 - t) / 0.100)
        val tone = exp(-t / 0.30) * sin(2 * PI * 660.0 * t) + 0.5 * exp(-t / 0.15) * sin(2 * PI * 990.0 * t)
        (shape * tone).toFloat()
    }
    return normalisePeak(FloatArray(sampleRate * count) { strike[it % sampleRate] }, CHIME_PEAK_DBFS)
}
