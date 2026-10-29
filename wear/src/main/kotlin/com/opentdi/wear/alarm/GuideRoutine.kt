package com.opentdi.wear.alarm

import android.content.Context
import com.opentdi.shared.audio.GuideClip
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.WavIo
import com.opentdi.shared.audio.lucidCue
import java.io.File
import java.io.IOException

/** One step of the guided back-to-sleep, [ms] long: a clip the phone sent, a silence, or the lucid cue. */
internal sealed interface GuideStep {
    val ms: Long

    data class Clip(val clip: GuideClip, override val ms: Long) : GuideStep

    data class Silence(override val ms: Long) : GuideStep

    data class Cue(val volume: Float, override val ms: Long) : GuideStep
}

/**
 * The guided back-to-sleep after WBTB (owner, 2026-10-28), about 19 min lying in bed with the eyes closed: settle on
 * the side, a MILD recap, SSILD cycles, 4 cues each followed by spoken lucidity guidance, the MILD intention last, then
 * 6 cues without words: the TLR training shape (sleep_and_dreams.md §5–6 [63][66][67][69][70][83]). A pause that fills
 * a slot (the settle up to 0:30, a cue each minute) is measured from the clips' real lengths [clipMs], so a long clip
 * moves what follows instead of overlapping it. The prompted cues play at [TRAINING_VOLUME], as the WBTB training did;
 * the wordless ones, as sleep comes, at [nightVolume], the level set in bed for the late cues (GUESSED: Carr used one
 * level, set in bed [63]). Timings SOURCED where a source is named, the rest GUESSED.
 */
internal fun guideSteps(clipMs: (GuideClip) -> Long, cueMs: Long, nightVolume: Float): List<GuideStep> = buildList {
    fun say(clip: GuideClip) = clipMs(clip).also { add(GuideStep.Clip(clip, it)) }
    fun pause(ms: Long) {
        add(GuideStep.Silence(ms.coerceAtLeast(MIN_PAUSE_MS)))
    }
    pause(30_000 - say(GuideClip.SETTLE)) // the recap at 0:30
    say(GuideClip.MILD)
    pause(35_000)
    say(GuideClip.SSILD)
    pause(5_000)
    say(GuideClip.FAST) // four fast cycles, 2–3 s per sense [67][69]
    pause(50_000)
    say(GuideClip.SLOW) // four slow cycles, about 20 s per sense: 4 min [67][69]
    pause(240_000)
    say(GuideClip.CUE_INTRO)
    pause(5_000)
    for (n in 1..PROMPTED_CUES) { // a cue each minute, each followed by Carr's guidance [63]
        val from = size
        add(GuideStep.Cue(TRAINING_VOLUME, cueMs))
        pause(2_000)
        PROMPT.forEachIndexed { i, part ->
            if (i > 0) pause(4_000) // GUESSED
            say(part)
        }
        pause(if (n < PROMPTED_CUES) 60_000 - subList(from, size).sumOf { it.ms } else 45_000L)
    }
    say(GuideClip.CLOSE) // the intention last, so sleep follows it [67]
    for (gap in WORDLESS_GAPS_MS) {
        pause(gap)
        add(GuideStep.Cue(nightVolume, cueMs))
    }
}

private const val PROMPTED_CUES = 4 // Konkoly's guided cues [70]
private val PROMPT =
    listOf(GuideClip.PROMPT_THOUGHTS, GuideClip.PROMPT_BODY, GuideClip.PROMPT_BREATH, GuideClip.PROMPT_AWARE)

// Konkoly's first 6 wordless gaps [70]; 6, not 12, as Salvesen played 6 [69]: GUESSED.
private val WORDLESS_GAPS_MS = listOf(45_000L, 70_000L, 55_000L, 65_000L, 70_000L, 80_000L)
private const val MIN_PAUSE_MS = 1_000L // one tick: two sounds never overlap

