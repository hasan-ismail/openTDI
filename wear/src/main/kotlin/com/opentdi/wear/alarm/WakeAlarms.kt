package com.opentdi.wear.alarm

import com.opentdi.shared.csv.csvLine
import com.opentdi.shared.model.LateCueMode
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.opentdi.shared.audio.nextAlarmAt
import com.opentdi.shared.config.MIN_SLEEP_BEFORE_WBTB_MIN
import com.opentdi.shared.config.StructuralConstants.RE_ONSET_MS
import com.opentdi.shared.config.wakeBeforeWbtb
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.AlarmTone
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.time.clock12
import com.opentdi.wear.MainActivity
import com.opentdi.wear.R
import com.opentdi.wear.link.PhoneLink
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.logEvent
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The night's watch alarms (owner, 2026-10-28): WBTB and the morning wake-up, each at the next time its clock time
 * comes round after recording starts. The recording service ticks it once a second on its sensing thread. It logs
 * w_alarm.csv in the session folder: when an alarm rang, the first movement after it (stillness below 0.5, the
 * accelerometer's own "moving" line) and when it was stopped, so the morning shows how long waking up took. Every
 * night it also logs when it saw you fall asleep (SLEEP ONSET), which can time the early wake-up (wbtbAfterSleepMin).
 */
class WakeAlarms(
    private val context: Context,
    var dir: File, // the session folder; moves on when the sensors restart mid-night
    startMs: Long,
    private var config: AlarmConfig,
    private val recordingStartMs: Long = startMs, // the night's start (nightStart)
    private val testNight: Boolean = false, // debug: sleep timing counts seconds for minutes (takeTestNight)
    // The backup ringing a morning whose recording died (WakeBackup): the wake-up only, now, with that morning's count.
    private val restored: WakeBackup.Record? = null,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var zone = ZoneId.systemDefault()
    private var wakeAt = when {
        restored != null -> startMs
        config.wakeEnabled -> nextAlarmAt(config.wakeAtMin, startMs, zone)
        else -> null
    }
    private var firstRingAt: Long? = restored?.firstRingAt // the first ring of a snoozed wake-up
    // Tonight's early wake-up is over: it rang, was skipped or started early, or the wake-up rang. Saved, so a
    // recording started again tonight (the app died, End the night and a new Start) does not ring it again.
    private var wbtbDoneAt: Long? = prefs.getLong(KEY_WBTB_DONE, -1).takeIf { it >= recordingStartMs }
    private var wbtbSkippedAt: Long? = prefs.getLong(KEY_WBTB_SKIPPED, -1).takeIf { it >= recordingStartMs }
    private var onsetMs: Long? = prefs.getLong(KEY_ONSET, -1).takeIf { it >= recordingStartMs } // likewise
    private val unitMs = if (testNight) 1_000L else 60_000L
    private val testTag = if (testNight) ";test=1" else "" // on the SLEEP and WBTB rows
    private val sleepOnset = SleepOnset(recordingStartMs, RE_ONSET_MS * unitMs / 60_000L)
    private var wbtbAt: Long? = null // planWbtb
    private var wbtbTimed = true // false while sleep timing waits to see you fall asleep: tick does not fire it
    private var wbtbFrom = "CLOCK" // what timed tonight's: SLEEP, or the set time (CLOCK, LATE, NO_ONSET, EARLY_START)
    private var wbtbPassedAt: Long? = null // tonight's time went by before it could ring: shown, and logged once
    private var aliveSavedAt = 0L
    private val player = AlarmPlayer(context)
    private var ringing: Ringing? = null
    private var backToSleepAt: Long? = null
    private var snoozes = restored?.snoozes ?: 0
    private var tappedSnoozes = restored?.tappedSnoozes ?: 0 // Snooze taps only: Stats count those, not ring-outs
    private var unanswered = restored?.unanswered ?: 0 // the backup's tries since a clock last rang or snoozed it
    private var morningDone = false // the wake-up was answered or ended: no backup for the rest of this recording
    private var lateCues = makeLateCues()
    private var guideOfferedAt: Long? = null // the back-to-sleep buzz, while "I'm in bed" waits for a tap
    private var guide: GuideRoutine? = null // the guided back-to-sleep, while it plays

    init {
        // From the night's start, so a time that went by while the app was off shows as PASSED instead of silently
        // moving to tomorrow.
        planWbtb(startMs, clockFromMs = recordingStartMs)
        if (restored == null) {
            syncBackup("START", startMs) // also replaces, or with the wake-up off clears, last night's
        } else {
            log(startMs, "BACKUP", "RESTORED", null, null, "due=${restored.dueAt};tries=${restored.unanswered}")
        }
    }

    private fun makeLateCues(): LateCues? = if (restored == null && config.wbtbEnabled &&
        config.lateCues != LateCueMode.OFF
    ) {
        LateCues(context, { config }) { event, still, hr, detail ->
            log(System.currentTimeMillis(), "CUES", event, still, hr, detail)
        }
    } else {
        null
    }

    /**
     * New settings from the phone during the recording: the night's state (a pending snooze, the back-to-sleep
     * buzz, armed cues and tonight's experiment coin) is kept; only an alarm whose time or switch changed is moved.
     */
    fun update(next: AlarmConfig, nowMs: Long) {
        val old = config
        config = next
        if (next.wakeEnabled != old.wakeEnabled || next.wakeAtMin != old.wakeAtMin) {
            wakeAt = if (next.wakeEnabled) nextAlarmAt(next.wakeAtMin, nowMs, zone) else null
            SensingStateHolder.update { it.copy(wakeAgainAtMs = null) } // a new wake time drops a pending snooze
            // A dropped snooze's count would use up the new time's re-rings, so a slept-through ring would not repeat.
            if (ringing?.kind != "WAKE") {
                // A snooze dropped for good (off, or moved to tomorrow) ends that morning; moved later, it rings then.
                val snoozedFrom = firstRingAt
                if (snoozedFrom != null && (wakeAt ?: Long.MAX_VALUE) - nowMs > NEW_NIGHT_MS) {
                    endMorning(nowMs, snoozedFrom, "CONFIG")
                }
                snoozes = 0
                tappedSnoozes = 0
                firstRingAt = null
            }
        }
        // Turned off on the phone while it rings: otherwise the ring-out snoozes would bring it back.
        if (!next.wakeEnabled && ringing?.kind == "WAKE") stop(nowMs, null, null)
        // Re-timed with the new settings; after tonight's rang, only tomorrow's (nextWbtbAt). Due on this very tick
        // (tickAlarms updates, then ticks): tick rings it first, so a change never loses it. Off is immediate.
        val due = wbtbTimed && wbtbAt?.let { it <= nowMs } == true
        if ((next.wbtbEnabled != old.wbtbEnabled || next.wbtbAtMin != old.wbtbAtMin ||
                next.wbtbAfterSleepMin != old.wbtbAfterSleepMin) && (!next.wbtbEnabled || !due)
        ) {
            planWbtb(nowMs, clockFromMs = nowMs)
        }
        if (old.wbtbEnabled && !next.wbtbEnabled) { // turned off on the phone: tonight's early wake-up ends here
            if (ringing?.kind == "WBTB") stop(nowMs, null, null, "TURNED_OFF") // not STOPPED: no stay-up, no report
            backToSleepAt = null
            guideOfferedAt = null
            SensingStateHolder.update {
                it.copy(wbtbBackToSleepAtMs = null, guideOffered = false, lateCuesTraining = false)
            }
        }
        if (!next.wbtbEnabled || next.lateCues == LateCueMode.OFF) {
            lateCues?.close()
            lateCues = null
        } else if (lateCues == null) {
            lateCues = makeLateCues()
        }
        log(nowMs, "CONFIG", "UPDATED", null, null, null)
        syncBackup("CONFIG", nowMs)
    }

    private class Ringing(
        val kind: String,
        val tone: AlarmTone,
        val startMs: Long,
        var movedAtMs: Long? = null,
        var escalated: Boolean = false,
        var rampFromMs: Long = startMs, // the escalation's melody rises from quiet again
    )

    /**
     * [stillness] and [hr] are the newest packet's; [seconds] the stillness of every packet since the last tick; [worn]
     * the watch is on the wrist, with fresh data from sensors that can tell (SleepOnset).
     */
    fun tick(nowMs: Long, stillness: Float?, hr: Float?, seconds: List<Float?>, worn: Boolean) {
        // Alive: a recording whose process dies without close() still leaves a stop time for nightStart. A test night
        // never does: the real night after it must not inherit its onset or its early wake-up. Nor a backup's morning,
        // whose night already stopped.
        if (!testNight && restored == null && nowMs - aliveSavedAt >= ALIVE_SAVE_MS) {
            aliveSavedAt = nowMs
            prefs.edit().putLong(KEY_STOPPED, nowMs).putString(KEY_LAST_DIR, dir.path).apply()
        }
        // The time zone changed (travel, a correction): each alarm keeps its date and clock time in the new zone.
        ZoneId.systemDefault().takeIf { it != zone }?.let { z ->
            if (firstRingAt == null) wakeAt = wakeAt?.let { sameClockTime(it, zone, z) } // a snooze is a timer
            wbtbAt = wbtbAt?.let { sameClockTime(it, zone, z) }
            zone = z
            syncBackup("ZONE", nowMs)
        }
        // Tonight's sleep onset, every night (Stats), until found or the deadline; before the ring's early return.
        val deadline = recordingStartMs + ONSET_DEADLINE_MIN * unitMs
        if (onsetMs == null && nowMs < deadline) {
            sleepOnset.add(nowMs, seconds, worn)?.let { found ->
                onsetMs = found
                prefs.edit().putLong(KEY_ONSET, found).apply()
                log(nowMs, "SLEEP", "ONSET", stillness, hr, "onset_ms=$found;night_start_ms=$recordingStartMs$testTag")
            }
        }
        if (!wbtbTimed && (onsetMs != null || nowMs >= deadline)) planWbtb(nowMs, clockFromMs = nowMs)
        publishWbtb(nowMs) // every tick: tonight's early wake-up comes within 12 h as the clock moves on
        lateCues?.tick(nowMs, stillness, hr, seconds)
        guide?.tick(nowMs, stillness, hr)
        val r = ringing
        if (r != null) {
            val moving = seconds.firstOrNull { it != null && it < MOVING }
            if (r.movedAtMs == null && moving != null) {
                r.movedAtMs = nowMs
                log(nowMs, r.kind, "FIRST_MOVEMENT", moving, hr, "after_ms=${nowMs - r.startMs}")
            }
            val elapsed = nowMs - r.startMs
            // A vibration alone did not wake everyone in the studies: after 2 min the melody joins it.
            if (r.tone == AlarmTone.VIBRATE_ONLY && !r.escalated && elapsed >= ESCALATE_MS) {
                r.escalated = true
                r.rampFromMs = nowMs
                player.start(AlarmTone.MELODY, 0.05f, gentle = r.kind == "WBTB", WatchOutputs.earbudsChosen(context))
                log(nowMs, r.kind, "ESCALATED", stillness, hr, "tone=${AlarmTone.MELODY}")
            }
            val rise = (nowMs - r.rampFromMs) / (config.rampS * 1000f)
            player.setVolume(if (config.rampS == 0) 1f else (0.05f + 0.95f * rise).coerceAtMost(1f))
            if (elapsed >= config.maxRingMin * 60_000L) {
                // Slept through it: the wake-up rings again a snooze later while snoozes are left, so no one oversleeps.
                if (r.kind == "WAKE" && snoozes < config.maxSnoozes) {
                    snooze(nowMs, stillness, hr, event = "RANG_OUT") // not SNOOZE: Stats count that as answered
                } else {
                    stop(nowMs, stillness, hr, "TIMED_OUT")
                }
            }
            return
        }
        backToSleepAt?.let {
            if (nowMs >= it) {
                backToSleepAt = null
                player.buzz()
                log(nowMs, "WBTB", "BACK_TO_SLEEP", stillness, hr, null)
                // With the guide, "I'm in bed" starts it and the late cues arm at its end: lying still through it is
                // not falling back asleep. Without it (switched off, or no clips from the phone) they arm now.
                if (guideReady()) guideOfferedAt = nowMs else lateCues?.arm(nowMs)
                val offered = guideOfferedAt != null
                SensingStateHolder.update { s -> s.copy(wbtbBackToSleepAtMs = null, guideOffered = offered) }
            }
        }
        guideOfferedAt?.let { buzz ->
            if (nowMs - buzz >= GUIDE_OFFER_MS) { // never tapped: as without the guide, counted from the buzz
                guideOfferedAt = null
                lateCues?.arm(buzz)
                SensingStateHolder.update { it.copy(guideOffered = false) }
            }
        }
        if (wbtbTimed && wbtbAt?.let { nowMs >= it } == true) {
            val detail = anchors() // before wbtbDone moves it to tomorrow's
            // Never a sleep-timed one: it comes at least that long after an onset inside the night (wbtbTime).
            if (wbtbTooSoon(nowMs, recordingStartMs, unitMs)) {
                log(nowMs, "WBTB", "SKIPPED", stillness, hr,
                    "reason=MIN_SLEEP_BEFORE_WBTB;recording_ms=${nowMs - recordingStartMs};$detail")
                wbtbDone(nowMs)
            } else {
                ring(nowMs, "WBTB", config.wbtbTone, gentle = true, stillness, hr, detail) // marks it done
            }
        } else if (wakeAt?.let { nowMs >= it } == true) {
            wakeAt = nextAlarmAt(config.wakeAtMin, nowMs, zone) // tomorrow; a snooze sets it sooner
            ring(nowMs, "WAKE", config.wakeTone, gentle = false, stillness, hr)
        }
    }

    /**
     * Snooze (wake-up alarm only, at most maxSnoozes): it rings again snoozeMin later; the wait counts as waking.
     * [event] RANG_OUT is the same, done by the ring timing out.
     */
    fun snooze(nowMs: Long, stillness: Float?, hr: Float?, event: String = "SNOOZE") {
        val r = ringing ?: return
        if (r.kind != "WAKE" || snoozes >= config.maxSnoozes) return
        snoozes++
        if (event == "SNOOZE") tappedSnoozes++
        ringing = null
        player.stop()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        WakeBackup.cancelFallback(context) // a real ring was answered or ended: the last resort goes with it
        log(nowMs, r.kind, event, stillness, hr, "after_ms=${nowMs - r.startMs};snooze=$snoozes")
        wakeAt = nowMs + config.snoozeMin * 60_000L
        firstRingAt = firstRingAt ?: r.startMs
        unanswered = 0
        SensingStateHolder.update { it.copy(alarmRinging = null, snoozesLeft = 0, wakeAgainAtMs = wakeAt) }
        syncBackup(event, nowMs)
    }

    /** "I'm awake" ([how] STOPPED), or the ring time ran out (TIMED_OUT). */
    fun stop(nowMs: Long, stillness: Float?, hr: Float?, how: String = "STOPPED") {
        val r = ringing ?: return
        ringing = null
        player.stop()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        WakeBackup.cancelFallback(context) // a real ring was answered or ended: the last resort goes with it
        val moved = r.movedAtMs?.let { it - r.startMs }
        log(nowMs, r.kind, how, stillness, hr, "after_ms=${nowMs - r.startMs};first_movement_ms=${moved ?: ""}")
        val total = nowMs - (firstRingAt ?: r.startMs) // snoozes count as part of waking up
        val summary = when {
            r.kind != "WAKE" -> null
            how == "STOPPED" -> "Woke up in ${clock(total)}" + when (tappedSnoozes) {
                0 -> "."
                1 -> " (1 snooze)."
                else -> " ($tappedSnoozes snoozes)."
            }
            else -> "The wake-up alarm stopped by itself after ${clock(total)}."
        }?.let { if (restored != null) "$it Tracking stopped during the night." else it }
        // With the guide the cue is trained in bed instead, not now while the sleeper is groggy.
        val trains = r.kind == "WBTB" && how == "STOPPED" && lateCues != null && !guideReady()
        if (r.kind == "WBTB" && how == "STOPPED") {
            backToSleepAt = nowMs + config.wbtbAwakeMin * 60_000L
            if (trains) lateCues?.train(nowMs)
        }
        if (r.kind == "WAKE") { // the morning is over: the next wake-up starts its own count
            snoozes = 0
            tappedSnoozes = 0
            firstRingAt = null
            morningDone = true
        }
        SensingStateHolder.update {
            it.copy(
                alarmRinging = null,
                wakeAgainAtMs = null,
                wbtbBackToSleepAtMs = backToSleepAt,
                lateCuesTraining = trains,
                lastWakeText = summary ?: it.lastWakeText,
                // A morning that rang out still gets its dream questions; a WBTB slept through does not.
                reportAfter = if (how == "STOPPED" || r.kind == "WAKE") r.kind else it.reportAfter,
            )
        }
        syncBackup(how, nowMs)
    }

    /** "I'm in bed" after the buzz: the guided back-to-sleep plays, and its end or a stop arms the late cues. */
    fun startGuide(nowMs: Long, stillness: Float?, hr: Float?) {
        val buzz = guideOfferedAt ?: return
        guideOfferedAt = null
        guide = watchGuide(
            context,
            config.lateCueVolume,
            log = { event, s, h, detail -> log(System.currentTimeMillis(), "WBTB", event, s, h, detail) },
        ) { at ->
            guide = null
            if (ringing?.kind != "WAKE") lateCues?.arm(at) // stopped by the wake-up: the night is over
            SensingStateHolder.update { it.copy(guideRunning = false) }
        }
        if (guide == null) lateCues?.arm(buzz) // a clip went bad since the buzz: as without the guide
        SensingStateHolder.update { it.copy(guideOffered = false, guideRunning = guide != null) }
        guide?.start(nowMs, stillness, hr)
    }

    /** A long press or the back key while the guide plays. */
    fun stopGuide(nowMs: Long, stillness: Float?, hr: Float?) {
        guide?.stop(nowMs, stillness, hr)
    }

    /**
     * "Skip the early wake-up": tonight's does not ring; the wake-up alarm and the rest of the night go on. Before it,
     * only End the night stopped it, and that also cancelled the wake-up alarm.
     */
    fun skipWbtbTonight(nowMs: Long, stillness: Float?, hr: Float?) {
        if (wbtbAhead(wbtbAt, nowMs, recordingStartMs, unitMs) == null) return
        wbtbSkippedAt = nowMs
        prefs.edit().putLong(KEY_WBTB_SKIPPED, nowMs).apply()
        log(nowMs, "WBTB", "SKIPPED", stillness, hr, "how=USER") // not the too-soon skip, which has recording_ms
        wbtbDone(nowMs)
    }

    /**
     * "I'm up now": awake before tonight's early wake-up, they start it themselves. It is the ring and "I'm awake" at
     * once, silent: the stay-up time, the guide or the back-to-sleep buzz, the late cues and its dream questions.
     */
    fun startWbtbNow(nowMs: Long, stillness: Float?, hr: Float?) {
        // Not before a ringing one could (4.5 h in): at bedtime "I'm up now" reads as "I'm still up".
        if (ringing != null || wbtbTooSoon(nowMs, recordingStartMs, unitMs) ||
            wbtbAhead(wbtbAt, nowMs, recordingStartMs, unitMs) == null
        ) {
            return
        }
        ringing = Ringing("WBTB", config.wbtbTone, nowMs)
        wbtbDone(nowMs)
        log(nowMs, "WBTB", "RING", stillness, hr, "how=USER_NOW")
        stop(nowMs, stillness, hr)
    }

    fun close() {
        val now = System.currentTimeMillis()
        // Ended during the morning's ring or snooze: that morning is over, and its dream questions are still due.
        (firstRingAt ?: ringing?.takeIf { it.kind == "WAKE" }?.startMs)?.let { endMorning(now, it, "NIGHT_ENDED") }
        // The exact stop time, over the last alive one: a recording started again soon after keeps the night's start.
        // A test night never does: the real night after it must not inherit its onset or its early wake-up. A backup's
        // morning leaves the stop time its died night left.
        if (restored == null) {
            prefs.edit {
                if (testNight) remove(KEY_STOPPED) else putLong(KEY_STOPPED, now)
                putString(KEY_LAST_DIR, dir.path)
            }
        }
        player.stop()
        if (ringing != null) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        lateCues?.close()
        lateCues = null // the night is over: a guide stopped now arms nothing
        guide?.stop(now, null, null)
        SensingStateHolder.update {
            it.copy(
                alarmRinging = null,
                wakeAgainAtMs = null,
                wbtbSkip = null,
                wbtbAtMs = null,
                wbtbAheadAtMs = null,
                wbtbNowFromMs = null,
                wbtbSkippedTonight = false,
                wbtbBackToSleepAtMs = null,
                lateCuesTraining = false,
                guideOffered = false,
                guideRunning = false,
            )
        }
    }

    /**
     * The morning that first rang at [firstRingMs] ended without "I'm awake" ([how]): Stats see it stopped, and its
     * dream questions are due unless a report was saved since.
     */
    private fun endMorning(nowMs: Long, firstRingMs: Long, how: String) {
        morningDone = true
        log(nowMs, "WAKE", "STOPPED", null, null, "how=$how")
        // shortcut: an early wake-up report saved during the snooze counts too; read the rows' "after" if that matters.
        if (File(dir, CsvFiles.W_MORNING.name).lastModified() < firstRingMs) {
            SensingStateHolder.update { it.copy(reportAfter = "WAKE") }
        }
    }

    /** The guide is on and every clip of it is on the watch; otherwise WBTB runs as it did before the guide. */
    private fun guideReady() = config.wbtbGuide && guideClipsPresent(context)

    /**
     * Tonight's early wake-up into [wbtbAt], and the screen. Without sleep timing, or once tonight's is over: the set
     * time as before (the next after [clockFromMs]; tomorrow's after tonight's). With it: [wbtbTime], held at the set
     * time while it waits to see you fall asleep. A time already gone is not rung late: it is skipped, shown, logged.
     */
    private fun planWbtb(nowMs: Long, clockFromMs: Long) {
        if (restored != null) return // a backup's morning rings the wake-up only
        val c = config
        val done = wbtbDoneAt
        val clockAt = if (c.wbtbEnabled) nextWbtbAt(c.wbtbAtMin, clockFromMs, done, zone) else null
        val timed = when {
            clockAt == null -> null
            done != null || c.wbtbAfterSleepMin == null -> clockAt to "CLOCK"
            else -> wbtbTime(c, recordingStartMs, onsetMs, clockAt, nowMs, testNight)
        }
        wbtbTimed = clockAt == null || timed != null
        wbtbAt = timed?.first ?: clockAt
        wbtbFrom = timed?.second ?: "CLOCK"
        if (done == null && wbtbTimed && wbtbAt?.let { it <= nowMs } == true) {
            log(nowMs, "WBTB", "SKIPPED", null, null, "reason=PASSED;at_ms=$wbtbAt;${anchors()}")
            wbtbPassedAt = nowMs
            wbtbDone(nowMs) // re-plans: the next night's
        } else {
            publishWbtb(nowMs)
        }
    }

    /**
     * Tells the screen about tonight's early wake-up: its time once decided, still to ring, skipped with "Skip the
     * early wake-up", or why it won't ring: too soon after the night began, so [tick] skips it, or its time passed.
     */
    private fun publishWbtb(nowMs: Long) {
        val skip = when {
            !config.wbtbEnabled -> null
            wbtbPassedAt?.let { nowMs - it < NEW_NIGHT_MS } == true -> "PASSED"
            wbtbAt?.let { wbtbTooSoon(it, recordingStartMs, unitMs) } == true -> "MIN_SLEEP_BEFORE_WBTB"
            else -> null
        }
        val at = wbtbAt.takeIf { wbtbTimed }
        val ahead = wbtbAhead(wbtbAt, nowMs, recordingStartMs, unitMs)
        val skipped = wbtbSkippedAt?.let { nowMs - it < NEW_NIGHT_MS } == true
        val nowFrom = recordingStartMs + MIN_SLEEP_BEFORE_WBTB_MIN * unitMs // startWbtbNow's guard
        SensingStateHolder.update {
            it.copy(
                wbtbSkip = skip, wbtbAtMs = at, wbtbAheadAtMs = ahead, wbtbSkippedTonight = skipped,
                wbtbNowFromMs = nowFrom,
            )
        }
    }

    /** Tonight's early wake-up is over: one set later, or a time moved, waits a night; saved for a restart tonight. */
    private fun wbtbDone(nowMs: Long) {
        wbtbDoneAt = nowMs
        prefs.edit().putLong(KEY_WBTB_DONE, nowMs).apply()
        planWbtb(nowMs, clockFromMs = nowMs)
    }

    /** For w_alarm.csv: what timed this early wake-up and the live onset, so each row stands alone in any folder. */
    private fun anchors() = "from=$wbtbFrom;onset_ms=${onsetMs ?: ""}$testTag"

    /** [detail] is appended to the RING row (anchors). */
    private fun ring(
        nowMs: Long,
        kind: String,
        tone: AlarmTone,
        gentle: Boolean,
        stillness: Float?,
        hr: Float?,
        detail: String? = null,
    ) {
        ringing = Ringing(kind, tone, nowMs) // first: a guide stopped below sees the wake-up and arms no cues
        wbtbDone(nowMs)
        // Before the sound starts: a ring that crashes is rung again by the backup, and counted (WakeBackup.retry).
        syncBackup("RING", nowMs)
        WakeBackup.cancelFallback(context)
        // The morning: no guide, no back-to-sleep buzz and no lucidity cue during the ring, the replay or the report,
        // even when the wake-up comes within the WBTB's time awake.
        if (kind == "WAKE") {
            guide?.stop(nowMs, stillness, hr)
            guideOfferedAt = null
            backToSleepAt = null
            lateCues?.disarm()
            PhoneLink.endNight(context) // the phone's voice stops: no round talks over the morning
        }
        player.start(tone, if (config.rampS == 0) 1f else 0.05f, gentle, WatchOutputs.earbudsChosen(context))
        val volume = "tone=$tone;alarm_volume=${player.alarmVolumeIndex()}"
        log(nowMs, kind, "RING", stillness, hr, volume + detail?.let { ";$it" }.orEmpty())
        val left = if (kind == "WAKE") config.maxSnoozes - snoozes else 0
        SensingStateHolder.update {
            it.copy(
                alarmRinging = kind,
                wakeAgainAtMs = null,
                snoozesLeft = left,
                guideOffered = guideOfferedAt != null,
                wbtbBackToSleepAtMs = backToSleepAt,
                lateCuesTraining = it.lateCuesTraining && kind != "WAKE",
            )
        }
        notifyRinging(kind, left > 0)
        if (unanswered != 0) { // the ring started without a crash: the backup's tries are answered
            unanswered = 0
            syncBackup("RINGING", nowMs)
        }
    }

    private fun notifyRinging(kind: String, canSnooze: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context, 2, HealthTrackingForegroundService.stopAlarmIntent(context), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing)
            .setContentTitle(if (kind == "WAKE") "Good morning" else "Early wake-up")
            .setContentText(
                "Tap I'm awake to stop the alarm." +
                    if (restored != null) " Tracking stopped during the night." else "",
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "I'm awake", stop)
            .apply {
                if (canSnooze) {
                    val snooze = PendingIntent.getService(
                        context, 3, HealthTrackingForegroundService.snoozeAlarmIntent(context), PendingIntent.FLAG_IMMUTABLE,
                    )
                    addAction(0, "Snooze ${config.snoozeMin} min", snooze)
                }
            }
            .setOngoing(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun log(nowMs: Long, kind: String, event: String, stillness: Float?, hr: Float?, detail: String?) =
        appendRow(dir, listOf(nowMs, kind, event, stillness, hr, detail))

    /** Keeps the system's backup at the next moment this clock must act on the wake-up (WakeBackup). */
    private fun syncBackup(why: String, nowMs: Long) {
        val r = ringing
        val due = WakeBackup.dueAt(wakeAt, r?.kind, r?.startMs, config.maxRingMin, morningDone, nowMs)
        val rec = due?.let {
            val firstRing = firstRingAt ?: r?.takeIf { it.kind == "WAKE" }?.startMs // a crash mid-ring keeps the total
            WakeBackup.Record(
                it, config.wakeAtMin, recordingStartMs, dir.path, snoozes, tappedSnoozes, firstRing, unanswered,
            )
        }
        WakeBackup.set(context, rec, why, logDir = dir.path)
    }

    /** The backup fired while this clock runs: it rings on its next tick, or already rings; the try is replaced. */
    fun backupFired(due: Long, nowMs: Long) {
        log(nowMs, "BACKUP", "HANDED_TO_LIVE", null, null, "due=$due;ringing=${ringing?.kind.orEmpty()}")
        syncBackup("LIVE", nowMs)
    }

    fun isRinging() = ringing != null

    companion object {
        /** One w_alarm.csv row, appended at once: a handful a night, each must survive a battery that dies at dawn. */
        internal fun appendRow(dir: File, row: List<Any?>) {
            val file = File(dir.apply { mkdirs() }, CsvFiles.W_ALARM.name)
            try {
                if (file.exists()) {
                    file.appendText(csvLine(row))
                } else {
                    CsvWriter(file.bufferedWriter(), CsvFiles.W_ALARM.columns).use { it.row(*row.toTypedArray<Any?>()) }
                }
            } catch (e: IOException) { // a full watch, or storage still locked after a restart: the alarm rings on
                logEvent("ALARM_LOG_FAILED", "event" to row[2], "error" to e.message)
            }
        }

        private const val MOVING = 0.5f // AccelConfig.moveStillness: below it a second counts as moving
        private const val ESCALATE_MS = 120_000L
        private const val GUIDE_OFFER_MS = 30 * 60_000L // GUESSED: how long after the buzz "I'm in bed" stays
        private const val CHANNEL_ID = "alarms"
        private const val NOTIFICATION_ID = 2
        private const val PREFS = "alarms"
        private const val KEY_CONFIG = "config"
        private const val KEY_NIGHT_START = "nightStart"
        private const val KEY_STOPPED = "stopped"
        private const val KEY_LAST_DIR = "lastDir" // the session folder of the night that ended last, or is running
        private const val KEY_WBTB_DONE = "wbtbDone" // counts only from tonight's start on
        private const val KEY_WBTB_SKIPPED = "wbtbSkipped" // likewise
        private const val KEY_ONSET = "sleepOnset" // tonight's live onset; likewise
        private const val LAST_NIGHT_MS = 14 * 3_600_000L // "Record a dream" for last night, until this long after it
        private const val SAME_NIGHT_GAP_MS = 2 * 3_600_000L // GUESSED: a restart within 2 h is the same night
        private const val ALIVE_SAVE_MS = 60_000L // the stop time a dying recording leaves is at most this old

        /** GUESSED: a recording this old when the phone starts the night is last night's, left running. */
        const val NEW_NIGHT_MS = 12 * 3_600_000L

        /**
         * Too early a WBTB cuts the first, deep half of the night and did worse (4.5 h vs 6 h, Erlacher 2022).
         * [unitMs]: a minute, or a test night's second.
         */
        internal fun wbtbTooSoon(atMs: Long, nightStartMs: Long, unitMs: Long = 60_000L) =
            atMs - nightStartMs < MIN_SLEEP_BEFORE_WBTB_MIN * unitMs

        /**
         * GUESSED: the onset is looked for this long after the night begins; later, the set time stands. Under
         * MIN_SLEEP_BEFORE_WBTB_MIN, so waiting for it can never delay a ring.
         */
        internal const val ONSET_DEADLINE_MIN = 180

        /**
         * Tonight's early wake-up with sleep timing on (owner, 2026-10-29; lucid_induction.md §5.2, §8.5 item 8):
         * wbtbAfterSleepMin after the live [onsetMs] when that comes before the set time [clockAtMs]; else the set
         * time, exactly as without sleep timing. With what timed it: SLEEP, or why the set time stands: CLOCK (the
         * wake-up comes before it, wakeBeforeWbtb, so it never rings tonight), LATE (falling
         * asleep then, or even at the night's start, is too late to ring earlier), NO_ONSET (none seen by the
         * deadline) or EARLY_START (the night began over NEW_NIGHT_MS before the set time: a nap then is not
         * tonight's sleep). Null while the watch should still wait to see you fall asleep. [test]: a test night counts
         * seconds for minutes and starts by day.
         */
        internal fun wbtbTime(
            c: AlarmConfig, nightStartMs: Long, onsetMs: Long?, clockAtMs: Long, nowMs: Long, test: Boolean = false,
        ): Pair<Long, String>? {
            val unit = if (test) 1_000L else 60_000L
            // Never under the 4.5-h guard, whatever a phone sent: a sleep-timed ring needs no wbtbTooSoon check.
            val afterMs = maxOf(c.wbtbAfterSleepMin ?: 0, MIN_SLEEP_BEFORE_WBTB_MIN) * unit
            return when {
                // As Settings says: the wake-up rings first and ends tonight's early wake-up, sleep timing or not.
                c.wakeBeforeWbtb() -> clockAtMs to "CLOCK"
                !test && clockAtMs - nightStartMs > NEW_NIGHT_MS -> clockAtMs to "EARLY_START"
                nightStartMs + afterMs >= clockAtMs -> clockAtMs to "LATE" // known at bedtime: no wait, skip shown now
                onsetMs != null && onsetMs + afterMs < clockAtMs -> onsetMs + afterMs to "SLEEP"
                onsetMs != null -> clockAtMs to "LATE"
                nowMs >= nightStartMs + ONSET_DEADLINE_MIN * unit -> clockAtMs to "NO_ONSET"
                else -> null
            }
        }

        private const val TEST_ARM_MS = 10 * 60_000L

        @Volatile
        private var testArmedAtMs: Long? = null

        /** Debug builds (MainActivity): the next night started within TEST_ARM_MS is a test night. */
        fun armTestNight(nowMs: Long) {
            testArmedAtMs = nowMs
        }

        /** Whether the night starting now is a test night; asked once per night. */
        fun takeTestNight(nowMs: Long): Boolean {
            val armed = testArmedAtMs
            testArmedAtMs = null
            return armed != null && nowMs - armed in 0 until TEST_ARM_MS
        }

        /**
         * The early wake-up at [minuteOfDay] next after [nowMs]. It rings once a night: after tonight's early wake-up
         * or wake-up rang ([doneAtMs]), a time moved later, or one after the wake-up, is the next night's.
         */
        internal fun nextWbtbAt(minuteOfDay: Int, nowMs: Long, doneAtMs: Long?, zone: ZoneId): Long =
            nextAlarmAt(minuteOfDay, maxOf(nowMs, (doneAtMs ?: 0L) + NEW_NIGHT_MS), zone)

        /** The early wake-up at [atMs] when it is tonight's and will ring: within 12 h, and not too soon to ring. */
        internal fun wbtbAhead(atMs: Long?, nowMs: Long, nightStartMs: Long, unitMs: Long = 60_000L): Long? =
            atMs?.takeIf { it - nowMs <= NEW_NIGHT_MS && !wbtbTooSoon(it, nightStartMs, unitMs) }

        /**
         * The night that ended last, for "Record a dream": its session folder and when it stopped (or was last alive,
         * if the app died); null when it ended [LAST_NIGHT_MS] or more ago, or its folder is gone.
         */
        fun lastNight(context: Context): Pair<File, Long>? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val dir = prefs.getString(KEY_LAST_DIR, null)?.let { File(it) }?.takeIf { it.isDirectory } ?: return null
            val endMs = prefs.getLong(KEY_STOPPED, -1).takeIf { it >= 0 } ?: return null
            return (dir to endMs).takeIf { System.currentTimeMillis() - endMs < LAST_NIGHT_MS }
        }

        /** [atMs] moved to the same date and clock time in [to] as it has in [from]. */
        internal fun sameClockTime(atMs: Long, from: ZoneId, to: ZoneId): Long =
            Instant.ofEpochMilli(atMs).atZone(from).toLocalDateTime().atZone(to).toInstant().toEpochMilli()

        /** m:ss, or "—" when it never happened. */
        fun clock(ms: Long?): String = ms?.let { String.format(Locale.ROOT, "%d:%02d", it / 60_000, it / 1000 % 60) } ?: "—"

        /**
         * When tonight's sleep began, for the WBTB's "too early" check: the start of a recording, or the night's earlier
         * one ([lastStartMs]) when it stopped, or was last alive ([lastStopMs]), less than [SAME_NIGHT_GAP_MS] before
         * this one began. A night ended at 3 AM, or whose app died then, and started again at 3:10 still had its 23:00
         * start; one begun [NEW_NIGHT_MS] or more earlier is last night's, even when it was stopped just now.
         */
        internal fun nightStart(recordingStartMs: Long, lastStartMs: Long?, lastStopMs: Long?): Long =
            if (lastStartMs != null && lastStopMs != null && lastStartMs <= lastStopMs &&
                recordingStartMs - lastStartMs < NEW_NIGHT_MS &&
                recordingStartMs - lastStopMs in 0 until SAME_NIGHT_GAP_MS
            ) {
                lastStartMs
            } else {
                recordingStartMs
            }

        /** [nightStart] from the night saved by the previous recording; saves this one's start for the next. */
        fun nightStart(context: Context, recordingStartMs: Long): Long {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun long(key: String) = prefs.getLong(key, -1).takeIf { it >= 0 }
            val start = nightStart(recordingStartMs, long(KEY_NIGHT_START), long(KEY_STOPPED))
            prefs.edit().putLong(KEY_NIGHT_START, start).remove(KEY_STOPPED).apply()
            return start
        }

        /** The alarms the phone sent last (Start the night or an alarm change); the defaults before any. */
        fun loadConfigJson(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONFIG, null)

        // A decode failure (e.g. an enum value from a newer phone) gives AlarmConfig(): wake-up at 7:00 and no early
        // wake-up. Never add an enum value to AlarmConfig without updating both apps together.
        fun parse(json: String?): AlarmConfig = json?.let {
            try {
                OpenTdiJson.decodeFromString(AlarmConfig.serializer(), it)
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: AlarmConfig()

        fun saveConfig(context: Context, config: AlarmConfig) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CONFIG, OpenTdiJson.encodeToString(AlarmConfig.serializer(), config)).apply()
            // In any watch state: the backup of a night whose recording died follows the phone's Off and new time too.
            WakeBackup.onPhoneConfig(context, config, System.currentTimeMillis())
        }

        /** "7 AM" in the watch's time zone, for the screen. */
        fun hhmm(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
            .let { clock12(it.hour * 60 + it.minute) }
    }
}
