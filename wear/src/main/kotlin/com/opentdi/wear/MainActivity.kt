package com.opentdi.wear

import android.app.NotificationManager
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
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
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
import androidx.wear.compose.material3.ScreenScaffoldDefaults
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
import com.opentdi.shared.time.clock12
import com.opentdi.wear.alarm.WakeAlarms
import com.opentdi.wear.alarm.WakeBackup
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
    private var notificationsOn by mutableStateOf(true) // read on resume, so a change in Settings shows on return

    // Lint sees the androidx.fragment 1.1 that play-services pulls in; its bug is in FragmentActivity, not used here.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterPermissions?.invoke()
        afterPermissions = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        armTestNight(intent)
        answerBackup(intent)
        // Always-on: in ambient the app stays on the dim screen instead of the watch taking it back to the watch face.
        lifecycle.addObserver(AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {}))
        setContent {
            val state by SensingStateHolder.state.collectAsStateWithLifecycle()
            var logRawAccelChoice by rememberSaveable { mutableStateOf(AccelConfig().logRawAccel) }
            var page by rememberSaveable { mutableStateOf(Page.MAIN) }
            var reportFromEnd by rememberSaveable { mutableStateOf(false) } // opened from End the night
            var reportDir by rememberSaveable { mutableStateOf<String?>(null) } // last night's, after it ended
            // A report page restored after the app was killed has no night behind it, nor any folder the phone copies.
            // Last night's has its folder, but a new night starting ends it: tonight's alarms open their own.
            LaunchedEffect(state.sessionId, state.running) {
                val over = if (reportDir == null) state.sessionId == null else state.running
                if (page == Page.REPORT && over) page = Page.MAIN
                if (page == Page.AWAKE && !state.running) page = Page.MAIN // the night ended meanwhile
            }
            fun openReport(fromEnd: Boolean, lastNight: File? = null) {
                reportFromEnd = fromEnd
                reportDir = lastNight?.path
                page = Page.REPORT
            }
            // Awake in the night: only the recording, and the night goes on. A report already due keeps its kind.
            fun saveDream() {
                SensingStateHolder.update { it.copy(reportAfter = it.reportAfter ?: "NIGHT") }
                openReport(fromEnd = false)
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
                                    onStop = stop, // Close OpenTDI; End the night asks first, from Details
                                    onAllow = ::allowSensors,
                                    onBasicSensors = { startSession(logRawAccel, androidSensorsOnly = true) },
                                    onStatusTap = ::onStatusTap,
                                    notificationsOff = !notificationsOn,
                                    onAllowNotifications = ::allowNotifications,
                                    onInBed = { HealthTrackingForegroundService.startGuide(this@MainActivity) },
                                    onReport = { openReport(fromEnd = false) },
                                    onLastNightReport = { openReport(fromEnd = false, lastNight = it) },
                                    onSkipWbtb = { HealthTrackingForegroundService.skipWbtb(this@MainActivity) },
                                    // The choice only when the early wake-up can be started now; otherwise the dream.
                                    onWokeUp = {
                                        val s = SensingStateHolder.state.value
                                        if (wbtbNowAt(s, System.currentTimeMillis()) != null) {
                                            page = Page.AWAKE
                                        } else {
                                            saveDream()
                                        }
                                    },
                                    onDetails = { page = Page.DETAILS },
                                )
                                Page.AWAKE -> AwakeScreen(
                                    wbtbAt = wbtbNowAt(state, System.currentTimeMillis()),
                                    onSaveDream = ::saveDream,
                                    onWbtbNow = {
                                        HealthTrackingForegroundService.wbtbNow(this@MainActivity)
                                        // Now, not when the service gets to it: the questions open as the early
                                        // wake-up's from the first frame.
                                        SensingStateHolder.update { it.copy(reportAfter = "WBTB") }
                                        openReport(fromEnd = false)
                                    },
                                )
                                Page.DETAILS -> DetailsScreen(
                                    state = state,
                                    logRawAccel = logRawAccel,
                                    onLogRawAccelChange = { logRawAccelChoice = it },
                                    onStart = { startSession(logRawAccel, androidSensorsOnly = false) },
                                    onSessions = { page = Page.SESSIONS },
                                    onEndNight = { page = Page.END_NIGHT },
                                )
                                Page.SESSIONS -> SessionsScreen(runningId = state.sessionId.takeIf { state.running })
                                Page.REPORT -> DreamReportScreen(
                                    // Last night's, by hand: not a morning report, so it stays out of the experiment.
                                    after = if (reportDir != null) "LATER" else state.reportAfter ?: "WAKE",
                                    dir = reportDir?.let(::File)
                                        ?: File(filesDir, "sessions/${state.sessionId ?: "reports"}"),
                                    requestMic = ::requestMic,
                                    onSaved = { SensingStateHolder.update { it.copy(reportAfter = null) } },
                                    // Read now, not when composed: a night ended from the phone has no End the night.
                                    onDone = {
                                        page = if (SensingStateHolder.state.value.running) backTo else Page.MAIN
                                    },
                                )
                                Page.END_NIGHT -> EndNightScreen(
                                    // During a snooze too: ended now, the wake-up never rings again to ask them.
                                    reportDue = state.reportAfter != null || state.wakeAgainAtMs != null,
                                    onReport = { openReport(fromEnd = true) },
                                    alarmsAhead = remember {
                                        val alarms = WakeAlarms.parse(WakeAlarms.loadConfigJson(this@MainActivity))
                                        alarmsLeft(
                                            alarms,
                                            state.wakeAgainAtMs,
                                            wbtbWontRing = state.wbtbAheadAtMs == null || state.wbtbSkip != null,
                                            System.currentTimeMillis(),
                                            state.wbtbAtMs,
                                        )
                                    },
                                    wbtbAhead = state.wbtbAheadAtMs != null,
                                    endLine = nightEndLine(
                                        WakeAlarms.parse(WakeAlarms.loadConfigJson(this@MainActivity)).wakeEnabled,
                                        System.currentTimeMillis() -
                                            (SystemClock.elapsedRealtime() - state.startedElapsedMs),
                                    ),
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

    override fun onNewIntent(intent: Intent) { // singleTop: an open app gets the adb start here
        super.onNewIntent(intent)
        armTestNight(intent)
        answerBackup(intent)
    }

    /** Opened from the backup's last-resort "Good morning" notification: that tap answers the wake-up. */
    private fun answerBackup(intent: Intent?) {
        val night = intent?.getLongExtra(WakeBackup.EXTRA_ANSWERED, -1L) ?: -1L
        if (night >= 0) WakeBackup.answered(this, night)
    }

    /** Device test (debug builds): `am start ... --ez com.opentdi.TEST_NIGHT true`; see WakeAlarms.takeTestNight. */
    private fun armTestNight(intent: Intent?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_TEST_NIGHT, false) == true) {
            WakeAlarms.armTestNight(System.currentTimeMillis())
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
                openAppSettings() // back from there, onResume waits for the phone
            }
        }
        permissionRequest.launch(missingPermissions().toTypedArray())
    }

    /**
     * Refused for good, Android shows no dialog any more: the app's settings page is where to allow it. [first], a
     * closer page, where the watch has it.
     */
    private fun openAppSettings(first: Intent? = null) {
        val details = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null),
        )
        for (intent in listOfNotNull(first, details)) {
            try {
                startActivity(intent)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                Unit
            }
        }
    }

    private fun notificationsEnabled() = getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** The notifications card: asks while Android still shows its dialog, otherwise opens the notification settings. */
    private fun allowNotifications() {
        val settings = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openAppSettings(settings)
        } else {
            afterPermissions = {
                if (!notificationsEnabled() &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    openAppSettings(settings)
                }
            }
            permissionRequest.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
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
        notificationsOn = notificationsEnabled()
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

    override fun onStop() {
        super.onStop()
        // Closed or behind the watch face with no night tracked or awaited, nothing keeps the app alive: write a report
        // left without Save now. Not under a permission dialog: the app comes straight back to it.
        val s = SensingStateHolder.state.value
        if (afterPermissions == null && !s.running && !s.waiting && ReportDraft.flush()) {
            SensingStateHolder.update { it.copy(reportAfter = null) }
        }
    }

    private fun onStatusTap(status: SensorStatus) {
        when (status) {
            SensorStatus.HSS_NOT_INSTALLED, SensorStatus.HSS_TOO_OLD ->
                resolving = SensingStateHolder.state.value.resolve?.invoke(this) == true
            SensorStatus.PERMISSION_MISSING -> {
                val hr = heartRatePermission(Build.VERSION.SDK_INT)
                afterPermissions = {
                    val s = SensingStateHolder.state.value
                    if (checkSelfPermission(hr) == PackageManager.PERMISSION_GRANTED) {
                        // A dropped tracker comes back only in a new session (§11.6), with the same sensor choice; a
                        // grant made in Settings too, which this request no longer sees as new.
                        if (s.running) {
                            val basicChosen = s.source == SensorSource.ANDROID_SENSORS && s.fallbackCause == null
                            HealthTrackingForegroundService.start(this, s.logRawAccel, androidSensorsOnly = basicChosen)
                        }
                    } else if (!shouldShowRequestPermissionRationale(hr)) {
                        openAppSettings()
                    }
                }
                permissionRequest.launch(missingPermissions().toTypedArray())
            }
            else -> Unit
        }
    }
}

