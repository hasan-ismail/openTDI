package com.opentdi.shared.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * One recorded night as the phone's Stats screens need it (owner, 2026-10-28: "nice graphs ... of your sleep time and
 * dream state over months and weeks"). The phone computes it with [summarizeNight] from its copy of a watch session
 * (DataLayerPaths.SESSION_FILES) and caches it. Every sleep figure is an estimate from wrist movement; heart rate is
 * shown, not used. Never a sleep stage (sleep_and_dreams.md §1–2); the method is in docs/references/sleep_stats.md.
 */
@Serializable
data class NightSummary(
    val sessionId: String,
    /** [SUMMARY_VERSION] when computed: a cached summary from an older computation is recomputed. */
    val version: Int,
    /** The evening the night began, noon to noon local ([nightDate]), ISO yyyy-MM-dd. */
    val nightOf: String,
    val zone: String,
    val recordStartMs: Long,
    val recordEndMs: Long,
    /** Null when the recording is too short or too sparse to estimate sleep. */
    val sleep: SleepEstimate? = null,
    val epochs: Epochs,
    val wake: AlarmOutcome? = null,
    val wbtb: WbtbOutcome? = null,
    val lateCues: LateCueOutcome? = null,
    val reports: List<DreamReport> = emptyList(),
    /** Dormio prompts that woke the sleeper during the recording: the phone's p_transitions.csv WAKING_USER rows. */
    val promptedWakings: Int = 0,
    /**
     * When the watch saw you fall asleep, live (SLEEP ONSET, or the onset_ms an early wake-up carries in a folder
     * without it); null before it was logged, or none seen.
     */
    val liveOnsetMs: Long? = null,
)

/** Estimated sleep for one night, in the consensus sleep diary's terms (TST, SOL, WASO, SE). */
@Serializable
data class SleepEstimate(
    val onsetMs: Long,
    val finalWakeMs: Long,
    /** Estimated total sleep time between onset and final wake. */
    val sleepMin: Int,
    /** Recording start to estimated onset: a stand-in for sleep-onset latency. */
    val latencyMin: Int,
    /** Wake after sleep onset, until the final wake. */
    val wasoMin: Int,
    val awakenings: Int,
    /** sleepMin as a share of recording start to final wake, gaps excluded (not CSD SE). */
    val efficiencyPct: Int,
    val hrSleepMeanBpm: Float? = null,
    val hrSleepMinBpm: Float? = null,
    /** Which rule produced it, e.g. "arm-angle v1": a changed rule must not mix silently into trends. */
    val method: String,
)

/** The night minute by minute from [startMs], for the night timeline; every list but an empty [remLikely] has one length. */
@Serializable
data class Epochs(
    val startMs: Long,
    val stepS: Int = 60,
    /** Share of the epoch's seconds with major movement, 0–1; null where the watch sent nothing. */
    val movement: List<Float?>,
    val hrBpm: List<Float?>,
    /** The sleep estimate per epoch; null where there is no estimate. */
    val asleep: List<Boolean?>,
    /**
     * "REM-likely v2" (RemLikely.kt): a rough guess from stillness, heart rate, HRV, twitches, skin temperature and
     * EDA, never a sleep stage; null where
     * [asleep] is, and empty without a sleep estimate.
     */
    val remLikely: List<Boolean?> = emptyList(),
)

/** The morning wake-up alarm (w_alarm.csv, alarm WAKE). */
@Serializable
data class AlarmOutcome(
    val ringAtMs: Long,
    /** "I'm awake"; null when it rang out. */
    val stoppedAtMs: Long? = null,
    val snoozes: Int = 0,
    /** First ring to "I'm awake", snoozes included: how long waking took. */
    val latencyS: Int? = null,
)

/** The early wake (WBTB) and the return to sleep after it. */
@Serializable
data class WbtbOutcome(
    val ringAtMs: Long? = null,
    val upAtMs: Long? = null,
    val backToSleepAtMs: Long? = null,
    /** Estimated falling back asleep (LateCues RE_ONSET). */
    val reOnsetMs: Long? = null,
    /** [reOnsetMs] minus [backToSleepAtMs], whole minutes. */
    val reOnsetLatencyMin: Int? = null,
    /** Why it did not ring: MIN_SLEEP_BEFORE_WBTB (too little sleep recorded before it), USER or PASSED. */
    val skipped: String? = null,
    /**
     * What timed it: SLEEP, or the set time (CLOCK; LATE, NO_ONSET or EARLY_START with sleep timing on); null in older
     * files.
     */
    val timedFrom: String? = null,
)

/** The late-night cues after WBTB (w_alarm.csv, alarm CUES). */
@Serializable
data class LateCueOutcome(
    /** LateCueMode name: ON or EXPERIMENT. */
    val mode: String,
    /**
     * Cue night or, in the EXPERIMENT, the no-cue arm; never shown before the experiment's next milestone (Stats spec,
     * Part C §C6): night 1's arm gives away night 2's.
     */
    val cueNight: Boolean,
    /** EXPERIMENT pair position, "1/2" or "2/2". */
    val pair: String? = null,
    val cuesPlayed: Int = 0,
    val wouldCue: Int = 0,
    /** EXPERIMENT block: the first night's ARMED time (`block=` in the ARMED detail); null where none was logged. */
    val blockId: Long? = null,
)

/** One w_morning.csv row: the report after the WAKE or WBTB alarm, or a later one. Null = skipped. */
@Serializable
data class DreamReport(
    val atMs: Long,
    /** WAKE, WBTB, or LATER: "Record a dream / From last night", which the experiment leaves out. */
    val after: String,
    /** The w_dream_*.m4a file in the same session folder. */
    val recording: String? = null,
    /** YES, WHITE (sure I dreamed, no content) or NO. */
    val recalled: String? = null,
    /** 0–5: knew it was a dream (LuCiD insight). */
    val insight: Int? = null,
    /** 0–5: could steer it (LuCiD control). */
    val control: Int? = null,
    val nightmare: Boolean? = null,
    val falseAwakening: Boolean? = null,
    val cueInDream: Boolean? = null,
    val cueWoke: Boolean? = null,
    /** 1–5. */
    val refreshed: Int? = null,
    val sleepParalysis: Boolean? = null,
    val unusualNight: Boolean? = null,
    /** After a guided WBTB: fell asleep during the guide, YES, NO or UNSURE. */
    val guideSleep: String? = null,
    /** What was odd in the dream: FORM, ACTION, CONTEXT or NONE; asked only when [recalled] is YES. */
    val odd: String? = null,
    /** One of the sleeper's dream signs showed up; asked only when the phone had sent signs. */
    val signSeen: Boolean? = null,
)

/** Bump whenever [summarizeNight]'s output changes for the same files. */
const val SUMMARY_VERSION = 6

/** The evening a time belongs to: noon to noon, so an evening and the night after it share one date. */
fun nightDate(epochMs: Long, zone: ZoneId): LocalDate =
    // On the local clock: 12 elapsed hours would move the boundary to 11 AM or 1 PM on a daylight-saving day.
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime().minusHours(12).toLocalDate()
