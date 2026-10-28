package com.opentdi.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.opentdi.mobile.ROUTINE
import com.opentdi.mobile.loadRoutine
import com.opentdi.mobile.saveRoutine

@Composable
fun RoutineCard() {
    val context = LocalContext.current
    var done by remember { mutableStateOf(loadRoutine(context)) }
    Section("Your bedtime routine (${done.size} of ${ROUTINE.size})") {
        Help("Small habits that help you fall asleep and dream. Tick them off; they reset each evening.")
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
