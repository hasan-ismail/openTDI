package com.opentdi.wear

import android.content.Context
import android.content.Intent
import com.opentdi.shared.sky.moonPhase
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
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
import com.opentdi.shared.model.PROTOCOL_VERSION
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.AccelProcessor
import com.opentdi.wear.alarm.WakeAlarms
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingState
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.heartRatePermission
import com.opentdi.wear.sensing.runtimePermissions
import com.opentdi.wear.sensing.statusText
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Starts and stops watch recording sessions and shows the sensor status and live values (Phase 1, §0). */
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
            var page by rememberSaveable { mutableStateOf(Page.MAIN) }
            val logRawAccel = if (state.running) state.logRawAccel else logRawAccelChoice
            MaterialTheme(colorScheme = NightColors) {
                AppScaffold {
                    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }
                    SwipeToDismissBox(
                        onDismissed = { page = Page.MAIN },
                        userSwipeEnabled = page != Page.MAIN,
                    ) { isBackground ->
                        when (if (isBackground) Page.MAIN else page) {
                            Page.MAIN -> MainScreen(
                                state = state,
                                onStart = { startSession(logRawAccel, androidSensorsOnly = false) },
                                onStop = {
                                    // Closing too: an open app would start waiting again the next time it resumes.
                                    HealthTrackingForegroundService.stop(this@MainActivity)
                                    finish()
                                },
                                onBasicSensors = { startSession(logRawAccel, androidSensorsOnly = true) },
                                onStatusTap = ::onStatusTap,
                                onAlarmStop = { HealthTrackingForegroundService.stopAlarm(this@MainActivity) },
                                onSnooze = { HealthTrackingForegroundService.snoozeAlarm(this@MainActivity) },
                                onReport = { page = Page.REPORT },
                                onDetails = { page = Page.DETAILS },
                                onSessions = { page = Page.SESSIONS },
                            )
                            Page.DETAILS -> DetailsScreen(
                                state = state,
                                logRawAccel = logRawAccel,
                                onLogRawAccelChange = { logRawAccelChoice = it },
                            )
                            Page.SESSIONS -> SessionsScreen(runningId = state.sessionId.takeIf { state.running })
                            Page.REPORT -> DreamReportScreen(
                                after = state.reportAfter ?: "WAKE",
                                dir = File(filesDir, "sessions/${state.sessionId ?: "reports"}"),
                                requestMic = ::requestMic,
                                onDone = {
                                    SensingStateHolder.update { it.copy(reportAfter = null) }
                                    page = Page.MAIN
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** Runs [then] once the microphone may be used, asking for it first; refused, nothing happens. */
    private fun requestMic(then: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            then()
        } else {
            afterPermissions = {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) then()
            }
            permissionRequest.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
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
        // Opening the app waits for the phone's Start session (owner, 2026-10-28), once a sensor permission allows a
        // health service; without one, Start recording asks for it first.
        val s0 = SensingStateHolder.state.value
        val missing = missingPermissions()
        if (!s0.running && !s0.waiting &&
            (heartRatePermission(Build.VERSION.SDK_INT) !in missing || Manifest.permission.ACTIVITY_RECOGNITION !in missing)
        ) {
            HealthTrackingForegroundService.waitForPhone(this)
        }
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

private enum class Page { MAIN, DETAILS, SESSIONS, REPORT }

// Near-black and dim with a muted indigo-lavender accent: the always-on screen stays lit beside the pillow (§14.6).
private val NightColors = ColorScheme(
    primary = Color(0xFF8E8BC6),
    primaryContainer = Color(0xFF221F3D),
    onPrimary = Color(0xFF0F0E1A),
    onPrimaryContainer = Color(0xFFB0ADD8),
    surfaceContainerLow = Color(0xFF0D0C12),
    surfaceContainer = Color(0xFF15141C),
    surfaceContainerHigh = Color(0xFF1D1C26),
    onSurface = Color(0xFFA9A7B8),
    onSurfaceVariant = Color(0xFF8A889A),
    background = Color.Black,
    onBackground = Color(0xFFA9A7B8),
)

/** The main screen: the sensor status in plain words, Start/Stop with the elapsed time, and the live values. */
@Composable
private fun MainScreen(
    state: SensingState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBasicSensors: () -> Unit,
    onStatusTap: (SensorStatus) -> Unit,
    onAlarmStop: () -> Unit,
    onSnooze: () -> Unit,
    onReport: () -> Unit,
    onDetails: () -> Unit,
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
    val packet = state.lastPacket?.packet
    val listState = rememberTransformingLazyColumnState()
    val moon = remember { moonPhase(System.currentTimeMillis()) }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        WatchStars()
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(16.dp)) { drawMoon(moon) } // tonight's real moon phase
                        Spacer(Modifier.width(6.dp))
                        Text("OpenTDI")
                    }
                }
            }
            state.alarmRinging?.let { kind ->
                item { BigLine(if (kind == "WAKE") "Good morning" else "Wake back to bed") }
                item { Button(onClick = onAlarmStop, modifier = Modifier.fillMaxWidth(), label = { Text("I'm awake") }) }
                if (state.snoozesLeft > 0) {
                    item {
                        FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text("Snooze") }
                    }
                }
            }
            state.wbtbBackToSleepAtMs?.let { at ->
                item {
                    Text(
                        "First, pinch your nose and try to breathe: if you can, you are dreaming. Stay up until " +
                            "${WakeAlarms.hhmm(at)}. Recall your last dream and its odd moments, then picture going " +
                            "back into it and noticing you are dreaming: next time I'm dreaming, I'll remember I'm " +
                            "dreaming. Keep the light dim and drink only water. A buzz says go back to sleep.",
                    )
                }
            }
            if (state.lateCuesTraining && state.wbtbBackToSleepAtMs != null) {
                item { Text("Tonight, when you hear this soft three-note sound, you are dreaming. Notice it.") }
            }
            if (state.alarmRinging == null) state.lastWakeText?.let { item { Text(it) } }
            if (state.alarmRinging == null && state.reportAfter != null) {
                item { Button(onClick = onReport, modifier = Modifier.fillMaxWidth(), label = { Text("Dream report") }) }
            }
            if (!state.running && state.waiting) {
                item { BigLine("Waiting for the phone") }
                item { Text("Tap Start session on your phone, or start recording here.") }
                item {
                    val context = LocalContext.current
                    // Apps cannot read or change Always On Display, so the watch offers its display settings instead.
                    FilledTonalButton(onClick = { openDisplaySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Always On Display off (saves battery)")
                    }
                }
            } else if (!state.running) {
                item { BigLine("Not recording") }
            } else if (state.status == null) {
                item { BigLine("Starting sensors…") }
            }
            // Stopped, only problems stay up: a denied permission is reported before any session runs.
            val shown = statuses(state).filter { state.running || it !in FINE }
            for (status in shown) item { StatusLine(status, state, onStatusTap, short = true) }
            if (state.status == SensorStatus.SDK_POLICY_ERROR) {
                item {
                    FilledTonalButton(onClick = onBasicSensors, modifier = Modifier.fillMaxWidth()) {
                        Text("Use basic watch sensors")
                    }
                }
            }
            item {
                if (state.running) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        secondaryLabel = { Text(elapsed(nowMs - state.startedElapsedMs)) },
                        label = { Text("Stop recording") },
                    )
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), label = { Text("Start recording") })
                }
            }
            if (state.waiting && !state.running) {
                item {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Stop waiting") },
                    )
                }
            }
            if (state.sessionId != null) {
                item {
                    TileRow {
                        Tile("Heart", format("%.0f", packet?.hr), "bpm", Modifier.weight(1f))
                        Tile("EDA", format("%.2f", packet?.skinConductance), "µS", Modifier.weight(1f))
                    }
                }
                item {
                    TileRow {
                        val still = packet?.stillnessScore?.let { "${(it * 100).roundToInt()}" } ?: "—"
                        Tile("Still", still, "%", Modifier.weight(1f))
                        Tile("Skin", format("%.1f", packet?.skinTemperature), "°C", Modifier.weight(1f))
                    }
                }
                item {
                    // %tR: the local 24-hour time of the epoch-ms timestamp
                    val at = state.lastSpo2AtMs?.let { String.format(Locale.ROOT, "%% at %tR", it) } ?: "%"
                    Tile("Last SpO2", state.lastSpo2?.toString() ?: "—", at, Modifier.fillMaxWidth())
                }
            }
            item { FilledTonalButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Details") } }
            item { FilledTonalButton(onClick = onSessions, modifier = Modifier.fillMaxWidth()) { Text("Sessions") } }
        }
    }
}

