package com.opentdi.mobile.ui

import com.opentdi.shared.model.WatchOutput
import com.opentdi.shared.model.LateCueMode
import com.opentdi.mobile.UiState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import java.util.Locale
import com.opentdi.shared.model.AlarmTone
import com.opentdi.shared.model.AlarmConfig
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.cues.NEURAL_VOICE_CHOICES
import com.opentdi.mobile.cues.PHASE1_CUES
import com.opentdi.mobile.cues.THEME_PLACEHOLDER
import com.opentdi.mobile.cues.cueSetProblem
import com.opentdi.mobile.cues.cueText
import com.opentdi.mobile.cues.mitThemeCueText
import com.opentdi.mobile.cues.speakerLabel
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.model.EndAlarm
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TtsEngine
import kotlin.math.roundToInt

internal typealias Edit = ((SessionConfig) -> SessionConfig) -> Unit

/** Settings in cards: cue words, voice, timing, sound, about. Locked while a session runs or cues are prepared. */
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    val cueProblem = remember(ui.config, ui.cues) { cueSetProblem(ui.cues, ui.config) }
    val idle = !status.running && !ui.preparing && !ui.testing
    val edit: Edit = viewModel::edit
    var advanced by rememberSaveable { mutableStateOf(false) }

    Page("Settings") {
        if (status.running) Help("Settings are locked while a session runs.")
        SwitchRow("Show advanced settings", advanced, enabled = true) { advanced = it }
        CompositionLocalProvider(LocalAdvanced provides advanced) { SettingsCards(viewModel, ui, idle, volumeOff, cueProblem) }
        if (advanced) {
            Section("Defaults") {
                Help(
                    "The default timing follows MIT Dormio's timer method (first cue after 10–15 min, 3 min drifting " +
                        "off, 7 min back to sleep). Resetting keeps your words, voice, alarm times and recordings.",
                )
                OutlinedButton(onClick = { edit(::withDefaultNumbers) }, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset sliders to the defaults")
                }
            }
        }
        Section("Set-up guide") {
            Help("The step-by-step guide from the first launch: the watch, the voice, alarms and your first night.")
            OutlinedButton(onClick = viewModel::restartOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("Open the set-up guide")
            }
        }
        AboutCard()
    }
}

/** [config] with every slider setting back at its default; words, voices, alarm times and recordings stay. */
fun withDefaultNumbers(config: SessionConfig): SessionConfig {
    val d = SessionConfig()
    return config.copy(
        countdownS = d.countdownS,
        maxCycles = d.maxCycles,
        sessionMaxMin = d.sessionMaxMin,
        timed = d.timed,
        audio = config.audio.copy(
            userGain = d.audio.userGain,
            fadeInMs = d.audio.fadeInMs,
            ttsSpeechRate = d.audio.ttsSpeechRate,
            phrasePauseMs = d.audio.phrasePauseMs,
        ),
        alarms = config.alarms.copy(
            wbtbAwakeMin = d.alarms.wbtbAwakeMin,
            rampS = d.alarms.rampS,
            maxRingMin = d.alarms.maxRingMin,
            maxSnoozes = d.alarms.maxSnoozes,
            snoozeMin = d.alarms.snoozeMin,
            lateCueDelayMin = d.alarms.lateCueDelayMin,
        ),
    )
}

@Composable
private fun SettingsCards(viewModel: MainViewModel, ui: UiState, idle: Boolean, volumeOff: Boolean, cueProblem: String?) {
    val config = ui.config
    val edit: Edit = viewModel::edit
    run {
        CueWordsCard(config, idle, edit, viewModel, ui.recordingCue)
        VoiceCard(viewModel, ui, idle, volumeOff, cueProblem)
        TimingCard(config, idle, edit)
        AlarmsCard(config, idle, edit, viewModel)
        SoundCard(config, idle, edit)
        WatchAudioCard(config, idle, edit, viewModel)
    }
}

