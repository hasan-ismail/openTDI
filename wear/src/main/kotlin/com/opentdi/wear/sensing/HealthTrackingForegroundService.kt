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
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The watch's `health` foreground service (§14.3): one [LoggingSession] at a time, with an Ongoing Activity. All
 * sensing runs on one thread, OTDI.sensing; this class only starts and stops sessions and ticks them once a second.
 */
class HealthTrackingForegroundService : LifecycleService() {
    private val thread = HandlerThread("OTDI.sensing").apply { start() }
    private val handler = Handler(thread.looper)
    private val sensing = handler.asCoroutineDispatcher()
    private var session: LoggingSession? = null // sensing thread only
    private var ticker: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSession()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            startInForeground()
            stopSession()
            val logRawAccel = intent?.getBooleanExtra(EXTRA_LOG_RAW_ACCEL, false) == true
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
            val s = LoggingSession(applicationContext, handler, config, androidSensorsOnly)
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
            }
        }
    }

    private fun stopSession() {
        ticker?.cancel()
        ticker = null
        handler.post {
            session?.stop()
            session = null
        }
    }

    private fun startInForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sensor logging", NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Logging sensors")
            .setContentIntent(open)
            .setOngoing(true)
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
    }

    companion object {
        private const val ACTION_STOP = "com.opentdi.wear.action.STOP_LOGGING"
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

        fun stop(context: Context) {
            context.startService(Intent(context, HealthTrackingForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
