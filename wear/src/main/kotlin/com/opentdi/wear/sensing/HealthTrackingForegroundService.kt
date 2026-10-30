package com.opentdi.wear.sensing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import com.opentdi.shared.model.AccelConfig
import com.opentdi.shared.model.EDA_STATUS_DETACHED
import com.opentdi.shared.model.HR_STATUS_DETACHED
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SessionConfig
import com.opentdi.wear.MainActivity
import com.opentdi.wear.R
import com.opentdi.wear.ReportDraft
import com.opentdi.wear.alarm.WakeAlarms
import com.opentdi.wear.alarm.WakeBackup
import com.opentdi.wear.link.TelemetrySender
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The watch's `health` foreground service (§14.3): one [LoggingSession] at a time, with an Ongoing Activity. All
 * sensing runs on one thread, OTDI.sensing; this class only starts and stops sessions and ticks them once a second.
 * Opening the app starts it waiting, sensors off (owner, 2026-10-28): the phone's Start session then starts the
 * recording from the background, which Android allows only because this service is already in the foreground.
 */
class HealthTrackingForegroundService : LifecycleService() {
    private val thread = HandlerThread("OTDI.sensing").apply { start() }
    private val handler = Handler(thread.looper)
    private val sensing = handler.asCoroutineDispatcher()
    private var session: LoggingSession? = null // sensing thread only
    private var ticker: Job? = null
    private var alarmOnly = false // the backup rings a morning whose recording died: no sensors, no recording
    private var alarms: WakeAlarms? = null // sensing thread only
    private var alarmsJson: String? = null
    private val telemetry by lazy { TelemetrySender(applicationContext, lifecycleScope) } // outlives a session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            // End the night (asked first while tracking), the phone's Stop, or stop(endNight): the wake-up's backup
            // goes now, and again once the clock is closed. Close OpenTDI with no night tracked keeps the backup of a
            // night whose recording died: it still wakes them.
            val endNight = (ticker != null && !alarmOnly) || intent?.getBooleanExtra(EXTRA_END_NIGHT, false) == true
            if (endNight) {
                WakeBackup.set(applicationContext, null, "END_NIGHT")
                WakeBackup.cancelFallback(applicationContext) // also one posted after the backup gave up
            }
            writeLeftReport()
            stopSession(clearBackup = endNight)
            alarmOnly = false
            SensingStateHolder.update { it.copy(waiting = false) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
        } else if (intent?.action == ACTION_BACKUP_RING) {
            val due = intent?.getLongExtra(EXTRA_DUE, -1L) ?: -1L
            if (!foreground && !startInForeground(ALARM_TEXT, backup = true)) {
                // Android may end the app for the missing startForeground: the try armed before this fires again.
                WakeBackup.notifyFallback(this)
            } else if (ticker != null) {
                handler.post { alarms?.backupFired(due, System.currentTimeMillis()) } // the night's own clock rings
            } else {
                startAlarmOnly(due)
            }
        } else if (intent?.action == ACTION_STOP_ALARM) {
            handler.post {
                val p = SensingStateHolder.state.value.lastPacket?.packet
                alarms?.stop(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
            }
        } else if (intent?.action == ACTION_SNOOZE_ALARM) {
            handler.post {
                val p = SensingStateHolder.state.value.lastPacket?.packet
                alarms?.snooze(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
            }
        } else if (intent?.action == ACTION_START_GUIDE) {
            handler.post {
                val p = SensingStateHolder.state.value.lastPacket?.packet
                alarms?.startGuide(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
            }
        } else if (intent?.action == ACTION_STOP_GUIDE) {
            handler.post {
                val p = SensingStateHolder.state.value.lastPacket?.packet
                alarms?.stopGuide(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
            }
        } else if (intent?.action == ACTION_SKIP_WBTB) {
            handler.post {
                val p = SensingStateHolder.state.value.lastPacket?.packet
                alarms?.skipWbtbTonight(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
            }
        } else if (intent?.action == ACTION_WBTB_NOW) {
            handler.post {
                val p = SensingStateHolder.state.value.lastPacket?.packet
                alarms?.startWbtbNow(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
            }
        } else if (intent?.action == ACTION_WAIT) {
            if (!foreground) startInForeground(WAITING_TEXT)
            if (ticker == null) {
                SensingStateHolder.update { it.copy(waiting = true) }
                // The app was opened: the backup of a night whose recording died shows, and one a force stop lost is
                // put back.
                WakeBackup.rearm(applicationContext, System.currentTimeMillis(), "OPEN")
            }
        } else if (alarmOnly) {
            Unit // a recording waits for the backup's ring to end; the alarm screen hides Start meanwhile
        } else if (intent?.action == ACTION_START_FROM_PHONE && ticker != null &&
            SystemClock.elapsedRealtime() - SensingStateHolder.state.value.startedElapsedMs < WakeAlarms.NEW_NIGHT_MS
        ) {
            Unit // already recording tonight: the night's recording goes on
        } else {
            // A second startForeground from the background could fail the health type's while-in-use check.
            if (foreground) showNotification(RECORDING_TEXT) else startInForeground(RECORDING_TEXT)
            SensingStateHolder.update { it.copy(waiting = false) }
            // A sensor restart during the recording (a permission granted, basic sensors, back from the Store) keeps
            // the night's alarms; the phone's Start over a recording left from last night begins a new night.
            val sameNight = ticker != null && intent?.action != ACTION_START_FROM_PHONE
            if (!sameNight) writeLeftReport()
            stopSession(endNight = !sameNight)
            val logRawAccel = intent?.getBooleanExtra(EXTRA_LOG_RAW_ACCEL, AccelConfig().logRawAccel) == true
            startSession(
                SessionConfig(accel = AccelConfig(logRawAccel = logRawAccel)),
                androidSensorsOnly = intent?.getBooleanExtra(EXTRA_ANDROID_SENSORS, false) == true,
                keepNight = sameNight,
            )
        }
        // A system restart would start a health FGS from the background; Phase 1 sessions are restarted by the user.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSession() // the backup stays: a service ended without a stop is what it is for
        foreground = false
        thread.quitSafely() // runs the posted session stop first
        super.onDestroy()
    }

    /**
     * The night ends (the phone's End the night too) or a new one begins: a dream report left without Save lives only
     * in memory, and the process may be killed once nothing runs. One still on screen is written by its own Save.
     * Main thread only, as ReportDraft.
     */
    private fun writeLeftReport() {
        if (!ReportDraft.onScreen && ReportDraft.flush()) SensingStateHolder.update { it.copy(reportAfter = null) }
    }

    private fun startSession(config: SessionConfig, androidSensorsOnly: Boolean, keepNight: Boolean = false) {
        ticker = lifecycleScope.launch(sensing) {
            val s = LoggingSession(applicationContext, handler, config, androidSensorsOnly, telemetry)
            session = s
            s.start(keepNight)
            // Scheduled on SystemClock.elapsedRealtime(); the wake lock keeps the uptime-based delay on time (§14.4).
            var seconds = 0L
            var next = SystemClock.elapsedRealtime()
            while (true) {
                next += 1000
                val wait = next - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait) else next = SystemClock.elapsedRealtime() // asleep: no burst of late ticks
                s.tick(++seconds)
                tickAlarms(s)
            }
        }
    }

    /** The alarms the phone sent last, rescheduled when it sends new ones while recording (a Start, an alarm change). */
    private fun tickAlarms(s: LoggingSession) {
        val json = WakeAlarms.loadConfigJson(applicationContext)
        val now = System.currentTimeMillis()
        val current = alarms
        val dir = File(filesDir, "sessions/${s.sessionId}")
        if (current == null) {
            val recordingStart = now - (SystemClock.elapsedRealtime() - SensingStateHolder.state.value.startedElapsedMs)
            val test = WakeAlarms.takeTestNight(now)
            // A test night is its own night, even right after a real one ended.
            val nightStart = if (test) recordingStart else WakeAlarms.nightStart(applicationContext, recordingStart)
            alarms = WakeAlarms(applicationContext, dir, now, WakeAlarms.parse(json), nightStart, testNight = test)
        } else {
            current.dir = dir // after a sensor restart the night's log follows the new session folder
            if (json != alarmsJson) current.update(WakeAlarms.parse(json), now) // keeps the night's state
        }
        alarmsJson = json
        val e = SensingStateHolder.state.value.lastPacket
        val p = e?.packet
        // Fresh, on the wrist and from sensors that can tell: anything else never looks like sleep (SleepOnset).
        val worn = e != null && now - e.emittedWallMs <= FRESH_PACKET_MS &&
            e.packet.source == SensorSource.SAMSUNG_SDK &&
            e.packet.hrStatus != HR_STATUS_DETACHED && e.packet.edaStatus != EDA_STATUS_DETACHED
        alarms?.tick(System.currentTimeMillis(), p?.stillnessScore, p?.hr, s.drainStillness(), worn)
    }

    /**
     * The backup's ring for a night whose recording died (WakeBackup): the wake-up's clock rebuilt from the record and
     * ticked without sensors while it rings. A snooze waits for the system's alarm, with nothing running meanwhile.
     */
    private fun startAlarmOnly(due: Long) {
        alarmOnly = true
        SensingStateHolder.update { it.copy(waiting = false) } // the phone sees IDLE: its Start waits for the ring
        ticker = lifecycleScope.launch(sensing) {
            val rec = WakeBackup.load(applicationContext)
            // Still this try: nothing ended the night, turned the wake-up off or answered it since the alarm fired.
            if (rec != null && rec.dueAt == due) {
                val json = WakeAlarms.loadConfigJson(applicationContext)
                val config = WakeAlarms.parse(json)
                val dir = File(rec.dir)
                val now = System.currentTimeMillis()
                val a = WakeAlarms(applicationContext, dir, now, config, rec.nightStartMs, restored = rec)
                alarms = a
                alarmsJson = json
                SensingStateHolder.update { it.copy(sessionId = dir.name) } // the dream questions join that night
                val lock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTDI:alarm")
                lock.acquire((config.maxRingMin + 1) * 60_000L) // the 1 Hz ramp and the ring's timeout need the CPU
                try {
                    do {
                        val j = WakeAlarms.loadConfigJson(applicationContext)
                        if (j != alarmsJson) a.update(WakeAlarms.parse(j), System.currentTimeMillis()) // Off stops it
                        alarmsJson = j
                        a.tick(System.currentTimeMillis(), null, null, emptyList(), worn = false)
                        delay(1000)
                    } while (a.isRinging())
                } finally {
                    if (lock.isHeld) lock.release()
                }
                alarms = null // not closed: that would end a snoozed morning, which the backup's record carries on
                alarmsJson = null
            }
            val me = coroutineContext[Job]
            withContext(Dispatchers.Main) { if (ticker === me) endAlarmOnly() }
        }
    }

    private fun endAlarmOnly() {
        ticker = null
        alarmOnly = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    /**
     * Stops the sensors; [endNight] false keeps the night's alarms for the session that starts next. [clearBackup]: the
     * night was ended on purpose, so its wake-up backup goes too, once the clock is closed and cannot re-arm it.
     */
    private fun stopSession(endNight: Boolean = true, clearBackup: Boolean = false) {
        ticker?.cancel()
        ticker = null
        handler.post {
            if (endNight) {
                alarms?.close()
                alarms = null
                alarmsJson = null
            }
            if (clearBackup) WakeBackup.set(applicationContext, null, "END_NIGHT")
            session?.stop()
            session = null
        }
    }

    private fun notification(text: String): NotificationCompat.Builder {
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
    }

    /** The notification with its Ongoing Activity: a plain update would drop the way back from the watch face. */
    private fun ongoing(text: String): Notification {
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = notification(text)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_ongoing)
            .setTouchIntent(open)
            .build()
            .apply(applicationContext)
        return notification.build()
    }

    private fun showNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, ongoing(text))

    /** [backup]: the backup's ring, started from its alarm; refused, it is noted and gives false (WakeBackup). */
    private fun startInForeground(text: String, backup: Boolean = false): Boolean {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Night tracking", NotificationManager.IMPORTANCE_LOW),
        )
        val type = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> 0
            // Open to apps holding USE_EXACT_ALARM; health would need a sensor permission in use, which an alarm lacks.
            backup -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, ongoing(text), type)
        } catch (e: RuntimeException) { // ForegroundServiceTypeNotAllowed..., ForegroundServiceStartNotAllowedException
            if (!backup) throw e
            WakeBackup.note(WakeBackup.load(this)?.dir, "START_REFUSED", "reason=${e.javaClass.simpleName}")
            return false
        }
        foreground = true
        return true
    }

