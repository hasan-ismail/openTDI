package com.opentdi.mobile

import com.opentdi.mobile.audio.AudioEvent
import com.opentdi.mobile.session.skippedCueError
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun aSkippedCueAtNight_saysWhenAndWhy_otherEventsSayNothing() {
        assertEquals(
            "The voice didn't play at 11:05 PM. Do Not Disturb is silencing it. In Do Not Disturb, allow Media.",
            skippedCueError(AudioEvent("CUE_SKIPPED", cue, detail = "dnd_muted"), "11:05 PM"),
        )
        assertEquals(
            "The voice didn't play at 1:10 AM. A call was on the phone.",
            skippedCueError(AudioEvent("CUE_SKIPPED", cue, detail = "phone_in_call"), "1:10 AM"),
        )
        // An unnamed reason gets no "Try again": that cue is gone.
        assertEquals(
            "The voice didn't play at 1:10 AM.",
            skippedCueError(AudioEvent("CUE_SKIPPED", cue, detail = "no_builtin_speaker"), "1:10 AM"),
        )
        assertNull(skippedCueError(AudioEvent("CUE_END", cue), "1:10 AM"))
        assertNull(skippedCueError(AudioEvent("DND_STATE", cue), "1:10 AM"))
    }

    @Test
    fun aWatchTest_saysWhyItDidNotPlay() {
        val carried = "c0ffee"
        assertEquals("The watch did not answer: is it connected?", watchTestProblem(null, carried))
        val unread = WatchStatus(WatchState.WAITING, understood = false, apk = "other")
        assertTrue(watchTestProblem(unread, carried)!!.startsWith("The watch app is out of date"))
        // A watch app from before the version fields can't say whether it read the test.
        assertTrue(watchTestProblem(WatchStatus(WatchState.WAITING), carried)!!.startsWith("If the watch didn't play"))
        assertNull(watchTestProblem(WatchStatus(WatchState.WAITING, apk = carried), carried))
        assertNull(watchTestProblem(WatchStatus(WatchState.WAITING, apk = "other"), carried)) // newer, but it read it
        assertNull(watchTestProblem(WatchStatus(WatchState.WAITING), null)) // nothing to compare with
    }
}
