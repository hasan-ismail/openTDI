package com.opentdi.mobile.ui

import com.opentdi.shared.model.AlarmConfig
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.config.validate
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionMode
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.WatchState
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("Almost ready" to null, statusLines(SessionStatus(), NOW, ready = false))
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
        assertEquals(CUES_DONE to null, statusLines(running(TdiState.SESSION_COMPLETE, cycle = 4), NOW, true))
        val ended = SessionStatus(state = TdiState.SESSION_COMPLETE, completedCycles = 4, maxCycles = 4)
        assertEquals(CUES_DONE to null, statusLines(ended, NOW, ready = true))
        assertEquals(QUICK_TEST_DONE to null, statusLines(ended.copy(quickTest = true), NOW, ready = true))
        assertEquals("0:00", clock(-5_000)) // an overdue step never shows a negative time
        assertEquals( // past its earliest time a loop waits for stillness
            "Waiting until you're still",
            statusLines(running(TdiState.AWAKE_INCUBATING, -1_000), NOW, ready = true).first,
        )
    }

    @Test
    fun testTiming_blocksStart_untilLucidNight() {
        val night = SessionConfig(mode = SessionMode.TIMED_CUES)
        val quick = TimingPreset.QUICK_TEST.applyTo(night)
        assertFalse(testTiming(night))
        assertTrue(testTiming(quick))
        assertFalse(testTiming(TimingPreset.LUCID_NIGHT.applyTo(quick))) // the row's fix
        val row = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.WAITING, firstCueMin = 1).first()
        assertEquals(Check.TIMING, row.check)
        assertTrue(row.blocking && !row.ok)
        assertTrue(checklist(false, DndEffect.PLAYS, true, null, true, WatchState.WAITING).none { it.check == Check.TIMING })
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
        assertEquals(listOf(Check.DND, Check.BATTERY, Check.CUES, Check.TEST_CUE, Check.WATCH), bad.map { it.check })
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
    fun checklist_asksForDoNotDisturb_onEitherRoute_withoutBlocking() {
        for (onWatch in listOf(false, true)) {
            val off = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.RECORDING, onWatch, dndOff = true)
                .single { it.check == Check.DND }
            assertTrue(!off.ok && !off.blocking && "Do Not Disturb" in off.text)
            assertTrue(
                checklist(false, DndEffect.PLAYS, true, null, true, WatchState.RECORDING, onWatch)
                    .single { it.check == Check.DND }.ok,
            )
        }
    }

    @Test
    fun checklist_warnsBelowAFullWatchBattery_withoutBlocking() {
        fun battery(pct: Int?) = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.RECORDING, watchBatteryPct = pct)
            .singleOrNull { it.check == Check.WATCH_BATTERY }
        assertNull(battery(null)) // unknown while the watch is not streaming
        assertTrue(battery(100)!!.ok)
        assertTrue(battery(80)!!.ok) // enough for a night
        val low = battery(62)!!
        assertTrue(!low.ok && !low.blocking && "62%" in low.text)
    }

    @Test
    fun checklist_withAlarmsOn_needsTheWatch() {
        val item = checklist(false, DndEffect.PLAYS, true, null, true, WatchState.IDLE, alarmsOn = true)
            .single { it.check == Check.WATCH }
        assertTrue(!item.ok && item.blocking && "alarms ring on the watch" in item.text)
    }

    @Test
    fun nightPlan_wrapsPastMidnight() {
        val cfg = SessionConfig(alarms = AlarmConfig(wakeEnabled = true, wakeAtMin = 7 * 60))
        val zone = ZoneId.of("America/New_York")
        val at23 = ZonedDateTime.of(2026, 10, 27, 23, 0, 0, 0, zone)
        val plan = nightPlan(cfg, at23)
        assertEquals(PlanMark("wake-up", 480), plan.last()) // 23:00 to 7:00
        assertEquals(PlanMark("Cues", 10, 180), plan.first())
        assertEquals("Cues from about 11:10 PM until 2 AM at the latest · wake-up 7 AM", nightPlanLine(cfg, at23))
        assertEquals(PlanMark("wake-up", 480), nightPlan(cfg, at23.withSecond(30)).last()) // still 7:00, not 6:59
        // DST ends at 2:00 that night: 3 real hours end at 1:00, and 7:00 is 9 hours away.
        val fallBack = ZonedDateTime.of(2026, 10, 31, 23, 0, 0, 0, zone)
        assertEquals(PlanMark("wake-up", 540), nightPlan(cfg, fallBack).last())
        assertEquals("Cues from about 11:10 PM until 1 AM at the latest · wake-up 7 AM", nightPlanLine(cfg, fallBack))
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
