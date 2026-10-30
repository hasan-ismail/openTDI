package com.opentdi.wear.alarm

import com.opentdi.shared.config.StructuralConstants.RE_ONSET_MS

/**
 * Tonight's sleep onset, live (owner, 2026-10-29): the start of the first stretch of [stretchMs] with no major movement
 * (§8.10, [addSecond]) while the watch is worn, from [armedAtMs], the night's start. LateCues' re-onset rule plus a
 * worn check, so a watch on the table never "falls asleep". No heart rate: it did not separate lying still awake from
 * sleep. GUESSED: checked offline on very few nights; every night now logs it (SLEEP ONSET) next to the morning's
 * estimate.
 */
internal class SleepOnset(private val armedAtMs: Long, private val stretchMs: Long = RE_ONSET_MS) {
    private val recentMoving = ArrayDeque<Boolean>()
    private var stillSinceMs: Long? = null

    /**
     * One tick: [seconds] the stillness of each packet since the last; [worn] false (off the wrist, stale, or sensors
     * that can't tell) breaks the stretch. The onset once found, confirmed [stretchMs] after it; null until then.
     */
    fun add(nowMs: Long, seconds: List<Float?>, worn: Boolean): Long? {
        for (second in seconds) stillSinceMs = if (recentMoving.addSecond(second)) null else stillSinceMs ?: nowMs
        if (!worn) stillSinceMs = null
        return reOnsetAt(armedAtMs, stillSinceMs, nowMs, stretchMs)
    }
}
