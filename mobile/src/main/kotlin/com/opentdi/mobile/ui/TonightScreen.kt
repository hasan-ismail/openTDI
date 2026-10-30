package com.opentdi.mobile.ui

import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.markTipsSeen
import com.opentdi.mobile.tipsSeen
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.cues.cueSetProblem
import com.opentdi.mobile.cues.guideNotOnWatch
import com.opentdi.mobile.link.WatchTelemetry
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.config.MAX_CUE_TEXT_CHARS
import com.opentdi.shared.config.validate
import java.time.ZonedDateTime
import java.util.Optional
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

private val OK_COLOR = Color(0xFF86B79C) // muted green, dim enough for a dark bedroom

/**
 * Tonight (owner, 2026-10-30: one button at bedtime, no choices at night): what the night is doing in one line and one
 * sentence, and Start the night or Stop the voice. Start opens a popup only when something needs fixing. Ending the
 * night is under Settings > More: it ends by itself after the morning alarm.
 */
@Composable
fun TonightScreen(
    viewModel: MainViewModel,
    onStart: () -> Unit,
    onQuickTest: () -> Unit,
    onStop: () -> Unit,
    onAllowBattery: () -> Unit,
    onRaiseVolume: () -> Unit,
    onOpenDnd: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenWatch: () -> Unit, // Settings > More > Watch, which installs the watch app
    quickAsk: Boolean, // Quick test was tapped in Settings > More
    onQuickAskSeen: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val carriedApk by viewModel.carriedApk.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    val speakerOff by viewModel.speakerOff.collectAsStateWithLifecycle(initialValue = false)
    val dnd by viewModel.dnd.collectAsStateWithLifecycle(initialValue = DndEffect.PLAYS)
    val dndOff by viewModel.dndOff.collectAsStateWithLifecycle(initialValue = false)
    val live by WatchTelemetry.live.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(SystemClock.elapsedRealtime()) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { // no ticking while the screen is off
            while (true) {
                value = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
    }
    val config = ui.config
    val context = LocalContext.current
    val errors = remember(config) { config.validate() }
    val cueProblem = remember(config, ui.cues) { cueSetProblem(ui.cues, config) }
    val streaming = live.receivedAtMs?.let { now - it <= STALE_MS } == true
    // null until the watch's first answer (up to 5 s), so nothing says "Can't reach the watch" on opening; after a tab
    // switch, the answer from just before.
    val watchAnswer by remember(viewModel) { viewModel.watchStatus.map { Optional.ofNullable(it) } }
        .collectAsStateWithLifecycle(initialValue = remember(viewModel) { viewModel.recentWatchAnswer() })
    val watchStatus = watchAnswer?.orElse(null)
    val checkingWatch = watchAnswer == null && !streaming
    val watch = if (streaming) WatchState.RECORDING else watchStatus?.state
    val onWatch = config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER
    val alarmsOn = config.alarms.wakeEnabled || config.alarms.wbtbEnabled
    val running = status.running
    // The popup opened the watch app once (owner, 2026-10-30: one button at bedtime).
    var openedWatch by rememberSaveable { mutableStateOf(false) }
    // When Start last opened it (elapsedRealtime), from the popup or at once: after 20 s the text says how by hand.
    var openedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    fun openWatch() {
        openedAt = SystemClock.elapsedRealtime()
        viewModel.openWatchApp()
    }
    val checks = checklist(
        volumeOff, dnd, ui.batteryUnrestricted, cueProblem,
        if (onWatch) ui.watchTested else ui.calibratedIndex != null, watch, onWatch,
        watchStatus?.batteryPct ?: live.watchBatteryPct.takeIf { streaming },
        alarmsOn = alarmsOn,
        firstCueMin = config.timed.firstSleepMaxMin.takeIf { testTiming(config) },
        dndOff = dndOff,
        speakerOff = speakerOff,
        running = running && !status.quickTest,
        watchAppOutdated = watchAppOutdated(watchStatus, carriedApk),
        openingWatch = openedAt?.let { now - it < 20_000 } == true,
        guideMissing = remember(config, ui.cues, ui.guideSentFor) { guideNotOnWatch(ui.cues, config, ui.guideSentFor) },
    )
    val idle = !status.running && !ui.preparing && !ui.testing
    fun waitsForWatch(quick: Boolean) = checkingWatch && (!quick || onWatch) // a phone-route Quick test never asks it
    // What Start stops on.
    fun problems(quick: Boolean) = checks.filter {
        !it.ok && !(checkingWatch && it.check == Check.WATCH) && !(quick && quickTestSkips(it, onWatch, watch))
    }
    // The watch tracks from Start the night until the morning, and says so after a phone restart too: starting
    // again would replay the falling-asleep voice, maybe near morning, so Start asks first.
    val nightUnderway = watch == WatchState.RECORDING
    // From the watch, not this process's memory: by morning the app may have been closed since the voice ended.
    val nightDone = nightUnderway && !status.running && !status.quickTest
    // Not held back by the watch's first answer (up to 5 s), so the headline does not flip on opening.
    val ready = errors.isEmpty() && problems(quick = false).isEmpty()
    // The pre-flight popup (owner, 2026-10-29: the checklist was "so ugly ... make it a one time popup"): Start opens
    // it only when something needs attention, listing just that. null = closed, else whether it is for a Quick test.
    var startAsk by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // The message when the popup opened, left out of it: it can be hours old (a watch test in Settings), and the
    // bedtime layout shows it under Start anyway.
    var oldMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var tipsOpen by rememberSaveable { mutableStateOf(!tipsSeen(context)) }

    fun fix(check: Check): Pair<String, () -> Unit>? {
        val fix = when (check) {
            Check.TIMING -> "Use night timing" to { viewModel.edit(TimingPreset.LUCID_NIGHT::applyTo) }
            Check.VOLUME -> "Turn up" to onRaiseVolume
            Check.SPEAKER -> "Bluetooth" to onOpenBluetooth
            Check.DND -> "Open settings" to onOpenDnd
            Check.BATTERY -> if (ui.batteryManual) null else "Allow" to onAllowBattery
            Check.CUES, Check.GUIDE -> "Get the voice ready" to {
                oldMessage = null // so the popup shows this try's failure even when it repeats the old one
                viewModel.prepareCues()
            }
            Check.TEST_CUE -> "Test the voice" to {
                oldMessage = null
                viewModel.testCue()
            }
            Check.WATCH_APP -> "Update the watch app" to {
                startAsk = null
                onOpenWatch()
            }
            Check.WATCH, Check.WATCH_BATTERY -> null
        }
        val fixEnabled = when (check) {
            Check.CUES, Check.GUIDE -> idle && config.theme.isNotBlank() && config.theme.length <= 60
            Check.TEST_CUE -> idle && cueProblem == null && (onWatch || (!volumeOff && !speakerOff))
            else -> true
        }
        return fix?.takeIf { fixEnabled }
    }

    fun begin(quick: Boolean) = if (quick) onQuickTest() else onStart()
    // No answer from the watch yet (up to 5 s after opening Tonight), or none since the screen came back on: the last
    // one can be hours old, such as "not tracking" from just before a night started.
    val unsure = checkingWatch || (!streaming && viewModel.recentWatchAnswer() == null)
    // A real Start tapped then waits for the answer, so a night already tracked still asks before replaying the voice,
    // and a low watch battery still shows.
    var startOnAnswer by rememberSaveable { mutableStateOf(false) }
    // Only the watch app is closed, as at most bedtimes: a real Start opens it and goes on at once, since the voice's
    // service resends Start until the watch tracks. Waiting here for the watch to open hung whenever the phone's screen
    // went off right after Start: no voice, no tracking and no alarms. Likewise when the screen goes off before the
    // watch's first answer: opening an app already open is harmless.
    fun onlyWatchClosed() =
        (watch == WatchState.IDLE || checkingWatch) && problems(false).all { it.check == Check.WATCH }
    fun tryStart(quick: Boolean, screenOff: Boolean = false) {
        val opens = !quick && onlyWatchClosed()
        val clear = errors.isEmpty() && (opens || (!waitsForWatch(quick) && problems(quick).isEmpty()))
        // Only a Start that would go on at once waits: a popup opens on the tap, while the user is still looking.
        if (!quick && unsure && !screenOff && clear && !nightUnderway) {
            startOnAnswer = true
            return
        }
        if (clear && (quick || !nightUnderway)) {
            if (opens) openWatch()
            begin(quick)
        } else {
            oldMessage = ui.cueMessage
            startAsk = quick
        }
    }
    // The popup the watch's answer opened (low watch battery, already tracking), maybe with nobody looking.
    var askFromWait by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startOnAnswer, unsure) {
        if (startOnAnswer && !unsure) {
            startOnAnswer = false
            if (idle) tryStart(quick = false)
            askFromWait = startAsk == false
        }
    }
    // Only a real Start opens the watch app, never a Quick test. It only opens: the watch then waits for this Start.
    LaunchedEffect(startAsk, watch) {
        if (startAsk == false && watch == WatchState.IDLE && !openedWatch) {
            openedWatch = true
            openWatch()
        }
    }
    LaunchedEffect(startAsk) {
        if (startAsk == null) {
            openedWatch = false
            askFromWait = false
        }
    }
    // Goes on by itself once only the watch opening is left, as tryStart; anything else keeps the popup open.
    val goOn = startAsk == false && openedWatch && idle && errors.isEmpty() && !waitsForWatch(false) && !unsure &&
        (problems(false).isEmpty() || onlyWatchClosed()) && !nightUnderway
    LaunchedEffect(goOn) {
        if (goOn) {
            startAsk = null
            begin(false)
        }
    }
    // With the screen off the watch's answers stop arriving: a popup left open must not start the night at the next
    // unlock, maybe in the morning. A rotation is not leaving.
    val activity = LocalActivity.current
    // A Start still waiting for the watch goes on as before the wait: the user thinks the night has started.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            if (startOnAnswer && idle) tryStart(quick = false, screenOff = true)
            // The answer's popup, unseen until the screen went off (not left for a fix's Settings page): the Start
            // goes on as at a tap of Start anyway; a closed watch app is opened, as tryStart does.
            if (askFromWait && startAsk == false && idle && errors.isEmpty() && !nightUnderway &&
                problems(false).all { !it.blocking || (it.check == Check.WATCH && watch == WatchState.IDLE) } &&
                context.getSystemService(PowerManager::class.java)?.isInteractive == false
            ) {
                if (watch == WatchState.IDLE) openWatch()
                begin(false)
            }
            startOnAnswer = false
            startAsk = null
        }
    }
    // A tab switch disposes this screen; a Start still waiting for the watch goes on as at screen-off.
    val goOnLeaving by rememberUpdatedState { if (startOnAnswer && idle) tryStart(quick = false, screenOff = true) }
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) goOnLeaving() } }
    // Always through the popup: on arriving here the volume, speaker and Do Not Disturb checks have not been read yet.
    LaunchedEffect(quickAsk) {
        if (quickAsk) {
            onQuickAskSeen()
            oldMessage = ui.cueMessage
            startAsk = true
        }
    }

    val sentence = when {
        // The watch's own list when it sends one: only it knows a WBTB skipped or too soon to ring.
        nightDone -> nightOnText(alarmsLeftTonight(watchStatus, config.alarms), config.alarms.wakeEnabled)
        // Not after a phone-only night's voice: it describes a night started now.
        !running && (status.state != TdiState.SESSION_COMPLETE || status.quickTest) ->
            tonightSummary(config, ZonedDateTime.now())
        else -> null
    }
    Page("Tonight") {
        MoonHeader(remember { System.currentTimeMillis() })
        StatusCard(
            status, now, nightDone, sentence,
            watchLink = when {
                streaming || watchStatus != null -> true
                watchAnswer != null -> false // asked, no answer
                else -> null
            },
        )
        if (ui.alarmsNotSent) Text(ALARMS_NOT_SENT, color = MaterialTheme.colorScheme.error)
        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("Stop the voice", style = MaterialTheme.typography.titleLarge)
            }
            // Only while the watch tracks: a Quick test arms no alarm, and a watch that missed Start rings none.
            if (alarmsOn && nightUnderway) Help("Your alarms on the watch stay on.")
            // Only what can go wrong while it runs, such as the watch out of reach or the volume turned off.
            val during = checks.filter {
                !it.ok && it.check !in NOT_WHILE_RUNNING && !(checkingWatch && it.check == Check.WATCH) &&
                    !(status.quickTest && quickTestSkips(it, onWatch, watch))
            }
            AnimatedVisibility(during.isNotEmpty(), enter = Appear, exit = Disappear) {
                Section(null) { during.forEach { CheckRow(it, fix(it.check)) } }
            }
        } else if (nightDone) {
            // No End the night here (owner, 2026-10-30): at midnight it cancelled both alarms; the night ends by itself.
            TextButton(onClick = { tryStart(quick = false) }, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text("Start the voice again")
            }
        } else {
            StartButton(enabled = idle, glow = idle && ready && !nightUnderway) { tryStart(quick = false) }
            if (startOnAnswer) Help("Checking the watch…")
            // Also what Get the voice ready said on leaving Settings, such as the voice it fell back to: Start does not
            // stop on it, so this is the only place it shows.
            if (ui.preparing) {
                Help(ui.prepareStatus)
            } else {
                ui.cueMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(
                        onClick = viewModel::prepareCues,
                        enabled = idle && config.theme.isNotBlank() && config.theme.length <= 60,
                    ) { Text("Get the voice ready") }
                }
            }
        }
    }

    startAsk?.let { quick ->
        val left = problems(quick)
        val again = !quick && nightUnderway
        // Held until a fresh watch answer: "already tracking" and a low watch battery show before anyone can tap.
        val blocked = !idle || errors.isNotEmpty() || waitsForWatch(quick) || (!quick && unsure) ||
            left.any { it.blocking }
        AlertDialog(
            onDismissRequest = { startAsk = null },
            title = { Text(if (quick) "Before the quick test" else "Before you start") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (again) {
                        Text(
                            if (nightDone && status.state == TdiState.SESSION_COMPLETE) {
                                "Tonight's voice is done, and the watch is still tracking your sleep. Start the " +
                                    "voice again?"
                            } else {
                                "The watch is already tracking your sleep. Start the voice now?"
                            },
                        )
                    }
                    errors.map(::settingsProblem).distinct().forEach {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (errors.any { !it.startsWith("theme") && !it.startsWith("audio.cueTexts") }) { // keeps those
                        TextButton(onClick = { viewModel.edit(::withDefaultNumbers) }, enabled = idle) {
                            Text("Reset to normal")
                        }
                    }
                    if (waitsForWatch(quick) || (!quick && unsure)) Text("Checking the watch…")
                    // Grouped, so the dot's color is not the only sign of what keeps Start off.
                    val (must, could) = left.partition { it.blocking }
                    if (must.isNotEmpty()) Text("Fix this to start:", style = MaterialTheme.typography.titleSmall)
                    must.forEach { CheckRow(it, fix(it.check)) }
                    if (could.isNotEmpty()) Text("Good to fix:", style = MaterialTheme.typography.titleSmall)
                    could.forEach { CheckRow(it, fix(it.check)) }
                    if (left.any { it.check == Check.BATTERY } && ui.batteryManual) {
                        Help("Settings > Apps > OpenTDI > Battery > Unrestricted")
                    }
                    when {
                        ui.preparing -> Help(ui.prepareStatus)
                        ui.testing -> Help("Testing the voice…")
                    }
                    ui.cueMessage?.takeIf { it != oldMessage }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when {
                        left.isEmpty() && !blocked -> Text("All set.", color = OK_COLOR)
                        !blocked -> Help("You can start anyway; fixing these makes the night more reliable.")
                        must.isNotEmpty() -> Help("Fix the first ones to start.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startAsk = null
                    begin(quick)
                }, enabled = !blocked) {
                    Text(
                        when {
                            left.isNotEmpty() -> "Start anyway"
                            again -> "Start the voice"
                            else -> "Start"
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = { startAsk = null }) { Text("Not now") } },
        )
    }
    if (tipsOpen) {
        BedtimeTipsDialog {
            markTipsSeen(context)
            tipsOpen = false
        }
    }
}

/** Checks a running session cannot act on, or that only matter before it starts. */
private val NOT_WHILE_RUNNING = setOf(Check.TIMING, Check.TEST_CUE, Check.WATCH_BATTERY, Check.GUIDE)

/** A settings error ([validate]) in plain words: the topic and the words a user can get wrong; Reset fixes the rest. */
private fun settingsProblem(error: String) = when {
    error.startsWith("theme") -> "Type a dream topic in Settings › What the voice says."
    error.startsWith("audio.cueTexts") ->
        "One of the voice's lines is too long ($MAX_CUE_TEXT_CHARS letters at most). Shorten it in Settings › What " +
            "the voice says › Edit the words."
    else -> "Some settings don't fit together."
}

/** Start, with a slow breathing halo when nothing needs attention (composed only then, so it costs nothing otherwise). */
@Composable
private fun StartButton(enabled: Boolean, glow: Boolean, onStart: () -> Unit) {
    val halo = if (glow) {
        rememberInfiniteTransition(label = "start").animateFloat(
            0f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
        )
    } else {
        null
    }
    val color = MaterialTheme.colorScheme.primary
    Button(
        onClick = onStart,
        enabled = enabled,
        // At least 64 dp, not exactly: with large text the words take two lines.
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).drawBehind {
            val v = halo?.value ?: 0f // read here, so only the drawing updates each frame
            if (v > 0f) {
                val g = 8.dp.toPx() * v
                drawRoundRect(
                    color, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g),
                    CornerRadius(size.height / 2 + g), alpha = 0.30f * (1f - v),
                )
            }
        },
    ) {
        Text("Start the night", style = MaterialTheme.typography.titleLarge)
    }
}

