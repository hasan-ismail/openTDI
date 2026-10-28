package com.opentdi.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.opentdi.mobile.ROUTINE
import com.opentdi.mobile.loadRoutine
import com.opentdi.mobile.saveRoutine

/** The bedtime routine, folded to one line with its progress; open at first while nothing is ticked yet. */
@Composable
fun RoutineCard() {
    val context = LocalContext.current
    var done by remember { mutableStateOf(loadRoutine(context)) }
    var open by rememberSaveable { mutableStateOf(done.isEmpty()) }
    val progress by animateFloatAsState(done.size.toFloat() / ROUTINE.size, tween(400), label = "routine")
    Section(null) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Shown" else "Hidden" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Bedtime routine · ${done.size} of ${ROUTINE.size} done",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Chevron(open)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        AnimatedVisibility(open, label = "routine") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Help("Small habits that help you fall asleep and dream. Tick them off; they reset each evening.")
                Help(
                    "Skip a hot bath if you feel faint, are pregnant, have heart problems or drank alcohol: a warm " +
                        "shower is safer. Trouble sleeping for weeks is worth a doctor's visit, not just a checklist.",
                )
                for (step in ROUTINE) {
                    val checked = step.id in done
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox) { on ->
                            done = if (on) done + step.id else done - step.id
                            saveRoutine(context, done)
                        },
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(step.text, style = MaterialTheme.typography.bodyLarge)
                            Help("${step.whenText} · ${step.why}")
                        }
                    }
                }
            }
        }
    }
}
