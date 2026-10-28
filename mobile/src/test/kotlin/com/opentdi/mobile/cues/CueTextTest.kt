package com.opentdi.mobile.cues

import com.opentdi.shared.model.PromptCue
import org.junit.Assert.assertEquals
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
}