/** The big line, then [sentence] (tonight in short, or what is left of the night) or the voice line while it plays. */
@Composable
private fun StatusCard(status: SessionStatus, now: Long, nightDone: Boolean, sentence: String?, watchLink: Boolean?) {
    val (headline, cycle) = statusLines(status, now, tracking = nightDone)
    val context = LocalContext.current
    val debuggable = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    Section(null) {
        // Green or red with words, so it never relies on colour alone; grey until the watch's first answer.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dot = when (watchLink) {
                true -> Color(0xFF66BB6A)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.outline
            }
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Text(
                when (watchLink) {
                    true -> "Watch connected"
                    false -> "Watch not connected"
                    null -> "Checking the watch…"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        AnimatedContent(
            headline,
            label = "headline",
            contentKey = { it.filterNot(Char::isDigit) }, // a calm fade when the state changes, not on every tick
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
        ) { Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
        sentence?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        cycle?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
            LoopDots(currentCycle(status), status.maxCycles)
        }
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (debuggable && (status.lastEvent != null || status.running)) {
            Fold("Technical details") {
                status.lastEvent?.let { Help("Last: $it") }
                if (status.running) Help("State ${status.state.name}")
            }
        }
    }
}

/** One dot per loop: done, the current one (half lit) and those to come. The cycle line says the same aloud. */
@Composable
private fun LoopDots(current: Int, total: Int) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 1..total) {
            val target = when {
                i < current -> colors.primary
                i == current -> colors.primary.copy(alpha = 0.5f)
                else -> colors.outline
            }
            val color by animateColorAsState(target, tween(500), label = "loop")
            Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        }
    }
}

/** A problem from [checklist]: only failing checks reach here. */
@Composable
private fun CheckRow(item: CheckItem, fix: Pair<String, () -> Unit>?) {
    val color = if (item.blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = "Needs attention" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The text says the same, so color is never the only cue.
            Canvas(Modifier.size(20.dp)) { drawCircle(color, radius = 5.dp.toPx()) }
            Text(item.text, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        // Under the words, lined up with them: beside them, large text left the words no room.
        if (fix != null) {
            FilledTonalButton(
                onClick = fix.second,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.padding(start = 32.dp),
            ) { Text(fix.first) }
        }
    }
}