    companion object {
        private const val ACTION_STOP = "com.opentdi.wear.action.STOP_LOGGING"
        private const val ACTION_WAIT = "com.opentdi.wear.action.WAIT_FOR_PHONE"
        private const val ACTION_STOP_ALARM = "com.opentdi.wear.action.STOP_ALARM"
        private const val ACTION_SNOOZE_ALARM = "com.opentdi.wear.action.SNOOZE_ALARM"
        private const val ACTION_START_GUIDE = "com.opentdi.wear.action.START_GUIDE"
        private const val ACTION_STOP_GUIDE = "com.opentdi.wear.action.STOP_GUIDE"
        private const val ACTION_SKIP_WBTB = "com.opentdi.wear.action.SKIP_WBTB"
        private const val ACTION_WBTB_NOW = "com.opentdi.wear.action.WBTB_NOW"
        private const val ACTION_START_FROM_PHONE = "com.opentdi.wear.action.START_FROM_PHONE"
        private const val ACTION_BACKUP_RING = "com.opentdi.wear.action.BACKUP_RING"
        private const val WAITING_TEXT = "Ready. Tap Start the night on your phone."
        private const val RECORDING_TEXT = "Tracking your sleep. Tap to open."
        private const val ALARM_TEXT = "Wake-up alarm. Tap to open."
        private const val EXTRA_LOG_RAW_ACCEL = "logRawAccel"
        private const val EXTRA_ANDROID_SENSORS = "androidSensorsOnly"
        private const val EXTRA_DUE = "due"
        private const val EXTRA_END_NIGHT = "endNight"
        private const val CHANNEL_ID = "sensing"
        private const val NOTIFICATION_ID = 1
        private const val FRESH_PACKET_MS = 30_000L // GUESSED: a few packet deadlines; an older packet is no data

        /** The service is in the foreground; process-wide, so the backup's receiver knows how to reach it. */
        @Volatile
        private var foreground = false

        /** The backup alarm fired (WakeBackupReceiver): a running service takes it, otherwise a fresh one rings it. */
        fun backupFired(context: Context, due: Long) {
            val intent = Intent(context, HealthTrackingForegroundService::class.java)
                .setAction(ACTION_BACKUP_RING)
                .putExtra(EXTRA_DUE, due)
            try {
                // In the foreground already: no second startForeground to promise. Otherwise the alarm clock's
                // exemption lets a fresh one start in the foreground from the background.
                if (foreground) context.startService(intent) else context.startForegroundService(intent)
            } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException among them
                WakeBackup.note(WakeBackup.load(context)?.dir, "START_REFUSED", "reason=${e.javaClass.simpleName}")
                WakeBackup.notifyFallback(context) // and the try armed before this fires again
            }
        }

