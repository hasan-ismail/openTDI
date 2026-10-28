package com.opentdi.mobile.audio

import android.app.NotificationManager

/** How Do Not Disturb treats a USAGE_MEDIA cue (§7.7, from AOSP ZenModeHelper); [message] is the spec's user text. */
enum class DndEffect(val message: String?) {
    PLAYS(null),
    MEDIA_NOT_ALLOWED("Do Not Disturb may mute cues. Allow 'Media sound' in the Do Not Disturb exceptions."),
    TOTAL_SILENCE("Do Not Disturb (Total silence) will mute every cue. Turn it off or allow media."),
}

/**
 * The §7.7 table. At session start TOTAL_SILENCE blocks Start and MEDIA_NOT_ALLOWED warns; before each cue anything
 * but PLAYS skips the cue as dnd_muted (the spec's "same mid-session rule" for both rows).
 */
fun dndEffect(interruptionFilter: Int, priorityCategories: Int): DndEffect = when {
    interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE -> DndEffect.TOTAL_SILENCE
    interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
        (priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA) == 0 -> DndEffect.MEDIA_NOT_ALLOWED
    else -> DndEffect.PLAYS
}

/** The interruption filter and the priority categories in force now (p_audio.csv dnd_filter, dnd_categories). */
fun NotificationManager.dndState(): Pair<Int, Int> =
    currentInterruptionFilter to consolidatedNotificationPolicy.priorityCategories
