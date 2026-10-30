package com.opentdi.mobile.ui

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentdi.mobile.MainViewModel
import com.opentdi.mobile.SessionItem
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.shared.csv.PhoneSessionSummary
import com.opentdi.shared.model.StateReason

/**
 * Settings' "Saved nights": the phone's session directories, newest first, to delete test nights; the running one
 * cannot be selected. A plain Column, not a LazyColumn, as it sits in the scrolling Settings page (at most 30 rows).
 */
@Composable
fun SavedNights(viewModel: MainViewModel) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val status by SessionMonitor.status.collectAsStateWithLifecycle()
    val runningId = status.sessionId.takeIf { status.running }
    val context = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf<List<String>?>(null) } // what the open dialog would delete
    val list = sessions.orEmpty()
    val deletable = list.map { it.id }.filter { it != runningId }
    val chosen = deletable.filter { it in selected }
    LaunchedEffect(status.running) { viewModel.loadSessions() } // again when a session starts or ends

    Section("Saved nights") {
        if (sessions?.isEmpty() == true) Text("No saved nights yet. Each night you start shows up here.")
        if (list.isNotEmpty()) {
            Help("What the voice did each night, newest first. Tap nights to select them for deleting.")
        }
        Column {
            for (s in list) {
                val running = s.id == runningId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = s.id in chosen, enabled = !running, role = Role.Checkbox) { on ->
                            selected = if (on) selected + s.id else selected - s.id
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = s.id in chosen, onCheckedChange = null, enabled = !running)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(DateUtils.formatDateTime(context, s.startMs, DATE_TIME))
                        Text(
                            if (running) "Running" else details(context, s),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Quiet buttons under the list: deleting is never the page's main action.
        if (list.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // wraps with large text
                OutlinedButton(onClick = { confirm = chosen }, enabled = chosen.isNotEmpty()) { Text("Delete selected") }
                TextButton(onClick = { confirm = deletable }, enabled = deletable.isNotEmpty()) { Text("Delete all") }
            }
        }
    }

    confirm?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (ids.size == 1) "Delete 1 night?" else "Delete ${ids.size} nights?") },
            text = {
                Text("What the voice did on these nights is removed from this phone and from Stats. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSessions(ids)
                        selected = selected - ids.toSet()
                        confirm = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private const val DATE_TIME = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_12HOUR

private fun details(context: Context, s: SessionItem): String = listOfNotNull(
    s.summary?.durationMs?.let { DateUtils.formatElapsedTime(it / 1000) },
    s.summary?.let(::outcome),
    Formatter.formatShortFileSize(context, s.bytes),
).joinToString(" · ")

private fun outcome(s: PhoneSessionSummary): String? {
    val how = when (s.endReason ?: return null) {
        StateReason.MAX_CYCLES -> "all done"
        StateReason.TIME_LIMIT -> "time limit"
        else -> "stopped" // USER_ABORT, which the service's error ends use too
    }
    return "${s.completedCycles} ${if (s.completedCycles == 1) "round" else "rounds"}, $how"
}
