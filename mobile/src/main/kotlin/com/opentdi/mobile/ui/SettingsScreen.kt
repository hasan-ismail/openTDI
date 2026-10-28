package com.opentdi.mobile.ui

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

private typealias Edit = ((SessionConfig) -> SessionConfig) -> Unit

/** Settings in cards: cue words, voice, timing, sound, about. Locked while a session runs or cues are prepared. */
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    val config = ui.config
    val cueProblem = remember(config, ui.cues) { cueSetProblem(ui.cues, config) }
    val idle = !status.running && !ui.preparing && !ui.testing
    val edit: Edit = viewModel::edit

    Page("Settings") {
        if (status.running) Help("Settings are locked while a session runs.")
        CueWordsCard(config, idle, edit)
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
        TimingCard(config, idle, edit)
        SoundCard(config, idle, edit)
        AboutCard()
    }
}

@Composable
private fun CueWordsCard(config: SessionConfig, enabled: Boolean, edit: Edit) {
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
        for (cue in PHASE1_CUES) CueWords(cue, config, enabled, edit)
        TextButton(
            onClick = { edit { it.copy(audio = it.audio.copy(cueTexts = emptyMap())) } },
            enabled = enabled && config.audio.cueTexts.isNotEmpty(),
        ) { Text("Reset all to the default words") }
    }
}

/** One cue's words: the user's own once edited, else the §7.1 wording with the theme placeholder. */
@Composable
private fun CueWords(cue: PromptCue, config: SessionConfig, enabled: Boolean, edit: Edit) {
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
    }
}

private fun cueLabel(cue: PromptCue) = when (cue) {
    PromptCue.INITIAL_INCUBATION -> "As you fall asleep"
    PromptCue.WAKE_PROMPT -> "Wake prompt (the question)"
    PromptCue.SLEEP_STATE_QUESTION -> "Sleep-state question"
    PromptCue.RE_INCUBATION -> "Back to sleep"
    PromptCue.SESSION_END -> "Session end, after the chime"
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

@Composable
private fun SoundCard(config: SessionConfig, enabled: Boolean, edit: Edit) {
    val cap = config.audio.routeCapGain[AudioRouteMode.NIGHTSTAND_SPEAKER] ?: 0f
    val gain = config.audio.userGain.coerceIn(0f, cap)
    Section("Sound") {
        Column {
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
