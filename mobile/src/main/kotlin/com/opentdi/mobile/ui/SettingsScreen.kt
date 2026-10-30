package com.opentdi.mobile.ui

import com.opentdi.shared.model.WatchOutput
import com.opentdi.shared.model.LateCueMode
import com.opentdi.mobile.UiState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.opentdi.shared.audio.alarmsAhead
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import com.opentdi.shared.time.clock12
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import com.opentdi.shared.model.AlarmTone
import com.opentdi.shared.model.AlarmConfig
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.markTipsSeen
import com.opentdi.mobile.cues.NEURAL_MODEL
import com.opentdi.mobile.cues.NEURAL_VOICE_CHOICES
import com.opentdi.mobile.cues.PHASE1_CUES
import com.opentdi.mobile.cues.THEME_PLACEHOLDER
import com.opentdi.mobile.cues.cueSetProblem
import com.opentdi.mobile.cues.cueText
import com.opentdi.mobile.cues.mitThemeCueText
import com.opentdi.mobile.cues.speakerLabel
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.shared.config.MAX_CUE_TEXT_CHARS
import com.opentdi.shared.config.MIN_SLEEP_BEFORE_WBTB_MIN
import com.opentdi.shared.config.WBTB_AFTER_SLEEP_MIN
import com.opentdi.shared.config.lateCueAfterOnsetMinThatFits
import com.opentdi.shared.config.lateCuesAfterWake
import com.opentdi.shared.config.wakeBeforeWbtb
import com.opentdi.shared.config.wakeTooSoonAfterWbtb
import com.opentdi.shared.config.wbtbAtMinBeforeWake
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.model.EndAlarm
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TtsEngine
import kotlin.math.roundToInt

internal typealias Edit = ((SessionConfig) -> SessionConfig) -> Unit

