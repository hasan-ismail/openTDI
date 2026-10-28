package com.opentdi.mobile

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.audio.dndEffect
import com.opentdi.mobile.audio.dndState
import com.opentdi.mobile.session.NapSessionService
import com.opentdi.mobile.session.closeKilledSession
import com.opentdi.mobile.session.deleteOldSessions
import com.opentdi.mobile.ui.OpenTdiTheme
import com.opentdi.mobile.ui.SessionScreen
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
        }
        setContent {
            OpenTdiTheme {
                SessionScreen(viewModel, onStart = ::start, onStop = ::stop)
            }
        }
    }

    private fun start() {
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
}
