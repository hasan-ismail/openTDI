package com.opentdi.mobile.cues

import com.opentdi.shared.audio.GuideClip
import com.opentdi.shared.model.SessionConfig

/** Prepare cues renders the guided back-to-sleep and sends it to the watch: WBTB on and the guide chosen. */
fun guideOn(config: SessionConfig) = config.alarms.wbtbEnabled && config.alarms.wbtbGuide

/**
 * The guide is on and the voice [set] is ready, but the watch never confirmed keeping all of this rendering's guide
 * clips ([sentFor]: the [CueSet.fingerprint] of the last full send): it would play an older guide, a mix, or none.
 */
fun guideNotOnWatch(set: CueSet?, config: SessionConfig, sentFor: String?) =
    guideOn(config) && set != null && cueSetProblem(set, config) == null && set.fingerprint != sentFor

const val GUIDE_NOT_ON_WATCH =
    "The sleep guide isn't on the watch yet. Keep the watch near the phone and tap Get the voice ready."

/**
 * The words of the guided back-to-sleep the watch plays after WBTB (owner, 2026-10-28), one text per [GuideClip]; the
 * order, the pauses and the cues between them are the watch's (GuideRoutine). Prepare cues renders them like the cues
 * (the natural voice when chosen, else the phone's own) and sends them to the watch over the Wear Data Layer, which may
 * relay through Google's servers, end-to-end encrypted, when Bluetooth is not available. Sources (sleep_and_dreams.md
 * §8): lying on the side [83][84]; falling asleep during the technique is fine (on 70.7% of occasions, unrelated to
 * lucidity [67]); the MILD recap and intention [66][67]; SSILD after Salvesen's spoken script, shortened [69]; the cue
 * guidance after Carr [63] and Konkoly [70], "sound" for "cue" and "clear and aware" for "lucid"; the first
 * dream sign as an if-then plan limited to dreams (GUESSED wording). No line tells the listener they are dreaming now
 * (docs/references/cue_wording.md). A practice aid: none of it is shown to raise lucid dreaming.
 */
fun guideText(clip: GuideClip, config: SessionConfig): String = when (clip) {
    GuideClip.SETTLE ->
        "Turn off the light and lie on your side. Close your eyes. I'll guide you for about twelve minutes. Then a " +
            "soft sound will play for a few more minutes. If you fall asleep along the way, that's fine."
    GuideClip.MILD -> {
        val sign = config.alarms.dreamSigns.firstOrNull()?.trim()
        // An upsetting dream is not rehearsed: MILD would replay a nightmare just before sleep (journal notes A7).
        "Think back to the dream you just had. If nothing comes back, or the dream was upsetting, pick a calm " +
            "dream you remember. Find one moment in it that was strange. Picture yourself back in that dream. This " +
            "time you notice the strange moment and know it's only a dream. Say silently to yourself: next time " +
            "I'm dreaming, I will remember I'm dreaming." +
            // Only inside a dream: "Next time I see X" taught doubting waking life whenever X happened awake.
            sign?.let { " Next time I dream of $it, I'll know I'm dreaming." }.orEmpty()
    }
    GuideClip.SSILD ->
        "Now move your attention between three things: what you see, what you hear, and what you feel. With " +
            "your eyes, notice the darkness behind your eyelids. Do not try to see anything. With your ears, " +
            "listen to any sound, outside or inside you. With your body, feel the blanket and your breathing. " +
            "Heaviness or tingling is normal. Thoughts will come. Let them go."
    GuideClip.FAST -> "Now do four quick rounds. Spend two or three seconds on each: eyes, ears, body. Start now."
    GuideClip.SLOW -> "Now do four slow rounds. Spend about twenty seconds on each. Start now."
    // The watch's WBTB screen showed the sound's meaning in a box; with the guide it is said here instead.
    GuideClip.CUE_INTRO ->
        "Now you'll hear a soft sound of three notes. Each time it plays, stay still with your eyes closed and " +
            "become aware. If you hear it later in a dream, you'll know it's only a dream. Between the sounds, " +
            "keep moving your attention: eyes, ears, body."
    GuideClip.PROMPT_THOUGHTS ->
        "As you hear the sound, you become clear and aware. Bring your attention to your thoughts. Notice where " +
            "your mind has wandered."
    GuideClip.PROMPT_BODY -> "Now notice your body and how it feels."
    GuideClip.PROMPT_BREATH -> "Notice your breathing."
    // Carr's check, left open: awake, the honest answer is no, so it never suggests the room is unreal.
    GuideClip.PROMPT_AWARE -> "Stay calm and aware. Notice whether anything here is different from being awake."
    GuideClip.CLOSE ->
        "From now on, the sound will play without words. If you hear it in a dream, you'll know it's only a " +
            "dream. You are safe in bed. Say silently to yourself: next time I'm dreaming, I will remember I'm " +
            "dreaming. You can drift off now."
}
