package com.opentdi.wear.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.opentdi.shared.audio.nextAlarmAt
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.wear.MainActivity
import com.opentdi.wear.R
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.logEvent
import java.io.File
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * The wake-up alarm's system-owned backup (B27). The live WakeAlarms stays the main path; this keeps one AlarmManager
 * alarm clock at the next moment that clock must act on the wake-up (ring it, or end its ring), so a restart, a killed
 * process or an app update mid-night still wakes the user. Fired, it hands the ring to a running clock or rings it in
 * the service's alarm-only mode. The wake-up only: the early wake-up's routine needs the recording.
 *
 * One record, written before its alarm is set and removed before it is cancelled, so an alarm always finds its record
 * or none. It lives in device-protected storage: a restart before the first unlock still finds it.
 */
object WakeBackup {
    @Serializable
    data class Record(
        val dueAt: Long, // epoch ms: when its alarm fires, and the id that alarm carries
        val wakeAtMin: Int, // the wake time it follows, so a change from the phone moves it in any watch state
        val nightStartMs: Long, // the night's start: due over MAX_NIGHT_MS after it, it is no night's any more
        val dir: String, // the night's session folder: its w_alarm.csv, and the dream questions
        val snoozes: Int = 0, // WakeAlarms' counts, so a backup ring keeps the morning's snoozes and its total
        val tappedSnoozes: Int = 0,
        val firstRingAt: Long? = null,
        val unanswered: Int = 0, // tries fired since a running clock last had the wake-up in hand
    )

    enum class Fire { NONE, STALE, WAKE_OFF, TOO_LATE, GIVE_UP, RING }

    const val ACTION_FIRE = "com.opentdi.wear.action.BACKUP_ALARM"
    const val EXTRA_DUE = "due"

    /** GUESSED: a wake-up missed by more than this (the watch was off) is not rung late; a restart takes minutes. */
    private const val LATE_LIMIT_MS = 60 * 60_000L

    /** GUESSED: a record due this long after its night began belongs to no night the user is still in. */
    private const val MAX_NIGHT_MS = 24 * 3_600_000L

    /** GUESSED: a try that did not ring (a refused or crashed ringer, a stuck clock) fires again this soon. */
    internal const val RETRY_MS = 60_000L

    /** GUESSED: this many tries with no clock answering means ringing itself fails: the last resort tells instead. */
    private const val MAX_TRIES = 2
    private const val PREFS = "alarm_backup"
    private const val KEY = "record"
    private const val FALLBACK_CHANNEL = "alarm_backup"
    private const val FALLBACK_ID = 3
    private val lock = Any()

    /**
     * When the backup must fire: the wake-up (or its snooze), the end of a ring the clock must answer first, none once
     * the morning is over. A time already past (the clock is about to ring) becomes a try [RETRY_MS] on.
     */
    internal fun dueAt(
        wakeAt: Long?,
        ringKind: String?,
        ringStartMs: Long?,
        maxRingMin: Int,
        morningDone: Boolean,
        nowMs: Long,
    ): Long? {
        val ringEnd = ringStartMs?.let { it + maxRingMin * 60_000L }
        val due = when {
            morningDone -> null
            ringKind == "WAKE" -> ringEnd // by then the clock has snoozed it (RANG_OUT) or stopped it
            wakeAt == null -> null // the wake-up is off
            ringEnd != null -> maxOf(wakeAt, ringEnd) // the early wake-up rings: the wake-up comes after it
            else -> wakeAt
        }
        return due?.let { if (it <= nowMs) nowMs + RETRY_MS else it }
    }

    /** What a fired alarm does; [config] null: unreadable before the first unlock, so that guard is skipped. */
    internal fun onFire(rec: Record?, firedDue: Long, config: AlarmConfig?, nowMs: Long): Fire = when {
        rec == null -> Fire.NONE // ended, answered or turned off
        rec.dueAt != firedDue -> Fire.STALE // replaced while this alarm was already on its way
        config?.wakeEnabled == false -> Fire.WAKE_OFF // a third guard: the phone's Off clears the record itself
        !keep(rec, nowMs) -> Fire.TOO_LATE
        rec.unanswered >= MAX_TRIES -> Fire.GIVE_UP
        else -> Fire.RING
    }

    /** Still worth ringing: missed by at most [LATE_LIMIT_MS], and still that night's morning. */
    internal fun keep(rec: Record, nowMs: Long) =
        nowMs - rec.dueAt <= LATE_LIMIT_MS && rec.dueAt - rec.nightStartMs <= MAX_NIGHT_MS

    /** The next try, armed before ringing: a ring that never starts fires again, counted toward [MAX_TRIES]. */
    internal fun retry(rec: Record, nowMs: Long) = rec.copy(dueAt = nowMs + RETRY_MS, unanswered = rec.unanswered + 1)

    /** A phone's alarm change, in any watch state: Off clears it, a new wake time moves it; it never arms one. */
    internal fun reconcile(rec: Record?, config: AlarmConfig, nowMs: Long, zone: ZoneId): Record? = when {
        rec == null || !config.wakeEnabled -> null
        config.wakeAtMin != rec.wakeAtMin -> rec.copy( // as WakeAlarms.update: a new wake time drops a pending snooze
            dueAt = nextAlarmAt(config.wakeAtMin, nowMs, zone),
            wakeAtMin = config.wakeAtMin,
            snoozes = 0,
            tappedSnoozes = 0,
            firstRingAt = null,
            unanswered = 0,
        ).takeIf { keep(it, nowMs) } // moved past that night's morning: the night has no wake-up left
        else -> rec
    }