private enum class Page { MAIN, AWAKE, DETAILS, SESSIONS, REPORT, END_NIGHT }

private const val EXTRA_TEST_NIGHT = "com.opentdi.TEST_NIGHT"
private const val UI_PREFS = "ui"
private const val KEY_AOD_ASKED = "aodAsked" // the waiting page's Turn on Always On Display was tapped once

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
 * The main screen: waiting for the phone, the one-time setup, the night being tracked with its alarms and one big
 * button for the moment (I woke up), or the night saved; after an alarm, the dream questions and the early wake-up come
 * first. End the night is under Details: the night ends by itself.
 */
@Composable
private fun MainScreen(
    state: SensingState,
    needsSetup: Boolean,
    onStop: () -> Unit,
    onAllow: () -> Unit,
    onBasicSensors: () -> Unit,
    onStatusTap: (SensorStatus) -> Unit,
    notificationsOff: Boolean,
    onAllowNotifications: () -> Unit,
    onInBed: () -> Unit,
    onReport: () -> Unit,
    onLastNightReport: (File) -> Unit,
    onSkipWbtb: () -> Unit,
    onWokeUp: () -> Unit,
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
    // Read each time the app comes back, as the 14 hours run out while it waits.
    var lastNight by remember { mutableStateOf<File?>(null) }
    LifecycleResumeEffect(state.running) {
        lastNight = WakeAlarms.lastNight(context)?.first
        onPauseOrDispose {}
    }
    val wbtbAt = state.wbtbAheadAtMs // tonight's early wake-up, while it can still be skipped or started
    // Keyed on it: a question left open as the early wake-up rang or moved must not come back for another one.
    var askSkip by remember(wbtbAt) { mutableStateOf(false) }
    // Asked once: after that it lives in Details, so a watch with it already on isn't nagged every night.
    val ui = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    var aodAsked by remember { mutableStateOf(ui.getBoolean(KEY_AOD_ASKED, false)) }
    // A night whose tracking died keeps its wake-up (WakeBackup): shown and offered off, as nothing else can stop it.
    val backupAt = state.backupAtMs.takeIf { !state.running }
    var askBackupOff by remember(backupAt) { mutableStateOf(false) }
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
            // First: a dream fades within minutes of waking. Not with "I woke up", which opens the same dream.
            if (state.reportAfter != null && !showIWokeUp(state)) {
                item {
                    Button(onClick = onReport, modifier = Modifier.fillMaxWidth(), label = { Text("Save my dream") })
                }
            }
            // A dream recalled after End the night still goes with that night, for Stats.
            lastNight?.takeIf { !state.running && state.reportAfter == null }?.let { dir ->
                item {
                    Button(
                        onClick = { onLastNightReport(dir) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Save my dream") },
                        secondaryLabel = { Text("From last night") },
                    )
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
                    ) { Line("If you hear this soft three-note sound in a dream tonight, you'll know it's only a dream.") }
                }
            }
            state.lastWakeText?.let { item { Line(it) } }
            backupAt?.let { at ->
                item { Line("Your wake-up alarm still rings at ${WakeAlarms.hhmm(at)}.") }
                item {
                    Button(
                        onClick = { askBackupOff = true },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Turn it off for this morning") },
                    )
                }
            }
            if (waitingOnly) {
                item { Canvas(Modifier.fillMaxWidth().height(56.dp)) { drawMoon(moon) } } // tonight's real moon phase
                item { BigLine("Ready for tonight") }
                item { Line("Tap Start the night on your phone. The watch starts with it.") }
                if (!aodAsked) item {
                    // Apps cannot read or change Always On Display, so the watch offers its display settings instead.
                    // With it off the watch went back to the watch face during the night (owner, 2026-10-29); with it
                    // on, this always-on app stays on the dim screen.
                    Button(
                        onClick = {
                            ui.edit { putBoolean(KEY_AOD_ASKED, true) }
                            aodAsked = true
                            openDisplaySettings(context)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Turn on Always On Display") },
                        // Two lines at most on the round screen: the main label already says what to switch on.
                        secondaryLabel = { Text("Then swipe right to come back") },
                    )
                }
            } else if (state.running) {
                item { BigLine(if (state.status == null) "Starting…" else "Sleep well") }
                // Nothing when an alarm is on but more than 12 h away: it is set, just not tonight's to list.
                val wbtbWontRing = wbtbAt == null || state.wbtbSkip != null
                val now = System.currentTimeMillis()
                val alarms = alarmsLeft(config, state.wakeAgainAtMs, wbtbWontRing, now, state.wbtbAtMs)
                    ?.let { "${it.replaceFirstChar(Char::uppercase)}." }
                    ?: "No alarm set on the watch".takeIf { !config.wakeEnabled && !config.wbtbEnabled }
                alarms?.let { item { Line(it) } }
                when (state.wbtbSkip) {
                    "MIN_SLEEP_BEFORE_WBTB" ->
                        item { Line("No early wake-up tonight: it's less than 4½ hours after you went to bed.") }
                    "PASSED" -> item { Line("No early wake-up tonight: its time had already passed.") }
                }
                if (state.wbtbSkippedTonight) item { Line("Early wake-up skipped tonight.") }
                if (showIWokeUp(state)) {
                    item {
                        Button(onClick = onWokeUp, modifier = Modifier.fillMaxWidth(), label = { Text("I woke up") })
                    }
                }
                if (wbtbAt != null) {
                    item {
                        Button(
                            onClick = { askSkip = true },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Skip the early wake-up") },
                            secondaryLabel = { Text("Tonight only") },
                        )
                    }
                }
                item {
                    val startMs = remember(state.startedElapsedMs) {
                        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - state.startedElapsedMs)
                    }
                    Line(nightEndLine(config.wakeEnabled, startMs), style = MaterialTheme.typography.labelSmall)
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
            // Without notifications a ring has no full-screen alarm, and behind the watch face no button stops it.
            if (notificationsOff && (state.waiting || state.running)) {
                item {
                    Card(onClick = onAllowNotifications, modifier = Modifier.fillMaxWidth()) {
                        Text("Notifications are off, so an alarm could ring with no button to stop it. Tap to allow.")
                    }
                }
            }
            if (state.status == SensorStatus.SDK_POLICY_ERROR) {
                item {
                    FilledTonalButton(onClick = onBasicSensors, modifier = Modifier.fillMaxWidth()) {
                        Text("Use basic sensors")
                    }
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
    // Asked first: tonight's early wake-up cannot be brought back once skipped.
    if (wbtbAt != null) {
        val at = WakeAlarms.hhmm(wbtbAt)
        // Timed from falling asleep and not yet decided: it rings by then, maybe sooner.
        val skipWhen = if (state.wbtbAtMs == null && config.wbtbAfterSleepMin != null) "by $at" else "at $at"
        AskFirst(
            visible = askSkip,
            title = "Skip tonight's early wake-up $skipWhen?",
            text = config.wakeAtMin.takeIf { config.wakeEnabled }
                ?.let { "Your wake-up alarm at ${clock12(it)} still rings." },
            onDismiss = { askSkip = false },
            onConfirm = onSkipWbtb,
        )
    }
    // Clears the record, its system alarm and any last-resort notification; the app goes on waiting for tonight.
    backupAt?.let { at ->
        AskFirst(
            visible = askBackupOff,
            title = "Turn off the wake-up alarm at ${WakeAlarms.hhmm(at)}?",
            text = "It won't ring this morning.",
            onDismiss = { askBackupOff = false },
            onConfirm = { WakeBackup.set(context, null, "USER_OFF") },
        )
    }
}

/**
 * After "I woke up" with tonight's early wake-up startable now ([wbtbAt]): save a dream and sleep again, or do the early
 * wake-up now. Two clearly named taps are the confirmation the old "I'm up now" question gave.
 */
@Composable
private fun AwakeScreen(wbtbAt: Long?, onSaveDream: () -> Unit, onWbtbNow: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { BigLine("You're awake") }
            item {
                Button(
                    onClick = onSaveDream,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Save my dream") },
                    secondaryLabel = { Text("Then sleep again") },
                )
            }
            // Gone if it rang, passed or was skipped meanwhile: starting it then would do nothing.
            wbtbAt?.let { at ->
                item {
                    Button(
                        onClick = onWbtbNow,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Do my early wake-up now") },
                        secondaryLabel = { Text("${WakeAlarms.hhmm(at)} won't ring") },
                    )
                }
            }
        }
    }
}

/** A yes or no question; yes closes it and then does [onConfirm]. */
@Composable
private fun AskFirst(visible: Boolean, title: String, text: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            )
        },
        // Not cut at the usual three lines with a large font: the time is the point of the question.
        title = { Text(title, maxLines = Int.MAX_VALUE) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        text = if (text == null) null else { { Text(text) } },
    )
}

