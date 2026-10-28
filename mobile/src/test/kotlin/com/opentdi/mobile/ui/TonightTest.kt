package com.opentdi.mobile.ui

import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.config.validate
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionMode
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.WatchState
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
        assertEquals( // past its earliest time a loop waits for stillness
            "Waiting until you're still",
            statusLines(running(TdiState.AWAKE_INCUBATING, -1_000), NOW, ready = true).first,
        )
    }

    @Test
    fun presets_fillTheTiming_andAreRecognised() {
        val config = SessionConfig(mode = SessionMode.TIMED_CUES)
        assertEquals(TimingPreset.LUCID_NIGHT, presetOf(config)) // the defaults are the lucid night
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
            "First cue after at least 10–15 min and 5 min of lying still, then 4 loops of 3 min drifting off, " +
                "1 min to answer and 7 min back to sleep. Moving restarts the wait.",
            timingSummary(config),
        )
    }

    @Test
    fun checklist_blocksOnVolumeCuesAndTotalSilence() {
        val allGood = checklist(false, DndEffect.PLAYS, true, null, tested = true, watch = WatchState.RECORDING)
        assertTrue(allGood.all { it.ok })
        val bad = checklist(true, DndEffect.TOTAL_SILENCE, false, "No cues", tested = false, watch = null)
        assertTrue(bad.none { it.ok })
        assertEquals(setOf(Check.VOLUME, Check.DND, Check.CUES), bad.filter { it.blocking }.map { it.check }.toSet())
        assertEquals("No cues", bad.single { it.check == Check.CUES }.text)
        assertEquals(DndEffect.TOTAL_SILENCE.message, bad.single { it.check == Check.DND }.text)
    }

    @Test
    fun checklist_onTheWatch_skipsThePhoneSpeaker_andNeedsTheWatch() {
        // The phone muted in Total silence does not matter; an untested, silent watch does.
        val bad = checklist(true, DndEffect.TOTAL_SILENCE, true, null, tested = false, watch = null, onWatch = true)
        assertEquals(listOf(Check.BATTERY, Check.CUES, Check.TEST_CUE, Check.WATCH), bad.map { it.check })
        assertEquals(setOf(Check.TEST_CUE, Check.WATCH), bad.filter { it.blocking && !it.ok }.map { it.check }.toSet())
        val good = checklist(true, DndEffect.TOTAL_SILENCE, true, null, tested = true, watch = WatchState.RECORDING, onWatch = true)
        assertTrue(good.all { it.ok })
        assertEquals("Test cue played on the watch", good.single { it.check == Check.TEST_CUE }.text)
        // A watch waiting for Start session is ready; one with OpenTDI closed is not.
        val waiting = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.WAITING, onWatch = true)
        assertTrue(waiting.single { it.check == Check.WATCH }.ok)
        val closed = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.IDLE, onWatch = true)
        assertTrue(closed.single { it.check == Check.WATCH }.let { !it.ok && it.blocking })
        assertTrue(checklist(false, DndEffect.PLAYS, true, null, true, WatchState.IDLE).single { it.check == Check.WATCH }
            .let { !it.ok && !it.blocking }) // optional when the phone plays the cues
    }

    @Test
    fun checklist_warnsBelowAFullWatchBattery_withoutBlocking() {
        fun battery(pct: Int?) = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.RECORDING, watchBatteryPct = pct)
            .singleOrNull { it.check == Check.WATCH_BATTERY }
        assertNull(battery(null)) // unknown while the watch is not streaming
        assertTrue(battery(100)!!.ok)
        val low = battery(62)!!
        assertTrue(!low.ok && !low.blocking && "62%" in low.text)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
