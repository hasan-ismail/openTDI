package com.opentdi.mobile.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class DndTest {
    // NotificationManager.INTERRUPTION_FILTER_* and Policy.PRIORITY_CATEGORY_* values (AOSP, API 23 / 28).
    private val unknown = 0
    private val all = 1
    private val priority = 2
    private val none = 3
    private val alarms = 4
    private val media = 64
    private val alarmsCategory = 32
    private val calls = 8

    @Test
    fun totalSilence_mutes_whateverTheCategories() {
        assertEquals(DndEffect.TOTAL_SILENCE, dndEffect(none, 0))
        assertEquals(DndEffect.TOTAL_SILENCE, dndEffect(none, media))
    }

    @Test
    fun priority_withoutMedia_mayMute() {
        assertEquals(DndEffect.MEDIA_NOT_ALLOWED, dndEffect(priority, 0))
        assertEquals(DndEffect.MEDIA_NOT_ALLOWED, dndEffect(priority, alarmsCategory or calls))
    }

    @Test
    fun priority_withMedia_plays() {
        assertEquals(DndEffect.PLAYS, dndEffect(priority, media))
        assertEquals(DndEffect.PLAYS, dndEffect(priority, media or alarmsCategory or calls))
    }

    @Test
    fun off_alarmsOnly_andUnknown_play() {
        // Alarms-only does not mute media (AOSP muteMedia needs priority-only).
        assertEquals(DndEffect.PLAYS, dndEffect(all, 0))
        assertEquals(DndEffect.PLAYS, dndEffect(alarms, 0))
        assertEquals(DndEffect.PLAYS, dndEffect(unknown, 0))
    }

    @Test
    fun messages_areTheSpecTexts() {
        assertEquals(null, DndEffect.PLAYS.message)
        assertEquals(
            "Do Not Disturb (Total silence) will mute every cue. Turn it off or allow media.",
            DndEffect.TOTAL_SILENCE.message,
        )
        assertEquals(
            "Do Not Disturb may mute cues. Allow 'Media sound' in the Do Not Disturb exceptions.",
            DndEffect.MEDIA_NOT_ALLOWED.message,
        )
    }
}
