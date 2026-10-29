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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.opentdi.shared.audio.alarmsAhead
import com.opentdi.shared.model.AccelConfig
import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.model.PROTOCOL_VERSION
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.AccelProcessor
import com.opentdi.wear.alarm.WakeAlarms
import com.opentdi.wear.link.PhoneLink
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingState
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.heartRatePermission
import com.opentdi.wear.sensing.runtimePermissions
import com.opentdi.wear.sensing.statusText
import java.io.File
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The watch app: waits for the phone's Start the night, shows the recording, the alarms, the guided back-to-sleep, the
 * dream report and End night.
 */
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
        // Always-on: in ambient the app stays on the dim screen instead of the watch taking it back to the watch face.
        lifecycle.addObserver(AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {}))
        setContent {
            val state by SensingStateHolder.state.collectAsStateWithLifecycle()
            var logRawAccelChoice by rememberSaveable { mutableStateOf(AccelConfig().logRawAccel) }
            var page by rememberSaveable { mutableStateOf(Page.MAIN) }
            var reportFromEnd by rememberSaveable { mutableStateOf(false) } // opened from End the night
            fun openReport(fromEnd: Boolean) {
                reportFromEnd = fromEnd
                page = Page.REPORT
            }
            // One step back; a report opened from End the night goes back there, so the night still ends.
            val backTo = when {
                page == Page.REPORT && reportFromEnd -> Page.END_NIGHT
                page == Page.SESSIONS -> Page.DETAILS
                else -> Page.MAIN
            }
            val logRawAccel = if (state.running) state.logRawAccel else logRawAccelChoice
            MaterialTheme(colorScheme = NightColors) {
                AppScaffold {
                    BackHandler(enabled = page != Page.MAIN) { page = backTo }
                    val ringing = state.alarmRinging
                    var lastRing by remember { mutableStateOf<String?>(null) }
                    // Under the alarm an open report stays open. Then I'm awake opens the dream questions: only it sets
                    // reportAfter in the update that ends the ring; Snooze and a timeout do not.
                    LaunchedEffect(ringing) {
                        if (ringing != null) {
                            lastRing = ringing
                            if (page != Page.REPORT) page = Page.MAIN
                        } else if (lastRing != null) {
                            if (state.reportAfter == lastRing && page != Page.REPORT) openReport(fromEnd = false)
                            lastRing = null
                        }
                    }
                    val stop = {
                        // The night is over: a report left without Save is written as it stands.
                        if (ReportDraft.flush()) SensingStateHolder.update { it.copy(reportAfter = null) }
                        // Closing too: an open app would start waiting again the next time it resumes. The whole task,
                        // so an older copy of this screen underneath cannot resume and start waiting.
                        HealthTrackingForegroundService.stop(this@MainActivity)
                        finishAndRemoveTask()
                    }
                    Box(Modifier.fillMaxSize()) {
                        SwipeToDismissBox(
                            onDismissed = { page = backTo },
                            // Not on the report: a swipe meant as back would leave it; the back key steps through it.
                            userSwipeEnabled = page !in setOf(Page.MAIN, Page.REPORT) && ringing == null,
                        ) { isBackground ->
                            when (if (isBackground) backTo else page) {
                                Page.MAIN -> MainScreen(
                                    state = state,
                                    needsSetup = !state.running && !state.waiting && !sensorAllowed(),
                                    // End the night asks first; Close OpenTDI does not.
                                    onStop = { if (state.running) page = Page.END_NIGHT else stop() },
                                    onAllow = ::allowSensors,
                                    onBasicSensors = { startSession(logRawAccel, androidSensorsOnly = true) },
                                    onStatusTap = ::onStatusTap,
                                    onInBed = { HealthTrackingForegroundService.startGuide(this@MainActivity) },
                                    onReport = { openReport(fromEnd = false) },
                                    onDetails = { page = Page.DETAILS },
                                )
                                Page.DETAILS -> DetailsScreen(
                                    state = state,
                                    logRawAccel = logRawAccel,
                                    onLogRawAccelChange = { logRawAccelChoice = it },
                                    onStart = { startSession(logRawAccel, androidSensorsOnly = false) },
                                    onSessions = { page = Page.SESSIONS },
                                )
                                Page.SESSIONS -> SessionsScreen(runningId = state.sessionId.takeIf { state.running })
                                Page.REPORT -> DreamReportScreen(
                                    after = state.reportAfter ?: "WAKE",
                                    dir = File(filesDir, "sessions/${state.sessionId ?: "reports"}"),
                                    requestMic = ::requestMic,
                                    onSaved = { SensingStateHolder.update { it.copy(reportAfter = null) } },
                                    onDone = { page = backTo },
                                )
                                Page.END_NIGHT -> EndNightScreen(
                                    reportDue = state.reportAfter != null,
                                    onReport = { openReport(fromEnd = true) },
                                    alarmsAhead = remember {
                                        val alarms = WakeAlarms.parse(WakeAlarms.loadConfigJson(this@MainActivity))
                                        alarmsLeft(
                                            alarms,
                                            state.wakeAgainAtMs,
                                            state.wbtbSkipTonight,
                                            System.currentTimeMillis(),
                                        )
                                    },
                                    onEnd = {
                                        // Only here: Close OpenTDI while waiting must not stop a phone's Quick test.
                                        PhoneLink.endNight(this@MainActivity)
                                        stop()
                                    },
                                    onKeep = { page = Page.MAIN },
                                )
                            }
                        }
                        if (state.guideRunning && ringing == null) {
                            GuideScreen { HealthTrackingForegroundService.stopGuide(this@MainActivity) }
                        }
                        // Over every page, not instead of it: an open report keeps its recording and answers underneath.
                        if (ringing != null) {
                            // The empty pointerInput keeps taps and swipes from the page underneath; BackHandler, back.
                            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {}) {
                                BackHandler {}
                                AlarmScreen(
                                    kind = ringing,
                                    snoozeMin = remember(ringing) {
                                        WakeAlarms.parse(WakeAlarms.loadConfigJson(this@MainActivity)).snoozeMin
                                    },
                                    snoozesLeft = state.snoozesLeft,
                                    onAlarmStop = { HealthTrackingForegroundService.stopAlarm(this@MainActivity) },
                                    onSnooze = { HealthTrackingForegroundService.snoozeAlarm(this@MainActivity) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Runs [then] once the microphone may be used, asking for it first; refused, the report says it didn't start. */
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

    /** The heart rate or movement may be read: a health service needs one of them (§14.3). */
    private fun sensorAllowed(): Boolean {
        val missing = missingPermissions()
        return heartRatePermission(Build.VERSION.SDK_INT) !in missing ||
            Manifest.permission.ACTIVITY_RECOGNITION !in missing
    }

    /** The one-time setup's Allow: asks for the permissions, then waits for the phone; it never starts a night. */
    private fun allowSensors() {
        afterPermissions = {
            if (sensorAllowed()) {
                HealthTrackingForegroundService.waitForPhone(this)
            } else if (!shouldShowRequestPermissionRationale(heartRatePermission(Build.VERSION.SDK_INT)) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)
            ) {
                // Refused for good: Android shows no dialog any more, so the app's settings page is where to allow it;
                // back from there, onResume waits for the phone.
                try {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", packageName, null),
                        ),
                    )
                } catch (e: android.content.ActivityNotFoundException) {
                    Unit
                }
            }
        }
        permissionRequest.launch(missingPermissions().toTypedArray())
    }

    /** Requests the runtime permissions first: a health FGS started without them throws (§14.3). */
    private fun startSession(logRawAccel: Boolean, androidSensorsOnly: Boolean) {
        val start = {
            if (sensorAllowed()) {
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
        // health service; without one, the one-time setup's Allow asks for it first.
        val s0 = SensingStateHolder.state.value
        if (!s0.running && !s0.waiting && sensorAllowed()) HealthTrackingForegroundService.waitForPhone(this)
        if (!resolving) return
        resolving = false
        // Back from Galaxy Store: only a new session reconnects to Health Sensor Service; still missing, it falls back.
        val s = SensingStateHolder.state.value
        if (s.running && s.fallbackCause in setOf(SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD)) {
            HealthTrackingForegroundService.start(this, s.logRawAccel, androidSensorsOnly = false)
        }
    }

    override fun onDestroy() {
        // Closed with no night tracked or awaited, nothing keeps the app alive: write a report left without Save now.
        val s = SensingStateHolder.state.value
        if (isFinishing && !s.running && !s.waiting && ReportDraft.flush()) {
            SensingStateHolder.update { it.copy(reportAfter = null) }
        }
        super.onDestroy()
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

private enum class Page { MAIN, DETAILS, SESSIONS, REPORT, END_NIGHT }

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

/**
 * The main screen: waiting for the phone, the one-time setup, the night being tracked with its alarms and End the
 * night, or the night saved; after an alarm, the dream questions and the early wake-up come first.
 */
@Composable
private fun MainScreen(
    state: SensingState,
    needsSetup: Boolean,
    onStop: () -> Unit,
    onAllow: () -> Unit,
    onBasicSensors: () -> Unit,
    onStatusTap: (SensorStatus) -> Unit,
    onInBed: () -> Unit,
    onReport: () -> Unit,
    onDetails: () -> Unit,
) {
    // Off in ambient and with the screen off: the stars stop, and the clock ticks once a minute.
    val awake = rememberInteractive()
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (state.running) {
        LaunchedEffect(state.sessionId, awake) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(if (awake) 1000 else 60_000)
            }
        }
    }
    val context = LocalContext.current
    // The alarms still to ring, so a night begun with old or no alarms shows it; read once a minute.
    val config = remember(nowMs / 60_000) { WakeAlarms.parse(WakeAlarms.loadConfigJson(context)) }
    val listState = rememberTransformingLazyColumnState()
    val moon = remember { moonPhase(System.currentTimeMillis()) }
    val waitingOnly = state.waiting && !state.running
    ScreenScaffold(scrollState = listState) { contentPadding ->
        if (awake) WatchStars()
        if (awake && waitingOnly) BreathingRing(MaterialTheme.colorScheme.primary)
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!waitingOnly) { // while waiting, the big moon below shows it
                            Canvas(Modifier.size(16.dp)) { drawMoon(moon) } // tonight's real moon phase
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("OpenTDI")
                    }
                }
            }
            // First: a dream fades within minutes of waking.
            if (state.reportAfter != null) {
                item {
                    Button(onClick = onReport, modifier = Modifier.fillMaxWidth(), label = { Text("Record your dream") })
                }
            }
            // The WBTB awake time (sleep_and_dreams.md §4 [63][65], §6 [83]): the stay-up time first, then the
            // reality check, one simple dream goal, and the side to sleep on, short for the small screen.
            state.wbtbBackToSleepAtMs?.let { at ->
                item { BigLine("Up until ${WakeAlarms.hhmm(at)}") }
                item { Line("Dim light. Water only.") }
                item { Line("Pinch your nose and try to breathe. If you can, you're dreaming.") }
                item { Line("Replay your dream. Think: next time I'm dreaming, I'll notice.") }
                item { Line("Pick one thing to do in that dream.") }
                item { Line("When the watch buzzes, go back to bed and lie on your side.") }
            }
            if (state.guideOffered) {
                item { BigLine("Back to bed") }
                item {
                    Button(
                        onClick = onInBed,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("I'm in bed") },
                        secondaryLabel = { Text("Starts the sleep guide (19 min)") },
                    )
                }
            }
            // Only without the guided back-to-sleep: with it the cue is trained in bed, and the guide says this aloud.
            if (state.lateCuesTraining && state.wbtbBackToSleepAtMs != null) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(12.dp),
                    ) { Line("Tonight, when you hear this soft three-note sound, you are dreaming. Notice it.") }
                }
            }
            state.lastWakeText?.let { item { Line(it) } }
            if (waitingOnly) {
                item { Canvas(Modifier.fillMaxWidth().height(56.dp)) { drawMoon(moon) } } // tonight's real moon phase
                item { BigLine("Ready for tonight") }
                item { Line("Tap Start the night on your phone. The watch starts with it.") }
                item {
                    // Apps cannot read or change Always On Display, so the watch offers its display settings instead.
                    // With it off the watch went back to the watch face during the night (owner, 2026-10-29); with it
                    // on, this always-on app stays on the dim screen.
                    Button(
                        onClick = { openDisplaySettings(context) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Turn on Always On Display") },
                        // Two lines at most on the round screen: the main label already says what to switch on.
                        secondaryLabel = { Text("Then swipe right to come back") },
                    )
                }
            } else if (state.running) {
                item { BigLine(if (state.status == null) "Starting…" else "Tracking your sleep") }
                // Nothing when an alarm is on but more than 12 h away: it is set, just not tonight's to list.
                val alarms = alarmsLeft(config, state.wakeAgainAtMs, state.wbtbSkipTonight, System.currentTimeMillis())
                    ?.let { "${it.replaceFirstChar(Char::uppercase)}." }
                    ?: "No alarm set on the watch".takeIf { !config.wakeEnabled && !config.wbtbEnabled }
                alarms?.let { item { Line(it) } }
                if (state.wbtbSkipTonight) {
                    item { Line("No early wake-up tonight: it's less than 4½ hours after you went to bed.") }
                }
            } else if (needsSetup) {
                item { BigLine("One-time setup") }
                item { Line("Let OpenTDI read your heart rate and movement.") }
                item { Button(onClick = onAllow, modifier = Modifier.fillMaxWidth(), label = { Text("Allow") }) }
            } else if (state.sessionId != null) { // without one, the app is just starting to wait
                item { BigLine("Night saved") }
                item { Line("See it on your phone in Stats.") }
                item { Button(onClick = onStop, modifier = Modifier.fillMaxWidth(), label = { Text("Close OpenTDI") }) }
            }
            // Only problems; Details lists every status.
            for (status in statuses(state).filter { it !in FINE }) {
                item { StatusLine(status, state, onStatusTap, short = true) }
            }
            if (state.status == SensorStatus.SDK_POLICY_ERROR) {
                item {
                    FilledTonalButton(onClick = onBasicSensors, modifier = Modifier.fillMaxWidth()) {
                        Text("Use basic sensors")
                    }
                }
            }
            if (state.running) {
                item {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        secondaryLabel = { Text(elapsed(nowMs - state.startedElapsedMs)) },
                        label = { Text("End the night") },
                    )
                }
            }
            if (waitingOnly) {
                item {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Close OpenTDI") },
                        secondaryLabel = { Text("Open it again before bed") },
                    )
                }
            }
            item { FilledTonalButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Details") } }
        }
    }
}