        /**
         * Starts a new logging session (stopping a running one). Call only from a visible activity once
         * READ_HEART_RATE (BODY_SENSORS up to API 35) or ACTIVITY_RECOGNITION is granted: a health FGS without one
         * of them throws (§14.3).
         */
        fun start(context: Context, logRawAccel: Boolean, androidSensorsOnly: Boolean) {
            context.startForegroundService(
                Intent(context, HealthTrackingForegroundService::class.java)
                    .putExtra(EXTRA_LOG_RAW_ACCEL, logRawAccel)
                    .putExtra(EXTRA_ANDROID_SENSORS, androidSensorsOnly),
            )
        }

        /** Starts the service waiting with the sensors off; call only from a visible activity, as [start]. */
        fun waitForPhone(context: Context) {
            context.startForegroundService(
                Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_WAIT),
            )
        }

        /**
         * The phone's Start session. Works only while this service runs (waiting or recording): from the background
         * Android refuses to start a service, and this throws IllegalStateException.
         */
        fun startFromPhone(context: Context) {
            context.startService(
                Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_START_FROM_PHONE),
            )
        }

        /** "I'm awake": stops a ringing alarm; the recording goes on. */
        fun stopAlarmIntent(context: Context): Intent =
            Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_STOP_ALARM)

        fun snoozeAlarmIntent(context: Context): Intent =
            Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_SNOOZE_ALARM)

        fun snoozeAlarm(context: Context) {
            context.startService(snoozeAlarmIntent(context))
        }

        fun stopAlarm(context: Context) {
            context.startService(stopAlarmIntent(context))
        }

        /** "I'm in bed": the guided back-to-sleep starts (WakeAlarms.startGuide). */
        fun startGuide(context: Context) {
            context.startService(
                Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_START_GUIDE),
            )
        }

        /** Holding the screen, or the back key, during the guide stops it. */
        fun stopGuide(context: Context) {
            context.startService(
                Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_STOP_GUIDE),
            )
        }

        /** "Skip the early wake-up": tonight's does not ring; tracking and the wake-up alarm go on. */
        fun skipWbtb(context: Context) {
            context.startService(
                Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_SKIP_WBTB),
            )
        }

        /**
         * "I'm up now": tonight's early wake-up starts now, as if it rang and I'm awake was tapped; it then sets
         * reportAfter "WBTB" (WakeAlarms.startWbtbNow).
         */
        fun wbtbNow(context: Context) {
            context.startService(
                Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_WBTB_NOW),
            )
        }

        /**
         * Ends tracking. A night being tracked ends with its wake-up backup; with no night tracked (Close OpenTDI) the
         * backup of a night whose recording died stays, unless [endNight] (End the night for that night).
         */
        fun stop(context: Context, endNight: Boolean = false) {
            context.startService(
                Intent(context, HealthTrackingForegroundService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_END_NIGHT, endNight),
            )
        }
    }
}
