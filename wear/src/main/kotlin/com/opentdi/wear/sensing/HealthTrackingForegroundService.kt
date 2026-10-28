package com.opentdi.wear.sensing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import com.opentdi.shared.model.AccelConfig
import com.opentdi.shared.model.SessionConfig
import com.opentdi.wear.MainActivity
import com.opentdi.wear.R
import com.opentdi.wear.alarm.WakeAlarms
import com.opentdi.wear.link.TelemetrySender
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private var foreground = false
    private var alarms: WakeAlarms? = null // sensing thread only
    private var alarmsJson: String? = null
    private val telemetry by lazy { TelemetrySender(applicationContext, lifecycleScope) } // outlives a session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSession()
            SensingStateHolder.update { it.copy(waiting = false) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
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
        } else if (intent?.action == ACTION_WAIT) {
            if (!foreground) startInForeground(WAITING_TEXT)
            if (ticker == null) SensingStateHolder.update { it.copy(waiting = true) }
        } else if (intent?.action == ACTION_START_FROM_PHONE && ticker != null) {
            Unit // already recording: the night's recording goes on
        } else {
            // A second startForeground from the background could fail the health type's while-in-use check.
            if (foreground) showNotification(RECORDING_TEXT) else startInForeground(RECORDING_TEXT)
            SensingStateHolder.update { it.copy(waiting = false) }
            stopSession()
            val logRawAccel = intent?.getBooleanExtra(EXTRA_LOG_RAW_ACCEL, AccelConfig().logRawAccel) == true
            startSession(
                SessionConfig(accel = AccelConfig(logRawAccel = logRawAccel)),
                androidSensorsOnly = intent?.getBooleanExtra(EXTRA_ANDROID_SENSORS, false) == true,
            )
        }
        // A system restart would start a health FGS from the background; Phase 1 sessions are restarted by the user.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSession()
        thread.quitSafely() // runs the posted session stop first
        super.onDestroy()
    }

    private fun startSession(config: SessionConfig, androidSensorsOnly: Boolean) {
        ticker = lifecycleScope.launch(sensing) {
            val s = LoggingSession(applicationContext, handler, config, androidSensorsOnly, telemetry)
            session = s
            s.start()
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

    /** The alarms the phone sent last, rescheduled when it sends new ones (a Start session while recording). */
    private fun tickAlarms(s: LoggingSession) {
        val json = WakeAlarms.loadConfigJson(applicationContext)
        if (alarms == null || json != alarmsJson) {
            alarms?.close()
            alarmsJson = json
            val dir = File(filesDir, "sessions/${s.sessionId}")
            alarms = WakeAlarms(applicationContext, dir, System.currentTimeMillis(), WakeAlarms.parse(json))
        }
        val p = SensingStateHolder.state.value.lastPacket?.packet
        alarms?.tick(System.currentTimeMillis(), p?.stillnessScore, p?.hr)
    }

    private fun stopSession() {
        ticker?.cancel()
        ticker = null
        handler.post {
            alarms?.close()
            alarms = null
            alarmsJson = null
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

    private fun showNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text).build())

    private fun startInForeground(text: String) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sensor logging", NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = notification(text)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_ongoing)
            .setTouchIntent(open)
            .build()
            .apply(applicationContext)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification.build(), type)
        foreground = true
    }

    companion object {
        private const val ACTION_STOP = "com.opentdi.wear.action.STOP_LOGGING"
        private const val ACTION_WAIT = "com.opentdi.wear.action.WAIT_FOR_PHONE"
        private const val ACTION_STOP_ALARM = "com.opentdi.wear.action.STOP_ALARM"
        private const val ACTION_SNOOZE_ALARM = "com.opentdi.wear.action.SNOOZE_ALARM"
        private const val ACTION_START_FROM_PHONE = "com.opentdi.wear.action.START_FROM_PHONE"
        private const val WAITING_TEXT = "Waiting for the phone"
        private const val RECORDING_TEXT = "Logging sensors"
        private const val EXTRA_LOG_RAW_ACCEL = "logRawAccel"
        private const val EXTRA_ANDROID_SENSORS = "androidSensorsOnly"
        private const val CHANNEL_ID = "sensing"
        private const val NOTIFICATION_ID = 1

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

        fun stop(context: Context) {
            context.startService(Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