/**
 * Everything else: the exact §11.6 texts, extra sensors, the raw accelerometer choice, the technical values, a night
 * without the phone and the saved nights.
 */
@Composable
private fun DetailsScreen(
    state: SensingState,
    logRawAccel: Boolean,
    onLogRawAccelChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onSessions: () -> Unit,
) {
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
            if (!state.running) {
                item {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Start without the phone") },
                        secondaryLabel = { Text("Alarms from your last night, no voice") },
                    )
                }
            }
            item {
                FilledTonalButton(onClick = onSessions, modifier = Modifier.fillMaxWidth()) { Text("Saved nights") }
            }
        }
    }
}

// The night is tracked in all of these; the basic sensors are the public app's normal case.
private val FINE = setOf(
    SensorStatus.CONNECTED_SAMSUNG,
    SensorStatus.FALLBACK_ANDROID_SENSORS,
    SensorStatus.SIMULATION,
    SensorStatus.AAR_NOT_BUNDLED,
    SensorStatus.TRACKER_UNSUPPORTED,
)

private fun statuses(state: SensingState) =
    listOfNotNull(state.fallbackCause?.takeIf { it != state.status }, state.status)

/** A status: a problem on the main screen ([short]) with its tap fix, or any status on Details, in plain words. */
@Composable
private fun StatusLine(status: SensorStatus, state: SensingState, onTap: (SensorStatus) -> Unit, short: Boolean) {
    val text = statusText(status, state.unsupported, state.edaAvailable, state.statusMessage)
    val tappable = status == SensorStatus.PERMISSION_MISSING ||
        (status in setOf(SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD) && state.resolve != null)
    if (short && tappable) {
        Card(onClick = { onTap(status) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
    } else {
        Line(text)
    }
}

/** Asks before End the night stops tracking and closes the app; Keep going, or swiping back, goes back. */
@Composable
private fun EndNightScreen(
    reportDue: Boolean,
    onReport: () -> Unit,
    alarmsAhead: String?,
    onEnd: () -> Unit,
    onKeep: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { BigLine("End the night?") }
            item { Line("Tracking stops and the app closes.") }

            alarmsAhead?.let { item { Line("${it.replaceFirstChar(Char::uppercase)} won't ring.") } }
            // Ending the night saves an unfinished report as it stands; questions not reached stay unanswered.
            if (reportDue) {
                item { Line("Your dream questions aren't done yet.") }
                item {
                    Button(
                        onClick = onReport,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Record your dream", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    )
                }
            }
            item {
                Button(
                    onClick = onKeep,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Keep going", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                )
            }
            item {
                Button(
                    onClick = onEnd,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("End the night", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

/**
 * While the guided back-to-sleep plays (owner, 2026-10-28), over every page: black. Holding the screen or the back key
 * stops it; a brush, a swipe or the bezel rolling against the pillow does not, since it cannot be restarted.
 */
@Composable
private fun GuideScreen(onStop: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onStop() }) },
        contentAlignment = Alignment.Center,
    ) {
        BackHandler(onBack = onStop)
        Line("Sleep guide playing. Hold the screen to stop it.", style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * A ringing alarm, whichever page was open: a dawn rising behind "Good morning", or tonight's moon for the early
 * (WBTB) wake, with "I'm awake" in the large button at the bottom edge.
 */
@Composable
private fun AlarmScreen(kind: String, snoozeMin: Int, snoozesLeft: Int, onAlarmStop: () -> Unit, onSnooze: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val moon = remember { moonPhase(System.currentTimeMillis()) }
    val morning = kind == "WAKE"
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onAlarmStop, buttonSize = EdgeButtonSize.Large) { Text("I'm awake") }
        },
    ) { contentPadding ->
        if (morning) Sunrise() else NightGlow()
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            if (!morning) item { Canvas(Modifier.fillMaxWidth().height(72.dp)) { drawMoon(moon) } }
            item {
                Text(
                    if (morning) "Good morning" else "Early wake-up",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Line(
                    if (morning) {
                        "Keep your eyes closed and replay your dream first."
                    } else {
                        "Up for a short while, then back to bed."
                    },
                )
            }
            if (snoozesLeft > 0) {
                item {
                    Button(
                        onClick = onSnooze,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Snooze $snoozeMin min") },
                        secondaryLabel = { Text("$snoozesLeft left") },
                    )
                }
            }
        }
    }
}

@Composable
private fun BigLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** Centred text: left-aligned lines are clipped by the round screen. */
@Composable
internal fun Line(text: String, style: TextStyle = LocalTextStyle.current) {
    Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = style)
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

/**
 * The alarms still to ring, worded as [alarmsAhead]: a snoozed or rung-out wake-up at its next ring
 * ([wakeAgainAtMs]), and no early wake-up that WakeAlarms will skip ([wbtbSkipTonight]).
 */
internal fun alarmsLeft(config: AlarmConfig, wakeAgainAtMs: Long?, wbtbSkipTonight: Boolean, nowMs: Long): String? =
    wakeAgainAtMs?.let { "the wake-up alarm at ${WakeAlarms.hhmm(it)}" }
        ?: alarmsAhead(config.copy(wbtbEnabled = config.wbtbEnabled && !wbtbSkipTonight), nowMs, ZoneId.systemDefault())

/** "2 h 5 min": in words, so the dim screen's once-a-minute update never reads like the clock. */
private fun elapsed(ms: Long): String {
    val min = ms.coerceAtLeast(0) / 60_000
    return "${min / 60} h ${min % 60} min"
}

/** The watch's display settings, where Always On Display is switched off; a watch without that screen does nothing. */
private fun openDisplaySettings(context: Context) {
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
    } catch (e: android.content.ActivityNotFoundException) {
        Unit
    }
}
