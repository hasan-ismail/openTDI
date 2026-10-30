package com.opentdi.shared.audio

import com.opentdi.shared.config.MIN_SLEEP_BEFORE_WBTB_MIN
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.AlarmTone
import com.opentdi.shared.time.clock12
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
        AlarmTone.VIBRATE_ONLY, AlarmTone.SPOKEN -> FloatArray(0) // SPOKEN: the player loads the phone's voice
    }
    val peak = out.maxOfOrNull { abs(it) } ?: 0f
    if (peak > 0f) for (k in out.indices) out[k] = out[k] / peak * 0.9f
    return out
}

/**
 * The late-night lucidity cue: three soft rising tones (400, 600, 800 Hz, ~650 ms in all), the cue of the Northwestern
 * home TLR study (Konkoly et al. 2024), peak 0.9. Distinct from every wake-up tone, so it never means "get up".
 */
fun lucidCue(sampleRate: Int): FloatArray {
    val out = FloatArray((0.65 * sampleRate).toInt())
    for ((i, f) in listOf(400.0, 600.0, 800.0).withIndex()) {
        val from = (i * 0.217 * sampleRate).toInt()
        val n = (0.2 * sampleRate).toInt()
        for (k in 0 until n) {
            val edge = min(1.0, min(k, n - k) / (0.02 * sampleRate)) // 20 ms fades: soft onsets
            out[from + k] = (sin(2 * PI * f * k / sampleRate) * edge * 0.9).toFloat()
        }
    }
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

/** Too early a WBTB cuts the first, deep half of the night (MIN_SLEEP_BEFORE_WBTB_MIN): the watch skips it. */
fun wbtbTooSoon(atMs: Long, nightStartMs: Long) = atMs - nightStartMs < MIN_SLEEP_BEFORE_WBTB_MIN * 60_000L

/**
 * The alarms of [config] still to ring tonight, soonest first, e.g. "the early wake-up at 4:30 AM and the wake-up
 * alarm at 7 AM"; null when none. One more than 12 h away has already rung this night, or belongs to the next one.
 * [wbtbAtMs]: the watch's decided early wake-up; without it, one timed from falling asleep is "by" its set time.
 */
fun alarmsAhead(config: AlarmConfig, nowMs: Long, zone: ZoneId, wbtbAtMs: Long? = null): String? = listOfNotNull(
    when {
        !config.wbtbEnabled -> null
        // The sleep-timed one, or after tonight's rang, tomorrow's (dropped below).
        wbtbAtMs != null -> wbtbAtMs to "the early wake-up at ${clock12(minuteOfDay(wbtbAtMs, zone))}"
        else -> nextAlarmAt(config.wbtbAtMin, nowMs, zone) to
            "the early wake-up ${if (config.wbtbAfterSleepMin != null) "by" else "at"} ${clock12(config.wbtbAtMin)}"
    },
    config.wakeAtMin.takeIf { config.wakeEnabled }
        ?.let { nextAlarmAt(it, nowMs, zone) to "the wake-up alarm at ${clock12(it)}" },
).filter { (at, _) -> at > nowMs && at - nowMs <= 12 * 3_600_000L }
    .sortedBy { it.first }
    .joinToString(" and ") { it.second }
    .ifEmpty { null }

private fun minuteOfDay(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }
