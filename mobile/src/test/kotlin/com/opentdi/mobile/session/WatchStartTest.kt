package com.opentdi.mobile.session

import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStartTest {
    private val missing = "missing"
    private val outdated = "outdated"
    private val carried = "c0ffee"
    private fun after(error: String?, answer: WatchStatus?, misses: Int, carried: String? = this.carried) =
        watchStartError(error, answer, misses, carried, missing, outdated)

    @Test
    fun aStartTheWatchCouldNotRead_isAnErrorAtOnce_untilOneIsRead() {
        val unread = WatchStatus(WatchState.WAITING, understood = false, apk = "other")
        assertEquals(outdated, after(null, unread, misses = 1)) // no 3-miss wait
        assertEquals(outdated, after(null, unread.copy(state = WatchState.RECORDING), misses = 1)) // tracking, no alarms
        assertEquals(outdated, after(outdated, null, misses = 5)) // out of reach changes nothing
        assertNull(after(outdated, WatchStatus(WatchState.RECORDING, apk = carried), misses = 0))
    }

    @Test
    fun aWatchAppFromBeforeTheCheck_isOutdatedAtOnce_unlessItTracks() {
        assertEquals(outdated, after(null, WatchStatus(WatchState.WAITING), misses = 1))
        assertEquals(outdated, after(null, WatchStatus(WatchState.IDLE), misses = 1))
        assertNull(after(outdated, WatchStatus(WatchState.RECORDING), misses = 0)) // it read Start after all
        assertNull(after(null, WatchStatus(WatchState.WAITING), misses = 1, carried = null)) // nothing to compare with
    }

    @Test
    fun aWaitingWatch_isMissingAfterThreeReplies_andOtherErrorsStay() {
        val waiting = WatchStatus(WatchState.WAITING, apk = carried)
        assertNull(after(null, waiting, misses = 2))
        assertEquals(missing, after(null, waiting, misses = 3))
        assertNull(after(missing, WatchStatus(WatchState.RECORDING, apk = carried), misses = 0))
        assertEquals("other", after("other", WatchStatus(WatchState.RECORDING, apk = carried), misses = 0))
    }
}
