package com.opentdi.mobile

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.ui.OnboardingScreen
import com.opentdi.mobile.ui.NightSky
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.audio.dndEffect
import com.opentdi.mobile.audio.dndState
import com.opentdi.mobile.link.WatchStreamListener
import com.opentdi.mobile.session.NapSessionService
import com.opentdi.mobile.session.closeKilledSession
import com.opentdi.mobile.session.deleteOldSessions
import com.opentdi.mobile.ui.BottomTabs
import com.opentdi.mobile.ui.OpenTdiTheme
import com.opentdi.mobile.ui.SessionsScreen
import com.opentdi.mobile.ui.SettingsScreen
import com.opentdi.mobile.ui.TonightScreen
import com.opentdi.mobile.ui.WatchScreen
import com.opentdi.mobile.ui.stats.StatsScreen
import com.opentdi.mobile.ui.stats.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val stats: StatsViewModel by viewModels()

    // The session runs whether or not notifications are allowed; the FGS notice then shows in Task Manager only.
    // Lint sees the androidx.fragment 1.1 that play-services pulls in; its bug is in FragmentActivity, not used here.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
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
        lifecycle.addObserver(WatchStreamListener(applicationContext))
        setContent {
            OpenTdiTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                BackHandler(enabled = tab != 0) { tab = 0 }
                NightSky() // behind every tab; the scaffold is see-through
                val onboarded by viewModel.onboarded.collectAsStateWithLifecycle()
                if (!onboarded) {
                    Box(Modifier.safeDrawingPadding()) {
                        OnboardingScreen(viewModel, onAllowBattery = ::allowBattery, onDone = viewModel::finishOnboarding)
                    }
                    return@OpenTdiTheme
                }
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = { BottomTabs(tab) { tab = it } },
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        // Both tabs show for 220 ms while they cross-fade; their effects are safe to repeat.
                        Crossfade(tab, animationSpec = tween(220), label = "tab") { shown ->
                            when (shown) {
                                0 -> TonightScreen(
                                    viewModel,
                                    onStart = ::start,
                                    onStop = ::stop,
                                    onAllowBattery = ::allowBattery,
                                    onRaiseVolume = ::raiseVolume,
                                    onOpenDnd = ::openDndSettings,
                                )
                                1 -> WatchScreen()
                                2 -> StatsScreen(stats)
                                3 -> SessionsScreen(viewModel)
                                else -> SettingsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.showBattery(batteryUnrestricted())
    }

    private fun start() {
        // The phone's volume and DND only matter when the phone speaker plays the cues.
        if (viewModel.ui.value.config.audio.route != AudioRouteMode.WATCH_WRIST_SPEAKER) {
            // A session calibrated at index 0 would scale later cues to silence (§7.7 step 5); the button is off then.
            if (getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
            val (filter, categories) = getSystemService(NotificationManager::class.java).dndState()
            if (dndEffect(filter, categories) == DndEffect.TOTAL_SILENCE) return // §7.7: blocks Start; Tonight says why
        }
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

    /** One step up on the media volume, with the system volume panel so the user sees it. */
    private fun raiseVolume() {
        getSystemService(AudioManager::class.java)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    // ZEN_MODE_SETTINGS is not public API, so the sound settings are the fallback.
    private fun openDndSettings() {
        openFirst(Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    /** Opens the first screen that can make battery use "Unrestricted"; without one the card shows the path. */
    @SuppressLint("BatteryLife") // an overnight session on a charging phone is the use this exemption is for
    private fun allowBattery() {
        val app = Uri.fromParts("package", packageName, null)
        val opened = openFirst(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
        )
        if (!opened) viewModel.showBatteryManual()
    }

    private fun openFirst(vararg intents: Intent) = intents.any { intent ->
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
