package com.opentdi.mobile.ui

import android.content.pm.ApplicationInfo
import android.os.SystemClock
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.opentdi.mobile.link.WatchTelemetry
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.audio.alarmsAhead
import com.opentdi.shared.config.MAX_CUE_TEXT_CHARS
import com.opentdi.shared.config.validate
import com.opentdi.shared.model.SessionConfig
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

private val OK_COLOR = Color(0xFF86B79C) // muted green, dim enough for a dark bedroom

/**
 * Tonight: what the night is doing, Start/Stop, tonight's plan, what to expect, and the Quick test and Bedtime tips
 * buttons. Start opens a popup only when something needs fixing.
 * While a session runs only the status, Stop and any problem stay on screen.
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
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
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
    // null until the watch's first answer (up to 5 s), so nothing says "Can't reach the watch" on opening.
    val watchAnswer by remember(viewModel) { viewModel.watchStatus.map { Optional.ofNullable(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val watchStatus = watchAnswer?.orElse(null)
    val checkingWatch = watchAnswer == null && !streaming
    val watch = if (streaming) WatchState.RECORDING else watchStatus?.state
    val onWatch = config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER
    val alarmsOn = config.alarms.wakeEnabled || config.alarms.wbtbEnabled
    val running = status.running
    val checks = checklist(
        volumeOff, dnd, ui.batteryUnrestricted, cueProblem,
        if (onWatch) ui.watchTested else ui.calibratedIndex != null, watch, onWatch,
        watchStatus?.batteryPct ?: live.watchBatteryPct.takeIf { streaming },
        alarmsOn = alarmsOn,
        firstCueMin = config.timed.firstSleepMaxMin.takeIf { testTiming(config) },
        dndOff = dndOff,
        speakerOff = speakerOff,
        running = running && !status.quickTest,
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
    // Not held back by the watch's first answer (up to 5 s), so the headline does not flip on opening; Start still waits.
    val ready = errors.isEmpty() && problems(quick = false).isEmpty()
    // The pre-flight popup (owner, 2026-10-29: the checklist was "so ugly ... make it a one time popup"): Start opens
    // it only when something needs attention, listing just that. null = closed, else whether it is for a Quick test.
    var startAsk by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // The message when the popup opened, left out of it: it can be hours old (a watch test in Settings), and the
    // bedtime layout shows it under Start anyway.
    var oldMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmStopWatch by rememberSaveable { mutableStateOf(false) }
    var tipsOpen by rememberSaveable { mutableStateOf(!tipsSeen(context)) }

    fun fix(check: Check): Pair<String, () -> Unit>? {
        val fix = when (check) {
            Check.TIMING -> "Use night timing" to { viewModel.edit(TimingPreset.LUCID_NIGHT::applyTo) }
            Check.VOLUME -> "Turn up" to onRaiseVolume
            Check.SPEAKER -> "Bluetooth" to onOpenBluetooth
            Check.DND -> "Open settings" to onOpenDnd
            Check.BATTERY -> if (ui.batteryManual) null else "Allow" to onAllowBattery
            Check.CUES -> "Get the voice ready" to {
                oldMessage = null // so the popup shows this try's failure even when it repeats the old one
                viewModel.prepareCues()
            }
            Check.TEST_CUE -> "Test the voice" to {
                oldMessage = null
                viewModel.testCue()
            }
            Check.WATCH, Check.WATCH_BATTERY -> null
        }
        val fixEnabled = when (check) {
            Check.CUES -> idle && config.theme.isNotBlank() && config.theme.length <= 60
            Check.TEST_CUE -> idle && cueProblem == null && (onWatch || (!volumeOff && !speakerOff))
            else -> true
        }
        return fix?.takeIf { fixEnabled }
    }

    fun begin(quick: Boolean) = if (quick) onQuickTest() else onStart()
    fun tryStart(quick: Boolean) {
        val clear = errors.isEmpty() && !waitsForWatch(quick) && problems(quick).isEmpty()
        if (clear && (quick || !nightUnderway)) {
            begin(quick)
        } else {
            oldMessage = ui.cueMessage
            startAsk = quick
        }
    }

    Page("Tonight") {
        MoonHeader(remember { System.currentTimeMillis() })
        StatusCard(status, now, ready, nightDone)
        if (ui.alarmsNotSent) Text(ALARMS_NOT_SENT, color = MaterialTheme.colorScheme.error)
        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
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
            // After the voice the next step is ending the night in the morning, so that is the big button.
            Button(onClick = { confirmStopWatch = true }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("End the night", style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = { tryStart(quick = false) }, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text("Start the voice again")
            }
        } else {
            StartButton(enabled = idle, glow = idle && ready && !nightUnderway) { tryStart(quick = false) }
            // Also what Get the voice ready said on leaving Settings, such as the sleep guide not reaching the watch:
            // Start does not stop on it, so this is the only place it shows.
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
            // The watch tracks the whole night, past the end of the voice: in the morning it is stopped here.
            if (nightUnderway) {
                OutlinedButton(onClick = { confirmStopWatch = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("End the night")
                }
            }
            NightPlanCard(config, ZonedDateTime.now())
            InstructionsCard(config)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { tryStart(quick = true) }, enabled = idle, modifier = Modifier.weight(1f)) {
                    Text("Quick test (5 min)")
                }
                OutlinedButton(onClick = { tipsOpen = true }, modifier = Modifier.weight(1f)) { Text("Bedtime tips") }
            }
        }
    }

    startAsk?.let { quick ->
        val left = problems(quick)
        val again = !quick && nightUnderway
        val blocked = !idle || errors.isNotEmpty() || waitsForWatch(quick) || left.any { it.blocking }
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
                    if (waitsForWatch(quick)) Text("Checking the watch…")
                    left.forEach { CheckRow(it, fix(it.check)) }
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
    if (confirmStopWatch) {
        // At night this button sits right under "Voice done": ending also cancels the alarms, and the watch holds an
        // unanswered dream report only in memory.
        val ahead = alarmsAhead(config.alarms, System.currentTimeMillis(), ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = { confirmStopWatch = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmStopWatch = false
                    viewModel.stopWatch()
                }) { Text("End the night") }
            },
            dismissButton = { TextButton(onClick = { confirmStopWatch = false }) { Text("Not yet") } },
            title = { Text("End the night?") },
            text = {
                Text(
                    "Answer the dream questions on the watch first. Then the watch stops tracking your sleep." +
                        (ahead?.let { " ${it.replaceFirstChar(Char::uppercase)} won't ring." } ?: ""),
                )
            },
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
private val NOT_WHILE_RUNNING = setOf(Check.TIMING, Check.TEST_CUE, Check.WATCH_BATTERY)

/** A settings error ([validate]) in plain words: the topic and the words a user can get wrong; Reset fixes the rest. */
private fun settingsProblem(error: String) = when {
    error.startsWith("theme") -> "Type a dream topic in Settings > Words."
    error.startsWith("audio.cueTexts") ->
        "One of the voice's lines is too long ($MAX_CUE_TEXT_CHARS letters at most). Shorten it in Settings > Words."
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
        modifier = Modifier.fillMaxWidth().height(64.dp).drawBehind {
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

@Composable
private fun StatusCard(status: SessionStatus, now: Long, ready: Boolean, nightDone: Boolean) {
    val (headline, cycle) = statusLines(status, now, ready, tracking = nightDone)
    val context = LocalContext.current
    val debuggable = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    Section(null) {
        AnimatedContent(
            headline,
            label = "headline",
            contentKey = { it.filterNot(Char::isDigit) }, // a calm fade when the state changes, not on every tick
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
        ) { Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
        if (!status.running && !ready && !nightDone && status.state != TdiState.SESSION_COMPLETE) {
            Help("Tap Start the night to see what's left.")
        }
        cycle?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
            LoopDots(currentCycle(status), status.maxCycles)
        }
        // The voice ends long before the night; the watch's tracking and alarms go on (owner, 2026-10-29).
        if (nightDone) {
            Text("The watch keeps tracking your sleep and runs your alarms. Tap End the night when you get up.")
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = "Needs attention" },
    ) {
        // The text says the same, so color is never the only cue.
        Canvas(Modifier.size(20.dp)) { drawCircle(color, radius = 5.dp.toPx()) }
        Text(item.text, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (fix != null) {
            FilledTonalButton(onClick = fix.second, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text(fix.first)
            }
        }
    }
}

@Composable
private fun InstructionsCard(config: SessionConfig) {
    Section("What to expect") {
        Text(whatToExpect(config), style = MaterialTheme.typography.bodyMedium)
    }
}