/**
 * Plays [steps] one after another on the alarms' 1 s tick, each when the one before has ended, and logs w_alarm.csv
 * rows: GUIDE_START, GUIDE_STEP for each clip or cue (its index), then GUIDE_END, or GUIDE_STOPPED after a long press or
 * the back key ([stop]). [done] gets that end or stop time once: the late cues arm then, so lying still through the guide
 * is not taken as falling back asleep.
 */
internal class GuideRoutine(
    private val steps: List<GuideStep>,
    private val play: (GuideStep) -> Unit,
    private val quiet: () -> Unit,
    private val log: (event: String, stillness: Float?, hr: Float?, detail: String?) -> Unit,
    private val done: (atMs: Long) -> Unit,
) {
    private val startsMs = steps.runningFold(0L) { at, step -> at + step.ms } // each step's start, then the end
    private var startMs = 0L
    private var next = 0
    private var over = false

    fun start(nowMs: Long, stillness: Float?, hr: Float?) {
        startMs = nowMs
        log("GUIDE_START", stillness, hr, "steps=${steps.size};total_s=${startsMs.last() / 1000}")
        tick(nowMs, stillness, hr)
    }

    fun tick(nowMs: Long, stillness: Float?, hr: Float?) {
        if (over) return
        while (next < steps.size && nowMs - startMs >= startsMs[next]) {
            val step = steps[next]
            if (step !is GuideStep.Silence) {
                play(step)
                log("GUIDE_STEP", stillness, hr, "step=$next;${if (step is GuideStep.Clip) step.clip.file else "cue"}")
            }
            next++
        }
        if (nowMs - startMs >= startsMs.last()) finish(nowMs, "GUIDE_END", stillness, hr, null)
    }

    /** A long press or the back key: the sound stops at once. */
    fun stop(nowMs: Long, stillness: Float?, hr: Float?) {
        if (over) return
        quiet()
        finish(nowMs, "GUIDE_STOPPED", stillness, hr, "step=${next - 1}")
    }

    private fun finish(nowMs: Long, event: String, stillness: Float?, hr: Float?, detail: String?) {
        over = true
        log(event, stillness, hr, detail)
        done(nowMs)
    }
}

/** Where the watch keeps the guide clip [name] that the phone sent (WristSpeakerService). */
internal fun guideFile(context: Context, name: String) = File(context.filesDir, "guide/$name.wav")

/** True when the phone has sent every guide clip. */
internal fun guideClipsPresent(context: Context) = GuideClip.entries.all { guideFile(context, it.file).isFile }

/**
 * The guide with the phone's clips, played where the late cues play (the speaker, or the watch's earbuds when chosen);
 * null when a clip is missing, damaged or empty, and the night then goes on as without the guide.
 */
internal fun watchGuide(
    context: Context,
    nightVolume: Float,
    log: (event: String, stillness: Float?, hr: Float?, detail: String?) -> Unit,
    done: (atMs: Long) -> Unit,
): GuideRoutine? {
    val clips = try {
        GuideClip.entries.associateWith { clip ->
            WavIo.read(guideFile(context, clip.file).readBytes()).also { require(it.samples.isNotEmpty()) { "empty" } }
        }
    } catch (e: IOException) {
        return null
    } catch (e: IllegalArgumentException) {
        return null
    }
    val sound = OneShots(context)
    val earbuds = WatchOutputs.earbudsChosen(context)
    val cue = WavAudio(lucidCue(CUE_RATE), CUE_RATE)
    return GuideRoutine(
        guideSteps({ lengthMs(clips.getValue(it)) }, lengthMs(cue), nightVolume),
        play = { step ->
            when (step) {
                // Full track volume: the phone faded each clip in at the cue volume (Prepare cues).
                is GuideStep.Clip -> clips.getValue(step.clip).let {
                    sound.play(it.samples, it.sampleRate, 1f, earbuds)
                }
                is GuideStep.Cue -> sound.play(cue.samples, cue.sampleRate, step.volume, earbuds)
                is GuideStep.Silence -> Unit
            }
        },
        quiet = sound::stop,
        log = log,
        done = done,
    )
}

private const val CUE_RATE = 24_000

private fun lengthMs(audio: WavAudio) = audio.samples.size * 1000L / audio.sampleRate
