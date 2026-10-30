package com.opentdi.mobile.ui

import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.cues.cueText
import com.opentdi.shared.model.PromptCue
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import java.util.Locale

/** The voice is over, and the night goes on: the same words as the watch's night page. */
const val CUES_DONE = "Sleep well"
const val QUICK_TEST_DONE = "Quick test done"

/**
 * The Tonight card's big line and the voice line under it, for [status] at [nowMs] (elapsedRealtime). [tracking]: the
 * watch tracks a night whose voice is over, maybe in an app process started since (status back to IDLE).
 */
fun statusLines(status: SessionStatus, nowMs: Long, tracking: Boolean = false): Pair<String, String?> {
    val left = status.nextEventAtRealtimeMs?.let { clock(it - nowMs) }
    val headline = if (!status.running) {
        when {
            status.state == TdiState.SESSION_COMPLETE -> if (status.quickTest) QUICK_TEST_DONE else CUES_DONE
            tracking -> CUES_DONE
            else -> "Ready for tonight" // the watch's waiting page says the same
        }
    } else {
        when (status.state) {
            TdiState.IDLE, TdiState.CALIBRATING_BASELINE -> "Starting"
            // Past its earliest time a loop is waiting for the watch to show stillness (StillnessGate).
            TdiState.AWAKE_INCUBATING -> if (status.nextEventAtRealtimeMs?.let { it <= nowMs } == true) {
                "Waiting until you're still"
            } else {
                countdown("Next voice", left) // "Next": the voice already spoke once at Start
            }
            TdiState.HYPNAGOGIA_DETECTED -> countdown("Question", left)
            TdiState.WAKING_USER, TdiState.RE_INCUBATING -> "Speaking…"
            TdiState.RECORDING_DREAM -> left?.let { "Say what's on your mind — $it left" } ?: "Say what's on your mind"
            TdiState.PAUSED -> "Paused"
            TdiState.SESSION_COMPLETE -> if (status.quickTest) QUICK_TEST_DONE else CUES_DONE
        }
    }
    val n = currentCycle(status)
    val shown = status.running && status.state != TdiState.SESSION_COMPLETE && n in 1..status.maxCycles
    return headline to if (shown) "Voice $n of ${status.maxCycles}" else null
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

/** "What to expect" in plain words, with the cue words and timing the session will really use. */
fun whatToExpect(config: SessionConfig): String {
    val still = if (config.timed.waitForStillness) " If you move around, it waits until you're still again." else ""
    return "Lie down and let your mind wander. When you tap Start, and again as you drift off, a soft voice says \"" +
        cueText(PromptCue.INITIAL_INCUBATION, config) + "\" Let the words drift into your thoughts; you don't need " +
        "to wake up. About ${span(config.countdownS)} later it asks \"" + cueText(PromptCue.WAKE_PROMPT, config) +
        "\" Say out loud, in a few words, what was going through your mind. Nothing is recorded: saying it keeps you " +
        "aware. Then it tells you that you can fall back asleep.$still"
}

internal fun span(s: Int) = when {
    s < 60 -> "$s s"
    s % 60 == 0 -> "${s / 60} min"
    else -> "${s / 60} min ${s % 60} s"
}

enum class Check { TIMING, VOLUME, SPEAKER, DND, BATTERY, CUES, TEST_CUE, WATCH, WATCH_APP, WATCH_BATTERY }

/** The watch answered from an app other than the one this phone carries: older (no [WatchStatus.apk]) or newer. */
fun watchAppOutdated(status: WatchStatus?, carriedApk: String?) =
    status != null && carriedApk != null && status.apk != carriedApk

/**
 * Test timing: the first cue would come before anyone could be asleep. The owner's first two nights both ran on
 * leftover Quick test timing (2026-10-27, 2026-10-28), so Start refuses it; Quick test runs once without saving.
 */
fun testTiming(config: SessionConfig) = config.timed.firstSleepMaxMin < REAL_FIRST_CUE_MIN

private const val REAL_FIRST_CUE_MIN = 5 // GUESSED: Lucid night waits 10–15 min, Quick test 1

/** One check: [text] says the problem and shows only when not [ok]; a [blocking] problem keeps Start off. */
data class CheckItem(val check: Check, val ok: Boolean, val text: String, val blocking: Boolean = false)

/**
 * The checks Start runs; Start's popup lists the ones that fail. On the phone route a Do Not Disturb that mutes media
 * blocks Start (§7.7), since the player then skips every cue; the DND state is polled every second while the screen
 * is visible. When the watch plays the cues ([onWatch]) the phone's volume and DND do not matter, and the watch must
 * be waiting or recording (its health service keeps the watch app running all night) and have played a test cue since
 * the app started ([tested]). [watch] is null when the watch does not answer. While a real night is [running], a
 * watch still waiting was never started, so it tracks nothing and rings no alarm.
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
    speakerOff: Boolean = false, // the phone's media output is not its own speaker (earbuds, headphones)
    running: Boolean = false, // a real night's voice is running (not a Quick test)
    watchAppOutdated: Boolean = false, // the watch runs another version of the app ([watchAppOutdated])
): List<CheckItem> = buildList {
    if (firstCueMin != null) {
        val wait = if (firstCueMin == 1) "1 minute" else "$firstCueMin minutes"
        add(
            CheckItem(
                Check.TIMING,
                false,
                "Your timing is set for a test: the first round would come after $wait. Use night timing for a " +
                    "real night.",
                blocking = true,
            ),
        )
    }
    val needed = onWatch || alarmsOn
    val why = when {
        onWatch -> " It plays tonight's voice."
        alarmsOn -> " Your alarms ring on the watch."
        else -> " You can start without it: the phone plays the voice."
    }
    if (!onWatch) {
        add(
            CheckItem(Check.VOLUME, !volumeOff, "Phone volume is off, so you would not hear the voice.", blocking = true),
        )
        // The service refuses to start then, and mid-night the player skips every cue (stream_device_not_speaker).
        if (speakerOff) {
            add(
                CheckItem(
                    Check.SPEAKER,
                    false,
                    "The phone's sound is going to headphones or a speaker, so you wouldn't hear the voice. " +
                        "Disconnect them, or turn on Play the voice on the watch in Settings.",
                    blocking = true,
                ),
            )
        }
    }
    // On either route: the phone's own Do Not Disturb settings matter to its speaker only, but its notifications (and
    // the watch's, which follows the phone's Do Not Disturb when synced) can wake anyone.
    add(
        when {
            !onWatch && dnd != DndEffect.PLAYS -> CheckItem(Check.DND, false, dnd.message.orEmpty(), blocking = true)
            dndOff -> CheckItem(
                Check.DND,
                false,
                "Turn on Do Not Disturb so notifications don't wake you. " +
                    if (onWatch) "The watch still plays the voice and the alarms." else
                        "Allow Media in it so the voice still plays.",
            )
            else -> CheckItem(Check.DND, true, "")
        },
    )
    add(
        CheckItem(Check.BATTERY, batteryUnrestricted, "Let OpenTDI run all night: tap Allow, then choose Unrestricted."),
    )
    add(CheckItem(Check.CUES, cueProblem == null, cueProblem.orEmpty(), blocking = true))
    add(
        if (onWatch) {
            CheckItem(
                Check.TEST_CUE,
                tested,
                "Test the voice on the watch at the volume you'll sleep with. It never gets louder than that.",
                blocking = true,
            )
        } else {
            CheckItem(
                Check.TEST_CUE,
                tested,
                "Test the voice at the volume you'll sleep with. It never gets louder than that.",
            )
        },
    )
    add(
        when (watch) {
            WatchState.RECORDING -> CheckItem(Check.WATCH, true, "", blocking = needed)
            WatchState.WAITING -> CheckItem(
                Check.WATCH,
                !running,
                "The watch isn't tracking your sleep yet. Keep it near the phone with OpenTDI open." +
                    why.takeIf { needed }.orEmpty(),
                blocking = needed,
            )
            WatchState.IDLE -> CheckItem(
                Check.WATCH,
                false,
                "Open OpenTDI on the watch: press the Home button, then tap OpenTDI.$why",
                blocking = needed,
            )
            null -> CheckItem(
                Check.WATCH,
                false,
                "Can't reach the watch. Keep it on your wrist, near the phone. Not installed yet? Settings › More › " +
                    "Install the watch app.$why",
                blocking = needed,
            )
        },
    )
    // Never blocking: most phone updates keep what the watch reads. Hidden while the watch tracks, since installing the
    // update would end tonight's tracking and alarms.
    if (watchAppOutdated && watch != WatchState.RECORDING) {
        add(
            CheckItem(
                Check.WATCH_APP,
                false,
                "The watch app is out of date, so your sleep tracking might not work tonight" +
                    (if (alarmsOn) " and your alarms might not ring." else "."),
            ),
        )
    }
    if (watchBatteryPct != null) {
        add(
            CheckItem(
                Check.WATCH_BATTERY,
                watchBatteryPct >= NIGHT_BATTERY_PCT,
                "Watch battery is at $watchBatteryPct%. A night takes about 70%: charge it now if you can.",
            ),
        )
    }
}

/**
 * A check a Quick test ignores: it runs on its own timing and starts no watch tracking or alarms, so the watch matters
 * only when it plays the voice, and then only if out of reach (its speaker service runs with the app closed).
 */
fun quickTestSkips(item: CheckItem, onWatch: Boolean, watch: WatchState?) = when (item.check) {
    Check.TIMING, Check.WATCH_BATTERY -> true
    Check.WATCH -> !onWatch || watch != null
    Check.WATCH_APP -> !onWatch // a Quick test uses the watch only to play the voice
    Check.DND -> !item.blocking // a daytime test needs no Do Not Disturb, unless it would mute the voice
    else -> false
}

// MEASURED: night 1 (2026-10-27) used about 7.5% an hour, so 9 h takes about 70%; 80 leaves a margin. Lower, Start asks
// first: below 100 every night it would ask every night.
private const val NIGHT_BATTERY_PCT = 80
