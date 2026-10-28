package com.opentdi.shared.timed

import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.EndAlarm
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.PromptCue.INITIAL_INCUBATION
import com.opentdi.shared.model.PromptCue.RE_INCUBATION
import com.opentdi.shared.model.PromptCue.SESSION_END
import com.opentdi.shared.model.PromptCue.WAKE_PROMPT
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionEndReason
import com.opentdi.shared.model.SessionMode
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.TimedCueConfig
import com.opentdi.shared.model.newSessionId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The DETECTION → TIMED_CUES fallback test of §16 (timed_fallbackMidSession) is Phase 2, like the fallback itself.
class TimedCueSchedulerTest {
    private val timedConfig = SessionConfig(mode = SessionMode.TIMED_CUES)
    private val cueMs =
        mapOf(INITIAL_INCUBATION to 4_000L, WAKE_PROMPT to 6_000L, RE_INCUBATION to 7_000L, SESSION_END to 9_000L)

    private fun sessionIds() =
        (0 until 10_000).asSequence().map { newSessionId(1_793_000_000_000L + it * 61_000L, Random(it)) }

    private fun latencyOf(sessionId: String) = TimedCueScheduler(sessionId, timedConfig).firstLatencyMin

    private fun scheduler(latencyMin: Int = 12) =
        TimedCueScheduler(sessionIds().first { latencyOf(it) == latencyMin }, timedConfig)

    // A config whose L is fixed, so any session ID will do.
    private fun fixedLatency(latencyMin: Int, sessionMaxMin: Int) = timedConfig.copy(
        sessionMaxMin = sessionMaxMin,
        timed = TimedCueConfig(firstSleepMinMin = latencyMin, firstSleepMaxMin = latencyMin),
    )

    private fun line(e: TimedEvent) = when (e) {
        is TimedEvent.StateChanged -> "${e.atMs} ${e.from}->${e.to} ${e.reason} c${e.cycle}"
        is TimedEvent.PlayCue -> "${e.atMs} play ${e.cue} c${e.cycle}"
        is TimedEvent.StartReportWindow -> "${e.atMs} report ${e.durationMs} c${e.cycle}"
        is TimedEvent.CycleCompleted -> "${e.atMs} cycle ${e.outcome} c${e.cycle}"
        is TimedEvent.SessionCompleted -> "${e.atMs} end ${e.reason}"
    }

    /** Plays every cue for its duration in [cueMs] and ticks at each deadline until the session ends. */
    private fun runToEnd(s: TimedCueScheduler): List<String> {
        val lines = mutableListOf<String>()
        val playing = mutableListOf<Pair<Long, PromptCue>>()
        fun take(events: List<TimedEvent>) = events.forEach { e ->
            lines += line(e)
            if (e is TimedEvent.PlayCue) playing += (e.atMs + cueMs.getValue(e.cue)) to e.cue
        }
        take(s.start())
        while (s.state != TdiState.SESSION_COMPLETE) {
            val cue = playing.minByOrNull { it.first }
            val tick = s.nextEventAtMs
            if (cue != null && (tick == null || cue.first <= tick)) {
                playing.remove(cue)
                take(s.onCueFinished(cue.second, cue.first))
            } else {
                take(s.onTick(checkNotNull(tick) { "stuck in ${s.state}" }))
            }
        }
        return lines
    }

