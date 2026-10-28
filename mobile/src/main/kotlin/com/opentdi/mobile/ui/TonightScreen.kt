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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.cues.cueSetProblem
import com.opentdi.mobile.link.WatchTelemetry
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.config.validate
import com.opentdi.shared.config.warnings
import com.opentdi.shared.model.SessionConfig
import java.time.LocalTime
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
    onStop: () -> Unit,
    onAllowBattery: () -> Unit,
    onRaiseVolume: () -> Unit,
    onOpenDnd: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    val dnd by viewModel.dnd.collectAsStateWithLifecycle(initialValue = DndEffect.PLAYS)
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
    val errors = remember(config) { config.validate() }
    val warnings = remember(config) { config.warnings() }
    val cueProblem = remember(config, ui.cues) { cueSetProblem(ui.cues, config) }
    val streaming = live.receivedAtMs?.let { now - it <= STALE_MS } == true
    // null until the watch's first answer (up to 5 s), so its row does not flash "Can't reach the watch" on opening.
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
    )
    val ready = errors.isEmpty() && checks.none { it.blocking && !it.ok }
    val idle = !status.running && !ui.preparing && !ui.testing
    val running = status.running
    // Shown rows: no watch row before its first answer, and none a running session cannot act on.
    val shownChecks = checks.filterNot {
        (checkingWatch && it.check == Check.WATCH) ||
            (running && (it.check == Check.WATCH_BATTERY || it.check == Check.TEST_CUE))
    }
    val toFix = errors.size + shownChecks.count { it.blocking && !it.ok }

    fun fix(check: Check): Pair<String, () -> Unit>? {
        val fix = when (check) {
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

    Page("Tonight") {
        MoonHeader(remember { System.currentTimeMillis() })
        StatusCard(status, now, ready)
        if (!running) alarmsLine(config.alarms)?.let { Help(it) }
        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Stop session", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            StartButton(idle && ready, onStart)
            // A disabled button is barely visible at night, so the reason sits right under it.
            if (!ready) {
                Text(
                    when (toFix) {
                        0 -> "Checking the watch…" // only the watch's first answer is missing
                        1 -> "Fix 1 item below to start"
                        else -> "Fix $toFix items below to start"
                    },
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            // The watch records the whole night, past the end of the cues: in the morning it is stopped here.
            if (watch == WatchState.RECORDING) {
                OutlinedButton(onClick = viewModel::stopWatch, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop watch recording")
                }
            }
        }
        AnimatedVisibility(!running || shownChecks.any { !it.ok }, enter = Appear, exit = Disappear) {
            Section("Before you sleep") {
                // Problems first; a fixed one ticks, then folds away into the ready list.
                for (item in shownChecks) {
                    key(item.check) {
                        AnimatedVisibility(
                            !item.ok,
                            enter = expandVertically() + fadeIn(tween(500)),
                            exit = fadeOut(tween(300, delayMillis = 700)) + shrinkVertically(tween(300, delayMillis = 700)),
                        ) { CheckRow(item, fix(item.check)) }
                    }
                }
                val okItems = checks.filter { it.ok }
                if (!running) {
                    AnimatedVisibility(okItems.size == checks.size, enter = Appear, exit = Disappear) {
                        Text("All set for tonight", style = MaterialTheme.typography.titleMedium, color = OK_COLOR)
                    }
                    if (okItems.isNotEmpty()) Fold("✓ ${okItems.size} ready") { okItems.forEach { CheckRow(it, null) } }
                }
                if (!ui.batteryUnrestricted && ui.batteryManual) {
                    Help("Settings > Apps > OpenTDI > Battery > Unrestricted")
                }
                when {
                    ui.preparing -> Help("Preparing the voice cues…")
                    ui.testing -> Help("Playing the test cue…")
                }
                ui.cueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            }
        }
        AnimatedVisibility(!running, enter = Appear, exit = Disappear) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NightPlanCard(config, LocalTime.now().let { it.hour * 60 + it.minute })
                RoutineCard()
                PresetCard(viewModel, enabled = idle)
                InstructionsCard(config)
            }
        }
    }
}

/** Start, with a slow breathing halo while it can be tapped (composed only then, so it costs nothing otherwise). */
@Composable
private fun StartButton(ready: Boolean, onStart: () -> Unit) {
    val glow = if (ready) {
        rememberInfiniteTransition(label = "start").animateFloat(
            0f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
        )
    } else {
        null
    }
    val halo = MaterialTheme.colorScheme.primary
    Button(
        onClick = onStart,
        enabled = ready,
        modifier = Modifier.fillMaxWidth().height(64.dp).drawBehind {
            val v = glow?.value ?: 0f // read here, so only the drawing updates each frame
            if (v > 0f) {
                val g = 8.dp.toPx() * v
                drawRoundRect(
                    halo, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g),
                    CornerRadius(size.height / 2 + g), alpha = 0.30f * (1f - v),
                )
            }
        },
    ) {
        Text("Start session", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StatusCard(status: SessionStatus, now: Long, ready: Boolean) {
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

@Composable
private fun PresetCard(viewModel: MainViewModel, enabled: Boolean) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Section("Timing: ${presetOf(ui.config)?.label ?: "Custom"}") {
        PresetButtons(ui.config, enabled, viewModel::edit)
        Help(timingSummary(ui.config))
    }
}

/** The timing presets in a row, the one [config] matches filled (Tonight and Settings). */
@Composable
internal fun PresetButtons(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val current = presetOf(config)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        for (preset in TimingPreset.entries) {
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
