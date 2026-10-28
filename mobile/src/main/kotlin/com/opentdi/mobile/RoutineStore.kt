package com.opentdi.mobile

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/** One step of the bedtime routine: [whenText] relative to bedtime, the step, and why, from lucid_induction.md §7. */
data class RoutineStep(val id: String, val whenText: String, val text: String, val why: String)

/**
 * The bedtime routine (owner, 2026-10-28: "a checklist like watch battery at 100 and a warm shower before bed").
 * Evidence and sources: docs/references/lucid_induction.md §7 (about falling asleep and REM, not lucidity itself).
 * The watch battery is also checked automatically in Before you sleep.
 */
val ROUTINE = listOf(
    RoutineStep(
        "caffeine",
        "9 h before bed",
        "Last coffee or tea",
        "A normal coffee needs about 9 h to stop costing sleep (24 studies).",
    ),
    RoutineStep(
        "alcohol",
        "All evening",
        "Skip alcohol tonight",
        "About 2 drinks delayed and shortened REM, when lucid dreams happen (27 studies).",
    ),
    RoutineStep(
        "workout",
        "4 h before bed",
        "Finish any hard workout",
        "A hard workout 2 h before bed delayed sleep by 36 min.",
    ),
    RoutineStep(
        "meal",
        "3–4 h before bed",
        "Eat a normal, not greasy dinner",
        "Eating within 3 h of bed went with more waking in the night.",
    ),
    RoutineStep(
        "watch",
        "Before your shower",
        "Charge the watch to 100%",
        "It records all night; wear it 30 min off the charger so its skin readings settle.",
    ),
    RoutineStep(
        "dim",
        "3 h before bed",
        "Dim, warm lamps only; no bright ceiling light",
        "Bright light holds back the body's sleepiness signal.",
    ),
    RoutineStep(
        "shower",
        "1–2 h before bed",
        "Warm shower or bath, 40–42 °C, at least 10 min",
        "You fall asleep about 10 min faster; about 90 min before bed works best.",
    ),
    RoutineStep(
        "todo",
        "Before bed",
        "Write tomorrow's to-do list on paper",
        "People fell asleep in 16 instead of 25 min.",
    ),
    RoutineStep(
        "screens",
        "30 min before bed",
        "Screens away (apart from tapping Start)",
        "In a small study, falling asleep went from 31 to 18 min.",
    ),
    RoutineStep(
        "room",
        "Bedtime",
        "Bedroom cool (17–19 °C) and fully dark",
        "A cooler, dark room kept a little more REM.",
    ),
    RoutineStep(
        "winddown",
        "In bed",
        "Slow breathing or relax each muscle in turn",
        "Relaxation helps you fall asleep (sleep-medicine guideline).",
    ),
    RoutineStep(
        "intention",
        "In bed",
        "Tell yourself: next time I'm dreaming, I'll notice I'm dreaming",
        "The core of MILD, the best-tested lucid technique.",
    ),
    RoutineStep(
        "recall",
        "In bed",
        "Plan to report your dream as soon as you wake",
        "People who recall more dreams have more lucid dreams.",
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