/**
 * Everything else: the exact §11.6 texts, extra sensors, the raw accelerometer choice, the technical values, a night
 * without the phone, the saved nights, Always On Display and, while tracking, End the night.
 */
@Composable
private fun DetailsScreen(
    state: SensingState,
    logRawAccel: Boolean,
    onLogRawAccelChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onSessions: () -> Unit,
    onEndNight: () -> Unit,
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
            item {
                Button(
                    onClick = { openDisplaySettings(context) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Always On Display") },
                    secondaryLabel = { Text("Keeps OpenTDI on the screen at night") },
                )
            }
            // Last and out of the way: the night ends by itself, and ending it cancels the alarms still to come.
            if (state.running) {
                item {
                    Button(
                        onClick = onEndNight,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("End the night now") },
                        secondaryLabel = { Text("It ends by itself in the morning") },
                    )
                }
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
    val text = statusText(status, state.unsupported, state.edaAvailable)
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
    wbtbAhead: Boolean,
    endLine: String, // nightEndLine: with the wake-up alarm off there is no morning alarm to end it
    onEnd: () -> Unit,
    onKeep: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { BigLine("End the night?") }
            item { Line("Tracking stops and the app closes.") }
            item { Line(endLine) }
            // Ending in the middle of the night used to cancel the alarms still to come without a word.
            alarmsAhead?.let { item { Line("${it.replaceFirstChar(Char::uppercase)} won't ring.") } }
            if (wbtbAhead) {
                item { Line("Only want to skip the early wake-up? Go back and tap Skip the early wake-up.") }
            }
            // Ending the night saves an unfinished report as it stands; questions not reached stay unanswered.
            if (reportDue) {
                item { Line("Your dream questions aren't done yet.") }
                item {
                    Button(
                        onClick = onReport,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Save my dream", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
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
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onStop() }) }
            // As the scrolling pages have: with large text the lines reach the round screen's edge.
            .padding(ScreenScaffoldDefaults.contentPadding),
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
                        "Lie still and replay your dream first."
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
        "SpO2 ${state.lastSpo2?.let { "$it % at ${state.lastSpo2AtMs?.let(WakeAlarms::hhmm) ?: "—"}" } ?: "—"}",
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
 * ([wakeAgainAtMs]), and no early wake-up that won't ring tonight ([wbtbWontRing]: too soon after bedtime, rung,
 * skipped or started early).
 */
internal fun alarmsLeft(
    config: AlarmConfig,
    wakeAgainAtMs: Long?,
    wbtbWontRing: Boolean,
    nowMs: Long,
    wbtbAtMs: Long? = null, // the watch's decided early wake-up (alarmsAhead)
): String? = wakeAgainAtMs?.let { "the wake-up alarm at ${WakeAlarms.hhmm(it)}" } ?: alarmsAhead(
    config.copy(wbtbEnabled = config.wbtbEnabled && !wbtbWontRing), nowMs, ZoneId.systemDefault(), wbtbAtMs,
)

/**
 * "I woke up" only when nothing else is due first: a ring, a snooze, dream questions, the stay-up time or the guide. A
 * dream left unsaved in the night does not hide it: "I woke up" goes on with that dream, or to the early wake-up.
 */
internal fun showIWokeUp(state: SensingState): Boolean =
    state.running && state.alarmRinging == null && state.wakeAgainAtMs == null &&
        (state.reportAfter == null || state.reportAfter == "NIGHT") &&
        state.wbtbBackToSleepAtMs == null && !state.guideOffered && !state.guideRunning

/**
 * Tonight's early wake-up, while "Do my early wake-up now" can start it: not before a ringing one could, when it reads as
 * "I'm still up" (WakeAlarms.startWbtbNow does nothing then). Null otherwise.
 */
internal fun wbtbNowAt(state: SensingState, nowMs: Long): Long? =
    state.wbtbAheadAtMs?.takeIf { state.wbtbNowFromMs?.let { nowMs >= it } == true }

/**
 * When the night ends by itself (WakeAlarms.nightOver): after the wake-up alarm, or else 12 h after this recording
 * began ([startMs]); after a restart in the night that reads a little late, never early.
 */
internal fun nightEndLine(wakeEnabled: Boolean, startMs: Long): String =
    if (wakeEnabled) {
        "The night ends by itself after your wake-up alarm."
    } else {
        "The night ends by itself by ${WakeAlarms.hhmm(startMs + WakeAlarms.NEW_NIGHT_MS)}."
    }

/** The watch's display settings, where Always On Display is switched off; a watch without that screen does nothing. */
private fun openDisplaySettings(context: Context) {
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
    } catch (e: android.content.ActivityNotFoundException) {
        Unit
    }
}
