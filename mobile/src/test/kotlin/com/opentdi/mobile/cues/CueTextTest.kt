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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTextTest {
    @Test
    fun defaultWording_isLucidity_andIgnoresTheTheme() {
        assertEquals(
            "You are safe in bed. If you hear this voice in a dream, you'll know it's only a dream.",
            cueText(PromptCue.INITIAL_INCUBATION),
        )
        assertEquals(
            "You were falling asleep. What was going through your mind? Was it a dream?",
            cueText(PromptCue.WAKE_PROMPT),
        )
        assertEquals(
            "You can fall back asleep now. When you dream, you'll know it's only a dream.",
            cueText(PromptCue.RE_INCUBATION),
        )
        assertEquals("That's all for now. Sleep well.", cueText(PromptCue.SESSION_END))
        assertEquals(
            cueText(PromptCue.RE_INCUBATION, SessionConfig(theme = "a tree")),
            cueText(PromptCue.RE_INCUBATION, SessionConfig(theme = "a fork")),
        )
    }

    @Test
    fun mitWording_isSection7_1WithTheTheme() {
        assertEquals("Remember to think of a tree.", mitThemeCueText(PromptCue.INITIAL_INCUBATION, "a tree"))
        assertEquals(
            "You're falling asleep. Please tell me what's going through your mind.",
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
            "Good morning, Hasan. It's time to wake up. Lie still with your eyes closed and think back to your dreams.",
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
        assertTrue(settle.contains("about twelve minutes. Then a soft sound will play for a few more minutes."))
        val generic = guideText(GuideClip.MILD, wbtb)
        assertTrue(generic.endsWith("next time I'm dreaming, I will remember I'm dreaming."))
        val signed = wbtb.copy(alarms = wbtb.alarms.copy(dreamSigns = listOf("my old school", "flying")))
        val ifThen = " Next time I dream of my old school, I'll know I'm dreaming."
        assertEquals(generic + ifThen, guideText(GuideClip.MILD, signed)) // the first sign only
        // The guide on, or a new first sign, asks for Prepare cues; switched off, the cues stay ready.
        assertNotEquals(cueFingerprint(SessionConfig(), "v"), cueFingerprint(wbtb, "v"))
        assertNotEquals(cueFingerprint(wbtb, "v"), cueFingerprint(signed, "v"))
        assertEquals(cueFingerprint(SessionConfig(), "v"), cueFingerprint(off, "v"))
    }

    @Test
    fun noBuiltInLine_saysYouAreDreamingNow_orSpeaksOfReal() {
        // A listener may be awake: "dream" only as "if… in a dream", "when you dream" or a question (cue_wording.md).
        val wbtb = SessionConfig(alarms = AlarmConfig(wbtbEnabled = true, dreamSigns = listOf("flying")))
        val lines = PHASE1_CUES.map { cueText(it, wbtb) } + GuideClip.entries.map { guideText(it, wbtb) } +
            PHASE1_CUES.map { mitThemeCueText(it, "a tree") }
        val claim = Regex("""\b(you are|you're) dreaming\b|\breal\b""", RegexOption.IGNORE_CASE)
        for (line in lines) assertFalse(line, claim.containsMatchIn(line))
    }

    @Test
    fun oldBuiltInWords_giveWayToTheNewOnes_ownWordsStay() {
        // Words the dream-topic button stored, with the topic "dreaming", plus one old default and one own line.
        val saved = mapOf(
            PromptCue.INITIAL_INCUBATION to "Remember to think of [THEME].",
            PromptCue.WAKE_PROMPT to "You're falling asleep. Please tell me, what's going through your mind?",
            PromptCue.RE_INCUBATION to "Remember to think of [THEME]. You can fall back asleep now.",
            PromptCue.SESSION_END to "That was the last cue for tonight. Sleep well.",
            PromptCue.MORNING_WAKE to "Good morning. Rise and shine.",
        )
        val config = SessionConfig(theme = "dreaming", audio = AudioConfig(cueTexts = saved))
        val migrated = dropOldBuiltInWords(config)
        assertEquals(mapOf(PromptCue.MORNING_WAKE to "Good morning. Rise and shine."), migrated.audio.cueTexts)
        assertEquals("dreaming", migrated.theme)
        assertEquals(cueText(PromptCue.WAKE_PROMPT), cueText(PromptCue.WAKE_PROMPT, migrated))
        assertNotEquals(cueFingerprint(config, "v"), cueFingerprint(migrated, "v")) // so the voice is made again
        val clean = SessionConfig()
        assertSame(clean, dropOldBuiltInWords(clean)) // nothing old: the same config back
    }

    @Test
    fun ownWords_replaceTheTemplate_blankOrMissingKeepIt() {
        val own = "You are dreaming. You are in control."
        val config = SessionConfig(
            theme = "a fork",
            audio = AudioConfig(cueTexts = mapOf(PromptCue.INITIAL_INCUBATION to own, PromptCue.WAKE_PROMPT to "  ")),
        )
        assertEquals(own, cueText(PromptCue.INITIAL_INCUBATION, config))
        assertEquals(cueText(PromptCue.WAKE_PROMPT), cueText(PromptCue.WAKE_PROMPT, config))
        assertEquals(cueText(PromptCue.RE_INCUBATION), cueText(PromptCue.RE_INCUBATION, config))
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

    @Test
    fun guideNotOnWatch_untilTheWatchKeptThisRenderingsGuide() {
        val config = SessionConfig(theme = "a tree", alarms = AlarmConfig(wbtbEnabled = true))
        val set = CueSet("a tree", "engine", "v", emptyList(), cueFingerprint(config, "v"))
        assertTrue(guideNotOnWatch(set, config, sentFor = null)) // never sent, or a send that failed partway
        assertTrue(guideNotOnWatch(set, config, sentFor = "an older rendering")) // the old words still on the watch
        assertFalse(guideNotOnWatch(set, config, sentFor = set.fingerprint))
        assertFalse(guideNotOnWatch(null, config, sentFor = null)) // Get the voice ready shows for the voice itself
        val off = config.copy(alarms = config.alarms.copy(wbtbGuide = false))
        val offSet = set.copy(fingerprint = cueFingerprint(off, "v"))
        assertFalse(guideNotOnWatch(offSet, off, sentFor = null))
    }
}
