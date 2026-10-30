package com.opentdi.mobile.cues

import com.opentdi.shared.audio.GuideClip
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTextTest {
    @Test
    fun defaultWording_isLucidity_andIgnoresTheTheme() {
        assertEquals("You are dreaming. You are in control.", cueText(PromptCue.INITIAL_INCUBATION, "a tree"))
        assertEquals(
            "You're falling asleep. You are dreaming now. What do you notice?",
            cueText(PromptCue.WAKE_PROMPT, "a tree"),
        )
        assertEquals(
            "You are dreaming. You are in control. You can fall back asleep now.",
            cueText(PromptCue.RE_INCUBATION, "a fork"),
        )
        assertEquals("That was the last cue for tonight. Sleep well.", cueText(PromptCue.SESSION_END, "a tree"))
        assertEquals(cueText(PromptCue.RE_INCUBATION, "a tree"), cueText(PromptCue.RE_INCUBATION, "a fork"))
    }

    @Test
    fun mitWording_isSection7_1WithTheTheme() {
        assertEquals("Remember to think of a tree.", mitThemeCueText(PromptCue.INITIAL_INCUBATION, "a tree"))
        assertEquals(
            "You're falling asleep. Please tell me, what's going through your mind?",
            mitThemeCueText(PromptCue.WAKE_PROMPT, "a tree"),
        )
        assertEquals(
            "Remember to think of a fork. You can fall back asleep now.",
            mitThemeCueText(PromptCue.RE_INCUBATION, "a fork"),
        )
    }

    @Test
    fun phase1Cues_areThePlayedCuesAndTheSpokenWakeUp() {
        assertEquals(
            listOf(
                PromptCue.INITIAL_INCUBATION, PromptCue.WAKE_PROMPT, PromptCue.RE_INCUBATION, PromptCue.SESSION_END,
                PromptCue.MORNING_WAKE,
            ),
            PHASE1_CUES,
        )
    }

    @Test
    fun spokenWakeUp_saysTheSleepersName_whereverTheWordsSayGoodMorning() {
        val named = SessionConfig(alarms = AlarmConfig(sleeperName = " Hasan "))
        assertEquals(
            "Good morning, Hasan. Keep your eyes closed, and replay your dream before you move.",
            cueText(PromptCue.MORNING_WAKE, named),
        )
        val own = named.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.MORNING_WAKE to "Wake up slowly.")))
        assertEquals("Wake up slowly.", cueText(PromptCue.MORNING_WAKE, own))
        // Lightly edited words keep the name, and a new name asks for Prepare cues again.
        val edited = named.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.MORNING_WAKE to "Good morning. Lie still.")))
        assertEquals("Good morning, Hasan. Lie still.", cueText(PromptCue.MORNING_WAKE, edited))
        val renamed = edited.copy(alarms = AlarmConfig(sleeperName = "Sam"))
        assertNotEquals(cueFingerprint(edited, "v"), cueFingerprint(renamed, "v"))
        // Other cues never say it.
        val ownNight = named.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.WAKE_PROMPT to "Good morning.")))
        assertEquals("Good morning.", cueText(PromptCue.WAKE_PROMPT, ownNight))
        // A new own recording asks for Prepare cues again.
        val recorded = named.copy(audio = AudioConfig(ownVoice = mapOf(PromptCue.WAKE_PROMPT to "abc")))
        assertTrue(cueFingerprint(named, "v") != cueFingerprint(recorded, "v"))
    }

    @Test
    fun guide_saysTheFirstSignAsAnIfThenLine_andJoinsTheFingerprintOnlyWhenOn() {
        val wbtb = SessionConfig(alarms = AlarmConfig(wbtbEnabled = true))
        assertTrue(guideOn(wbtb)) // on by default once WBTB is
        assertFalse(guideOn(SessionConfig()))
        val off = SessionConfig(alarms = AlarmConfig(wbtbEnabled = true, wbtbGuide = false))
        assertFalse(guideOn(off))
        val settle = guideText(GuideClip.SETTLE, wbtb)
        assertTrue(settle.contains("about twelve minutes, then only the soft sound will play for a few more."))
        val generic = guideText(GuideClip.MILD, wbtb)
        assertTrue(generic.endsWith("next time I'm dreaming, I will remember I'm dreaming."))
        val signed = wbtb.copy(alarms = wbtb.alarms.copy(dreamSigns = listOf("my old school", "flying")))
        val ifThen = " Next time I see my old school, I'll know I'm dreaming."
        assertEquals(generic + ifThen, guideText(GuideClip.MILD, signed)) // the first sign only
        // The guide on, or a new first sign, asks for Prepare cues; switched off, the cues stay ready.
        assertNotEquals(cueFingerprint(SessionConfig(), "v"), cueFingerprint(wbtb, "v"))
        assertNotEquals(cueFingerprint(wbtb, "v"), cueFingerprint(signed, "v"))
        assertEquals(cueFingerprint(SessionConfig(), "v"), cueFingerprint(off, "v"))
    }

    @Test
    fun ownWords_replaceTheTemplate_blankOrMissingKeepIt() {
        val own = "You are dreaming. You are in control."
        val config = SessionConfig(
            theme = "a fork",
            audio = AudioConfig(cueTexts = mapOf(PromptCue.INITIAL_INCUBATION to own, PromptCue.WAKE_PROMPT to "  ")),
        )
        assertEquals(own, cueText(PromptCue.INITIAL_INCUBATION, config))
        assertEquals(cueText(PromptCue.WAKE_PROMPT, "a fork"), cueText(PromptCue.WAKE_PROMPT, config))
        assertEquals(cueText(PromptCue.RE_INCUBATION, "a fork"), cueText(PromptCue.RE_INCUBATION, config))
    }

    @Test
    fun ownWords_followTheThemeThroughThePlaceholder() {
        // The field is prefilled with the placeholder, so an edit (here a comma) keeps it.
        val edited = mitThemeCueText(PromptCue.RE_INCUBATION, THEME_PLACEHOLDER).replace(". You", ", you")
        assertEquals("Remember to think of [THEME], you can fall back asleep now.", edited)
        val audio = AudioConfig(cueTexts = mapOf(PromptCue.RE_INCUBATION to edited))
        val config = SessionConfig(theme = "a tree", audio = audio)
        assertEquals(
            "Remember to think of a tree, you can fall back asleep now.",
            cueText(PromptCue.RE_INCUBATION, config),
        )
        assertEquals(
            "Remember to think of a fork, you can fall back asleep now.",
            cueText(PromptCue.RE_INCUBATION, config.copy(theme = "a fork")),
        )
    }

    @Test
    fun phrases_splitAfterPunctuationAndKeepIt() {
        assertEquals(
            listOf("You're falling asleep.", "Please tell me,", "what's going through your mind?"),
            splitPhrases("You're falling asleep. Please tell me, what's going through your mind?"),
        )
        assertEquals(listOf("You are dreaming.", "You are in control."), splitPhrases("You are dreaming. You are in control."))
        assertEquals(listOf("you are dreaming you are in control"), splitPhrases("you are dreaming you are in control"))
        assertEquals(listOf("Wait;", "a tree:", "yes!", "Now?"), splitPhrases("Wait; a tree: yes! Now?"))
        assertEquals(listOf("A tree", "a forest."), splitPhrases(" A tree \n a forest. "))
        // No space after it: a decimal point or a thousands comma is not a break; dots alone are no phrase.
        assertEquals(listOf("Think of 3.5 trees,", "or 1,000..."), splitPhrases("Think of 3.5 trees, or 1,000... . !"))
        assertEquals(emptyList<String>(), splitPhrases("   "))
    }

    @Test
    fun staleCues_areNamedUntilPreparedForExactlyThisConfig() {
        val config = SessionConfig(theme = "a tree")
        val prepared = CueSet("a tree", "engine", "en-us-x-voice", emptyList(), cueFingerprint(config, "en-us-x-voice"))
        assertNull(cueSetProblem(prepared, config))
        assertTrue(cueSetProblem(null, config)!!.startsWith("The voice isn't ready"))
        assertTrue(cueSetProblem(prepared, config.copy(theme = "a fork"))!!.startsWith("The voice was made for \"a tree\""))
        val changes = listOf(
            config.copy(audio = AudioConfig(ttsSpeechRate = 0.8f)),
            config.copy(audio = AudioConfig(phrasePauseMs = 900)),
            config.copy(audio = AudioConfig(fadeInMs = 1000)), // sets the lead before the first phrase
            config.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.SESSION_END to "Wake up gently."))),
        )
        for (changed in changes) {
            assertTrue("$changed", cueSetProblem(prepared, changed)!!.startsWith("Your changes need the voice"))
        }
        // A set from before the fingerprint existed counts as changed; another voice changes the fingerprint.
        assertTrue(cueSetProblem(prepared.copy(fingerprint = ""), config) != null)
        assertNotEquals(cueFingerprint(config, "en-us-x-voice"), cueFingerprint(config, "en-gb-x-voice"))
        // A blank override renders the template's words, so the cues stay ready.
        val same = config.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.WAKE_PROMPT to "")))
        assertNull(cueSetProblem(prepared, same))
    }
}