    private fun cycleLines(k: Int, dwellAt: Long, last: Boolean): List<String> {
        val wake = dwellAt + 180_000
        val report = wake + 6_000
        val reInc = report + 60_000
        val end = reInc + 7_000
        return listOf(
            "$dwellAt AWAKE_INCUBATING->HYPNAGOGIA_DETECTED TIMED_DWELL c$k",
            "$dwellAt play INITIAL_INCUBATION c$k",
            "$wake HYPNAGOGIA_DETECTED->WAKING_USER PHONE_STAGE c$k",
            "$wake play WAKE_PROMPT c$k",
            "$report WAKING_USER->RECORDING_DREAM PHONE_STAGE c$k",
            "$report report 60000 c$k",
            "$reInc RECORDING_DREAM->RE_INCUBATING PHONE_STAGE c$k",
            "$reInc play RE_INCUBATION c$k",
            "$end cycle COMPLETED c$k",
        ) + if (last) {
            listOf(
                "$end RE_INCUBATING->SESSION_COMPLETE MAX_CYCLES c$k",
                "$end end MAX_CYCLES",
                "$end play SESSION_END c$k",
            )
        } else {
            listOf("$end RE_INCUBATING->AWAKE_INCUBATING CYCLE_COMPLETED c$k")
        }
    }

    @Test
    fun timed_latencyInclusive10to15() {
        val latencies = sessionIds().map(::latencyOf).toList()
        assertEquals(10_000, latencies.size)
        assertEquals((10..15).toSet(), latencies.toSet())
        assertEquals(latencies, sessionIds().map(::latencyOf).toList()) // seeded by the session ID
    }

    @Test
    fun timed_defaultTimeline() {
        val s = scheduler(latencyMin = 12)
        val lines = runToEnd(s)
        // Dwell at L = 720 s, wake at 900 s; a cycle takes 180 + 6 + 60 + 7 = 253 s, and 420 s pass between cycles.
        val expected = listOf("0 IDLE->AWAKE_INCUBATING SESSION_START c0", "0 play INITIAL_INCUBATION c0") +
            (1..4).flatMap { k -> cycleLines(k, dwellAt = 720_000L + (k - 1) * 673_000L, last = k == 4) }
        assertEquals(expected, lines)
        assertTrue(lines.contains("720000 play INITIAL_INCUBATION c1"))
        assertTrue(lines.contains("900000 play WAKE_PROMPT c1"))
        assertEquals("2992000 play SESSION_END c4", lines.last())
        assertEquals(4, s.completedCycles)
        assertNull(s.nextEventAtMs)
    }

    @Test
    fun nextEventAt_followsTheTimeline() {
        val s = scheduler()
        assertNull(s.nextEventAtMs)
        s.start()
        assertEquals(720_000L, s.nextEventAtMs)
        s.onTick(720_000)
        assertEquals(900_000L, s.nextEventAtMs)
        s.onTick(900_000)
        assertNull(s.nextEventAtMs) // wake prompt playing
        s.onCueFinished(WAKE_PROMPT, 905_500)
        assertEquals(965_500L, s.nextEventAtMs) // report window from the prompt's real end
        s.onTick(965_500)
        assertNull(s.nextEventAtMs)
    }

    @Test
    fun earlyTicksAndOtherCues_changeNothing() {
        val s = scheduler()
        s.start()
        assertEquals(emptyList<TimedEvent>(), s.onTick(719_999))
        assertEquals(emptyList<TimedEvent>(), s.onCueFinished(INITIAL_INCUBATION, 4_000))
        assertEquals(emptyList<TimedEvent>(), s.onCueFailed(INITIAL_INCUBATION, 4_000)) // skipped, timeline unchanged
        assertEquals(720_000L, s.nextEventAtMs)
    }

    @Test
    fun pauseWhileWaiting_shiftsTheNextDwell() {
        val s = scheduler()
        s.start()
        assertEquals(listOf("300000 AWAKE_INCUBATING->PAUSED PAUSED c0"), s.pause(300_000).map(::line))
        assertNull(s.nextEventAtMs)
        assertEquals(emptyList<TimedEvent>(), s.onTick(400_000))
        assertEquals(emptyList<TimedEvent>(), s.pause(410_000))
        assertEquals(listOf("420000 PAUSED->AWAKE_INCUBATING RESUMED c0"), s.resume(420_000).map(::line))
        assertEquals(840_000L, s.nextEventAtMs) // 720 s + 120 s paused
        assertEquals(emptyList<TimedEvent>(), s.onTick(839_999))
        assertEquals(
            listOf("840000 AWAKE_INCUBATING->HYPNAGOGIA_DETECTED TIMED_DWELL c1", "840000 play INITIAL_INCUBATION c1"),
            s.onTick(840_000).map(::line),
        )
    }

