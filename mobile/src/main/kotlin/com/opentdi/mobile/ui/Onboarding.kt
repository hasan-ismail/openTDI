package com.opentdi.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.cues.cueSetProblem
import com.opentdi.mobile.session.SessionMonitor

private enum class Step(val title: String) {
    WELCOME("Welcome"),
    SAFETY("Before you start"),
    WATCH("Your watch"),
    SENSORS("The watch's sensors"),
    VOICE("The voice"),
    ALARMS("Alarms"),
    FIRST_NIGHT("Your first night"),
}

/**
 * The set-up guide (owner, 2026-10-28: "a hold-your-hand process"): one step at a time from what OpenTDI does to the
 * first night, using the real controls (watch install, voice, alarms), so nothing is set up twice. Shown on the first
 * launch and again from Settings.
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel, onAllowBattery: () -> Unit, onDone: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val step = Step.entries[index]
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    val cueProblem = remember(ui.config, ui.cues) { cueSetProblem(ui.cues, ui.config) }
    val idle = !status.running && !ui.preparing && !ui.testing

    Page(step.title) {
        Help("Step ${index + 1} of ${Step.entries.size}")
        LinearProgressIndicator(progress = { (index + 1f) / Step.entries.size }, modifier = Modifier.fillMaxWidth())
        when (step) {
            Step.WELCOME -> Section(null) {
                Text("OpenTDI helps you notice that you are dreaming: a lucid dream.", style = MaterialTheme.typography.titleMedium)
                Bullets(
                    "As you fall asleep, the phone or watch softly says \"You are dreaming. You are in control.\"",
                    "It waits until the watch shows you lying still, so tossing and turning is left alone.",
                    "Optionally the watch wakes you briefly in the early morning (WBTB), the best-supported way " +
                        "to have a lucid dream at home.",
                    "In the morning the watch wakes you gently and you tell it your dream.",
                    "Everything stays on your phone and watch.",
                )
            }
            Step.SAFETY -> Section(null) {
                Text(
                    "OpenTDI is wellness and research software, not a medical device. Its sleep estimates may be wrong.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Help(
                    "Talk to a doctor first if you have ever had a psychotic disorder, narcolepsy, frequent sleep " +
                        "paralysis, sleepwalking or another sleep disorder. Lucid-dream practice disturbs sleep a " +
                        "little; take nights off when you need them.",
                )
                Row(
                    Modifier.fillMaxWidth().toggleable(value = agreed, role = Role.Checkbox) { agreed = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = null)
                    Text("I understand", modifier = Modifier.weight(1f))
                }
            }
            Step.WATCH -> {
                WatchSetupCard()
                Help("Already have OpenTDI on the watch? Go on to the next step.")
            }
            Step.SENSORS -> {
                SensorsStep(onAllowBattery)
                SamsungKitCard()
            }

            Step.VOICE -> {
                Help("Choose a voice, tap Prepare cues, then Test cue at the volume you will sleep with.")
                VoiceCard(viewModel, ui, idle, volumeOff, cueProblem)
            }
            Step.ALARMS -> {
                Help("A gentle wake-up on the watch, and if you like, a short WBTB wake in the early morning.")
                AlarmsCard(ui.config, idle, viewModel::edit, viewModel)
            }
            Step.FIRST_NIGHT -> Section(null) {
                Text("Every night:", style = MaterialTheme.typography.titleMedium)
                Bullets(
                    "Go through Your bedtime routine on the Tonight tab.",
                    "Open OpenTDI on the watch: it waits for the phone.",
                    "Tap Start session on the phone, lie down and let go.",
                    "In the morning, tell the watch your dream and answer its few questions.",
                )
                Help("Give it a few weeks: lucid dreams come on some nights, not every night.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (index > 0) OutlinedButton(onClick = { index-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = { if (index == Step.entries.lastIndex) onDone() else index++ },
                enabled = step != Step.SAFETY || agreed,
                modifier = Modifier.weight(1f),
            ) { Text(if (index == Step.entries.lastIndex) "Start using OpenTDI" else "Next") }
        }
        if (step != Step.SAFETY && index != Step.entries.lastIndex) {
            TextButton(onClick = onDone) { Text("Skip the guide") }
        }
    }
}

@Composable
private fun Bullets(vararg lines: String) {
    for (line in lines) Text("•  $line", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SensorsStep(onAllowBattery: () -> Unit) {
    Section(null) {
        Text("Optional: Samsung's own sensors. The cues work with the watch's basic sensors too.")
        Text("Samsung only lets approved apps read its sensors, or any app while the watch is in developer mode.")
        // Samsung asks that its developer-mode guide come from Samsung, so the app links to it instead of copying it.
        Help("Samsung explains developer mode on developer.samsung.com/health/sensor/guide/developer-mode.html.")
        Text("Then add Samsung's kit below (it stays on your devices).")
        Text("So Android does not stop a night's session:")
        Button(onClick = onAllowBattery, modifier = Modifier.fillMaxWidth()) { Text("Allow unrestricted battery") }
    }
}
