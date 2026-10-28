package com.opentdi.mobile

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/** One step of the bedtime routine: [whenText] relative to bedtime, the step, and why, from lucid_induction.md §6. */
data class RoutineStep(val id: String, val whenText: String, val text: String, val why: String)

/**
 * The bedtime routine (owner, 2026-10-28: "a checklist like watch battery at 100 and a warm shower before bed").
 * Evidence and sources: docs/references/lucid_induction.md §6. The watch battery is checked automatically above.
 */
val ROUTINE = listOf(
    RoutineStep(
        "caffeine",
        "From early afternoon",
        "No more coffee, tea, cola or energy drinks",
        "Caffeine lingers for hours and delays sleep.",
    ),
    RoutineStep(
        "alcohol",
        "Evening",
        "Skip alcohol tonight",
        "Alcohol suppresses REM sleep, when lucid dreams happen.",
    ),
    RoutineStep(
        "meal",
        "2–3 h before bed",
        "Finish a normal-sized dinner",
        "A heavy late meal makes falling asleep harder.",
    ),
    RoutineStep(
        "charge",
        "Before the shower",
        "Put the watch on its charger",
        "It needs a full charge for the whole night.",
    ),
    RoutineStep(
        "shower",
        "1–2 h before bed",
        "Take a warm shower or bath",
        "Warm water then cooling down helps you fall asleep faster.",
    ),
    RoutineStep(
        "dim",
        "1 h before bed",
        "Dim the lights and put screens away",
        "Bright light pushes back your sleepiness.",
    ),
    RoutineStep(
        "cool",
        "Bedtime",
        "Make the bedroom cool, dark and quiet",
        "A cool room suits deep and REM sleep.",
    ),
    RoutineStep(
        "intention",
        "In bed",
        "Tell yourself: tonight I will notice that I'm dreaming",
        "Setting the intention is the core of MILD.",
    ),
)

private const val PREFS = "routine"
private const val KEY_NIGHT = "night"
private const val KEY_DONE = "done"

/** The evening a time belongs to: noon to noon, so an evening and the night after it share one checklist. */
fun nightOf(epochMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMs - 12 * 3_600_000L).atZone(zone).toLocalDate().toString()

/** The steps ticked tonight; a new evening starts empty. */
fun loadRoutine(context: Context, nowMs: Long = System.currentTimeMillis()): Set<String> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getString(KEY_NIGHT, null) != nightOf(nowMs, ZoneId.systemDefault())) return emptySet()
    return prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()
}

fun saveRoutine(context: Context, done: Set<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_NIGHT, nightOf(System.currentTimeMillis(), ZoneId.systemDefault()))
        .putStringSet(KEY_DONE, done)
        .apply()
}
