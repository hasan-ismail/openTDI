package com.opentdi.shared.timed

import com.opentdi.shared.model.CycleOutcome
import com.opentdi.shared.model.EndAlarm
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionEndReason
import com.opentdi.shared.model.StateReason
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.TdiState.AWAKE_INCUBATING
import com.opentdi.shared.model.TdiState.HYPNAGOGIA_DETECTED
import com.opentdi.shared.model.TdiState.IDLE
import com.opentdi.shared.model.TdiState.PAUSED
import com.opentdi.shared.model.TdiState.RECORDING_DREAM
import com.opentdi.shared.model.TdiState.RE_INCUBATING
import com.opentdi.shared.model.TdiState.SESSION_COMPLETE
import com.opentdi.shared.model.TdiState.WAKING_USER
import kotlin.random.Random

/** What the [TimedCueScheduler] tells the phone service, in order. [atMs] is the `elapsedMs` of the call. */
sealed interface TimedEvent {
    val atMs: Long

    data class StateChanged(
        override val atMs: Long,
        val from: TdiState,
        val to: TdiState,
        val reason: StateReason,
        val cycle: Int,
    ) : TimedEvent

    /** Play [cue] now and report its end with [TimedCueScheduler.onCueFinished] or [TimedCueScheduler.onCueFailed]. */
    data class PlayCue(override val atMs: Long, val cue: PromptCue, val cycle: Int) : TimedEvent

    /** Phase 1 report window: nothing is recorded; the scheduler moves on by itself after [durationMs]. */
    data class StartReportWindow(override val atMs: Long, val cycle: Int, val durationMs: Long) : TimedEvent

    data class CycleCompleted(override val atMs: Long, val cycle: Int, val outcome: CycleOutcome) : TimedEvent

    data class SessionCompleted(override val atMs: Long, val reason: SessionEndReason) : TimedEvent
}

/**
 * The sensor-free TIMED_CUES driver of §4.1–4.3, Phase 1 variant, for a config that passes validate(). Pure and not
 * thread-safe. `elapsedMs` is the phone's elapsed-realtime since session start. Every timer counts from when the
 * previous step actually ended; time spent PAUSED does not count (sessionMaxMin included); a cycle cut by a pause is
 * dropped. With a [gate] a loop also waits until the sleeper is still (StillnessGate), movement cuts the dwell, and a
 * watch that stops streaming falls back to the timer alone when autoFallbackToTimed is on.
 */
