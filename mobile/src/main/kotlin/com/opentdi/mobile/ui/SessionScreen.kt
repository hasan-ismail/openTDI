package com.opentdi.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.mobile.session.SessionStatus
import com.opentdi.shared.config.validate
import com.opentdi.shared.config.warnings
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TdiState
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Pure black for an AMOLED screen in a dark bedroom; muted accents, nothing bright.
private val Colors = darkColorScheme(
    primary = Color(0xFF8FA8BF),
    onPrimary = Color.Black,
    secondary = Color(0xFF8FA8BF),
    tertiary = Color(0xFFD9A55B),
    background = Color.Black,
    onBackground = Color(0xFFDADADA),
    surface = Color.Black,
    onSurface = Color(0xFFDADADA),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    error = Color(0xFFE08080),
)

private val Type = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        labelLarge = base.labelLarge.copy(fontSize = 17.sp),
    )
}

@Composable
fun OpenTdiTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, content = content)

@Composable
fun SessionScreen(viewModel: MainViewModel, onStart: () -> Unit, onStop: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val config = ui.config
    val cues = ui.cues
    val errors = remember(config) { config.validate() }
    val warnings = remember(config) { config.warnings() }
    val cuesReady = cues?.theme == config.theme
    val idle = !status.running && !ui.preparing && !ui.testing
    val edit = viewModel::edit

    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("OpenTDI", style = MaterialTheme.typography.headlineMedium)
            StatusCard(status)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = idle && cuesReady && errors.isEmpty()) { Text("Start session") }
                OutlinedButton(onClick = onStop, enabled = status.running) { Text("Stop session") }
            }
            ui.dnd.message?.let {
                val color = if (ui.dnd == DndEffect.TOTAL_SILENCE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                Text(it, color = color)
            }
            errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            if (ui.calibratedIndex == null) {
                Text(
                    "Tap Test cue once at your bedtime volume: cues never play louder than that volume.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            HorizontalDivider()
            OutlinedTextField(
                value = config.theme,
                onValueChange = { theme -> edit { it.copy(theme = theme) } },
                label = { Text("Theme") },
                singleLine = true,
                enabled = idle,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::prepareCues, enabled = idle && config.theme.isNotBlank() && config.theme.length <= 60) {
                    Text("Prepare cues")
                }
                OutlinedButton(onClick = viewModel::testCue, enabled = idle && cuesReady) { Text("Test cue") }
            }
            Text(
                when {
                    ui.preparing -> "Preparing the cues with text-to-speech…"
                    ui.testing -> "Playing the test cue…"
                    cuesReady -> "Cues ready (voice ${cues.voice})."
                    cues != null -> "The cues are for \"${cues.theme}\". Prepare them again for this theme."
                    else -> "No cues yet: tap Prepare cues."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            ui.cueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            Text("Timed cues", style = MaterialTheme.typography.titleLarge)
            TimedSettings(config, idle, edit)

            HorizontalDivider()
            Text("Cue sound", style = MaterialTheme.typography.titleLarge)
            SoundSettings(config, idle, edit)

            HorizontalDivider()
            Text("Before you start", style = MaterialTheme.typography.titleLarge)
            Text(instructions(config.theme), style = MaterialTheme.typography.bodyMedium)
            Text("Not a medical device.", fontWeight = FontWeight.Bold)
            Text(WELLNESS_STATEMENT, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusCard(status: SessionStatus) {
    val now by produceState(SystemClock.elapsedRealtime(), status.running) {
        while (status.running) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stateLabel(status.state), style = MaterialTheme.typography.headlineSmall)
            Text(status.state.name, style = MaterialTheme.typography.labelMedium)
            if (status.running) {
                // Between cycles the scheduler still reports the last cycle's number.
                Text(
                    if (status.state == TdiState.AWAKE_INCUBATING) {
                        "Waiting for cycle ${status.completedCycles + 1} of ${status.maxCycles}"
                    } else {
                        "Cycle ${status.cycle} of ${status.maxCycles}"
                    },
                )
                val next = status.nextEventAtRealtimeMs
                Text(if (next == null) "Cue playing" else "Next step in ${clock(next - now)}")
            }
            status.lastEvent?.let { Text("Last: $it", style = MaterialTheme.typography.bodyMedium) }
            status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TimedSettings(config: SessionConfig, enabled: Boolean, edit: ((SessionConfig) -> SessionConfig) -> Unit) {
    val timed = config.timed
    IntSetting("First sleep, earliest", timed.firstSleepMinMin, 1..60, 1, "min", enabled) { v ->
        edit { it.copy(timed = it.timed.copy(firstSleepMinMin = v)) }
    }
    IntSetting("First sleep, latest", timed.firstSleepMaxMin, 1..90, 1, "min", enabled) { v ->
        edit { it.copy(timed = it.timed.copy(firstSleepMaxMin = v)) }
    }
    IntSetting("Dwell before the wake prompt", config.countdownS, 60..300, 15, "s", enabled) { v ->
        edit { it.copy(countdownS = v) }
    }
    IntSetting("Report window", timed.phase1ReportWindowS, 30..90, 5, "s", enabled) { v ->
        edit { it.copy(timed = it.timed.copy(phase1ReportWindowS = v)) }
    }
    IntSetting("Back to sleep", timed.betweenSleepS, 60..1200, 30, "s", enabled) { v ->
        edit { it.copy(timed = it.timed.copy(betweenSleepS = v)) }
    }
    IntSetting("Loops", config.maxCycles, 1..10, 1, "", enabled) { v -> edit { it.copy(maxCycles = v) } }
    IntSetting("Session limit", config.sessionMaxMin, 15..180, 5, "min", enabled) { v ->
        edit { it.copy(sessionMaxMin = v) }
    }
}

@Composable
private fun SoundSettings(config: SessionConfig, enabled: Boolean, edit: ((SessionConfig) -> SessionConfig) -> Unit) {
    val cap = config.audio.routeCapGain[AudioRouteMode.NIGHTSTAND_SPEAKER] ?: 0f
    val gain = config.audio.userGain.coerceIn(0f, cap)
    Column {
        Text("Cue volume: ${percent(gain)} (speaker cap ${percent(cap)})")
        // The slider stops at the speaker cap (§7.7 step 3); playback clamps to it as well.
        Slider(
            value = gain,
            onValueChange = { g -> edit { it.copy(audio = it.audio.copy(userGain = (g * 100).roundToInt() / 100f)) } },
            valueRange = 0f..cap,
            enabled = enabled,
        )
    }
    IntSetting("Fade-in", config.audio.fadeInMs, 0..5000, 100, "ms", enabled) { v ->
        edit { it.copy(audio = it.audio.copy(fadeInMs = v)) }
    }
    Text("Route: Nightstand speaker")
}

@Composable
private fun IntSetting(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column {
        Text("$label: $value $unit".trimEnd())
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = {
                val snapped = (range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range)
                if (snapped != value) onChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = enabled,
        )
    }
}

private fun stateLabel(state: TdiState) = when (state) {
    TdiState.IDLE -> "No session"
    TdiState.CALIBRATING_BASELINE -> "Calibrating"
    TdiState.AWAKE_INCUBATING -> "Falling asleep"
    TdiState.HYPNAGOGIA_DETECTED -> "Dwell"
    TdiState.WAKING_USER -> "Wake prompt"
    TdiState.RECORDING_DREAM -> "Report window" // Phase 1 records nothing (§4.3)
    TdiState.RE_INCUBATING -> "Re-incubation"
    TdiState.PAUSED -> "Paused"
    TdiState.SESSION_COMPLETE -> "Session complete"
}

private fun clock(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, s / 60, s % 60)
}

private fun percent(gain: Float) = "${(gain * 100).roundToInt()} %"

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

// §17.4, which includes the FS24 screening advice.
private const val WELLNESS_STATEMENT =
    "OpenTDI is open-source wellness and research software for exploring targeted dream incubation at sleep " +
        "onset. It does not diagnose, treat, cure, mitigate or prevent any disease or condition, and it is not a " +
        "sleep-staging or medical device. Its sleep-onset estimates come from unvalidated thresholds and may be " +
        "wrong. Heart rate, inter-beat interval, skin conductance and motion data from the Samsung Health Sensor " +
        "SDK are \"for fitness and wellness information only, not for the diagnosis or treatment of any medical " +
        "condition\" (Samsung). Do not use OpenTDI if you have a history of psychotic disorders, narcolepsy, " +
        "recurrent sleep paralysis, parasomnias or other sleep disorders without talking to a clinician first. " +
        "This project is not affiliated with or endorsed by the MIT Media Lab or Samsung."
