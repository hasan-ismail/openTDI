package com.opentdi.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.delay

private val OK_COLOR = Color(0xFF86B79C) // muted green, dim enough for a dark bedroom

/** Tonight: what the session is doing, Start/Stop, the pre-flight checklist and the timing presets. */
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
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val config = ui.config
    val errors = remember(config) { config.validate() }
    val warnings = remember(config) { config.warnings() }
    val cueProblem = remember(config, ui.cues) { cueSetProblem(ui.cues, config) }
    val streaming = live.receivedAtMs?.let { now - it <= STALE_MS } == true
    val watchStatus by viewModel.watchStatus.collectAsStateWithLifecycle(initialValue = null)
    val watch = if (streaming) WatchState.RECORDING else watchStatus?.state
    val onWatch = config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER
    val checks = checklist(
        volumeOff, dnd, ui.batteryUnrestricted, cueProblem,
        if (onWatch) ui.watchTested else ui.calibratedIndex != null, watch, onWatch,
        watchStatus?.batteryPct ?: live.watchBatteryPct.takeIf { streaming },
    )
    val ready = errors.isEmpty() && checks.none { it.blocking && !it.ok }
    val idle = !status.running && !ui.preparing && !ui.testing

    Page("Tonight") {
        StatusCard(status, now, ready)
        if (status.running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Stop session", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            Button(onClick = onStart, enabled = idle && ready, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Start session", style = MaterialTheme.typography.titleLarge)
            }
            // The watch records the whole night, past the end of the cues: in the morning it is stopped here.
            if (watch == WatchState.RECORDING) {
                OutlinedButton(onClick = viewModel::stopWatch, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop watch recording")
                }
            }
        }
        Section("Before you sleep") {
            for (item in checks) {
                val fix: Pair<String, () -> Unit>? = when (item.check) {
                    Check.VOLUME -> "Turn up" to onRaiseVolume
                    Check.DND -> "Settings" to onOpenDnd
                    Check.BATTERY -> if (ui.batteryManual) null else "Allow" to onAllowBattery
                    Check.CUES -> "Prepare" to viewModel::prepareCues
                    Check.TEST_CUE -> "Test cue" to viewModel::testCue
                    Check.WATCH, Check.WATCH_BATTERY -> null
                }
                val fixEnabled = when (item.check) {
                    Check.CUES -> idle && config.theme.isNotBlank() && config.theme.length <= 60
                    Check.TEST_CUE -> idle && cueProblem == null && (onWatch || !volumeOff)
                    else -> true
                }
                CheckRow(item, fix?.takeIf { fixEnabled })
            }
            if (!ui.batteryUnrestricted && ui.batteryManual) {
                Help("Settings > Apps > OpenTDI > Battery > Unrestricted")
            }
            when {
                ui.preparing -> Help("Preparing the cues with text-to-speech…")
                ui.testing -> Help("Playing the test cue…")
            }
            ui.cueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        }
        PresetCard(viewModel, enabled = idle)
        InstructionsCard(config.theme)
    }
}

@Composable
private fun StatusCard(status: SessionStatus, now: Long, ready: Boolean) {
    val (headline, cycle) = statusLines(status, now, ready)
    Section(null) {
        Text(headline, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        cycle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status.lastEvent?.let { Help("Last: $it") }
        if (status.running) Help("State ${status.state.name}")
    }
}

@Composable
private fun CheckRow(item: CheckItem, fix: Pair<String, () -> Unit>?) {
    val color = when {
        item.ok -> OK_COLOR
        item.blocking -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) } // the text says the same, so color is never the only cue
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
    val current = presetOf(ui.config)
    Section("Timing: ${current?.label ?: "Custom"}") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            for (preset in TimingPreset.entries) {
                val onClick = { viewModel.edit(preset::applyTo) }
                if (preset == current) {
                    Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(preset.label) }
                } else {
                    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text(preset.label)
                    }
                }
            }
        }
        Help(timingSummary(ui.config))
    }
}

@Composable
private fun InstructionsCard(theme: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Section("What to expect") {
        if (open) Text(instructions(theme), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Read the instructions") }
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
