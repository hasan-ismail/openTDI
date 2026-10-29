package com.opentdi.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.opentdi.mobile.UiState
import com.opentdi.mobile.cues.cueSetProblem
import com.opentdi.mobile.session.SessionMonitor

private enum class Step(val title: String) {
    WELCOME("Welcome"),
    SAFETY("Before you start"),
    WATCH("Your watch"),
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
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val volumeOff by viewModel.mediaVolumeOff.collectAsStateWithLifecycle(initialValue = false)
    val cueProblem = remember(ui.config, ui.cues) { cueSetProblem(ui.cues, ui.config) }
    val idle = !status.running && !ui.preparing && !ui.testing

    BackHandler(enabled = index > 0) { index-- } // Back goes back a step instead of closing the app
    Column(Modifier.fillMaxSize()) {
        val progress by animateFloatAsState((index + 1f) / Step.entries.size, tween(400), label = "progress")
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Each step is its own page, so each one opens scrolled to the top.
        AnimatedContent(
            index,
            Modifier.weight(1f),
            label = "step",
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (slideInHorizontally { d * it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -d * it / 4 } + fadeOut())
            },
        ) { i -> // the step this page shows; never index, which moves on before the old page has gone
            StepPage(i, agreed, { agreed = it }, { index = it }, viewModel, ui, idle, volumeOff, cueProblem, onAllowBattery, onDone)
        }
    }
}

@Composable
private fun StepPage(
    i: Int,
    agreed: Boolean,
    onAgree: (Boolean) -> Unit,
    goTo: (Int) -> Unit,
    viewModel: MainViewModel,
    ui: UiState,
    idle: Boolean,
    volumeOff: Boolean,
    cueProblem: String?,
    onAllowBattery: () -> Unit,
    onDone: () -> Unit,
) {
    val step = Step.entries[i]
    Page(step.title) {
        Help("Step ${i + 1} of ${Step.entries.size}")
        when (step) {
            Step.WELCOME -> Section(null) {
                Text("OpenTDI helps you notice that you are dreaming: a lucid dream.", style = MaterialTheme.typography.titleMedium)
                Bullets(
                    "As you fall asleep, the phone or watch softly says \"You are dreaming. You are in control.\"",
                    "It waits until the watch shows you lying still, so tossing and turning is left alone.",
                    "Optionally the watch wakes you briefly in the early morning, the best-supported way to have a " +
                        "lucid dream at home.",
                    "In the morning the watch wakes you gently and asks about your dream.",
                    "Everything stays on your phone and watch.",
                )
            }
            Step.SAFETY -> Section(null) {
                Text(
                    "OpenTDI is wellness and research software, not a medical device. Its sleep estimates may be wrong.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                // sleep_and_dreams.md §6 [91][92][93]
                Help(
                    "If you have a diagnosed sleep, mood, seizure or mental-health condition, ask your clinician " +
                        "before using OpenTDI.",
                )
                Help(
                    "Talk to a doctor first if you have ever had a psychotic disorder, narcolepsy, frequent sleep " +
                        "paralysis, sleepwalking or another sleep disorder. Lucid-dream practice disturbs sleep a " +
                        "little; take nights off when you need them.",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = agreed, role = Role.Checkbox, onValueChange = onAgree),
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
            Step.VOICE -> {
                Help("Choose a voice, tap Get the voice ready, then Test the voice at the volume you'll sleep with.")
                VoiceCard(viewModel, ui, idle, volumeOff, cueProblem)
            }
            Step.ALARMS -> {
                Help(
                    "The watch wakes you gently at this time, then asks about your dream. Change the time if you " +
                        "need to.",
                )
                AlarmsCard(ui.config, idle, viewModel::edit, viewModel)
            }
            Step.FIRST_NIGHT -> Section(null) {
                Text("Every night:", style = MaterialTheme.typography.titleMedium)
                Bullets(
                    "Open OpenTDI on the watch: it waits for the phone.",
                    "Tap Start the night on the phone, lie down and let go. If anything needs fixing first, it tells you.",
                    "In the morning, answer the watch's dream questions.",
                )
                Help("Give it a few weeks: lucid dreams come on some nights, not every night.")
                if (!ui.batteryUnrestricted) { // as checked at the last resume, so it goes once allowed
                    Text("One last thing: let OpenTDI run all night.")
                    Button(onClick = onAllowBattery, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow it to run all night")
                    }
                    if (ui.batteryManual) Help("Settings > Apps > OpenTDI > Battery > Unrestricted")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (i > 0) OutlinedButton(onClick = { goTo(i - 1) }, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = { if (i == Step.entries.lastIndex) onDone() else goTo(i + 1) },
                enabled = step != Step.SAFETY || agreed,
                modifier = Modifier.weight(1f),
            ) { Text(if (i == Step.entries.lastIndex) "Start using OpenTDI" else "Next") }
        }
        if (step > Step.SAFETY && i != Step.entries.lastIndex) { // never before the safety step is acknowledged
            TextButton(onClick = onDone) { Text("Skip the guide") }
        }
    }
}

@Composable
private fun Bullets(vararg lines: String) {
    for (line in lines) Text("•  $line", style = MaterialTheme.typography.bodyMedium)
}
