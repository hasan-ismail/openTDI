package com.opentdi.mobile.cues

import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTextTest {
    @Test
    fun wording_isSection7_1WithTheTheme() {
        assertEquals("Remember to think of a tree.", cueText(PromptCue.INITIAL_INCUBATION, "a tree"))
        assertEquals(
            "You're falling asleep. Please tell me, what's going through your mind?",
            cueText(PromptCue.WAKE_PROMPT, "a tree"),
        )
        assertEquals(
            "Remember to think of a fork. You can fall back asleep now.",
            cueText(PromptCue.RE_INCUBATION, "a fork"),
        )
        assertEquals("The session is over. You can wake up now.", cueText(PromptCue.SESSION_END, "a tree"))
    }

    @Test
    fun phase1Cues_areTheFourPlayedCues() {
        assertEquals(
            listOf(PromptCue.INITIAL_INCUBATION, PromptCue.WAKE_PROMPT, PromptCue.RE_INCUBATION, PromptCue.SESSION_END),
            PHASE1_CUES,
        )
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
        assertEquals("Remember to think of a fork. You can fall back asleep now.", cueText(PromptCue.RE_INCUBATION, config))
    }

    @Test
    fun ownWords_followTheThemeThroughThePlaceholder() {
        // The field is prefilled with the placeholder, so an edit (here a comma) keeps it.
        val edited = cueText(PromptCue.RE_INCUBATION, THEME_PLACEHOLDER).replace(". You", ", you")
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
        assertTrue(cueSetProblem(null, config)!!.startsWith("No cues"))
        assertTrue(cueSetProblem(prepared, config.copy(theme = "a fork"))!!.startsWith("The cues are for \"a tree\""))
        val changes = listOf(
            config.copy(audio = AudioConfig(ttsSpeechRate = 0.8f)),
            config.copy(audio = AudioConfig(phrasePauseMs = 900)),
            config.copy(audio = AudioConfig(fadeInMs = 1000)), // sets the lead before the first phrase
            config.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.SESSION_END to "Wake up gently."))),
        )
        for (changed in changes) {
            assertTrue("$changed", cueSetProblem(prepared, changed)!!.startsWith("The cue words,"))
        }
        // A set from before the fingerprint existed counts as changed; another voice changes the fingerprint.
        assertTrue(cueSetProblem(prepared.copy(fingerprint = ""), config) != null)
        assertNotEquals(cueFingerprint(config, "en-us-x-voice"), cueFingerprint(config, "en-gb-x-voice"))
        // A blank override renders the template's words, so the cues stay ready.
        val same = config.copy(audio = AudioConfig(cueTexts = mapOf(PromptCue.WAKE_PROMPT to "")))
        assertNull(cueSetProblem(prepared, same))
    }
}
