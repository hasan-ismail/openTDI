package com.opentdi.mobile.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.opentdi.shared.config.validate
import com.opentdi.shared.model.SessionConfig
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

private val OK_COLOR = Color(0xFF86B79C) // muted green, dim enough for a dark bedroom

/**
 * Tonight: what the session is doing, Start/Stop, the pre-flight checklist, the plan, the routine and the timing.
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
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
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
    val checks = checklist(
        volumeOff, dnd, ui.batteryUnrestricted, cueProblem,
        if (onWatch) ui.watchTested else ui.calibratedIndex != null, watch, onWatch,
        watchStatus?.batteryPct ?: live.watchBatteryPct.takeIf { streaming },
        alarmsOn = config.alarms.wakeEnabled || config.alarms.wbtbEnabled,
        firstCueMin = config.timed.firstSleepMaxMin.takeIf { testTiming(config) },
        dndOff = dndOff,
    )
    val idle = !status.running && !ui.preparing && !ui.testing
    val running = status.running
    // What Start stops on; a Quick test runs on its own timing, so test timing does not hold it back.
    fun problems(quick: Boolean) = checks.filter {
        !it.ok && !(quick && it.check == Check.TIMING) && !(checkingWatch && it.check == Check.WATCH)
    }
    // Not held back by the watch's first answer (up to 5 s), so the headline does not flip on opening; Start still waits.
    val ready = errors.isEmpty() && problems(quick = false).isEmpty()
    // The pre-flight popup (owner, 2026-10-29: the checklist was "so ugly ... make it a one time popup"): Start opens
    // it only when something needs attention, listing just that. null = closed, else whether it is for a Quick test.
    var startAsk by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var confirmStopWatch by rememberSaveable { mutableStateOf(false) }
    var tipsOpen by rememberSaveable { mutableStateOf(!tipsSeen(context)) }

    fun fix(check: Check): Pair<String, () -> Unit>? {
        val fix = when (check) {
            Check.TIMING -> "Use Lucid night" to { viewModel.edit(TimingPreset.LUCID_NIGHT::applyTo) }
            Check.VOLUME -> "Turn up" to onRaiseVolume
            Check.DND -> "Open settings" to onOpenDnd
            Check.BATTERY -> if (ui.batteryManual) null else "Allow" to onAllowBattery
            Check.CUES -> "Prepare cues" to viewModel::prepareCues
            Check.TEST_CUE -> "Play test cue" to viewModel::testCue
            Check.WATCH, Check.WATCH_BATTERY -> null
        }
        val fixEnabled = when (check) {
            Check.CUES -> idle && config.theme.isNotBlank() && config.theme.length <= 60
            Check.TEST_CUE -> idle && cueProblem == null && (onWatch || !volumeOff)
            else -> true
        }
        return fix?.takeIf { fixEnabled }
    }

    fun begin(quick: Boolean) = if (quick) onQuickTest() else onStart()
    fun tryStart(quick: Boolean) {
        if (errors.isEmpty() && !checkingWatch && problems(quick).isEmpty()) begin(quick) else startAsk = quick
    }

    Page("Tonight") {
        MoonHeader(remember { System.currentTimeMillis() })
        StatusCard(status, now, ready, watchRecording = watch == WatchState.RECORDING)
        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Stop session", style = MaterialTheme.typography.titleLarge)
            }
            // Only what can go wrong while it runs, such as the watch out of reach or the volume turned off.
            val during = checks.filter {
                !it.ok && it.check !in NOT_WHILE_RUNNING && !(checkingWatch && it.check == Check.WATCH)
            }
            AnimatedVisibility(during.isNotEmpty(), enter = Appear, exit = Disappear) {
                Section(null) { during.forEach { CheckRow(it, fix(it.check)) } }
            }
        } else {
            StartButton(enabled = idle, glow = idle && ready) { tryStart(quick = false) }
            if (ui.preparing) Help(ui.prepareStatus)
            // The watch records the whole night, past the end of the cues: in the morning it is stopped here.
            if (watch == WatchState.RECORDING) {
                OutlinedButton(onClick = { confirmStopWatch = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop watch recording")
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
        val blocked = !idle || errors.isNotEmpty() || checkingWatch || left.any { it.blocking }
        AlertDialog(
            onDismissRequest = { startAsk = null },
            title = { Text(if (quick) "Before the quick test" else "Before you start") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    errors.forEach { Text(settingsProblem(it), color = MaterialTheme.colorScheme.error) }
                    if (checkingWatch) Text("Checking the watch…")
                    left.forEach { CheckRow(it, fix(it.check)) }
                    if (left.any { it.check == Check.BATTERY } && ui.batteryManual) {
                        Help("Settings > Apps > OpenTDI > Battery > Unrestricted")
                    }
                    when {
                        ui.preparing -> Help(ui.prepareStatus)
                        ui.testing -> Help("Playing the test cue…")
                    }
                    ui.cueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                }, enabled = !blocked) { Text(if (left.isEmpty()) "Start" else "Start anyway") }
            },
            dismissButton = { TextButton(onClick = { startAsk = null }) { Text("Not now") } },
        )
    }
    if (confirmStopWatch) {
        // At night this button sits right under "Cues done": stopping also cancels the alarms.
        val ahead = alarmsAhead(config.alarms, System.currentTimeMillis(), ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = { confirmStopWatch = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmStopWatch = false
                    viewModel.stopWatch()
                }) { Text("Stop recording") }
            },
            dismissButton = { TextButton(onClick = { confirmStopWatch = false }) { Text("Keep recording") } },
            title = { Text("End the night on the watch?") },
            text = {
                Text("The watch stops recording." + (ahead?.let { " ${it.replaceFirstChar(Char::uppercase)} won't ring." } ?: ""))
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

/** A settings error ([validate]) in plain words; the theme is the one a user can easily get wrong. */
private fun settingsProblem(error: String) = if (error.startsWith("theme")) {
    "Type a dream theme of up to 60 characters in Settings."
} else {
    "A setting is out of range ($error). Change it in Settings under Show advanced settings."
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
private fun StatusCard(status: SessionStatus, now: Long, ready: Boolean, watchRecording: Boolean) {
    val (headline, cycle) = statusLines(status, now, ready)
    Section(null) {
        AnimatedContent(
            headline,
            label = "headline",
            contentKey = { it.filterNot(Char::isDigit) }, // a calm fade when the state changes, not on every tick
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
        ) { Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
        cycle?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
            LoopDots(currentCycle(status), status.maxCycles)
        }
        // The cue loops end long before the night; the watch's recording and alarms go on (owner, 2026-10-29).
        if (status.state == TdiState.SESSION_COMPLETE && watchRecording) {
            Text("The watch keeps recording and runs your alarms until you stop it in the morning.")
        }
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (status.lastEvent != null || status.running) {
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

@Composable
private fun CheckRow(item: CheckItem, fix: Pair<String, () -> Unit>?) {
    val color = when {
        item.ok -> OK_COLOR
        item.blocking -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val tick by animateFloatAsState(if (item.ok) 1f else 0f, tween(450), label = "tick")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            stateDescription = if (item.ok) "Done" else "Needs attention"
        },
    ) {
        // A dot that shrinks as a tick draws itself; the text says the same, so color is never the only cue.
        Canvas(Modifier.size(20.dp)) {
            if (tick < 1f) drawCircle(color, radius = 5.dp.toPx() * (1f - tick))
            if (tick > 0f) {
                val full = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.55f)
                    lineTo(size.width * 0.42f, size.height * 0.75f)
                    lineTo(size.width * 0.8f, size.height * 0.3f)
                }
                val part = Path()
                PathMeasure().apply {
                    setPath(full, false)
                    getSegment(0f, length * tick, part)
                }
                drawPath(part, OK_COLOR, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(
            item.text,
            color = if (item.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!item.ok && fix != null) {
            FilledTonalButton(onClick = fix.second, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text(fix.first)
            }
        }
    }
}

/** The night presets in a row, the one [config] matches filled (Tonight and Settings); Quick test has its own button. */
@Composable
internal fun PresetButtons(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val current = presetOf(config)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        for (preset in TimingPreset.entries - TimingPreset.QUICK_TEST) {
            val onClick = { edit(preset::applyTo) }
            if (preset == current) {
                Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(preset.label) }
            } else {
                OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(preset.label)
                }
            }
        }
    }
}

@Composable
private fun InstructionsCard(config: SessionConfig) {
    Section("What to expect") {
        Text(whatToExpect(config), style = MaterialTheme.typography.bodyMedium)
        Fold("Full research instructions") {
            Text(instructions(config.theme), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// SR23 Supplement §1.1 instructions ([P] §5.2), adapted to the theme as §7.1 asks.
private fun instructions(theme: String) =
    "This experiment is investigating the relationship between mental rest and cognitive flexibility. Varied " +
        "imagery, memories, words, or bodily feelings may come up throughout the experiment. The aim of this " +
        "exercise is to observe them, stay with them, follow them lightly and see where they go. One thing not to " +
        "worry about is questioning whether you are asleep. This period in between sleep and wake feels to some " +
        "people like sleep, to others just like relaxation or mind wandering. All are completely fine, just watch " +
        "your mind and relax. Sleep cannot be forced, just allowed. Head towards sleep, but don't worry at all " +
        "where you are in it, just relax.\n\n" +
        "After you lie down, you will be asked to think of $theme. Relax, hold that theme in your mind. A few " +
        "times, you will be told you are falling asleep and reminded of the dream theme. These prompts are not to " +
        "wake you up fully, just to make sure you do not descend into deep sleep, and to keep you aware so that you " +
        "can keep observing your mind. Just stay still when the prompts come. Again, we're interested in your " +
        "thinking in this semi-lucid period. Whenever you are prompted to report, please just vocally report what " +
        "was going through your mind, and report whether you think you were asleep, by either saying awake, " +
        "halfway or asleep. Then relax and drift towards sleep again."