    internal fun encode(rec: Record): String = OpenTdiJson.encodeToString(Record.serializer(), rec)

    internal fun decode(text: String?): Record? = text?.let {
        try {
            OpenTdiJson.decodeFromString(Record.serializer(), it)
        } catch (e: IllegalArgumentException) { // SerializationException: unreadable, so no backup
            null
        }
    }

    fun load(context: Context): Record? = decode(prefs(context).getString(KEY, null))

    /**
     * Arms the backup for [rec], or clears it (null); nothing when unchanged, unless [force] (a restart lost the
     * alarm). Its rows go to [logDir], else the record's own night.
     */
    @SuppressLint("MissingPermission") // USE_EXACT_ALARM, declared, is the alarm-clock app's SCHEDULE_EXACT_ALARM
    fun set(context: Context, rec: Record?, why: String, logDir: String? = null, force: Boolean = false) {
        synchronized(lock) {
            val old = load(context)
            if (rec == old && !force) return
            val dir = logDir ?: rec?.dir ?: old?.dir
            val am = context.getSystemService(AlarmManager::class.java)
            if (rec == null) {
                prefs(context).edit().remove(KEY).commit() // first: an alarm already on its way finds no record
                am.cancel(fireIntent(context, 0)) // PendingIntent equality ignores extras: this is the one alarm
                cancelFallback(context) // answered, ended or turned off: the last resort stops too
            } else {
                prefs(context).edit().putString(KEY, encode(rec)).commit() // first: the alarm always finds its record
                try {
                    am.setAlarmClock(
                        AlarmManager.AlarmClockInfo(rec.dueAt, showIntent(context)),
                        fireIntent(context, rec.dueAt),
                    )
                } catch (e: SecurityException) { // USE_EXACT_ALARM is granted at install: not expected
                    SensingStateHolder.update { it.copy(backupAtMs = null) } // the screen must not promise it
                    note(dir, "NOT_ARMED", "reason=SecurityException")
                    return
                }
            }
            SensingStateHolder.update { it.copy(backupAtMs = rec?.dueAt) }
            if (rec == null) note(dir, "CLEARED", "why=$why") else note(dir, "ARMED", "due=${rec.dueAt};why=$why")
        }
    }

    /**
     * The alarm armed for [due] fired: a fire that must not ring is settled here. Otherwise the next try is armed
     * first, so a ring that never starts (a refused or crashed ringer, a stuck clock) fires again, and its time is
     * returned for the ringer. Before the first unlock after a restart ([unlocked] false) the ringer cannot read its
     * files: each try posts the last-resort notification instead.
     */
    fun fire(context: Context, due: Long, config: AlarmConfig?, nowMs: Long, unlocked: Boolean): Long? =
        synchronized(lock) {
            val rec = load(context)
            val f = onFire(rec, due, config, nowMs)
            note(rec?.dir, "FIRED", "due=$due;result=$f")
            if (rec == null || f != Fire.RING) {
                if (f == Fire.WAKE_OFF || f == Fire.TOO_LATE || f == Fire.GIVE_UP) set(context, null, f.name)
                if (f == Fire.GIVE_UP) notifyFallback(context)
                null
            } else {
                val next = retry(rec, nowMs)
                set(context, next, "TRY")
                if (!unlocked) notifyFallback(context)
                next.dueAt.takeIf { unlocked }
            }
        }

    /** After a restart, an update or opening the app: a wake-up missed by at most [LATE_LIMIT_MS] rings at once. */
    fun rearm(context: Context, nowMs: Long, why: String) {
        synchronized(lock) {
            val rec = load(context) ?: return
            if (keep(rec, nowMs)) set(context, rec, why, force = true) else set(context, null, "MISSED_$why")
        }
    }

    /** The phone's alarms, saved in any watch state (WakeAlarms.saveConfig): a died night follows Off too. */
    fun onPhoneConfig(context: Context, config: AlarmConfig, nowMs: Long) {
        synchronized(lock) {
            set(context, reconcile(load(context), config, nowMs, ZoneId.systemDefault()), "PHONE_CONFIG")
        }
    }

    /** The last resort when the ringer cannot run: one insistent alarm-sound notification. */
    fun notifyFallback(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(FALLBACK_CHANNEL, "Wake-up alarm (backup)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 700, 500, 700, 500)
            },
        )
        val open = PendingIntent.getActivity(
            context, 5, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, FALLBACK_CHANNEL)
            .setSmallIcon(R.drawable.ic_ongoing)
            .setContentTitle("Good morning")
            .setContentText("Your wake-up alarm. Tap to open OpenTDI.")
            .setCategory(NotificationCompat.CATEGORY_ALARM) // with USAGE_ALARM: through Do Not Disturb's "alarms" rule
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        nm.notify(FALLBACK_ID, n)
    }

    /** A real ring, or the backup cleared: the last resort must not sound over it or after it. */
    fun cancelFallback(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(FALLBACK_ID)

    /** One BACKUP row in the night's w_alarm.csv (when its folder is known) and one logcat line. */
    fun note(dir: String?, event: String, detail: String) {
        logEvent("BACKUP_$event", "detail" to detail)
        dir ?: return
        WakeAlarms.appendRow(File(dir), listOf(System.currentTimeMillis(), "BACKUP", event, null, null, detail))
    }

    // Times and a folder name only: fine in device-protected storage, which a restart can read before the unlock.
    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // One request code: setting it again replaces the alarm, and [due] only tells apart a delivery already on its way.
    private fun fireIntent(context: Context, due: Long) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WakeBackupReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_DUE, due),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** What the system opens when the user taps its "next alarm". */
    private fun showIntent(context: Context) =
        PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
}