/** Everything else: the exact §11.6 texts, extra sensors, the raw accelerometer choice and the technical values. */
@Composable
private fun DetailsScreen(state: SensingState, logRawAccel: Boolean, onLogRawAccelChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION") // the flags overload needs API 33; the watch's minSdk is 30
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Details") } }
            for (status in statuses(state)) item { StatusLine(status, state, onTap = {}, short = false) }
            for ((extra, why) in state.extrasOff) item { Line("${extra.label} off: $why.") }
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
            for (value in liveValues(state)) item { Line(value) }
            item { Line("App $version · protocol $PROTOCOL_VERSION") }
        }
    }
}

private val FINE = setOf(SensorStatus.CONNECTED_SAMSUNG, SensorStatus.FALLBACK_ANDROID_SENSORS, SensorStatus.SIMULATION)

private fun statuses(state: SensingState) =
    listOfNotNull(state.fallbackCause?.takeIf { it != state.status }, state.status)

/** A status on the main screen ([short]): connected in two words, a problem in its §11.6 text with its tap fix. */
@Composable
private fun StatusLine(status: SensorStatus, state: SensingState, onTap: (SensorStatus) -> Unit, short: Boolean) {
    if (short && status == SensorStatus.CONNECTED_SAMSUNG) {
        BigLine(if (state.edaAvailable) "Sensors connected" else "Sensors connected, no EDA")
        return
    }
    val text = statusText(status, state.unsupported, state.edaAvailable, state.statusMessage)
    val tappable = status == SensorStatus.PERMISSION_MISSING ||
        (status in setOf(SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD) && state.resolve != null)
    if (short && tappable) {
        Card(onClick = { onTap(status) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
    } else {
        Line(text)
    }
}

@Composable
private fun BigLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun Line(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun Tile(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun liveValues(state: SensingState): List<String> {
    val sessionId = state.sessionId ?: return emptyList()
    val packet = state.lastPacket?.packet
    return listOf(
        "Session $sessionId",
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

/** The watch's display settings, where Always On Display is switched off; a watch without that screen does nothing. */
private fun openDisplaySettings(context: Context) {
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
    } catch (e: android.content.ActivityNotFoundException) {
        Unit
    }
}
