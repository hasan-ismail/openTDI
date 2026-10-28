package com.opentdi.wear.alarm

import com.opentdi.shared.audio.GuideClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRoutineTest {
    // The clips as rendered at about 2 words a second, with the phrase pauses and the lead before the first word.
    private val clipMs = mapOf(
        GuideClip.SETTLE to 25_000L,
        GuideClip.MILD to 25_000L,
        GuideClip.SSILD to 35_000L,
        GuideClip.FAST to 10_000L,
        GuideClip.SLOW to 9_000L,
        GuideClip.CUE_INTRO to 20_000L,
        GuideClip.PROMPT_THOUGHTS to 9_000L,
        GuideClip.PROMPT_BODY to 5_000L,
        GuideClip.PROMPT_BREATH to 3_000L,
        GuideClip.PROMPT_AWARE to 10_000L,
        GuideClip.CLOSE to 14_000L,
    )
    private val night = 0.15f
    private val steps = guideSteps(clipMs::getValue, cueMs = 650, nightVolume = night)
    private val startsMs = steps.runningFold(0L) { at, step -> at + step.ms } // each step's start, then the end

    private fun startOf(clip: GuideClip) = startsMs[steps.indexOfFirst { it is GuideStep.Clip && it.clip == clip }]

    private class Run(steps: List<GuideStep>) {
        val events = mutableListOf<String>()
        val played = mutableListOf<GuideStep>()
        var quiets = 0
        val done = mutableListOf<Long>()
        val guide = GuideRoutine(steps, { played += it }, { quiets++ }, { event, _, _, _ -> events += event }) {
            done += it
        }
    }

    @Test
    fun steps_followTheScript() {
        val prompt =
            listOf(GuideClip.PROMPT_THOUGHTS, GuideClip.PROMPT_BODY, GuideClip.PROMPT_BREATH, GuideClip.PROMPT_AWARE)
        val intro = listOf(GuideClip.SETTLE, GuideClip.MILD, GuideClip.SSILD, GuideClip.FAST, GuideClip.SLOW)
        assertEquals(
            intro + GuideClip.CUE_INTRO + List(4) { prompt }.flatten() + GuideClip.CLOSE,
            steps.filterIsInstance<GuideStep.Clip>().map { it.clip },
        )
        // 4 cues at the training level, each just before its guidance, then 6 wordless ones at the night level.
        val volumes = steps.filterIsInstance<GuideStep.Cue>().map { it.volume }
        assertEquals(List(4) { TRAINING_VOLUME } + List(6) { night }, volumes)
        val sounds = steps.filter { it !is GuideStep.Silence }
        for (i in sounds.indices) {
            if (sounds[i] is GuideStep.Cue && i < sounds.lastIndex && sounds[i + 1] is GuideStep.Clip) {
                assertEquals(GuideClip.PROMPT_THOUGHTS, (sounds[i + 1] as GuideStep.Clip).clip)
            }
        }
        assertTrue(sounds.takeLast(6).all { it is GuideStep.Cue })
        // A pause of at least one tick between any two sounds, so the 1 s tick never lets them overlap.
        assertTrue(steps.zipWithNext().none { (a, b) -> a !is GuideStep.Silence && b !is GuideStep.Silence })
        assertTrue(steps.filterIsInstance<GuideStep.Silence>().all { it.ms >= 1_000 })
    }

    @Test
    fun timeline_recapAt30s_aCueEachMinute_konkolysGaps_about19MinutesInAll() {
        assertEquals(30_000L, startOf(GuideClip.MILD))
        val cueStarts = steps.indices.filter { steps[it] is GuideStep.Cue }.map { startsMs[it] }
        assertEquals(listOf(60_000L, 60_000L, 60_000L), cueStarts.take(4).zipWithNext { a, b -> b - a })
        val close = steps.indexOfFirst { it is GuideStep.Clip && it.clip == GuideClip.CLOSE }
        val gaps = (close + 1 until steps.size).filter { steps[it] is GuideStep.Cue }.map { steps[it - 1].ms }
        assertEquals(listOf(45_000L, 70_000L, 55_000L, 65_000L, 70_000L, 80_000L), gaps)
        assertEquals(19 * 60_000.0, startsMs.last().toDouble(), 30_000.0) // the Settings text says about 19 min

        // A slow voice: each clip ends before the next sound starts, and the rest just moves later.
        val slow = guideSteps({ 70_000L }, 650, night)
        assertTrue(slow.filterIsInstance<GuideStep.Silence>().all { it.ms >= 1_000 })
        assertEquals(71_000L, slow[0].ms + slow[1].ms) // the settle overran 0:30: one tick of quiet, then the recap
    }

    @Test
    fun run_playsEachSoundOnce_thenEndsAndArmsOnce() {
        val run = Run(steps)
        run.guide.start(0, 0.9f, 60f)
        var t = 0L
        while (run.done.isEmpty() && t < 30 * 60_000L) {
            t += 1000
            run.guide.tick(t, 0.9f, 60f)
        }
        assertEquals(steps.filter { it !is GuideStep.Silence }, run.played)
        assertEquals(listOf("GUIDE_START") + List(run.played.size) { "GUIDE_STEP" } + "GUIDE_END", run.events)
        assertEquals(listOf(t), run.done)
        assertTrue(t - startsMs.last() in 0L until 1000L) // ends on the first tick after the last cue
        run.guide.tick(t + 1000, null, null)
        run.guide.stop(t + 2000, null, null)
        assertEquals(listOf(t), run.done) // nothing after the end
        assertEquals(0, run.quiets)
    }

    @Test
    fun stop_atAnyStep_logsGuideStoppedAndNothingMore() {
        for (k in steps.indices) {
            val run = Run(steps)
            run.guide.start(0, null, null)
            run.guide.tick(startsMs[k], null, null)
            val events = run.events.size
            val played = run.played.size
            run.guide.stop(startsMs[k] + 400, 0.9f, 60f)
            run.guide.tick(startsMs.last() + 60_000, null, null)
            run.guide.stop(startsMs.last() + 61_000, null, null)
            assertEquals("step $k", listOf("GUIDE_STOPPED"), run.events.drop(events))
            assertEquals("step $k", played, run.played.size)
            assertEquals("step $k", listOf(startsMs[k] + 400), run.done) // the late cues arm right away
            assertEquals("step $k", 1, run.quiets)
            assertFalse("step $k", "GUIDE_END" in run.events)
        }
    }
}
