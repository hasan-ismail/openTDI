package com.opentdi.mobile.ui

import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.config.validate
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionMode
import com.opentdi.shared.model.TdiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonightTest {
    private fun running(state: TdiState, nextInMs: Long? = null, cycle: Int = 0, completed: Int = 0) = SessionStatus(
        running = true, state = state, cycle = cycle, completedCycles = completed, maxCycles = 4,
        nextEventAtRealtimeMs = nextInMs?.let { NOW + it },
    )

    @Test
    fun statusLines_sayWhatHappensNext() {
        assertEquals("Ready" to null, statusLines(SessionStatus(), NOW, ready = true))
        assertEquals("Not ready yet" to null, statusLines(SessionStatus(), NOW, ready = false))
        assertEquals(
            "Waiting — first cue in 12:30" to "Cycle 1 of 4",
            statusLines(running(TdiState.AWAKE_INCUBATING, 750_000), NOW, ready = true),
        )
        assertEquals(
            "Drifting off — next cue in 3:00" to "Cycle 1 of 4",
            statusLines(running(TdiState.HYPNAGOGIA_DETECTED, 179_001, cycle = 1), NOW, ready = true),
        )
        assertEquals("Cue playing" to "Cycle 2 of 4", statusLines(running(TdiState.WAKING_USER, cycle = 2), NOW, true))
        assertEquals(
            "Listening for your answer — 0:24 left",
            statusLines(running(TdiState.RECORDING_DREAM, 24_000, cycle = 2), NOW, true).first,
        )
        assertEquals(
            "Back to sleep — next cue in 6:10" to "Cycle 3 of 4", // the scheduler still says cycle 2 here
            statusLines(running(TdiState.AWAKE_INCUBATING, 370_000, cycle = 2, completed = 2), NOW, true),
        )
        assertEquals("Session complete" to null, statusLines(running(TdiState.SESSION_COMPLETE, cycle = 4), NOW, true))
        val ended = SessionStatus(state = TdiState.SESSION_COMPLETE, completedCycles = 4, maxCycles = 4)
        assertEquals("Session complete" to null, statusLines(ended, NOW, ready = true))
        assertEquals("0:00", clock(-5_000)) // an overdue step never shows a negative time
    }

    @Test
    fun presets_fillTheTiming_andAreRecognised() {
        val config = SessionConfig(mode = SessionMode.TIMED_CUES)
        assertEquals(TimingPreset.LUCID_NAP, presetOf(config)) // the defaults are the lucid nap
        val quick = TimingPreset.QUICK_TEST.applyTo(config)
        assertEquals(TimingPreset.QUICK_TEST, presetOf(quick))
        assertEquals(emptyList<String>(), quick.validate())
        assertEquals(60, quick.countdownS)
        assertEquals(2, quick.maxCycles)
        assertNull(presetOf(quick.copy(maxCycles = 3)))
        assertEquals(
            "First cue after 1 min, then 2 loops of 1 min drifting off, 30 s to answer and 1 min back to sleep.",
            timingSummary(quick),
        )
        assertEquals(
            "First cue after 10–15 min, then 4 loops of 3 min drifting off, 1 min to answer and 7 min back to sleep.",
            timingSummary(config),
        )
    }

    @Test
    fun checklist_blocksOnVolumeCuesAndTotalSilence() {
        val allGood = checklist(false, DndEffect.PLAYS, true, null, tested = true, watchStreaming = true)
        assertTrue(allGood.all { it.ok })
        val bad = checklist(true, DndEffect.TOTAL_SILENCE, false, "No cues", tested = false, watchStreaming = false)
        assertTrue(bad.none { it.ok })
        assertEquals(setOf(Check.VOLUME, Check.DND, Check.CUES), bad.filter { it.blocking }.map { it.check }.toSet())
        assertEquals("No cues", bad.single { it.check == Check.CUES }.text)
        assertEquals(DndEffect.TOTAL_SILENCE.message, bad.single { it.check == Check.DND }.text)
    }

    @Test
    fun checklist_onTheWatch_skipsThePhoneSpeaker_andNeedsTheWatch() {
        // The phone muted in Total silence does not matter; an untested, silent watch does.
        val bad = checklist(true, DndEffect.TOTAL_SILENCE, true, null, tested = false, watchStreaming = false, onWatch = true)
        assertEquals(listOf(Check.BATTERY, Check.CUES, Check.TEST_CUE, Check.WATCH), bad.map { it.check })
        assertEquals(setOf(Check.TEST_CUE, Check.WATCH), bad.filter { it.blocking && !it.ok }.map { it.check }.toSet())
        val good = checklist(true, DndEffect.TOTAL_SILENCE, true, null, tested = true, watchStreaming = true, onWatch = true)
        assertTrue(good.all { it.ok })
        assertEquals("Test cue played on the watch", good.single { it.check == Check.TEST_CUE }.text)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