/** The voice and the cues: engine, voice, Prepare cues and Test cue (also a step of the set-up guide). */
@Composable
internal fun VoiceCard(viewModel: MainViewModel, ui: UiState, idle: Boolean, volumeOff: Boolean, cueProblem: String?) {
    val config = ui.config
    val edit: Edit = viewModel::edit
    Section("Voice") {
        // These apply when the cues are prepared; after a change the status line below asks for Prepare cues.
        val neural = config.audio.ttsEngine == TtsEngine.NEURAL
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = neural, enabled = idle, role = Role.Switch) { on ->
                    edit { it.copy(audio = it.audio.copy(ttsEngine = if (on) TtsEngine.NEURAL else TtsEngine.SYSTEM)) }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Natural voice (on-device model)", modifier = Modifier.weight(1f))
            Switch(checked = neural, onCheckedChange = null, enabled = idle)
        }
        Help("A natural-sounding voice that runs on the phone itself. Off: your phone's text-to-speech voice.")
        if (neural) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (speaker in NEURAL_VOICE_CHOICES) {
                    FilterChip(
                        selected = config.audio.neuralSpeaker == speaker,
                        onClick = { edit { it.copy(audio = it.audio.copy(neuralSpeaker = speaker)) } },
                        label = { Text(speakerLabel(speaker)) },
                        enabled = idle,
                    )
                }
            }
        }
        IntSetting(
            "Speaking speed", "How fast the voice speaks; 100 % is normal speed.",
            (config.audio.ttsSpeechRate * 100).roundToInt(), 50..120, 5, "%", idle,
        ) { v -> edit { it.copy(audio = it.audio.copy(ttsSpeechRate = v / 100f)) } }
        IntSetting(
            "Pause between phrases", "Silence between the short phrases of a cue.",
            config.audio.phrasePauseMs, 0..2000, 100, "ms", idle,
        ) { v -> edit { it.copy(audio = it.audio.copy(phrasePauseMs = v)) } }
        Text(
            when {
                ui.preparing -> "Preparing the cues with text-to-speech…"
                ui.testing -> "Playing the test cue…"
                cueProblem == null -> "Cues ready (voice ${ui.cues?.voice})."
                else -> cueProblem
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        ui.cueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::prepareCues,
                enabled = idle && config.theme.isNotBlank() && config.theme.length <= 60,
                modifier = Modifier.weight(1f),
            ) { Text("Prepare cues") }
            OutlinedButton(
                onClick = viewModel::testCue,
                // The phone's media volume only matters when the phone speaker plays the cues.
                enabled = idle && cueProblem == null &&
                    (!volumeOff || config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER),
                modifier = Modifier.weight(1f),
            ) { Text("Test cue") }
        }
        Help(
            "Prepare cues turns the words into speech on the phone. Test cue plays the first cue once: " +
                "play it at your bedtime volume, because cues never play louder than that.",
        )
    }
}

