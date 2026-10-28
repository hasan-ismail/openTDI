package com.opentdi.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.opentdi.shared.model.AccelConfig
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.AccelProcessor
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingState
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.heartRatePermission
import com.opentdi.wear.sensing.runtimePermissions
import com.opentdi.wear.sensing.statusText
import java.util.Locale
import kotlinx.coroutines.delay

/** Starts and stops watch logging sessions and shows the sensor status and live values (Phase 1, §0). */
class MainActivity : ComponentActivity() {
    private var afterPermissions: (() -> Unit)? = null
    private var resolving = false // Galaxy Store was opened to install or update Health Sensor Service

    // Lint sees the androidx.fragment 1.1 that play-services pulls in; its bug is in FragmentActivity, not used here.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterPermissions?.invoke()
        afterPermissions = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by SensingStateHolder.state.collectAsStateWithLifecycle()
            var logRawAccelChoice by rememberSaveable { mutableStateOf(AccelConfig().logRawAccel) }
            var sessionsOpen by rememberSaveable { mutableStateOf(false) }
            val logRawAccel = if (state.running) state.logRawAccel else logRawAccelChoice
            MaterialTheme(colorScheme = NightColors) {
                AppScaffold {
                    BackHandler(enabled = sessionsOpen) { sessionsOpen = false }
                    SwipeToDismissBox(
                        onDismissed = { sessionsOpen = false },
                        userSwipeEnabled = sessionsOpen,
                    ) { isBackground ->
                        if (sessionsOpen && !isBackground) {
                            SessionsScreen(runningId = state.sessionId.takeIf { state.running })
                        } else {
                            SessionScreen(
                                state = state,
                                logRawAccel = logRawAccel,
                                onLogRawAccelChange = { logRawAccelChoice = it },
                                onStart = { startSession(logRawAccel, androidSensorsOnly = false) },
                                onStop = { HealthTrackingForegroundService.stop(this@MainActivity) },
                                onBasicSensors = { startSession(logRawAccel, androidSensorsOnly = true) },
                                onStatusTap = ::onStatusTap,
                                onSessions = { sessionsOpen = true },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun missingPermissions() = runtimePermissions(Build.VERSION.SDK_INT).filter {
        checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
    }

    /** Requests the runtime permissions first: a health FGS started without them throws (§14.3). */
    private fun startSession(logRawAccel: Boolean, androidSensorsOnly: Boolean) {
        val start = {
            val missing = missingPermissions()
            if (heartRatePermission(Build.VERSION.SDK_INT) !in missing ||
                Manifest.permission.ACTIVITY_RECOGNITION !in missing
            ) {
                HealthTrackingForegroundService.start(this, logRawAccel, androidSensorsOnly)
            } else {
                SensingStateHolder.update { it.copy(status = SensorStatus.PERMISSION_MISSING) }
            }
        }
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            start()
        } else {
            afterPermissions = start
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!resolving) return
        resolving = false
        // Back from Galaxy Store: only a new session reconnects to Health Sensor Service; still missing, it falls back.
        val s = SensingStateHolder.state.value
        if (s.running && s.fallbackCause in setOf(SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD)) {
            HealthTrackingForegroundService.start(this, s.logRawAccel, androidSensorsOnly = false)
        }
    }

    private fun onStatusTap(status: SensorStatus) {
        when (status) {
            SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD ->
                resolving = SensingStateHolder.state.value.resolve?.invoke(this) == true
            SensorStatus.PERMISSION_MISSING -> {
                val missing = missingPermissions()
                afterPermissions = {
                    // A dropped tracker comes back only in a new session (§11.6), with the same sensor choice.
                    val s = SensingStateHolder.state.value
                    if (s.running && missingPermissions().size < missing.size) {
                        val basicChosen = s.source == SensorSource.ANDROID_SENSORS && s.fallbackCause == null
                        HealthTrackingForegroundService.start(this, s.logRawAccel, androidSensorsOnly = basicChosen)
                    }
                }
                permissionRequest.launch(missing.toTypedArray())
            }
            else -> Unit
        }
    }
}

// Near-black and dim: the always-on screen stays lit beside the pillow (§14.6).
private val NightColors = ColorScheme(
    primary = Color(0xFF7C93B0),
    primaryContainer = Color(0xFF1C2633),
    onPrimary = Color(0xFF0B0F14),
    onPrimaryContainer = Color(0xFFA9B6C6),
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    onSurface = Color(0xFFA6A6A6),
    onSurfaceVariant = Color(0xFF8A8A8A),
    background = Color.Black,
    onBackground = Color(0xFFA6A6A6),
)

@Composable
private fun SessionScreen(
    state: SensingState,
    logRawAccel: Boolean,
    onLogRawAccelChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBasicSensors: () -> Unit,
    onStatusTap: (SensorStatus) -> Unit,
    onSessions: () -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (state.running) {
        LaunchedEffect(state.sessionId) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(1000)
            }
        }
    }
    val statuses = listOfNotNull(state.fallbackCause?.takeIf { it != state.status }, state.status)
    val values = liveValues(state, nowMs)
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("OpenTDI sensors") } }
            if (state.running && state.status == null) item { Line("Starting sensors…") }
            for (status in statuses) item { StatusLine(status, state, onStatusTap) }
            for ((extra, why) in state.extrasOff) item { Line("${extra.label} off: $why.") }
            if (state.status == SensorStatus.SDK_POLICY_ERROR) {
                item {
                    FilledTonalButton(onClick = onBasicSensors, modifier = Modifier.fillMaxWidth()) {
                        Text("Use basic watch sensors")
                    }
                }
            }
            item {
                val onClick = if (state.running) onStop else onStart
                FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.running) "Stop logging session" else "Start logging session")
                }
            }
            item {
                SwitchButton(
                    checked = logRawAccel,
                    onCheckedChange = onLogRawAccelChange,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log raw accelerometer") }
            }
            item {
                // Wear OS has no per-app battery screen (its request intent opens a no-op FakeSettingsActivity);
                // the health foreground service and its wake lock keep a session running (§14.4).
                Line("Battery: no setting needed on the watch")
            }
            item {
                FilledTonalButton(onClick = onSessions, modifier = Modifier.fillMaxWidth()) { Text("Sessions") }
            }
            for (value in values) item { Line(value) }
        }
    }
}

