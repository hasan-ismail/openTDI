package com.opentdi.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.UserManager
import com.opentdi.wear.sensing.HealthTrackingForegroundService

/** The wake-up alarm's backup firing (WakeBackup): only this app's own alarm reaches it. */
class WakeBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WakeBackup.ACTION_FIRE) return
        // Before the first unlock after a restart the alarm settings cannot be read, nor the ringer run.
        val unlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
        val config = if (unlocked) WakeAlarms.parse(WakeAlarms.loadConfigJson(context)) else null
        val due = intent.getLongExtra(WakeBackup.EXTRA_DUE, -1)
        val next = WakeBackup.fire(context, due, config, System.currentTimeMillis(), unlocked) ?: return
        // The alarm's own wake lock ends with onReceive: this one holds the CPU until the service rings.
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTDI:backup")
            .acquire(60_000)
        HealthTrackingForegroundService.backupFired(context, next)
    }
}

/**
 * Puts the backup back after a restart, which clears every alarm (LOCKED_BOOT_COMPLETED comes before the first
 * unlock), or after an update. Exported because the system sends these: only it can, and nothing else is acted on.
 */
class WakeBackupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val why = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> "BOOT"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "UPDATE"
            else -> return
        }
        WakeBackup.rearm(context, System.currentTimeMillis(), why)
    }
}