/**
 * Settings (owner, 2026-10-30: fewer screens and options): the alarms, where the voice plays and what it says, then
 * everything else behind More at the bottom. Locked while the voice plays or is made. [showWatch]: opened from the
 * Tonight popup's Update the watch app, so More opens on the watch's install card.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, onQuickTest: () -> Unit, showWatch: Boolean, onWatchShown: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    // null until the watch's first answer (up to 5 s), so nothing flashes on opening.
    val watchAnswer by remember(viewModel) { viewModel.watchStatus.map { Optional.ofNullable(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val cueProblem = remember(ui.config, ui.cues) { cueSetProblem(ui.cues, ui.config) }
    val idle = !status.running && !ui.preparing && !ui.testing
    val edit: Edit = viewModel::edit
    val context = LocalContext.current
    var advanced by rememberSaveable { mutableStateOf(showWatch) }
    var tipsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmStopWatch by rememberSaveable { mutableStateOf(false) }
    val watchCards = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { // Unit: clearing showWatch must not cancel the scroll
        if (showWatch) {
            onWatchShown()
            delay(300) // after the tab's cross-fade, once the cards are laid out
            watchCards.bringIntoView()
        }
    }
    // Leaving Settings gets the voice ready for what changed here, so Start is not blocked at bedtime.
    val latest by rememberUpdatedState(Triple(idle, cueProblem, ui.config.theme.length in 1..60))
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        onDispose {
            // A rotation or theme change rebuilds the screen; that is not leaving Settings.
            if (activity?.isChangingConfigurations != true && latest.first && latest.second != null && latest.third) {
                viewModel.prepareCues()
            }
        }
    }

    Page("Settings") {
        when {
            status.running ->
                Help("Settings are locked while the voice plays. Tap Stop the voice on Tonight to change them.")
            !idle -> Help("Settings are locked while the voice is made or tested.")
        }
        CompositionLocalProvider(LocalAdvanced provides advanced) {
            SettingsCards(viewModel, ui, idle, volumeOff, cueProblem, watchAnswer)
        }
        SwitchRow("More", advanced, enabled = true) { advanced = it }
        AnimatedVisibility(advanced, label = "more") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("Try it") {
                    OutlinedButton(onClick = onQuickTest, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                        Text("Quick test (5 min)")
                    }
                    Help("Hear the voice twice in a few minutes. No alarms.")
                    OutlinedButton(onClick = { tipsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Bedtime tips")
                    }
                }
                Text("How a night works", style = MaterialTheme.typography.titleMedium)
                NightPlanCard(ui.config, ZonedDateTime.now())
                InstructionsCard(ui.config)
                Column(Modifier.bringIntoViewRequester(watchCards), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Watch", style = MaterialTheme.typography.titleMedium)
                    WatchCards()
                }
                // Not needed: the night ends by itself after the morning alarm (owner, 2026-10-30).
                if (watchAnswer?.orElse(null)?.state == WatchState.RECORDING) {
                    Section(null) {
                        OutlinedButton(onClick = { confirmStopWatch = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("End the night now")
                        }
                        Help("Not needed: the night ends by itself after your morning alarm.")
                    }
                }
                Section("Defaults") {
                    Help(
                        "The normal timing follows MIT Dormio's timer method: the first round after 10–15 min, 3 min " +
                            "before the question, 7 min before the next round. Resetting keeps your words, voice, " +
                            "alarm times and recordings.",
                    )
                    OutlinedButton(
                        onClick = { edit(::withDefaultNumbers) },
                        enabled = idle,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reset all of these to normal") }
                }
                SavedNights(viewModel)
                Section("Set-up guide") {
                    Help(
                        "The step-by-step guide from the first launch: the watch, the voice, alarms and your first " +
                            "night.",
                    )
                    OutlinedButton(onClick = viewModel::restartOnboarding, modifier = Modifier.fillMaxWidth()) {
                        Text("Open the set-up guide")
                    }
                }
            }
        }
        AboutCard() // outside More: "Not a medical device" stays easy to find
    }

    if (tipsOpen) {
        BedtimeTipsDialog {
            markTipsSeen(context)
            tipsOpen = false
        }
    }
    if (confirmStopWatch) {
        // Ending also cancels the alarms, and the watch holds an unanswered dream report only in memory.
        val ahead = alarmsAhead(ui.config.alarms, System.currentTimeMillis(), ZoneId.systemDefault())
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
            lateCueAfterOnsetMin = d.alarms.lateCueAfterOnsetMin,
            wbtbAfterSleepMin = config.alarms.wbtbAfterSleepMin?.let { WBTB_AFTER_SLEEP_MIN }, // the switch stays
        ),
    )
}

/** The few things people change, then, with More on, the voice, the timing and the sound. */
@Composable
private fun SettingsCards(
    viewModel: MainViewModel,
    ui: UiState,
    idle: Boolean,
    volumeOff: Boolean,
    cueProblem: String?,
    watchAnswer: Optional<WatchStatus>?,
) {
    val config = ui.config
    val edit: Edit = viewModel::edit
    AlarmsCard(config, idle, edit, viewModel)
    WatchAudioCard(config, idle, edit, watchAnswer)
    CueWordsCard(config, idle, edit, viewModel, ui.recordingCue)
    if (LocalAdvanced.current) {
        VoiceCard(viewModel, ui, idle, volumeOff, cueProblem)
        TimingCard(config, idle, edit)
        SoundCard(config, idle, edit)
    }
}

