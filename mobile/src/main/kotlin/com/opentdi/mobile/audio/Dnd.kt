package com.opentdi.mobile.audio

import android.app.NotificationManager

/** How Do Not Disturb treats a USAGE_MEDIA cue (§7.7, from AOSP ZenModeHelper); [message] is Start's popup line. */
enum class DndEffect(val message: String?) {
    PLAYS(null),
    MEDIA_NOT_ALLOWED(
        "Do Not Disturb is silencing media, so you would not hear the voice. In Do Not Disturb, allow Media, then " +
            "come back.",
    ),
    TOTAL_SILENCE(
        "Do Not Disturb is on total silence, so you would not hear the voice. Turn it off, or allow Media in it.",
    ),
}

/**
 * The §7.7 table. On the phone route both rows block Start, since before each cue anything but PLAYS skips the cue
 * as dnd_muted (the spec's "same mid-session rule" for both rows): a night with no voice at all.
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
