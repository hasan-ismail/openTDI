package com.opentdi.wear.alarm

import kotlin.random.Random
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
class WakeAlarms(private val context: Context, private val dir: File, startMs: Long, private val config: AlarmConfig) {
    private val zone = ZoneId.systemDefault()
    private var wakeAt = if (config.wakeEnabled) nextAlarmAt(config.wakeAtMin, startMs, zone) else null
    private var firstRingAt: Long? = null // the first ring of a snoozed wake-up
    private var wbtbAt = if (config.wbtbEnabled) nextAlarmAt(config.wbtbAtMin, startMs, zone) else null
    private val player = AlarmPlayer(context)
    private var ringing: Ringing? = null
    private var backToSleepAt: Long? = null
    private var csv: CsvWriter? = null
    private var snoozes = 0
    private val lateCues = if (config.wbtbEnabled && config.lateCues != LateCueMode.OFF) {
        // EXPERIMENT: a coin decides tonight, unseen, so cue and no-cue nights can be compared (§5.4).
        val cueNight = config.lateCues == LateCueMode.ON || Random.nextBoolean()
        LateCues(context, config, cueNight) { event, still, hr, detail -> log(System.currentTimeMillis(), "CUES", event, still, hr, detail) }
    } else {
        null
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
                player.start(AlarmTone.MELODY, 0.05f, gentle = r.kind == "WBTB")
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
                lateCues?.arm(nowMs)
                SensingStateHolder.update { s -> s.copy(wbtbBackToSleepAtMs = null) }
            }
        }
        if (wbtbAt?.let { nowMs >= it } == true) {
            wbtbAt = null
            ring(nowMs, "WBTB", config.wbtbTone, gentle = true, stillness, hr)
        } else if (wakeAt?.let { nowMs >= it } == true) {
            wakeAt = null
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
            "Stay still with your eyes closed and replay your dream before you move. You moved after " +
                "${clock(moved)} and stopped the alarm after ${clock(total)}" +
                (if (snoozes > 0) " ($snoozes snoozes)." else ".")
        } else {
            null
        }
        if (r.kind == "WBTB" && how == "STOPPED") {
            backToSleepAt = nowMs + config.wbtbAwakeMin * 60_000L
            lateCues?.train(nowMs)
        }
        SensingStateHolder.update {
            it.copy(
                alarmRinging = null,
                wbtbBackToSleepAtMs = backToSleepAt,
                lateCuesTraining = r.kind == "WBTB" && how == "STOPPED" && lateCues != null,
                lastWakeText = summary ?: it.lastWakeText,
                reportAfter = if (how == "STOPPED") r.kind else it.reportAfter,
            )
        }
    }

    fun close() {
        player.stop()
        if (ringing != null) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        SensingStateHolder.update { it.copy(alarmRinging = null, wbtbBackToSleepAtMs = null, lateCuesTraining = false) }
        csv?.close()
    }

    private fun ring(nowMs: Long, kind: String, tone: AlarmTone, gentle: Boolean, stillness: Float?, hr: Float?) {
        ringing = Ringing(kind, tone, nowMs)
        player.start(tone, if (config.rampS == 0) 1f else 0.05f, gentle)
        log(nowMs, kind, "RING", stillness, hr, "tone=$tone;alarm_volume=${player.alarmVolumeIndex()}")
        val left = if (kind == "WAKE") config.maxSnoozes - snoozes else 0
        SensingStateHolder.update { it.copy(alarmRinging = kind, snoozesLeft = left) }
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
            .setContentTitle(if (kind == "WAKE") "Good morning" else "Wake back to bed")
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

    private fun log(nowMs: Long, kind: String, event: String, stillness: Float?, hr: Float?, detail: String?) {
        val w = csv ?: CsvWriter(File(dir, CsvFiles.W_ALARM.name).bufferedWriter(), CsvFiles.W_ALARM.columns)
            .also { csv = it }
        w.row(nowMs, kind, event, stillness, hr, detail)
        w.flush() // a handful of rows a night: each must survive a battery that dies at dawn
    }

    companion object {
        private const val MOVING = 0.5f // AccelConfig.moveStillness: below it a second counts as moving
        private const val ESCALATE_MS = 120_000L
        private const val CHANNEL_ID = "alarms"
        private const val NOTIFICATION_ID = 2
        private const val PREFS = "alarms"
        private const val KEY_CONFIG = "config"

        /** m:ss, or "—" when it never happened. */
        fun clock(ms: Long?): String = ms?.let { String.format(Locale.ROOT, "%d:%02d", it / 60_000, it / 1000 % 60) } ?: "—"

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

        /** "07:00" in the watch's time zone, for the screen. */
        fun hhmm(epochMs: Long): String =
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
    }
}
