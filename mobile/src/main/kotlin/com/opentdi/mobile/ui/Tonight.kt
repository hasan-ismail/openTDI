package com.opentdi.mobile.ui

import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TdiState
import java.util.Locale

/** The Tonight card's big line and the cycle line under it, for [status] at [nowMs] (elapsedRealtime). */
fun statusLines(status: SessionStatus, nowMs: Long, ready: Boolean): Pair<String, String?> {
    val left = status.nextEventAtRealtimeMs?.let { clock(it - nowMs) }
    val headline = if (!status.running) {
        when {
            status.state == TdiState.SESSION_COMPLETE -> "Session complete"
            ready -> "Ready"
            else -> "Not ready yet"
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
            TdiState.SESSION_COMPLETE -> "Session complete"
        }
    }
    // Between cycles the scheduler still reports the last cycle's number.
    val n = if (status.state == TdiState.AWAKE_INCUBATING) status.completedCycles + 1 else status.cycle
    val shown = status.running && status.state != TdiState.SESSION_COMPLETE && n in 1..status.maxCycles
    return headline to if (shown) "Cycle $n of ${status.maxCycles}" else null
}

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
) {
    QUICK_TEST("Quick test", 1, 1, 60, 30, 60, 2, waitForStillness = false),
    LUCID_NAP("Lucid nap", 10, 15, 180, 60, 420, 4, waitForStillness = true),
    ;

    fun applyTo(config: SessionConfig) = config.copy(
        countdownS = dwellS,
        maxCycles = loops,
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

private fun span(s: Int) = when {
    s < 60 -> "$s s"
    s % 60 == 0 -> "${s / 60} min"
    else -> "${s / 60} min ${s % 60} s"
}

enum class Check { VOLUME, DND, BATTERY, CUES, TEST_CUE, WATCH, WATCH_BATTERY }

/** One pre-flight line: [ok], or a problem said in [text]; a [blocking] problem keeps Start off. */
data class CheckItem(val check: Check, val ok: Boolean, val text: String, val blocking: Boolean = false)

/**
 * The Tonight checklist. Total silence blocks Start (§7.7) so the red line explains why; the DND state is polled
 * every second while the screen is visible, and Start checks it again when tapped. When the watch plays the cues
 * ([onWatch]) the phone's volume and DND do not matter, and the watch must be recording (its health service keeps the
 * watch app running all night) and have played a test cue since the app started ([tested]).
 */
fun checklist(
    volumeOff: Boolean,
    dnd: DndEffect,
    batteryUnrestricted: Boolean,
    cueProblem: String?,
    tested: Boolean,
    watchStreaming: Boolean,
    onWatch: Boolean = false,
    watchBatteryPct: Int? = null, // known only while the watch streams
): List<CheckItem> = buildList {
    if (!onWatch) {
        add(
            CheckItem(
                Check.VOLUME,
                !volumeOff,
                if (volumeOff) "Media volume is off, so cues would be silent." else "Media volume is on",
                blocking = true,
            ),
        )
        add(
            CheckItem(
                Check.DND,
                dnd == DndEffect.PLAYS,
                dnd.message ?: "Do Not Disturb lets cues play",
                blocking = dnd == DndEffect.TOTAL_SILENCE,
            ),
        )
    }
    add(
        CheckItem(
            Check.BATTERY,
            batteryUnrestricted,
            if (batteryUnrestricted) "Battery use is unrestricted" else
                "Allow unrestricted battery so Android doesn't stop the session while you sleep.",
        ),
    )
    add(CheckItem(Check.CUES, cueProblem == null, cueProblem ?: "Cues are prepared", blocking = true))
    add(
        when {
            !onWatch -> CheckItem(
                Check.TEST_CUE,
                tested,
                if (tested) "Test cue played at bedtime volume" else
                    "Play a test cue once at your bedtime volume: cues never play louder than that.",
            )
            tested -> CheckItem(Check.TEST_CUE, true, "Test cue played on the watch", blocking = true)
            else -> CheckItem(
                Check.TEST_CUE,
                false,
                "Play a test cue on the watch at your bedtime volume: cues never play louder than that.",
                blocking = true,
            )
        },
    )
    add(
        when {
            watchStreaming -> CheckItem(Check.WATCH, true, "Watch is streaming", blocking = onWatch)
            onWatch -> CheckItem(
                Check.WATCH,
                false,
                "Start recording on the watch: it plays tonight's cues.",
                blocking = true,
            )
            else -> CheckItem(Check.WATCH, false, "Watch not streaming (optional): start recording on the watch.")
        },
    )
    if (watchBatteryPct != null) {
        add(
            CheckItem(
                Check.WATCH_BATTERY,
                watchBatteryPct >= 100,
                if (watchBatteryPct >= 100) "Watch battery is full" else
                    "Watch battery is at $watchBatteryPct%: charge it to 100% before a night. Recording uses about " +
                        "8% an hour, so a full charge lasts about 12 hours.",
            ),
        )
    }
}