    @Test
    fun pauseMidCycle_dropsTheCycle() {
        val s = scheduler()
        s.start()
        s.onTick(720_000)
        s.onTick(900_000)
        assertEquals(TdiState.WAKING_USER, s.state)
        s.pause(903_000)
        assertEquals(emptyList<TimedEvent>(), s.onCueFailed(WAKE_PROMPT, 903_000)) // the service stopped the prompt
        assertEquals(listOf("1000000 PAUSED->AWAKE_INCUBATING RESUMED c1"), s.resume(1_000_000).map(::line))
        assertEquals(0, s.completedCycles)
        assertEquals(1_420_000L, s.nextEventAtMs)
        assertEquals("1420000 AWAKE_INCUBATING->HYPNAGOGIA_DETECTED TIMED_DWELL c1", line(s.onTick(1_420_000).first()))
    }

    @Test
    fun pausedTime_doesNotCountTowardsTheSessionLimit() {
        val s = TimedCueScheduler("any", fixedLatency(latencyMin = 15, sessionMaxMin = 15))
        s.start()
        s.pause(100_000)
        s.resume(160_000)
        assertEquals(960_000L, s.nextEventAtMs)
        assertEquals(emptyList<TimedEvent>(), s.onTick(900_000))
        val end = s.onTick(960_000).filterIsInstance<TimedEvent.StateChanged>().single()
        assertEquals(TdiState.SESSION_COMPLETE, end.to)
    }

    @Test
    fun abort_fromEveryState() {
        val reach: List<(TimedCueScheduler) -> Unit> = listOf(
            {},
            { it.onTick(720_000) },
            { it.onTick(720_000); it.onTick(900_000) },
            { it.onTick(720_000); it.onTick(900_000); it.onCueFinished(WAKE_PROMPT, 906_000) },
            { it.onTick(720_000); it.onTick(900_000); it.onCueFinished(WAKE_PROMPT, 906_000); it.onTick(966_000) },
            { it.onTick(720_000); it.pause(800_000) },
        )
        val states = reach.map { go ->
            val s = scheduler()
            s.start()
            go(s)
            val from = s.state
            assertEquals(
                listOf("990000 $from->SESSION_COMPLETE USER_ABORT c${s.cycle}", "990000 end USER"),
                s.abort(SessionEndReason.USER, 990_000).map(::line),
            )
            assertEquals(emptyList<TimedEvent>(), s.onTick(5_000_000))
            assertEquals(emptyList<TimedEvent>(), s.onCueFinished(RE_INCUBATION, 5_000_000))
            assertEquals(emptyList<TimedEvent>(), s.abort(SessionEndReason.USER, 5_000_000))
            assertNull(s.nextEventAtMs)
            from
        }
        assertEquals(
            listOf(
                TdiState.AWAKE_INCUBATING, TdiState.HYPNAGOGIA_DETECTED, TdiState.WAKING_USER,
                TdiState.RECORDING_DREAM, TdiState.RE_INCUBATING, TdiState.PAUSED,
            ),
            states,
        )
        assertEquals(emptyList<TimedEvent>(), scheduler().abort(SessionEndReason.USER, 0)) // never started
    }

