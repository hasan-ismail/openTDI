package com.opentdi.shared.audio

import kotlin.math.pow

/** The §7.4 fade-in, linear in dB from −60 dB to 0 dB over [fadeInMs]; fadeInMs = 0 disables it. */
class FadeEnvelope(private val sampleRate: Int, private val fadeInMs: Int) {
    /** G_fade for sample [n] of a cue: exactly 0 at n = 0, 1 from t = T on. */
    fun gain(n: Int): Double {
        if (fadeInMs <= 0) return 1.0
        if (n == 0) return 0.0
        val tOverT = n * 1000.0 / (sampleRate.toDouble() * fadeInMs)
        return if (tOverT >= 1.0) 1.0 else 10.0.pow((-60.0 + 60.0 * tOverT) / 20.0)
    }

    /** out[n] = clamp(x[n] · G_fade(n) · targetGain, −1, 1), with targetGain = G_target from [targetGain]. */
    fun applyTo(samples: FloatArray, targetGain: Float): FloatArray =
        FloatArray(samples.size) { n -> clampSample((samples[n] * gain(n) * targetGain).toFloat()) }
}
