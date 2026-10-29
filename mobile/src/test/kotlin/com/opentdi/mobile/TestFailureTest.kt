package com.opentdi.mobile

import com.opentdi.mobile.audio.AudioEvent
import com.opentdi.shared.model.PromptCue
import org.junit.Assert.assertEquals
import org.junit.Test

class TestFailureTest {
    private val cue = PromptCue.INITIAL_INCUBATION

    @Test
    fun theDetailNamesTheProblem_beforeTheEvent() {
        assertEquals(
            "Do Not Disturb is silencing it. In Do Not Disturb, allow Media.",
            testFailure(AudioEvent("CUE_SKIPPED", cue, detail = "dnd_muted")),
        )
        // The watch's volume-off carries a detail; the phone's has none.
        assertEquals(
            "The watch's volume is off. Turn it up on the watch.",
            testFailure(AudioEvent("VOLUME_ZERO", cue, detail = "watch_media_volume_off")),
        )
        assertEquals("The phone's volume is off. Turn it up.", testFailure(AudioEvent("VOLUME_ZERO", cue)))
    }

    @Test
    fun anythingElse_isAPlainRetry() {
        assertEquals("The voice didn't play. Try again.", testFailure(AudioEvent("CUE_END", cue, detail = "track_error")))
        assertEquals("The voice didn't play. Try again.", testFailure(null))
    }
}