    @Test
    fun failedWakePrompt_abortsTheCycleUncounted() {
        val s = scheduler()
        s.start()
        s.onTick(720_000)
        s.onTick(900_000)
        assertEquals(
            listOf("900500 cycle ABORTED_AUDIO c1", "900500 WAKING_USER->AWAKE_INCUBATING CYCLE_ABORTED c1"),
            s.onCueFailed(WAKE_PROMPT, 900_500).map(::line),
        )
        assertEquals(0, s.completedCycles)
        assertEquals(1_320_500L, s.nextEventAtMs) // betweenSleepS after the failure
        assertEquals("1320500 AWAKE_INCUBATING->HYPNAGOGIA_DETECTED TIMED_DWELL c1", line(s.onTick(1_320_500).first()))
    }

    @Test
    fun failedReIncubation_isSkippedAndTheCycleCounts() {
        val s = scheduler()
        s.start()
        s.onTick(720_000)
        s.onTick(900_000)
        s.onCueFinished(WAKE_PROMPT, 906_000)
        s.onTick(966_000)
        assertEquals(
            listOf("966100 cycle COMPLETED c1", "966100 RE_INCUBATING->AWAKE_INCUBATING CYCLE_COMPLETED c1"),
            s.onCueFailed(RE_INCUBATION, 966_100).map(::line),
        )
        assertEquals(1, s.completedCycles)
        assertEquals(1_386_100L, s.nextEventAtMs)
    }

    @Test
    fun timeLimit_betweenCycles_winsOverADueDwell() {
        val config = fixedLatency(latencyMin = 15, sessionMaxMin = 15) // the dwell and the limit both fall at 900 s
        val s = TimedCueScheduler("any", config)
        s.start()
        assertEquals(
            listOf(
                "900000 AWAKE_INCUBATING->SESSION_COMPLETE TIME_LIMIT c0",
                "900000 end TIME_LIMIT",
                "900000 play SESSION_END c0",
            ),
            s.onTick(900_000).map(::line),
        )
        val silent = TimedCueScheduler("any", config.copy(audio = AudioConfig(endAlarm = EndAlarm.NONE)))
        silent.start()
        assertEquals(
            listOf("900000 AWAKE_INCUBATING->SESSION_COMPLETE TIME_LIMIT c0", "900000 end TIME_LIMIT"),
            silent.onTick(900_000).map(::line),
        )
    }

    @Test
    fun timeLimit_duringACycle_finishesTheCycleFirst() {
        val s = TimedCueScheduler("any", fixedLatency(latencyMin = 12, sessionMaxMin = 15))
        s.start()
        s.onTick(720_000)
        assertEquals("900000 play WAKE_PROMPT c1", line(s.onTick(900_000).last())) // the limit (900 s) does not stop it
        s.onCueFinished(WAKE_PROMPT, 906_000)
        s.onTick(966_000)
        assertEquals(
            listOf(
                "973000 cycle COMPLETED c1",
                "973000 RE_INCUBATING->AWAKE_INCUBATING CYCLE_COMPLETED c1",
                "973000 AWAKE_INCUBATING->SESSION_COMPLETE TIME_LIMIT c1",
                "973000 end TIME_LIMIT",
                "973000 play SESSION_END c1",
            ),
            s.onCueFinished(RE_INCUBATION, 973_000).map(::line),
        )
    }

    @Test
    fun staleCueCallbacks_areIgnored() {
        val s = scheduler()
        s.start()
        s.onTick(720_000)
        assertEquals(emptyList<TimedEvent>(), s.onCueFinished(WAKE_PROMPT, 730_000)) // not playing
        s.onTick(900_000)
        s.onCueFinished(WAKE_PROMPT, 906_000)
        assertEquals(emptyList<TimedEvent>(), s.onCueFinished(WAKE_PROMPT, 906_001)) // duplicate
        assertEquals(TdiState.RECORDING_DREAM, s.state)
    }

    @Test
    fun maxCyclesOne_endsAfterTheFirstCycle() {
        val s = TimedCueScheduler(sessionIds().first { latencyOf(it) == 12 }, timedConfig.copy(maxCycles = 1))
        assertEquals(cycleLines(1, 720_000L, last = true), runToEnd(s).drop(2))
    }
}
