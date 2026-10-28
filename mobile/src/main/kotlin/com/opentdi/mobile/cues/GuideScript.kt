package com.opentdi.mobile.cues

import com.opentdi.shared.audio.GuideClip
import com.opentdi.shared.model.SessionConfig

/** Prepare cues renders the guided back-to-sleep and sends it to the watch: WBTB on and the guide chosen. */
fun guideOn(config: SessionConfig) = config.alarms.wbtbEnabled && config.alarms.wbtbGuide

/**
 * The words of the guided back-to-sleep the watch plays after WBTB (owner, 2026-10-28), one text per [GuideClip]; the
 * order, the pauses and the cues between them are the watch's (GuideRoutine). Prepare cues renders them like the cues
 * (the natural voice when chosen, else the phone's own) and sends them to the watch over the Wear Data Layer, which may
 * relay through Google's servers, end-to-end encrypted, when Bluetooth is not available. Sources (sleep_and_dreams.md
 * §8): lying on the side [83][84]; falling asleep during the technique is fine (on 70.7% of occasions, unrelated to
 * lucidity [67]); the MILD recap and intention [66][67]; SSILD after Salvesen's spoken script, shortened [69]; the cue
 * guidance after Carr [63] and Konkoly [70], "sound" for "cue"; the first dream sign as an if-then plan (GUESSED
 * wording). A practice aid: none of it is shown to raise lucid dreaming.
 */
fun guideText(clip: GuideClip, config: SessionConfig): String = when (clip) {
    GuideClip.SETTLE ->
        "Lie down on your side, eyes closed, light off. Get comfortable. I'll guide you for about twelve minutes, " +
            "then only the soft sound will play for a few more. If you fall asleep along the way, that's fine."
    GuideClip.MILD -> {
        val sign = config.alarms.dreamSigns.firstOrNull()?.trim()
        "Bring back the dream you just recorded, and its odd moment. Picture yourself back in it, noticing: this is " +
            "a dream. Say it silently: next time I'm dreaming, I will remember I'm dreaming." +
            sign?.let { " Next time I see $it, I'll know I'm dreaming." }.orEmpty()
    }
    GuideClip.SSILD ->
        "Now cycles of attention: sight, sound, body. Sight: the darkness behind your eyelids; don't try to see " +
            "anything. Sound: whatever you can hear, outside or inside you. Body: the weight of the blanket, your " +
            "breath, any tingling or heaviness; just watch it calmly. Thoughts will come; let them go."
    GuideClip.FAST -> "Four quick cycles: two or three seconds on each. Sight, sound, body. Start now."
    GuideClip.SLOW -> "Now slower: about twenty seconds on each. Four rounds. Start now."
    // The watch's WBTB screen showed the sound's meaning in a box; with the guide it is said here instead.
    GuideClip.CUE_INTRO ->
        "Now we'll pair your sound with this calm awareness. Tonight, when you hear this soft three-note sound, you " +
            "are dreaming. When you hear it, stay still, eyes closed, and become lucid. Between sounds, keep cycling."
    GuideClip.PROMPT_THOUGHTS ->
        "As you notice the sound, you become lucid. Bring your attention to your thoughts; notice where your mind " +
            "has wandered."
    GuideClip.PROMPT_BODY -> "Now observe your body, sensations and feelings."
    GuideClip.PROMPT_BREATH -> "Observe your breathing."
    GuideClip.PROMPT_AWARE ->
        "Remain critically aware, lucid, and notice how this experience is in any way different from normal waking."
    GuideClip.CLOSE ->
        "From now on the sound comes without words. Each time you hear it, become lucid. Next time I'm dreaming, I " +
            "will remember I'm dreaming. You can drift off now."
}
