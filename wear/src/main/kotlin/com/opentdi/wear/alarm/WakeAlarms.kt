package com.opentdi.wear.alarm

import com.opentdi.shared.csv.csvLine
import com.opentdi.shared.model.LateCueMode
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.opentdi.shared.audio.nextAlarmAt
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.AlarmTone
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.time.clock12
import com.opentdi.wear.MainActivity
import com.opentdi.wear.R
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingStateHolder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The night's watch alarms (owner, 2026-10-28): WBTB and the morning wake-up, each at the next time its clock time
 * comes round after recording starts. The recording service ticks it once a second on its sensing thread. It logs
 * w_alarm.csv in the session folder: when an alarm rang, the first movement after it (stillness below 0.5, the
 * accelerometer's own "moving" line) and when it was stopped, so the morning shows how long waking up took.
 */
class WakeAlarms(
    private val context: Context,
    private val dir: File,
    startMs: Long,
    private var config: AlarmConfig,
    private val recordingStartMs: Long = startMs,
) {
    private val zone = ZoneId.systemDefault()
    private var wakeAt = if (config.wakeEnabled) nextAlarmAt(config.wakeAtMin, startMs, zone) else null
    private var firstRingAt: Long? = null // the first ring of a snoozed wake-up
    private var wbtbAt = if (config.wbtbEnabled) nextAlarmAt(config.wbtbAtMin, startMs, zone) else null
    private val player = AlarmPlayer(context)
    private var ringing: Ringing? = null
    private var backToSleepAt: Long? = null
    private var snoozes = 0
    private var lateCues = makeLateCues()
    private var guideOfferedAt: Long? = null // the back-to-sleep buzz, while "I'm in bed" waits for a tap
    private var guide: GuideRoutine? = null // the guided back-to-sleep, while it plays

    private fun makeLateCues(): LateCues? = if (config.wbtbEnabled && config.lateCues != LateCueMode.OFF) {
        LateCues(context, { config }) { event, still, hr, detail ->
            log(System.currentTimeMillis(), "CUES", event, still, hr, detail)
        }
    } else {
        null
    }

    /**
     * New settings from a Start session during the recording: the night's state (a pending snooze, the back-to-sleep
     * buzz, armed cues and tonight's experiment coin) is kept; only an alarm whose time or switch changed is moved.
     */
    fun update(next: AlarmConfig, nowMs: Long) {
        val old = config
        config = next
        if (next.wakeEnabled != old.wakeEnabled || next.wakeAtMin != old.wakeAtMin) {
            wakeAt = if (next.wakeEnabled) nextAlarmAt(next.wakeAtMin, nowMs, zone) else null
        }
        if (next.wbtbEnabled != old.wbtbEnabled || next.wbtbAtMin != old.wbtbAtMin) {
            wbtbAt = if (next.wbtbEnabled) nextAlarmAt(next.wbtbAtMin, nowMs, zone) else null
        }
        if (!next.wbtbEnabled || next.lateCues == LateCueMode.OFF) {
            lateCues?.close()
            lateCues = null
        } else if (lateCues == null) {
            lateCues = makeLateCues()
        }
        log(nowMs, "CONFIG", "UPDATED", null, null, null)
    }

    private class Ringing(
        val kind: String,
        val tone: AlarmTone,
        val startMs: Long,
        var movedAtMs: Long? = null,
        var escalated: Boolean = false,
        var rampFromMs: Long = startMs, // the escalation's melody rises from quiet again
    )

    fun tick(nowMs: Long, stillness: Float?, hr: Float?) {
        lateCues?.tick(nowMs, stillness, hr)
        guide?.tick(nowMs, stillness, hr)
        val r = ringing
        if (r != null) {
            if (r.movedAtMs == null && stillness != null && stillness < MOVING) {
                r.movedAtMs = nowMs
                log(nowMs, r.kind, "FIRST_MOVEMENT", stillness, hr, "after_ms=${nowMs - r.startMs}")
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
            if (elapsed >= config.maxRingMin * 60_000L) stop(nowMs, stillness, hr, "TIMED_OUT")
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
        if (wbtbAt?.let { nowMs >= it } == true) {
            wbtbAt = nextAlarmAt(config.wbtbAtMin, nowMs, zone) // tomorrow, if the recording runs on
            // Too early a WBTB cuts the first, deep half of the night and did worse (4.5 h vs 6 h, Erlacher 2022).
            if (nowMs - recordingStartMs < MIN_SLEEP_BEFORE_WBTB_MS) {
                log(nowMs, "WBTB", "SKIPPED", stillness, hr, "recording_ms=${nowMs - recordingStartMs}")
            } else {
                ring(nowMs, "WBTB", config.wbtbTone, gentle = true, stillness, hr)
            }
        } else if (wakeAt?.let { nowMs >= it } == true) {
            wakeAt = nextAlarmAt(config.wakeAtMin, nowMs, zone) // tomorrow; a snooze sets it sooner
            ring(nowMs, "WAKE", config.wakeTone, gentle = false, stillness, hr)
        }
    }

    /** Snooze (wake-up alarm only, at most maxSnoozes): it rings again snoozeMin later; the wait counts as waking. */
    fun snooze(nowMs: Long, stillness: Float?, hr: Float?) {
        val r = ringing ?: return
        if (r.kind != "WAKE" || snoozes >= config.maxSnoozes) return
        snoozes++
        ringing = null
        player.stop()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        log(nowMs, r.kind, "SNOOZE", stillness, hr, "after_ms=${nowMs - r.startMs};snooze=$snoozes")
        wakeAt = nowMs + config.snoozeMin * 60_000L
        firstRingAt = firstRingAt ?: r.startMs
        SensingStateHolder.update { it.copy(alarmRinging = null, snoozesLeft = 0) }
    }

    /** "I'm awake" ([how] STOPPED), or the ring time ran out (TIMED_OUT). */
    fun stop(nowMs: Long, stillness: Float?, hr: Float?, how: String = "STOPPED") {
        val r = ringing ?: return
        ringing = null
        player.stop()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        val moved = r.movedAtMs?.let { it - r.startMs }
        log(nowMs, r.kind, how, stillness, hr, "after_ms=${nowMs - r.startMs};first_movement_ms=${moved ?: ""}")
        val total = nowMs - (firstRingAt ?: r.startMs) // snoozes count as part of waking up
        val summary = if (r.kind == "WAKE") {
            "Before you move: pinch your nose and try to breathe through it; if you can, you are dreaming. " +
                "Then, eyes closed, replay your dream. You moved after " +
                "${clock(moved)} and stopped the alarm after ${clock(total)}" +
                (if (snoozes > 0) " ($snoozes snoozes)." else ".")
        } else {
            null
        }
        // With the guide the cue is trained in bed instead, not now while the sleeper is groggy.
        val trains = r.kind == "WBTB" && how == "STOPPED" && lateCues != null && !guideReady()
        if (r.kind == "WBTB" && how == "STOPPED") {
            backToSleepAt = nowMs + config.wbtbAwakeMin * 60_000L
            if (trains) lateCues?.train(nowMs)
        }
        if (r.kind == "WAKE") { // the morning is over: the next wake-up starts its own count
            snoozes = 0
            firstRingAt = null
        }
        SensingStateHolder.update {
            it.copy(
                alarmRinging = null,
                wbtbBackToSleepAtMs = backToSleepAt,
                lateCuesTraining = trains,
                lastWakeText = summary ?: it.lastWakeText,
                reportAfter = if (how == "STOPPED") r.kind else it.reportAfter,
            )
        }
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

    /** Any tap or bezel turn while the guide plays. */
    fun stopGuide(nowMs: Long, stillness: Float?, hr: Float?) {
        guide?.stop(nowMs, stillness, hr)
    }

    fun close() {
        // The stop time, so a recording started again soon after keeps the night's start (nightStart).
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STOPPED, System.currentTimeMillis()).apply()
        player.stop()
        if (ringing != null) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        lateCues?.close()
        lateCues = null // the night is over: a guide stopped now arms nothing
        guide?.stop(System.currentTimeMillis(), null, null)
        SensingStateHolder.update {
            it.copy(
                alarmRinging = null,
                wbtbBackToSleepAtMs = null,
                lateCuesTraining = false,
                guideOffered = false,
                guideRunning = false,
            )
        }
    }

    /** The guide is on and every clip of it is on the watch; otherwise WBTB runs as it did before the guide. */
    private fun guideReady() = config.wbtbGuide && guideClipsPresent(context)

    private fun ring(nowMs: Long, kind: String, tone: AlarmTone, gentle: Boolean, stillness: Float?, hr: Float?) {
        ringing = Ringing(kind, tone, nowMs) // first: a guide stopped below sees the wake-up and arms no cues
        // The morning: no guide, no back-to-sleep buzz and no lucidity cue during the ring, the replay or the report,
        // even when the wake-up comes within the WBTB's time awake.
        if (kind == "WAKE") {
            guide?.stop(nowMs, stillness, hr)
            guideOfferedAt = null
            backToSleepAt = null
            lateCues?.disarm()
        }
        player.start(tone, if (config.rampS == 0) 1f else 0.05f, gentle, WatchOutputs.earbudsChosen(context))
        log(nowMs, kind, "RING", stillness, hr, "tone=$tone;alarm_volume=${player.alarmVolumeIndex()}")
        val left = if (kind == "WAKE") config.maxSnoozes - snoozes else 0
        SensingStateHolder.update {
            it.copy(
                alarmRinging = kind,
                snoozesLeft = left,
                guideOffered = guideOfferedAt != null,
                wbtbBackToSleepAtMs = backToSleepAt,
                lateCuesTraining = it.lateCuesTraining && kind != "WAKE",
            )
        }
        notifyRinging(kind, left > 0)
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
            .setContentTitle(if (kind == "WAKE") "Good morning" else "Early wake")
            .setContentText("Tap I'm awake to stop the alarm.")
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

    /** One w_alarm.csv row, appended at once: a handful a night, each must survive a battery that dies at dawn. */
    private fun log(nowMs: Long, kind: String, event: String, stillness: Float?, hr: Float?, detail: String?) {
        val row = listOf<Any?>(nowMs, kind, event, stillness, hr, detail)
        val file = File(dir.apply { mkdirs() }, CsvFiles.W_ALARM.name)
        if (file.exists()) {
            file.appendText(csvLine(row))
        } else {
            CsvWriter(file.bufferedWriter(), CsvFiles.W_ALARM.columns).use { it.row(*row.toTypedArray<Any?>()) }
        }
    }

    companion object {
        private const val MOVING = 0.5f // AccelConfig.moveStillness: below it a second counts as moving
        private const val ESCALATE_MS = 120_000L
        private const val MIN_SLEEP_BEFORE_WBTB_MS = 270 * 60_000L // 4.5 h
        private const val GUIDE_OFFER_MS = 30 * 60_000L // GUESSED: how long after the buzz "I'm in bed" stays
        private const val CHANNEL_ID = "alarms"
        private const val NOTIFICATION_ID = 2
        private const val PREFS = "alarms"
        private const val KEY_CONFIG = "config"
        private const val KEY_NIGHT_START = "nightStart"
        private const val KEY_STOPPED = "stopped"
        private const val SAME_NIGHT_GAP_MS = 2 * 3_600_000L // GUESSED: a restart within 2 h is the same night

        /** m:ss, or "—" when it never happened. */
        fun clock(ms: Long?): String = ms?.let { String.format(Locale.ROOT, "%d:%02d", it / 60_000, it / 1000 % 60) } ?: "—"

        /**
         * When tonight's sleep began, for the WBTB's "too early" check: the start of a recording, or the night's earlier
         * one ([lastStartMs]) when it stopped ([lastStopMs]) less than [SAME_NIGHT_GAP_MS] before this one began. A
         * night ended at 3 AM and started again at 3:10 still had its 23:00 start.
         */
        internal fun nightStart(recordingStartMs: Long, lastStartMs: Long?, lastStopMs: Long?): Long =
            if (lastStartMs != null && lastStopMs != null && lastStartMs <= lastStopMs &&
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

        /** The alarms the phone sent with its last Start session; the defaults (both off) before any. */
        fun loadConfigJson(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONFIG, null)

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
        }

        /** "7 AM" in the watch's time zone, for the screen. */
        fun hhmm(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
            .let { clock12(it.hour * 60 + it.minute) }
    }
}
