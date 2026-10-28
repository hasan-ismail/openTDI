package com.opentdi.shared.audio

import com.opentdi.shared.model.AlarmTone
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * One loop of [tone] as mono float samples at [sampleRate], peak 0.9; the player repeats it and ramps the volume.
 * Synthesised, so the app ships no audio files. VIBRATE_ONLY is empty.
 */
fun alarmLoop(tone: AlarmTone, sampleRate: Int): FloatArray {
    fun buffer(seconds: Double) = FloatArray((seconds * sampleRate).toInt())
    val out = when (tone) {
        // A rising-and-falling pentatonic phrase: melodic alarms left people less groggy than harsh ones.
        AlarmTone.MELODY -> buffer(3.0).also { b ->
            listOf(392.0, 440.0, 523.25, 587.33, 659.25, 587.33, 523.25, 440.0).forEachIndexed { i, f ->
                bell(b, sampleRate, start = i * 0.3, freq = f, decay = 4.0)
            }
        }
        AlarmTone.GENTLE_CHIMES -> buffer(3.6).also { b ->
            listOf(523.25, 659.25, 783.99).forEachIndexed { i, f ->
                bell(b, sampleRate, start = i * 0.6, freq = f, decay = 2.5)
            }
        }
        AlarmTone.RISING_TONE -> buffer(2.0).also { b ->
            val n = (1.5 * sampleRate).toInt()
            var phase = 0.0
            for (k in 0 until n) {
                phase += 2 * PI * (440.0 + 440.0 * k / n) / sampleRate // 440 → 880 Hz
                val edge = min(1.0, min(k, n - k) / (0.05 * sampleRate)) // 50 ms fades: no click
                b[k] = (sin(phase) * edge).toFloat()
            }
        }
        // A low 520 Hz square-ish beep: the pitch and shape with the lowest waking thresholds (Bruck et al. 2009).
        AlarmTone.CLASSIC_BEEPS -> buffer(1.4).also { b ->
            for (beep in 0 until 4) {
                val start = (beep * 0.2 * sampleRate).toInt()
                val n = (0.1 * sampleRate).toInt()
                for (k in 0 until n) {
                    val edge = min(1.0, min(k, n - k) / (0.005 * sampleRate))
                    val w = 2 * PI * 520.0 * k / sampleRate
                    b[start + k] = ((sin(w) + sin(3 * w) / 3 + sin(5 * w) / 5) * edge).toFloat()
                }
            }
        }
        AlarmTone.VIBRATE_ONLY -> FloatArray(0)
    }
    val peak = out.maxOfOrNull { abs(it) } ?: 0f
    if (peak > 0f) for (k in out.indices) out[k] = out[k] / peak * 0.9f
    return out
}

/** Adds a soft bell at [start] s: three partials with a 5 ms attack and an exponential decay, cut at the buffer end. */
private fun bell(b: FloatArray, sampleRate: Int, start: Double, freq: Double, decay: Double) {
    val from = (start * sampleRate).toInt()
    for (k in 0 until b.size - from) {
        val t = k.toDouble() / sampleRate
        val env = min(1.0, t / 0.005) * exp(-decay * t)
        val s = sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t) + 0.1 * sin(6 * PI * freq * t)
        b[from + k] += (s * env).toFloat()
    }
}

/** The next time after [fromMs] that the local clock in [zone] reads [minuteOfDay] (0..1439), as epoch ms. */
fun nextAlarmAt(minuteOfDay: Int, fromMs: Long, zone: ZoneId): Long {
    val from = Instant.ofEpochMilli(fromMs).atZone(zone)
    var at = from.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone)
    if (!at.isAfter(from)) at = at.plusDays(1)
    return at.toInstant().toEpochMilli()
}
