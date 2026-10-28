package com.opentdi.mobile.stats

import android.content.Context
import com.opentdi.shared.stats.NightSummary
import java.io.File
import java.time.LocalDate

/** Everything the Stats screens show about one night (noon to noon), from the watch, the phone and the routine. */
data class NightRow(
    val night: LocalDate,
    /** From the phone's copy of the watch session that recorded the night; null when the watch recorded nothing. */
    val watch: NightSummary?,
    /** The phone's sleep-onset cue session(s) that night; null when none ran. */
    val phone: PhoneNight?,
    /** Bedtime-routine steps ticked that evening, and how many there were; null when none was ticked. */
    val routineDone: Int?,
    val routineTotal: Int?,
    /** The night's dream recordings copied from the watch (w_dream_*.m4a), oldest first. */
    val recordings: List<File> = emptyList(),
)

/** The phone's own sessions that night (p_transitions.csv, p_audio.csv). */
data class PhoneNight(
    val sessionIds: List<String>,
    val startMs: Long,
    val endMs: Long,
    val loopsCompleted: Int,
    val cuesPlayed: Int,
)

/** The result of one copy from the watch. */
data class SyncResult(val files: Int, val bytes: Long, val error: String? = null)

/**
 * The nights the phone knows, newest first, and copying new watch data to the phone.
 *
 * shortcut: stubs until the stats work fills them in (watch copies under filesDir/watch/<sessionId>/).
 */
object NightsRepository {
    @Suppress("UNUSED_PARAMETER")
    fun rows(context: Context): List<NightRow> = emptyList()

    @Suppress("UNUSED_PARAMETER")
    suspend fun syncFromWatch(context: Context): SyncResult = SyncResult(0, 0, "not built yet")
}