class TimedCueScheduler(
    sessionId: String,
    private val config: SessionConfig,
    private val gate: StillnessGate? = null,
) {
    /** The first sleep latency L: a uniform whole minute in [firstSleepMinMin, firstSleepMaxMin], seeded by the ID. */
    val firstLatencyMin: Int =
        Random(sessionId.hashCode()).nextInt(config.timed.firstSleepMinMin, config.timed.firstSleepMaxMin + 1)

    var state: TdiState = IDLE
        private set

    /** The cycle in progress or the last one, 0 before the first dwell; an aborted cycle keeps its number. */
    var cycle: Int = 0
        private set
    var completedCycles: Int = 0
        private set

    /** When the next timed step is due, for a UI countdown; null while waiting for a cue to end, or when idle. */
    val nextEventAtMs: Long?
        get() = when (state) {
            AWAKE_INCUBATING -> minOf(nextDwellAtMs, sessionEndsAtMs)
            HYPNAGOGIA_DETECTED, RECORDING_DREAM -> stepEndsAtMs
            else -> null
        }

    private var nextDwellAtMs = 0L
    private var stepEndsAtMs = 0L // dwell end, or report window end
    private var sessionEndsAtMs = 0L
    private var pausedAtMs = 0L
    private var pausedFrom = IDLE
    private var now = 0L
    private val out = mutableListOf<TimedEvent>()

    /** Row 2: the session starts at elapsedMs 0 in AWAKE_INCUBATING with the incubation cue. */
    fun start(): List<TimedEvent> = call(0) {
        check(state == IDLE) { "TimedCueScheduler.start() called twice" }
        sessionEndsAtMs = config.sessionMaxMin * 60_000L
        nextDwellAtMs = firstLatencyMin * 60_000L
        move(AWAKE_INCUBATING, StateReason.SESSION_START)
        play(PromptCue.INITIAL_INCUBATION)
    }

    fun onTick(elapsedMs: Long): List<TimedEvent> = call(elapsedMs) { advance() }

    fun onCueFinished(cue: PromptCue, elapsedMs: Long): List<TimedEvent> = call(elapsedMs) {
        if (state == WAKING_USER && cue == PromptCue.WAKE_PROMPT) {
            val reportMs = config.timed.phase1ReportWindowS * 1000L
            move(RECORDING_DREAM, StateReason.PHONE_STAGE) // row 11; the report window is timed from the prompt's end
            out += TimedEvent.StartReportWindow(now, cycle, reportMs)
            stepEndsAtMs = now + reportMs
        } else if (state == RE_INCUBATING && cue == PromptCue.RE_INCUBATION) {
            completeCycle(CycleOutcome.COMPLETED)
        }
        advance()
    }

    /** A failed WAKE_PROMPT aborts the cycle (row 15); any other failed cue is skipped and the timeline goes on. */
    fun onCueFailed(cue: PromptCue, elapsedMs: Long): List<TimedEvent> = call(elapsedMs) {
        if (state == WAKING_USER && cue == PromptCue.WAKE_PROMPT) {
            completeCycle(CycleOutcome.ABORTED_AUDIO)
        } else if (state == RE_INCUBATING && cue == PromptCue.RE_INCUBATION) {
            completeCycle(CycleOutcome.COMPLETED)
        }
        advance()
    }

    /** Row 19; the service stops any playing cue when it sees PAUSED. */
    fun pause(elapsedMs: Long): List<TimedEvent> = call(elapsedMs) {
        if (state in ACTIVE) {
            pausedFrom = state
            pausedAtMs = now
            move(PAUSED, StateReason.PAUSED)
        }
    }

    /** Row 20: back to AWAKE_INCUBATING; after a cycle cut by the pause, the next dwell is betweenSleepS away. */
    fun resume(elapsedMs: Long): List<TimedEvent> = call(elapsedMs) {
        if (state == PAUSED) {
            val pausedMs = now - pausedAtMs
            sessionEndsAtMs += pausedMs
            nextDwellAtMs = if (pausedFrom == AWAKE_INCUBATING) nextDwellAtMs + pausedMs else now + betweenMs()
            move(AWAKE_INCUBATING, StateReason.RESUMED)
            advance()
        }
    }

    /** Row 21: ends the session at once, without the SESSION_END cue. */
    fun abort(reason: SessionEndReason, elapsedMs: Long): List<TimedEvent> = call(elapsedMs) {
        if (state != IDLE && state != SESSION_COMPLETE) end(reason, chime = false)
    }

    private fun advance() {
        when (state) {
            AWAKE_INCUBATING -> if (now >= sessionEndsAtMs) {
                end(SessionEndReason.TIME_LIMIT, chime = true) // row 22, checked only between cycles
            } else if (now >= nextDwellAtMs) {
                val reason = when {
                    gate == null -> StateReason.TIMED_DWELL
                    gate.fresh(now) -> if (gate.settled(now)) StateReason.ONSET else null
                    config.timed.autoFallbackToTimed -> StateReason.FALLBACK_TO_TIMED
                    else -> null
                } ?: return
                cycle = completedCycles + 1
                move(HYPNAGOGIA_DETECTED, reason) // row T1
                play(PromptCue.INITIAL_INCUBATION)
                stepEndsAtMs = now + config.countdownS * 1000L
            }
            HYPNAGOGIA_DETECTED -> if (gate?.moving(now) == true) {
                move(AWAKE_INCUBATING, StateReason.MAJOR_MOVEMENT) // §8.10: the cycle starts over once still again
                nextDwellAtMs = now + config.detection.refractoryS * 1000L
            } else if (now >= stepEndsAtMs) {
                move(WAKING_USER, StateReason.PHONE_STAGE) // row T2
                play(PromptCue.WAKE_PROMPT)
            }
            RECORDING_DREAM -> if (now >= stepEndsAtMs) {
                move(RE_INCUBATING, StateReason.PHONE_STAGE) // row 12
                play(PromptCue.RE_INCUBATION)
            }
            else -> Unit
        }
    }

    private fun completeCycle(outcome: CycleOutcome) {
        out += TimedEvent.CycleCompleted(now, cycle, outcome)
        if (outcome == CycleOutcome.COMPLETED) completedCycles++
        if (completedCycles >= config.maxCycles) {
            end(SessionEndReason.MAX_CYCLES, chime = true) // row 14
        } else {
            move( // rows 13, 15
                AWAKE_INCUBATING,
                if (outcome == CycleOutcome.COMPLETED) StateReason.CYCLE_COMPLETED else StateReason.CYCLE_ABORTED,
            )
            nextDwellAtMs = now + betweenMs()
        }
    }

    private fun end(reason: SessionEndReason, chime: Boolean) {
        val stateReason = when (reason) {
            SessionEndReason.MAX_CYCLES -> StateReason.MAX_CYCLES
            SessionEndReason.TIME_LIMIT -> StateReason.TIME_LIMIT
            else -> StateReason.USER_ABORT // no StateReason for SENSOR_FAILURE / ERROR; SessionCompleted keeps it
        }
        move(SESSION_COMPLETE, stateReason)
        out += TimedEvent.SessionCompleted(now, reason)
        if (chime && config.audio.endAlarm == EndAlarm.CHIME) play(PromptCue.SESSION_END)
    }

    private fun betweenMs() = config.timed.betweenSleepS * 1000L

    private fun move(to: TdiState, reason: StateReason) {
        out += TimedEvent.StateChanged(now, state, to, reason, cycle)
        state = to
    }

    private fun play(cue: PromptCue) {
        out += TimedEvent.PlayCue(now, cue, cycle)
    }

    private inline fun call(elapsedMs: Long, block: () -> Unit): List<TimedEvent> {
        now = elapsedMs
        block()
        return out.toList().also { out.clear() }
    }

    private companion object {
        val ACTIVE = setOf(AWAKE_INCUBATING, HYPNAGOGIA_DETECTED, WAKING_USER, RECORDING_DREAM, RE_INCUBATING)
    }
}
