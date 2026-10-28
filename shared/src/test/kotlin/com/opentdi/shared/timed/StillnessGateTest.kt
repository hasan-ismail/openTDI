package com.opentdi.shared.timed

import com.opentdi.shared.model.PromptCue.INITIAL_INCUBATION
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionMode
import com.opentdi.shared.model.StateReason
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.TimedCueConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessGateTest {
    // Earliest loop after 1 min; a loop needs 60 s of stillness (120 s without a heart-rate drop).
    private val config = SessionConfig(
        mode = SessionMode.TIMED_CUES,
        timed = TimedCueConfig(firstSleepMinMin = 1, firstSleepMaxMin = 1, stillForS = 60),
    )

    private fun StillnessGate.seconds(from: Int, until: Int, stillness: Float, hr: Float) {
        for (t in from until until) add(t * 1000L, stillness, hr)
    }

    @Test
    fun gate_needsStillnessAndAHeartRateDrop_andIgnoresATwitch() {
        val gate = StillnessGate(config)
        assertFalse(gate.fresh(0))
        gate.seconds(0, 180, 1f, 70f) // the baseline: 70 bpm
        assertFalse(gate.hrDropped()!!)
        gate.seconds(180, 185, 0.1f, 70f) // a major movement: 3+ of 5 seconds not still
        assertTrue(gate.moving(184_000))
        assertEquals(0, gate.stillForMs(184_000))
        gate.seconds(185, 250, 1f, 64f) // still again, 64 <= 70 * 0.93
        gate.add(250_000, 0.1f, 64f) // one twitch is not a movement
        gate.seconds(251, 260, 1f, 64f)
        assertFalse(gate.moving(259_000))
        assertTrue(gate.hrDropped()!!)
        assertTrue(gate.settled(259_000))
        assertFalse(gate.fresh(259_000 + 121_000)) // the watch went quiet: nothing is settled any more
        assertFalse(gate.settled(259_000 + 121_000))
    }

    @Test
    fun gate_withoutAHeartRateDrop_waitsTwiceAsLong() {
        val gate = StillnessGate(config)
        gate.seconds(0, 200, 1f, 70f)
        assertFalse(gate.settled(110_000)) // no drop: 110 s of stillness is not yet twice 60 s
        assertTrue(gate.settled(199_000))
    }

    @Test
    fun scheduler_waitsUntilStill_andMovementCutsTheDwell() {
        val gate = StillnessGate(config)
        val s = TimedCueScheduler("any", config, gate)
        s.start()
        s.onCueFinished(INITIAL_INCUBATION, 4_000)
        for (t in 0 until 90) { // tossing and turning past the earliest time (60 s)
            gate.add(t * 1000L, 0.1f, 72f)
            s.onTick(t * 1000L)
        }
        assertEquals(TdiState.AWAKE_INCUBATING, s.state)
        var startedAt: Long? = null
        for (t in 90 until 200) { // then lying still with a lower heart rate
            gate.add(t * 1000L, 1f, 60f)
            val started = s.onTick(t * 1000L).any {
                it is TimedEvent.StateChanged && it.to == TdiState.HYPNAGOGIA_DETECTED && it.reason == StateReason.ONSET
            }
            if (started) {
                startedAt = t * 1000L
                break
            }
        }
        assertEquals(152_000L, startedAt) // stillness counts from 92 s, the first second without a major movement
        for (t in 153 until 156) gate.add(t * 1000L, 0.1f, 70f)
        val cut = s.onTick(156_000).filterIsInstance<TimedEvent.StateChanged>().single()
        assertEquals(TdiState.AWAKE_INCUBATING to StateReason.MAJOR_MOVEMENT, cut.to to cut.reason)
    }

    @Test
    fun scheduler_withAQuietWatch_fallsBackToTheTimer_onlyWhenAllowed() {
        val s = TimedCueScheduler("any", config, StillnessGate(config))
        s.start()
        s.onCueFinished(INITIAL_INCUBATION, 4_000)
        val dwell = s.onTick(60_000).filterIsInstance<TimedEvent.StateChanged>().single()
        assertEquals(StateReason.FALLBACK_TO_TIMED, dwell.reason)

        val strict = config.copy(timed = config.timed.copy(autoFallbackToTimed = false))
        val waiting = TimedCueScheduler("any", strict, StillnessGate(strict))
        waiting.start()
        waiting.onCueFinished(INITIAL_INCUBATION, 4_000)
        assertNull(waiting.onTick(600_000).filterIsInstance<TimedEvent.StateChanged>().firstOrNull())
    }
}
