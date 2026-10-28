package com.opentdi.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.audio.dndEffect
import com.opentdi.mobile.audio.dndState
import com.opentdi.mobile.session.NapSessionService
import com.opentdi.mobile.session.closeKilledSession
import com.opentdi.mobile.session.deleteOldSessions
import com.opentdi.mobile.ui.OpenTdiTheme
import com.opentdi.mobile.ui.SessionScreen
import com.opentdi.mobile.ui.SessionsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // The session runs whether or not notifications are allowed; the FGS notice then shows in Task Manager only.
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startSessionService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                deleteOldSessions(filesDir) // §12.1
                closeKilledSession(applicationContext) // §14.3
            }
            if (!batteryUnrestricted()) allowBattery() // once per launch; onResume only re-checks
        }
        setContent {
            OpenTdiTheme {
                var sessions by rememberSaveable { mutableStateOf(false) }
                if (sessions) {
                    BackHandler { sessions = false }
                    SessionsScreen(viewModel, onBack = { sessions = false })
                } else {
                    SessionScreen(
                        viewModel,
                        onStart = ::start,
                        onStop = ::stop,
                        onAllowBattery = ::allowBattery,
                        onSessions = { sessions = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.showBattery(batteryUnrestricted())
    }

    private fun start() {
        // A session calibrated at index 0 would scale later cues to silence (§7.7 step 5); the button is off then too.
        if (getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
        val (filter, categories) = getSystemService(NotificationManager::class.java).dndState()
        val dnd = dndEffect(filter, categories)
        viewModel.showDnd(dnd)
        if (dnd == DndEffect.TOTAL_SILENCE) return // §7.7: blocks Start
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            startSessionService()
        } else {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Called while this activity is visible, which the foreground service start needs (§14.3).
    private fun startSessionService() {
        startForegroundService(Intent(this, NapSessionService::class.java))
    }

    private fun stop() {
        startService(Intent(this, NapSessionService::class.java).setAction(NapSessionService.ACTION_STOP))
    }

    private fun batteryUnrestricted() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    /** Opens the first screen that can make battery use "Unrestricted"; without one the card shows the path. */
    @SuppressLint("BatteryLife") // an overnight session on a charging phone is the use this exemption is for
    private fun allowBattery() {
        val app = Uri.fromParts("package", packageName, null)
        val opened = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
        ).any { intent ->
            try {
                startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
        if (!opened) viewModel.showBatteryManual()
    }
}