@Composable
private fun CueWordsCard(
    config: SessionConfig,
    enabled: Boolean,
    edit: Edit,
    viewModel: MainViewModel,
    recordingCue: PromptCue?,
) {
    Section("Cue words") {
        Help("What the voice says. By default it tells you that you are dreaming and leaves the rest to you.")
        OutlinedTextField(
            value = config.theme,
            onValueChange = { theme -> edit { it.copy(theme = theme) } },
            label = { Text("Theme") },
            supportingText = { Text("Said wherever the words contain $THEME_PLACEHOLDER.") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                // Kept with the placeholder, so the words follow a later theme change.
                val mit = PHASE1_CUES.associateWith { mitThemeCueText(it, THEME_PLACEHOLDER) }
                    .filter { (cue, text) -> text != cueText(cue, THEME_PLACEHOLDER) }
                edit { it.copy(audio = it.audio.copy(cueTexts = it.audio.cueTexts + mit)) }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Use MIT theme wording") }
        Help("MIT Dormio's wording asks you to think of the theme instead.")
        for (cue in PHASE1_CUES) CueWords(cue, config, enabled, edit, viewModel, recordingCue)
        TextButton(
            onClick = { edit { it.copy(audio = it.audio.copy(cueTexts = emptyMap())) } },
            enabled = enabled && config.audio.cueTexts.isNotEmpty(),
        ) { Text("Reset all to the default words") }
    }
}

/** One cue's words: the user's own once edited, else the §7.1 wording with the theme placeholder. */
@Composable
private fun CueWords(
    cue: PromptCue,
    config: SessionConfig,
    enabled: Boolean,
    edit: Edit,
    viewModel: MainViewModel,
    recordingCue: PromptCue?,
) {
    val template = cueText(cue, THEME_PLACEHOLDER) // so an edit keeps the placeholder, not today's theme
    val own = config.audio.cueTexts[cue]
    Column {
        OutlinedTextField(
            value = own ?: template,
            onValueChange = { text -> edit { it.copy(audio = it.audio.copy(cueTexts = it.audio.cueTexts + (cue to text))) } },
            label = { Text(cueLabel(cue)) },
            placeholder = { Text(template) }, // an emptied field is rendered with these words
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { edit { it.copy(audio = it.audio.copy(cueTexts = it.audio.cueTexts - cue)) } },
            enabled = enabled && own != null,
        ) { Text("Reset to default") }
        OwnVoiceRow(cue, config, enabled, viewModel, recordingCue)
    }
}

/** Record the cue in your own voice (a familiar voice disturbs sleep less), or go back to the synthetic one. */
@Composable
private fun OwnVoiceRow(
    cue: PromptCue,
    config: SessionConfig,
    enabled: Boolean,
    viewModel: MainViewModel,
    recordingCue: PromptCue?,
) {
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.recordOwnVoice(cue)
    }
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            recordingCue == cue -> Button(onClick = viewModel::stopOwnVoice) { Text("Stop recording") }
            else -> OutlinedButton(
                onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.recordOwnVoice(cue)
                    } else {
                        mic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = enabled && recordingCue == null,
            ) { Text(if (cue in config.audio.ownVoice) "Record again" else "Record my voice") }
        }
        if (cue in config.audio.ownVoice) {
            TextButton(onClick = { viewModel.deleteOwnVoice(cue) }, enabled = enabled && recordingCue == null) {
                Text("Use the synthetic voice")
            }
        }
    }
    if (recordingCue == cue) Help("Say the words slowly and calmly, then tap Stop (at most 20 s).")
    if (cue in config.audio.ownVoice && recordingCue != cue) Help("Your own recording plays for this cue. Tap Prepare cues.")
}

private fun cueLabel(cue: PromptCue) = when (cue) {
    PromptCue.INITIAL_INCUBATION -> "As you fall asleep"
    PromptCue.WAKE_PROMPT -> "Wake prompt (the question)"
    PromptCue.SLEEP_STATE_QUESTION -> "Sleep-state question"
    PromptCue.RE_INCUBATION -> "Back to sleep"
    PromptCue.SESSION_END -> "Session end, after the chime"
    PromptCue.MORNING_WAKE -> "Spoken wake-up alarm (watch)"
}

@Composable
private fun TimingCard(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val timed = config.timed
    Section("Timing: ${presetOf(config)?.label ?: "Custom"}") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            for (preset in TimingPreset.entries) {
                OutlinedButton(onClick = { edit(preset::applyTo) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(preset.label)
                }
            }
        }
        val still = timed.waitForStillness
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = still, enabled = enabled, role = Role.Switch) { on ->
                    edit { it.copy(timed = it.timed.copy(waitForStillness = on)) }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wait until I'm still")
                Help(
                    "Each loop also waits until the watch shows you lying still with your heart rate settling, and " +
                        "moving restarts the wait. Needs the watch recording; without it the timer alone runs.",
                )
            }
            Switch(checked = still, onCheckedChange = null, enabled = enabled)
        }
        if (still) {
            IntSetting(
                "Still for", "How long you lie still before a loop starts.",
                timed.stillForS / 60, 1..30, 1, "min", enabled,
            ) { v -> edit { it.copy(timed = it.timed.copy(stillForS = v * 60)) } }
        }
        IntSetting(
            "First cue, earliest", "The first cue comes at a random minute between earliest and latest.",
            timed.firstSleepMinMin, 1..60, 1, "min", enabled,
        ) { v -> edit { it.copy(timed = it.timed.copy(firstSleepMinMin = v)) } }
        IntSetting(
            "First cue, latest", "Give yourself time to fall asleep before the first cue.",
            timed.firstSleepMaxMin, 1..90, 1, "min", enabled,
        ) { v -> edit { it.copy(timed = it.timed.copy(firstSleepMaxMin = v)) } }
        IntSetting(
            "Drifting off (dwell)", "Time between the dream cue and the question that wakes you lightly.",
            config.countdownS, 60..300, 15, "s", enabled,
        ) { v -> edit { it.copy(countdownS = v) } }
        IntSetting(
            "Time to answer", "How long you have to say what was going through your mind.",
            timed.phase1ReportWindowS, 30..90, 5, "s", enabled,
        ) { v -> edit { it.copy(timed = it.timed.copy(phase1ReportWindowS = v)) } }
        IntSetting(
            "Back to sleep", "Time to drift off again before the next loop.",
            timed.betweenSleepS, 60..1200, 30, "s", enabled,
        ) { v -> edit { it.copy(timed = it.timed.copy(betweenSleepS = v)) } }
        IntSetting(
            "Loops", "How many cue-and-question rounds one session has.",
            config.maxCycles, 1..10, 1, "", enabled,
        ) { v -> edit { it.copy(maxCycles = v) } }
        IntSetting(
            "Session limit", "The session ends after this long at the latest.",
            config.sessionMaxMin, 15..180, 5, "min", enabled,
        ) { v -> edit { it.copy(sessionMaxMin = v) } }
    }
}