/** The voice: engine, speaker, Get the voice ready and Test the voice (also a step of the set-up guide). */
@Composable
internal fun VoiceCard(viewModel: MainViewModel, ui: UiState, idle: Boolean, volumeOff: Boolean, cueProblem: String?) {
    val config = ui.config
    val edit: Edit = viewModel::edit
    val context = LocalContext.current
    Section("Voice") {
        // These apply when the voice is made; after a change the status line below asks for Get the voice ready.
        val neural = config.audio.ttsEngine == TtsEngine.NEURAL
        // Its model can only be put on the phone with adb (tools/voice), so most phones never have it.
        val installed = remember { File(context.filesDir, "voice/$NEURAL_MODEL/model.int8.onnx").isFile }
        if (installed || neural) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = neural, enabled = idle, role = Role.Switch) { on ->
                        edit { it.copy(audio = it.audio.copy(ttsEngine = if (on) TtsEngine.NEURAL else TtsEngine.SYSTEM)) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Natural-sounding voice", modifier = Modifier.weight(1f))
                Switch(checked = neural, onCheckedChange = null, enabled = idle)
            }
            Help("Works without internet. Off: your phone's built-in voice.")
        }
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
        Text(
            when {
                ui.preparing -> ui.prepareStatus
                ui.testing -> "Testing the voice…"
                cueProblem == null -> "The voice is ready."
                else -> cueProblem
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        ui.cueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // The phone's media volume only matters when the phone speaker plays the voice.
        val muted = volumeOff && config.audio.route != AudioRouteMode.WATCH_WRIST_SPEAKER
        if (muted) {
            Text("Phone volume is off, so you would not hear the voice.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                context.getSystemService(AudioManager::class.java)
                    .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }) { Text("Turn up") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::prepareCues,
                enabled = idle && config.theme.isNotBlank() && config.theme.length <= 60,
                modifier = Modifier.weight(1f),
            ) { Text("Get the voice ready") }
            OutlinedButton(
                onClick = viewModel::testCue,
                enabled = idle && cueProblem == null && !muted,
                modifier = Modifier.weight(1f),
            ) { Text("Test the voice") }
        }
        Help(
            "Get the voice ready turns the words into speech (about a minute). Then test it at the volume you'll " +
                "sleep with: the voice never gets louder than that.",
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
    Section("What the voice says") {
        Help("By default, it helps you notice when you're dreaming.")
        // The default words ignore the topic. A topic left blank by an older version stays shown, as it blocks Start.
        val showTopic = remember { config.theme.length !in 1..60 } ||
            config.audio.cueTexts.values.any { THEME_PLACEHOLDER in it }
        if (showTopic) {
            OutlinedTextField(
                value = config.theme,
                onValueChange = { theme -> edit { it.copy(theme = theme.take(60)) } },
                label = { Text("Dream topic") },
                supportingText = {
                    Text(
                        "The voice says this wherever the words show $THEME_PLACEHOLDER. Write it as you'd say it, " +
                            "like a tree or flying. Up to 60 letters.",
                    )
                },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Kept open while a cue is being recorded, so its Stop button never hides.
        Fold("Edit the words", forceOpen = recordingCue != null) {
            OutlinedButton(
                onClick = {
                    // Kept with the placeholder, so the words follow a later topic change.
                    val mit = PHASE1_CUES.associateWith { mitThemeCueText(it, THEME_PLACEHOLDER) }
                        .filter { (cue, text) -> text != cueText(cue, THEME_PLACEHOLDER) }
                    edit { it.copy(audio = it.audio.copy(cueTexts = it.audio.cueTexts + mit)) }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use dream-topic wording") }
            Help(
                "Instead of helping you notice a dream, the voice asks you to think of a topic you choose (MIT " +
                    "Dormio's wording).",
            )
            for (cue in PHASE1_CUES) CueWords(cue, config, enabled, edit, viewModel, recordingCue)
            TextButton(
                onClick = { edit { it.copy(audio = it.audio.copy(cueTexts = emptyMap())) } },
                enabled = enabled && config.audio.cueTexts.isNotEmpty(),
            ) { Text("Reset all to the default words") }
        }
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
            onValueChange = { text ->
                val words = text.take(MAX_CUE_TEXT_CHARS)
                edit { it.copy(audio = it.audio.copy(cueTexts = it.audio.cueTexts + (cue to words))) }
            },
            label = { Text(cueLabel(cue)) },
            placeholder = { Text(template) }, // an emptied field is rendered with these words
            supportingText = { Text("${(own ?: template).length} / $MAX_CUE_TEXT_CHARS") }, // shows a cut paste
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
    var micBlocked by remember { mutableStateOf(false) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After two refusals Android answers false at once, without asking: say why and where to allow it.
        micBlocked = !granted
        if (granted) viewModel.recordOwnVoice(cue)
    }
    val context = LocalContext.current
    // Wraps: with large text the two buttons don't fit side by side.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
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
                Text("Use the app's voice")
            }
        }
    }
    if (micBlocked) {
        Help("OpenTDI is not allowed to use the microphone. Tap below, then Permissions > Microphone > Allow.")
        TextButton(onClick = {
            micBlocked = false
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }) { Text("Allow the microphone") }
    }
    if (recordingCue == cue) Help("Say the words slowly and calmly, then tap Stop (at most 20 s).")
    if (cue in config.audio.ownVoice && recordingCue != cue) {
        Help("Your own recording plays here. Tap Get the voice ready.")
    }
}

private fun cueLabel(cue: PromptCue) = when (cue) {
    PromptCue.INITIAL_INCUBATION -> "As you fall asleep"
    PromptCue.WAKE_PROMPT -> "The question that wakes you lightly"
    PromptCue.SLEEP_STATE_QUESTION -> "Sleep-state question"
    PromptCue.RE_INCUBATION -> "Back to sleep"
    PromptCue.SESSION_END -> "After the last round"
    PromptCue.MORNING_WAKE -> "Spoken wake-up alarm (watch)"
}

@Composable
private fun TimingCard(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val timed = config.timed
    val recommended = presetOf(config) == TimingPreset.LUCID_NIGHT
    Section("Timing: ${if (recommended) "recommended" else "your own"}") {
        if (!recommended) {
            TextButton(onClick = { edit(TimingPreset.LUCID_NIGHT::applyTo) }, enabled = enabled) {
                Text("Back to the recommended timing")
            }
        }
        val still = timed.waitForStillness
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = still, enabled = enabled, role = Role.Switch) { on ->
                    edit { it.copy(timed = it.timed.copy(waitForStillness = on)) }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wait until I'm still")
                Help(
                    "Each round also waits until the watch shows you lying still with your heart rate settling, and " +
                        "moving restarts the wait. Needs the watch tracking your sleep; without it the timer alone " +
                        "runs.",
                )
            }
            Switch(checked = still, onCheckedChange = null, enabled = enabled)
        }
        if (still) {
            IntSetting(
                "Still for", "How long you lie still before a round starts.",
                timed.stillForS / 60, 1..30, 1, "min", enabled,
            ) { v -> edit { it.copy(timed = it.timed.copy(stillForS = v * 60)) } }
        }
        // Each pushes the other along, so earliest never passes latest (validation would block Start), and latest
        // stays before Stop after (else the night ends before its first round).
        IntSetting(
            "First round, earliest", "The first round comes at a random minute between earliest and latest.",
            timed.firstSleepMinMin, 1..60, 1, "min", enabled,
        ) { v ->
            edit {
                val latest = maxOf(v, it.timed.firstSleepMaxMin)
                it.copy(
                    sessionMaxMin = maxOf(it.sessionMaxMin, latest / 5 * 5 + 5),
                    timed = it.timed.copy(firstSleepMinMin = v, firstSleepMaxMin = latest),
                )
            }
        }
        IntSetting(
            "First round, latest", "Give yourself time to fall asleep before the first round.",
            timed.firstSleepMaxMin, 1..90, 1, "min", enabled,
        ) { v ->
            edit {
                it.copy(
                    sessionMaxMin = maxOf(it.sessionMaxMin, v / 5 * 5 + 5),
                    timed = it.timed.copy(firstSleepMaxMin = v, firstSleepMinMin = minOf(v, it.timed.firstSleepMinMin)),
                )
            }
        }
        IntSetting(
            "Before the question", "Time between the voice and its question.",
            config.countdownS, 60..300, 15, "s", enabled,
        ) { v -> edit { it.copy(countdownS = v) } }
        IntSetting(
            "Time to answer", "How long you have to say what was going through your mind.",
            timed.phase1ReportWindowS, 30..90, 5, "s", enabled,
        ) { v -> edit { it.copy(timed = it.timed.copy(phase1ReportWindowS = v)) } }
        IntSetting(
            "Before the next round", "Time to drift off again before the next round.",
            timed.betweenSleepS, 60..1200, 30, "s", enabled,
        ) { v -> edit { it.copy(timed = it.timed.copy(betweenSleepS = v)) } }
        IntSetting(
            "Rounds", "How many times the voice speaks and asks its question.",
            config.maxCycles, 1..10, 1, "", enabled,
        ) { v -> edit { it.copy(maxCycles = v) } }
        IntSetting(
            "Stop after", "The voice stops after this long, even if rounds are left.",
            config.sessionMaxMin, 15..180, 5, "min", enabled,
        ) { v ->
            edit {
                val latest = minOf(it.timed.firstSleepMaxMin, v - 5)
                val earliest = minOf(it.timed.firstSleepMinMin, latest)
                it.copy(sessionMaxMin = v, timed = it.timed.copy(firstSleepMinMin = earliest, firstSleepMaxMin = latest))
            }
        }
    }
}

/** The watch alarms (owner, 2026-10-28): they ring on the watch, so the phone can stay in another room. */
@Composable
internal fun AlarmsCard(config: SessionConfig, enabled: Boolean, edit: Edit, viewModel: MainViewModel) {
    val onTest: (AlarmTone) -> Unit = viewModel::testAlarm
    val alarms = config.alarms
    fun change(transform: (AlarmConfig) -> AlarmConfig) = edit { it.copy(alarms = transform(it.alarms)) }
    val notSent = viewModel.ui.collectAsStateWithLifecycle().value.alarmsNotSent
    // Hiding a row never changes its value: tones, snoozes and the rest keep what was set.
    val more = LocalAdvanced.current
    Section("Alarms (on the watch)") {
        if (notSent) Text(ALARMS_NOT_SENT, color = MaterialTheme.colorScheme.error)
        Help("They ring on the watch while it tracks your sleep. Start the night sends them to the watch.")
        SwitchRow("Wake-up alarm", alarms.wakeEnabled, enabled) { on -> change { it.copy(wakeEnabled = on) } }
        if (alarms.wakeEnabled) {
            TimeRow("Wake up at", alarms.wakeAtMin, enabled) { m -> change { it.copy(wakeAtMin = m) } }
            if (more) ToneChips(alarms.wakeTone, enabled, onTest) { t -> change { it.copy(wakeTone = t) } }
            IntSetting(
                "Snoozes", "Each one waits ${alarms.snoozeMin} min. 0 = no snooze button.", alarms.maxSnoozes, 0..5, 1,
                "", enabled,
            ) { v -> change { it.copy(maxSnoozes = v) } }
        }
        if (more && AlarmTone.SPOKEN in listOfNotNull(
                alarms.wakeTone.takeIf { alarms.wakeEnabled },
                alarms.wbtbTone.takeIf { alarms.wbtbEnabled },
            )
        ) {
            OutlinedTextField(
                value = alarms.sleeperName,
                onValueChange = { name -> change { it.copy(sleeperName = name.take(30)) } },
                label = { Text("Your name") },
                supportingText = {
                    val name = alarms.sleeperName.trim().ifEmpty { "<name>" }
                    // Own wake-up words without "Good morning." never get the name (cueText).
                    val said = alarms.sleeperName.isBlank() || name in cueText(PromptCue.MORNING_WAKE, config)
                    Text(
                        if (said) {
                            "The spoken wake-up says \"Good morning, $name.\" Then tap Get the voice ready."
                        } else {
                            "Your own wake-up words are used. Add your name to them under What the voice says."
                        },
                    )
                },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow("Early wake-up", alarms.wbtbEnabled, enabled) { on ->
            // Late cues follow a WBTB alarm, so they go off with it (else Start would be blocked by validation).
            change { it.copy(wbtbEnabled = on, lateCues = if (on) it.lateCues else LateCueMode.OFF) }
        }
        if (alarms.wbtbEnabled) {
            val after = alarms.wbtbAfterSleepMin
            if (more) {
                SwitchRow("Time it from when you fall asleep", after != null, enabled) { on ->
                    change { it.copy(wbtbAfterSleepMin = if (on) WBTB_AFTER_SLEEP_MIN else null) }
                }
            }
            // With sleep timing the set time is the latest it rings, and the time used when the watch can't tell.
            val timeLabel = if (after == null) "Early wake-up at" else "No later than"
            TimeRow(timeLabel, alarms.wbtbAtMin, enabled) { m -> change { it.copy(wbtbAtMin = m) } }
            if (after != null) {
                Help(
                    "The watch wakes you ${hoursText(after)} after it sees you fall asleep. If you fall asleep after " +
                        "${clock12(alarms.wbtbAtMin - after)}, or it can't tell, it wakes you at " +
                        "${clock12(alarms.wbtbAtMin)} instead, even with less sleep.",
                )
                // Only Samsung's kit tells the watch it is worn, which seeing you fall asleep needs (tickAlarms' worn).
                val watchAnswer by remember(viewModel) { viewModel.watchStatus.map { Optional.ofNullable(it) } }
                    .collectAsStateWithLifecycle(initialValue = null)
                if (watchAnswer?.orElse(null)?.let { it.samsungKit == null } == true) { // no answer: unknown, say nothing
                    Text(
                        "Your watch needs Samsung's sensor kit for this (Settings › More › Samsung's sensor kit). " +
                            "Without it, the early wake-up rings at the set time.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IntSetting(
                    "Wake me after falling asleep",
                    "${hoursText(after)}. The studies used 4½ to 6½ hours, most often 6.",
                    after, MIN_SLEEP_BEFORE_WBTB_MIN..420, 15, "min", enabled,
                ) { v -> change { it.copy(wbtbAfterSleepMin = v) } }
            }
            Help(
                (if (after == null) "About 5 to 6 hours after you fall asleep. " else "") + "A buzz tells you when " +
                    "to go back to bed. Best on 1 to 3 nights a week: skip it on the watch on the other nights.",
            )
            IntSetting(
                "Stay up for", "Before the back-to-sleep buzz.", alarms.wbtbAwakeMin, 5..60, 5, "min", enabled,
            ) { v -> change { it.copy(wbtbAwakeMin = v) } }
            if (alarms.wakeTooSoonAfterWbtb()) {
                Text(
                    "Your wake-up alarm is less than 2 hours after you go back to bed: too soon for the dreams " +
                        "that follow.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
                TextButton(
                    onClick = { change { it.copy(wbtbAtMin = it.wbtbAtMinBeforeWake()) } },
                    enabled = enabled,
                ) { Text("$timeLabel ${clock12(alarms.wbtbAtMinBeforeWake())} instead") }
            } else if (alarms.wakeBeforeWbtb()) {
                Text(
                    "Your wake-up alarm is before your early wake-up, so the early wake-up won't ring.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (more) {
                ToneChips(alarms.wbtbTone, enabled, onTest) { t -> change { it.copy(wbtbTone = t) } }
                SwitchRow("Guided back-to-sleep (on the watch)", alarms.wbtbGuide, enabled) { on ->
                    change { it.copy(wbtbGuide = on) }
                }
                if (alarms.wbtbGuide) {
                    Help(
                        "After the buzz, tap I'm in bed on the watch. Get the voice ready sends the guide to the " +
                            "watch.",
                    )
                }
                LateCuesSettings(alarms, enabled, viewModel, ::change)
            }
        }
        if (alarms.wakeEnabled || alarms.wbtbEnabled) {
            IntSetting(
                "Volume rise", "The tone starts quiet and reaches the watch's alarm volume after this long.",
                alarms.rampS, 0..300, 10, "s", enabled,
            ) { v -> change { it.copy(rampS = v) } }
        }
    }
}

/** "6 hours", "4½ hours", "6¾ hours": a whole number of quarter hours in words. */
internal fun hoursText(min: Int): String = "${min / 60}${listOf("", "¼", "½", "¾")[min % 60 / 15]} hours"

/** Late-night cues after WBTB (lucid_induction.md §5): off, on, or a blinded experiment. */
@Composable
private fun LateCuesSettings(
    alarms: AlarmConfig,
    enabled: Boolean,
    viewModel: MainViewModel,
    change: ((AlarmConfig) -> AlarmConfig) -> Unit,
) {
    Text("Soft sounds after the early wake-up")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val modes = listOf(LateCueMode.OFF to "Off", LateCueMode.ON to "On", LateCueMode.EXPERIMENT to "Test if they help")
        for ((mode, label) in modes) {
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
            LateCueMode.OFF -> "No sounds after the early wake-up."
            LateCueMode.ON ->
                "The watch teaches you a soft three-note sound, then plays it quietly once you're asleep again. Not " +
                    "proven to work."
            LateCueMode.EXPERIMENT ->
                "The watch secretly plays the sounds on some nights and not others, so Stats can show whether they help." +
                    if (alarms.wakeEnabled) "" else " Turn on the wake-up alarm: Stats compares the dream questions " +
                        "after it."
        },
    )
    if (alarms.lateCues != LateCueMode.OFF) {
        Text("Sound loudness: ${(alarms.lateCueVolume * 100).roundToInt()} %")
        Help("Lying in bed, set it so you would notice the sound but it would not wake you.")
        Slider(
            value = alarms.lateCueVolume,
            onValueChange = { v -> change { it.copy(lateCueVolume = (v * 50).roundToInt() / 50f) } },
            valueRange = 0.02f..1f,
            enabled = enabled,
        )
        TextButton(onClick = { viewModel.testLateCue(alarms.lateCueVolume) }, enabled = enabled) {
            Text("Play the sound on the watch")
        }
        IntSetting(
            "First sound after",
            "How long after you fall back asleep. Dreams usually return about 40 minutes later.",
            alarms.lateCueAfterOnsetMin, 10..120, 5, "min", enabled,
        ) { v -> change { it.copy(lateCueAfterOnsetMin = v) } }
        if (alarms.lateCuesAfterWake()) {
            Text(
                "Your wake-up alarm comes before the first sound, so no sound would play.",
                color = MaterialTheme.colorScheme.tertiary,
            )
            alarms.lateCueAfterOnsetMinThatFits()?.let { fit ->
                TextButton(onClick = { change { it.copy(lateCueAfterOnsetMin = fit) } }, enabled = enabled) {
                    Text("First sound after $fit min instead")
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
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
        Text("$label ${clock12(minuteOfDay)}", modifier = Modifier.weight(1f))
        TextButton(onClick = { picking = true }, enabled = enabled) { Text("Change") }
    }
    if (picking) {
        val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * 60 + state.minute)
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            // Scrolls: the clock is taller than a phone held sideways or with a large display size.
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state) } },
        )
    }
}

/** The tone choices, and a button that plays the selected one on the watch for a few seconds. */
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
    AlarmTone.SPOKEN -> "Spoken"
}

/** Where the cues play (phone or watch), and where the watch plays them and its alarms: speaker or its earbuds. */
@Composable
private fun WatchAudioCard(config: SessionConfig, enabled: Boolean, edit: Edit, watchAnswer: Optional<WatchStatus>?) {
    Section("Where the voice plays") {
        val onWatch = config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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
                Text("Play the voice on the watch")
                Help(
                    if (onWatch) {
                        "The watch plays the voice at its media volume. The phone can be in another room."
                    } else {
                        "The voice plays from the phone speaker."
                    },
                )
            }
            Switch(checked = onWatch, onCheckedChange = null, enabled = enabled)
        }
        // Someone who chose earbuds keeps seeing the choice, so they can go back to the speaker.
        if (LocalAdvanced.current || config.audio.watchOutput == WatchOutput.EARBUDS) {
            Text("The watch plays through:")
            Help(
                "Where the watch plays the voice and alarms. Earbuds let you sleep with them in without waking anyone.",
            )
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
        }
        if (config.audio.watchOutput == WatchOutput.EARBUDS) {
            Text(
                watchAnswer?.let { answer ->
                    answer.orElse(null)?.earbuds?.let { "Earbuds connected to the watch: $it" }
                        ?: "No earbuds connected to the watch right now: the speaker plays instead."
                } ?: "Checking the watch…",
                style = MaterialTheme.typography.bodyMedium,
            )
            Help(
                "Pair the earbuds with the watch, not the phone: on the watch, Settings > Connections > Bluetooth. " +
                    "Alarms ring in the earbuds and on the watch, so an earbud that falls out cannot hide them. " +
                    "Test the voice once with the earbuds in: they keep their own volume.",
            )
        }
    }
}

@Composable
private fun SoundCard(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val cap = config.audio.routeCapGain[AudioRouteMode.NIGHTSTAND_SPEAKER] ?: 0f
    val top = maxOf(MIN_GAIN, cap)
    val gain = config.audio.userGain.coerceIn(MIN_GAIN, top)
    Section("Sound") {
        AnimatedVisibility(LocalAdvanced.current, label = "volume") {
            Column {
                Text("Voice loudness")
                Help("Fine-tunes the voice. Set the main loudness with your volume buttons, then Test the voice.")
                // The slider stops at the speaker cap (§7.7 step 3); playback clamps to it as well.
                Slider(
                    value = gain,
                    onValueChange = { g ->
                        edit { it.copy(audio = it.audio.copy(userGain = (g * 100).roundToInt() / 100f)) }
                    },
                    valueRange = MIN_GAIN..top,
                    enabled = enabled,
                )
            }
        }
        val chime = config.audio.endAlarm == EndAlarm.CHIME
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = chime, enabled = enabled, role = Role.Switch) { on ->
                    edit { it.copy(audio = it.audio.copy(endAlarm = if (on) EndAlarm.CHIME else EndAlarm.NONE)) }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Goodnight chime")
                Help("A soft chime and a goodnight line after the last round.")
            }
            Switch(checked = chime, onCheckedChange = null, enabled = enabled)
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

private const val MIN_GAIN = 0.15f // a voice at 0 % would make the night silent

/**
 * While [com.opentdi.mobile.UiState.alarmsNotSent]: the phone resends the change until the watch answers, with the app
 * closed too (WatchSyncJob).
 */
internal const val ALARMS_NOT_SENT =
    "The watch doesn't have your alarm change yet. Keep it near the phone: the phone keeps sending it. " +
        "If this stays, update the watch app: Settings › More › Install the watch app."

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
