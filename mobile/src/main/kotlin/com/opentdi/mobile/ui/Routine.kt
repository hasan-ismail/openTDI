package com.opentdi.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentdi.mobile.ROUTINE

/** The bedtime routine as tips, in a popup: it opens by itself once, then from Settings > More > Bedtime tips. */
@Composable
fun BedtimeTipsDialog(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Text("Got it") } },
        title = { Text("Bedtime tips") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Help("Small habits that help you fall asleep and dream. None of them is required.")
                for (step in ROUTINE) {
                    Column {
                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
                        Help("${step.whenText} · ${step.why}")
                    }
                }
                Help(
                    "Skip a hot bath if you feel faint, are pregnant, have heart problems or drank alcohol: a warm " +
                        "shower is safer. Trouble sleeping for weeks is worth a doctor's visit.",
                )
            }
        },
    )
}
