package com.opentdi.mobile

import android.content.Context

/** One step of the bedtime routine: [whenText] relative to bedtime, the step, and why, from lucid_induction.md §7. */
data class RoutineStep(val whenText: String, val text: String, val why: String)

/**
 * The bedtime routine (owner, 2026-10-28: "a checklist like watch battery at 100 and a warm shower before bed"), shown
 * as tips since 2026-10-29 ("the checklist is so ugly ... make it a one time popup").
 * Evidence and sources: docs/references/lucid_induction.md §7 (about falling asleep and REM, not lucidity itself).
 * The watch battery is also checked when Start is tapped.
 */
val ROUTINE = listOf(
    RoutineStep(
        "9 h before bed",
        "Last coffee or tea",
        "A normal coffee needs about 9 h to stop costing sleep (24 studies).",
    ),
    RoutineStep(
        "All evening",
        "Skip alcohol tonight",
        "About 2 drinks delayed and shortened REM, when lucid dreams happen (27 studies).",
    ),
    RoutineStep(
        "4 h before bed",
        "Finish any hard workout",
        "A hard workout 2 h before bed delayed sleep by 36 min.",
    ),
    RoutineStep(
        "3–4 h before bed",
        "Eat a normal, not greasy dinner",
        "Eating within 3 h of bed went with more waking in the night.",
    ),
    RoutineStep(
        "Before your shower",
        "Charge the watch to 100%",
        "It tracks your sleep all night; wear it 30 min off the charger so its skin readings settle.",
    ),
    RoutineStep(
        "Before bed",
        "Keep the watch's Always On Display on",
        "OpenTDI then stays on the watch screen all night, dimmed, instead of going back to the watch face " +
            "(Settings > Display on the watch).",
    ),
    RoutineStep(
        "3 h before bed",
        "Dim, warm lamps only; no bright ceiling light",
        "Bright light holds back the body's sleepiness signal.",
    ),
    RoutineStep(
        "1–2 h before bed",
        "Warm shower or bath, 40–42 °C, at least 10 min",
        "You fall asleep about 10 min faster; about 90 min before bed works best.",
    ),
    RoutineStep(
        "Before bed",
        "Write tomorrow's to-do list on paper",
        "People fell asleep in 16 instead of 25 min.",
    ),
    RoutineStep(
        "30 min before bed",
        "Screens away (apart from tapping Start)",
        "In a small study, falling asleep went from 31 to 18 min.",
    ),
    RoutineStep(
        "Bedtime",
        "Bedroom cool (17–19 °C) and fully dark",
        "A cooler, dark room kept a little more REM.",
    ),
    RoutineStep(
        "In bed",
        "Slow breathing or relax each muscle in turn",
        "Relaxation helps you fall asleep (sleep-medicine guideline).",
    ),
    RoutineStep(
        "In bed",
        "Tell yourself: next time I'm dreaming, I'll notice I'm dreaming",
        "The core of MILD, the best-tested lucid technique.",
    ),
    RoutineStep(
        "In bed",
        "Plan to answer the dream questions as soon as you wake",
        "People who recall more dreams have more lucid dreams.",
    ),
)

private const val PREFS = "routine"
private const val KEY_TIPS_SEEN = "tipsSeen"

/** True once the bedtime tips were shown: they open by themselves only the first time. */
fun tipsSeen(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TIPS_SEEN, false)

fun markTipsSeen(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TIPS_SEEN, true).apply()
}
