package com.opentdi.shared.sky

import kotlin.math.floor

/** The moon's phase at [epochMs]: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
fun moonPhase(epochMs: Long): Double {
    val days = (epochMs - NEW_MOON_2000_MS) / 86_400_000.0
    val cycles = days / SYNODIC_DAYS
    return cycles - floor(cycles)
}

fun moonPhaseName(phase: Double): String = when {
    phase < 0.03 || phase > 0.97 -> "New moon"
    phase < 0.22 -> "Waxing crescent"
    phase < 0.28 -> "First quarter"
    phase < 0.47 -> "Waxing gibbous"
    phase < 0.53 -> "Full moon"
    phase < 0.72 -> "Waning gibbous"
    phase < 0.78 -> "Last quarter"
    else -> "Waning crescent"
}

private const val NEW_MOON_2000_MS = 947_182_440_000L // 2000-01-06 18:14 UTC, a new moon
private const val SYNODIC_DAYS = 29.530588853
