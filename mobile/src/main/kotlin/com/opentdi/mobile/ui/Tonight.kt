package com.opentdi.mobile.ui

import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.cues.cueText
import com.opentdi.shared.model.PromptCue
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.WatchState
import java.util.Locale

/** Only the falling-asleep cue loops are over, not the night: "Session complete" read as the watch's end too. */
const val CUES_DONE = "Cues done for tonight"
const val QUICK_TEST_DONE = "Quick test done"

/** The Tonight card's big line and the cycle line under it, for [status] at [nowMs] (elapsedRealtime). */
fun statusLines(status: SessionStatus, nowMs: Long, ready: Boolean): Pair<String, String?> {
    val left = status.nextEventAtRealtimeMs?.let { clock(it - nowMs) }
    val headline = if (!status.running) {
        when {
            status.state == TdiState.SESSION_COMPLETE -> if (status.quickTest) QUICK_TEST_DONE else CUES_DONE
            ready -> "Ready"
            else -> "Almost ready"
        }
    } else {
        when (status.state) {
            TdiState.IDLE, TdiState.CALIBRATING_BASELINE -> "Starting"
            // Past its earliest time a loop is waiting for the watch to show stillness (StillnessGate).
            TdiState.AWAKE_INCUBATING -> if (status.nextEventAtRealtimeMs?.let { it <= nowMs } == true) {
                "Waiting until you're still"
            } else {
                countdown(if (status.completedCycles == 0) "Waiting — first cue" else "Back to sleep — next cue", left)
            }
            TdiState.HYPNAGOGIA_DETECTED -> countdown("Drifting off — next cue", left)
            TdiState.WAKING_USER, TdiState.RE_INCUBATING -> "Cue playing"
            TdiState.RECORDING_DREAM -> left?.let { "Listening for your answer — $it left" } ?: "Listening for your answer"
            TdiState.PAUSED -> "Paused"
            TdiState.SESSION_COMPLETE -> if (status.quickTest) QUICK_TEST_DONE else CUES_DONE
        }
    }
    val n = currentCycle(status)
    val shown = status.running && status.state != TdiState.SESSION_COMPLETE && n in 1..status.maxCycles
    return headline to if (shown) "Cycle $n of ${status.maxCycles}" else null
}

/** The cycle the session is in or waiting for; between cycles the scheduler still reports the last one's number. */
fun currentCycle(status: SessionStatus) =
    if (status.state == TdiState.AWAKE_INCUBATING) status.completedCycles + 1 else status.cycle

private fun countdown(what: String, left: String?) = if (left == null) what else "$what in $left"

