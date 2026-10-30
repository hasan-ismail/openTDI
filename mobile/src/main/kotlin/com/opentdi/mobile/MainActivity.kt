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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.mobile.audio.CuePlayer
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.audio.dndEffect
import com.opentdi.mobile.audio.dndState
import com.opentdi.mobile.link.WatchStreamListener
import com.opentdi.mobile.session.NapSessionService
import com.opentdi.mobile.session.closeKilledSession
import com.opentdi.mobile.session.deleteOldSessions
import com.opentdi.mobile.ui.BottomTabs
import com.opentdi.mobile.ui.OpenTdiTheme
import com.opentdi.mobile.ui.SettingsScreen
import com.opentdi.mobile.ui.TonightScreen
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startSessionService(quickTest) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        quickTest = savedInstanceState?.getBoolean(KEY_QUICK_TEST) == true
        volumeControlStream = AudioManager.STREAM_MUSIC // the volume keys set the voice's volume, not the ringer's
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                deleteOldSessions(filesDir) // §12.1
                closeKilledSession(applicationContext) // §14.3
            }
        }
        lifecycle.addObserver(WatchStreamListener(applicationContext))
        setContent {
            OpenTdiTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var quickAsk by rememberSaveable { mutableStateOf(false) } // Settings' Quick test, asked on Tonight
                var showWatch by rememberSaveable { mutableStateOf(false) } // Settings opens on the watch's install
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
                                    onStart = { start(quick = false) },
                                    onQuickTest = { start(quick = true) },
                                    onStop = ::stop,
                                    onAllowBattery = ::allowBattery,
                                    onRaiseVolume = ::raiseVolume,
                                    onOpenDnd = ::openDndSettings,
                                    onOpenBluetooth = { openFirst(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                                    onOpenWatch = {
                                        showWatch = true
                                        tab = 2
                                    },
                                    quickAsk = quickAsk,
                                    onQuickAskSeen = { quickAsk = false },
                                )
                                1 -> StatsScreen(stats)
                                // A tab saved by an older version as 3 (Settings, after the old Watch tab) lands here.
                                else -> SettingsScreen(
                                    viewModel,
                                    onQuickTest = {
                                        quickAsk = true
                                        tab = 0
                                    },
                                    showWatch = showWatch,
                                    onWatchShown = { showWatch = false },
                                )
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

    private var quickTest = false // kept across a recreation while the notification permission is asked

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_QUICK_TEST, quickTest)
    }

    private fun start(quick: Boolean) {
        quickTest = quick
        // The phone's volume and DND only matter when the phone speaker plays the cues.
        if (viewModel.ui.value.config.audio.route != AudioRouteMode.WATCH_WRIST_SPEAKER) {
            // Calibrated at index 0, a session would scale later cues to silence (§7.7 step 5); Start's popup blocks it.
            if (getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
            if (!CuePlayer(this).streamOnSpeaker()) return // earbuds connected since the last poll; the popup says why
            val (filter, categories) = getSystemService(NotificationManager::class.java).dndState()
            if (dndEffect(filter, categories) != DndEffect.PLAYS) return // Start's popup says why
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            startSessionService(quick)
        } else {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Called while this activity is visible, which the foreground service start needs (§14.3).
    private fun startSessionService(quick: Boolean) {
        startForegroundService(
            Intent(this, NapSessionService::class.java).putExtra(NapSessionService.EXTRA_QUICK_TEST, quick),
        )
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

    /** Opens the first screen that can make battery use "Unrestricted"; without one, Start's popup shows the path. */
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

private const val KEY_QUICK_TEST = "quickTest"