/** The watch alarms (owner, 2026-10-28): they ring on the watch, so the phone can stay in another room. */
@Composable
internal fun AlarmsCard(config: SessionConfig, enabled: Boolean, edit: Edit, viewModel: MainViewModel) {
    val onTest: (AlarmTone) -> Unit = viewModel::testAlarm
    val alarms = config.alarms
    fun change(transform: (AlarmConfig) -> AlarmConfig) = edit { it.copy(alarms = transform(it.alarms)) }
    Section("Alarms (on the watch)") {
        Help("They ring on the watch while it records. Start session sends them to the watch.")
        SwitchRow("Wake-up alarm", alarms.wakeEnabled, enabled) { on -> change { it.copy(wakeEnabled = on) } }
        if (alarms.wakeEnabled) {
            TimeRow("Wake up at", alarms.wakeAtMin, enabled) { m -> change { it.copy(wakeAtMin = m) } }
            Help("The watch logs how long you take to wake up: your first movement, and when you tap I'm awake.")
            ToneChips(alarms.wakeTone, enabled, onTest) { t -> change { it.copy(wakeTone = t) } }
            IntSetting(
                "Snoozes", "Each one waits ${alarms.snoozeMin} min. 0 = no snooze button.", alarms.maxSnoozes, 0..5, 1,
                "", enabled,
            ) { v -> change { it.copy(maxSnoozes = v) } }
        }
        if (AlarmTone.SPOKEN in listOfNotNull(
                alarms.wakeTone.takeIf { alarms.wakeEnabled },
                alarms.wbtbTone.takeIf { alarms.wbtbEnabled },
            )
        ) {
            OutlinedTextField(
                value = alarms.sleeperName,
                onValueChange = { name -> change { it.copy(sleeperName = name.take(30)) } },
                label = { Text("Your name") },
                supportingText = {
                    Text("The spoken wake-up says it (Settings > Cue words has its words). Then tap Prepare cues.")
                },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow("Wake back to bed (WBTB)", alarms.wbtbEnabled, enabled) { on ->
            // Late cues follow a WBTB alarm, so they go off with it (else Start would be blocked by validation).
            change { it.copy(wbtbEnabled = on, lateCues = if (on) it.lateCues else LateCueMode.OFF) }
        }
        if (alarms.wbtbEnabled) {
            TimeRow("Wake briefly at", alarms.wbtbAtMin, enabled) { m -> change { it.copy(wbtbAtMin = m) } }
            Help(
                "About 6 hours after you fall asleep. Stay up for a while, recall your last dream and repeat " +
                    "\"next time I'm dreaming, I'll remember I'm dreaming\"; a buzz then says go back to sleep. " +
                    "1 to 3 nights a week; set the wake-up alarm at least 30 min later.",
            )
            IntSetting(
                "Stay up for", "Before the back-to-sleep buzz.", alarms.wbtbAwakeMin, 5..60, 5, "min", enabled,
            ) { v -> change { it.copy(wbtbAwakeMin = v) } }
            ToneChips(alarms.wbtbTone, enabled, onTest) { t -> change { it.copy(wbtbTone = t) } }
            LateCuesSettings(alarms, enabled, viewModel, ::change)
        }
        if (alarms.wakeEnabled || alarms.wbtbEnabled) {
            IntSetting(
                "Volume rise", "The tone starts quiet and reaches the watch's alarm volume after this long.",
                alarms.rampS, 0..300, 10, "s", enabled,
            ) { v -> change { it.copy(rampS = v) } }
        }
    }
}

/** Late-night cues after WBTB (lucid_induction.md §5): off, on, or a blinded experiment. */
@Composable
private fun LateCuesSettings(
    alarms: AlarmConfig,
    enabled: Boolean,
    viewModel: MainViewModel,
    change: ((AlarmConfig) -> AlarmConfig) -> Unit,
) {
    Text("Late-night cues")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((mode, label) in listOf(LateCueMode.OFF to "Off", LateCueMode.ON to "On", LateCueMode.EXPERIMENT to "Experiment")) {
            FilterChip(
                selected = alarms.lateCues == mode,
                onClick = { change { it.copy(lateCues = mode) } },
                label = { Text(label) },
                enabled = enabled,
            )
        }
    }
    Help(
        when (alarms.lateCues) {
            LateCueMode.OFF -> "Soft three-note cues after WBTB, while you lie still. Unproven: try them as an experiment."
            LateCueMode.ON -> "After WBTB the watch trains the cue, then plays it softly once you are still again."
            LateCueMode.EXPERIMENT ->
                "Each night the watch secretly picks cues or no cues, so your morning reports show whether they help."
        },
    )
    if (alarms.lateCues != LateCueMode.OFF) {
        Text("Cue loudness: ${(alarms.lateCueVolume * 100).roundToInt()} %")
        Help("Lying in bed, set it so you would notice the cue but it would not wake you.")
        Slider(
            value = alarms.lateCueVolume,
            onValueChange = { v -> change { it.copy(lateCueVolume = (v * 50).roundToInt() / 50f) } },
            valueRange = 0.02f..1f,
            enabled = enabled,
        )
        TextButton(onClick = { viewModel.testLateCue(alarms.lateCueVolume) }, enabled = enabled) {
            Text("Play the cue on the watch")
        }
        IntSetting(
            "Cues start after", "Minutes after the back-to-sleep buzz; REM came about 40 min after WBTB in a lab study.",
            alarms.lateCueDelayMin, 10..120, 5, "min", enabled,
        ) { v -> change { it.copy(lateCueDelayMin = v) } }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(label: String, minuteOfDay: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${hhmm(minuteOfDay)}", modifier = Modifier.weight(1f))
        TextButton(onClick = { picking = true }, enabled = enabled) { Text("Change") }
    }
    if (picking) {
        val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * 60 + state.minute)
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

/** The tone choices; tapping the selected one again plays it on the watch for a few seconds. */
@Composable
private fun ToneChips(selected: AlarmTone, enabled: Boolean, onTest: (AlarmTone) -> Unit, onSelect: (AlarmTone) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (tone in AlarmTone.entries) {
            FilterChip(
                selected = tone == selected,
                onClick = { onSelect(tone) },
                label = { Text(toneLabel(tone)) },
                enabled = enabled,
            )
        }
    }
    TextButton(onClick = { onTest(selected) }, enabled = enabled) { Text("Play \"${toneLabel(selected)}\" on the watch") }
}

private fun toneLabel(tone: AlarmTone) = when (tone) {
    AlarmTone.MELODY -> "Melody"
    AlarmTone.GENTLE_CHIMES -> "Soft chimes"
    AlarmTone.RISING_TONE -> "Rising tone"
    AlarmTone.CLASSIC_BEEPS -> "Low beeps"
    AlarmTone.VIBRATE_ONLY -> "Vibrate only"
    AlarmTone.SPOKEN -> "Spoken (cue voice)"
}

private fun hhmm(minuteOfDay: Int) = String.format(Locale.ROOT, "%d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/** Where the watch plays cues, late cues and alarms: its speaker, or earbuds paired with the watch. */
@Composable
private fun WatchAudioCard(config: SessionConfig, enabled: Boolean, edit: Edit, viewModel: MainViewModel) {
    val watch by viewModel.watchStatus.collectAsStateWithLifecycle(initialValue = null)
    Section("Watch audio") {
        Help("Where the watch plays cues and alarms. Earbuds let you sleep with one or both in without waking anyone.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((output, label) in listOf(WatchOutput.SPEAKER to "Watch speaker", WatchOutput.EARBUDS to "Earbuds")) {
                FilterChip(
                    selected = config.audio.watchOutput == output,
                    onClick = { edit { it.copy(audio = it.audio.copy(watchOutput = output)) } },
                    label = { Text(label) },
                    enabled = enabled,
                )
            }
        }
        if (config.audio.watchOutput == WatchOutput.EARBUDS) {
            Text(
                watch?.earbuds?.let { "Connected to the watch: $it" }
                    ?: "No earbuds connected to the watch right now: the speaker plays instead.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Help(
                "Pair the earbuds with the watch, not the phone: on the watch, Settings > Connections > Bluetooth. " +
                    "Alarms ring in the earbuds and on the watch, so an earbud that falls out cannot hide them. " +
                    "Play a test cue once with the earbuds in: they keep their own volume.",
            )
        }
    }
}

@Composable
private fun SoundCard(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val cap = config.audio.routeCapGain[AudioRouteMode.NIGHTSTAND_SPEAKER] ?: 0f
    val gain = config.audio.userGain.coerceIn(0f, cap)
    Section("Sound") {
        if (LocalAdvanced.current) Column {
            Text("Cue volume: ${percent(gain)}")
            Help("Never above the speaker cap of ${percent(cap)}, and never louder than your test cue.")
            // The slider stops at the speaker cap (§7.7 step 3); playback clamps to it as well.
            Slider(
                value = gain,
                onValueChange = { g ->
                    edit { it.copy(audio = it.audio.copy(userGain = (g * 100).roundToInt() / 100f)) }
                },
                valueRange = 0f..cap,
                enabled = enabled,
            )
        }
        IntSetting(
            "Fade-in", "The voice rises gently over this time so it does not startle you.",
            config.audio.fadeInMs, 0..5000, 100, "ms", enabled,
        ) { v -> edit { it.copy(audio = it.audio.copy(fadeInMs = v)) } }
        val chime = config.audio.endAlarm == EndAlarm.CHIME
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = chime, enabled = enabled, role = Role.Switch) { on ->
                    edit { it.copy(audio = it.audio.copy(endAlarm = if (on) EndAlarm.CHIME else EndAlarm.NONE)) }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("End chime")
                Help("A soft chime and a sentence when the session ends.")
            }
            Switch(checked = chime, onCheckedChange = null, enabled = enabled)
        }
        val onWatch = config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = onWatch, enabled = enabled, role = Role.Switch) { on ->
                    edit {
                        val route = if (on) AudioRouteMode.WATCH_WRIST_SPEAKER else AudioRouteMode.NIGHTSTAND_SPEAKER
                        // Settings saved before the wrist speaker have no cap for it: it takes the nightstand's.
                        val caps = it.audio.routeCapGain
                        val cap = caps[route] ?: caps[AudioRouteMode.NIGHTSTAND_SPEAKER] ?: 0.6f
                        it.copy(audio = it.audio.copy(route = route, routeCapGain = caps + (route to cap)))
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Play cues on the watch")
                Help(
                    if (onWatch) {
                        "The watch speaker plays the cues, at the watch's media volume. The phone can be in another room."
                    } else {
                        "Cues play from the phone speaker (nightstand)."
                    },
                )
            }
            Switch(checked = onWatch, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    }
    Section("About") {
        Text("OpenTDI $version")
        Text("Not a medical device.", fontWeight = FontWeight.Bold)
        Text(WELLNESS_STATEMENT, style = MaterialTheme.typography.bodyMedium)
        Help("Free software under the GPL-3.0. Source and issues: github.com/hasan-ismail/openTDI")
    }
}

private fun percent(gain: Float) = "${(gain * 100).roundToInt()} %"

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