fun clock(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

/** A one-tap timing; the Timing settings show which one the config matches, or "Custom". */
enum class TimingPreset(
    val label: String,
    val firstMin: Int,
    val firstMax: Int,
    val dwellS: Int,
    val reportS: Int,
    val backToSleepS: Int,
    val loops: Int,
    val waitForStillness: Boolean,
    val sessionMin: Int,
) {
    QUICK_TEST("Quick test", 1, 1, 60, 30, 60, 2, waitForStillness = false, sessionMin = 15),
    LUCID_NIGHT("Lucid night", 10, 15, 180, 60, 420, 4, waitForStillness = true, sessionMin = 180),
    ;

    fun applyTo(config: SessionConfig) = config.copy(
        countdownS = dwellS,
        maxCycles = loops,
        sessionMaxMin = sessionMin,
        timed = config.timed.copy(
            firstSleepMinMin = firstMin,
            firstSleepMaxMin = firstMax,
            phase1ReportWindowS = reportS,
            betweenSleepS = backToSleepS,
            waitForStillness = waitForStillness,
        ),
    )
}

fun presetOf(config: SessionConfig): TimingPreset? = TimingPreset.entries.firstOrNull { it.applyTo(config) == config }

/** The timing of [config] in one plain sentence. */
fun timingSummary(config: SessionConfig): String {
    val t = config.timed
    val first = if (t.firstSleepMinMin == t.firstSleepMaxMin) "${t.firstSleepMinMin} min" else
        "${t.firstSleepMinMin}–${t.firstSleepMaxMin} min"
    val loops = if (config.maxCycles == 1) "1 loop" else "${config.maxCycles} loops"
    val timing = "$loops of ${span(config.countdownS)} drifting off, ${span(t.phase1ReportWindowS)} to answer and " +
        "${span(t.betweenSleepS)} back to sleep"
    return if (t.waitForStillness) {
        "First cue after at least $first and ${span(t.stillForS)} of lying still, then $timing. Moving restarts the wait."
    } else {
        "First cue after $first, then $timing."
    }
}

/** "What to expect" in plain words, with the cue words and timing the session will really use. */
fun whatToExpect(config: SessionConfig): String {
    val still = if (config.timed.waitForStillness) " If you move around, it waits until you're still again." else ""
    return "Lie down and let your mind wander. When it's time, a soft voice says \"" +
        cueText(PromptCue.INITIAL_INCUBATION, config) + "\" Let the words drift into your thoughts; you don't need " +
        "to wake up. About ${span(config.countdownS)} later it asks \"" + cueText(PromptCue.WAKE_PROMPT, config) +
        "\" Say out loud, in a few words, what was going through your mind. Then it tells you that you can fall " +
        "back asleep.$still"
}

private fun span(s: Int) = when {
    s < 60 -> "$s s"
    s % 60 == 0 -> "${s / 60} min"
    else -> "${s / 60} min ${s % 60} s"
}

enum class Check { TIMING, VOLUME, DND, BATTERY, CUES, TEST_CUE, WATCH, WATCH_BATTERY }

/**
 * Test timing: the first cue would come before anyone could be asleep. The owner's first two nights both ran on
 * leftover Quick test timing (2026-10-27, 2026-10-28), so Start refuses it; Quick test runs once without saving.
 */
fun testTiming(config: SessionConfig) = config.timed.firstSleepMaxMin < REAL_FIRST_CUE_MIN

private const val REAL_FIRST_CUE_MIN = 5 // GUESSED: Lucid night waits 10–15 min, Quick test 1

/** One pre-flight line: [ok], or a problem said in [text]; a [blocking] problem keeps Start off. */
data class CheckItem(val check: Check, val ok: Boolean, val text: String, val blocking: Boolean = false)

/**
 * The Tonight checklist. Total silence blocks Start (§7.7) so the red line explains why; the DND state is polled
 * every second while the screen is visible, and Start checks it again when tapped. When the watch plays the cues
 * ([onWatch]) the phone's volume and DND do not matter, and the watch must be waiting or recording (its health service
 * keeps the watch app running all night) and have played a test cue since the app started ([tested]). [watch] is null
 * when the watch does not answer.
 */
fun checklist(
    volumeOff: Boolean,
    dnd: DndEffect,
    batteryUnrestricted: Boolean,
    cueProblem: String?,
    tested: Boolean,
    watch: WatchState?,
    onWatch: Boolean = false,
    watchBatteryPct: Int? = null, // null while the watch does not answer
    alarmsOn: Boolean = false, // a wake-up or WBTB alarm, which rings on the watch
    firstCueMin: Int? = null, // set when the timing is test timing ([testTiming])
    dndOff: Boolean = false, // Do Not Disturb is off, so notifications can wake the sleeper
): List<CheckItem> = buildList {
    if (firstCueMin != null) {
        val wait = if (firstCueMin == 1) "1 minute" else "$firstCueMin minutes"
        add(
            CheckItem(
                Check.TIMING,
                false,
                "This is test timing: the first cue comes after $wait and the cues end soon after. Use Lucid " +
                    "night for a real night.",
                blocking = true,
            ),
        )
    }
    val needed = onWatch || alarmsOn
    val why = when {
        onWatch -> " It plays tonight's cues."
        alarmsOn -> " Your alarms ring on the watch."
        else -> " Optional: the phone plays the cues."
    }
    if (!onWatch) {
        add(
            CheckItem(
                Check.VOLUME,
                !volumeOff,
                if (volumeOff) "Phone volume is off, so you would not hear the cues." else "Phone volume is on",
                blocking = true,
            ),
        )
    }
    // On either route: the phone's own Do Not Disturb settings matter to its speaker only, but its notifications (and
    // the watch's, which follows the phone's Do Not Disturb when synced) can wake anyone.
    add(
        when {
            !onWatch && dnd != DndEffect.PLAYS ->
                CheckItem(Check.DND, false, dnd.message.orEmpty(), blocking = dnd == DndEffect.TOTAL_SILENCE)
            dndOff -> CheckItem(
                Check.DND,
                false,
                "Turn on Do Not Disturb so notifications don't wake you. Cues and alarms still play.",
            )
            else -> CheckItem(Check.DND, true, if (onWatch) "Do Not Disturb is on" else "Do Not Disturb lets cues play")
        },
    )
    add(
        CheckItem(
            Check.BATTERY,
            batteryUnrestricted,
            if (batteryUnrestricted) "Android will let the session run all night" else
                "Allow unrestricted battery so Android doesn't stop the session while you sleep.",
        ),
    )
    add(CheckItem(Check.CUES, cueProblem == null, cueProblem ?: "Voice cues are ready", blocking = true))
    add(
        when {
            !onWatch -> CheckItem(
                Check.TEST_CUE,
                tested,
                if (tested) "Test cue played at bedtime volume" else
                    "Play a test cue at the volume you'll sleep with. Cues never get louder than that.",
            )
            tested -> CheckItem(Check.TEST_CUE, true, "Test cue played on the watch", blocking = true)
            else -> CheckItem(
                Check.TEST_CUE,
                false,
                "Play a test cue on the watch at the volume you'll sleep with. Cues never get louder than that.",
                blocking = true,
            )
        },
    )
    add(
        when (watch) {
            WatchState.RECORDING -> CheckItem(Check.WATCH, true, "Watch is recording", blocking = needed)
            WatchState.WAITING ->
                CheckItem(Check.WATCH, true, "Watch is ready: Start the night starts it too", blocking = needed)
            WatchState.IDLE -> CheckItem(
                Check.WATCH,
                false,
                "Open OpenTDI on your watch so it records with the session.$why",
                blocking = needed,
            )
            null -> CheckItem(Check.WATCH, false, "Can't reach the watch. Open OpenTDI on it.$why", blocking = needed)
        },
    )
    if (watchBatteryPct != null) {
        add(
            CheckItem(
                Check.WATCH_BATTERY,
                watchBatteryPct >= NIGHT_BATTERY_PCT,
                if (watchBatteryPct >= NIGHT_BATTERY_PCT) "Watch battery is at $watchBatteryPct%" else
                    "Watch battery is at $watchBatteryPct%. A night of recording takes about 70%, so charge it first.",
            ),
        )
    }
}

// MEASURED: night 1 (2026-10-27) used about 7.5% an hour, so 9 h takes about 70%; 80 leaves a margin. Lower, Start asks
// first: below 100 every night it would ask every night.
private const val NIGHT_BATTERY_PCT = 80