@Composable
private fun StatusLine(status: SensorStatus, state: SensingState, onTap: (SensorStatus) -> Unit) {
    val text = statusText(status, state.unsupported, state.edaAvailable, state.statusMessage)
    val tappable = status == SensorStatus.PERMISSION_MISSING ||
        (status in setOf(SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD) && state.resolve != null)
    if (tappable) {
        Card(onClick = { onTap(status) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
    } else {
        Line(text)
    }
}

@Composable
internal fun Line(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

private fun liveValues(state: SensingState, nowMs: Long): List<String> {
    val sessionId = state.sessionId ?: return emptyList()
    val packet = state.lastPacket?.packet
    return listOfNotNull(
        "Session $sessionId",
        if (state.running) "Elapsed ${elapsed(nowMs - state.startedElapsedMs)}" else null,
        "Source ${state.source ?: "—"}",
        "HR ${format("%.0f", packet?.hr)} bpm",
        "IBIs ${state.ibisAccepted}",
        "RMSSD ${format("%.1f", packet?.rmssd)} ms",
        "EDA ${format("%.3f", packet?.skinConductance)} µS, status ${packet?.edaStatus ?: "—"}",
        "Skin ${format("%.1f", packet?.skinTemperature)} °C",
        // %tR: the local 24-hour time of the epoch-ms timestamp
        "SpO2 ${state.lastSpo2?.let { String.format(Locale.ROOT, "%d %% at %tR", it, state.lastSpo2AtMs) } ?: "—"}",
        "Stillness ${format("%.2f", packet?.stillnessScore)}",
        "Accel ${state.lastPacket?.accelSampleCount ?: 0} samples/s",
        "PPG ${format("%.1f", state.ppgRate)} samples/s",
        if (state.gravityMode == GravityMode.UNKNOWN) {
            "Gravity: deciding (${state.gravityProgress}/${AccelProcessor.GRAVITY_MODE_STILL_SECONDS} calm s)"
        } else {
            "Gravity ${state.gravityMode}"
        },
        "Packets ${state.packetsWritten}",
    )
}

private fun format(pattern: String, value: Float?) = value?.let { String.format(Locale.ROOT, pattern, it) } ?: "—"

private fun elapsed(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
}
