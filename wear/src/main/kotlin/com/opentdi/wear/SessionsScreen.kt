package com.opentdi.wear

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.deleteSessionDirs
import com.opentdi.shared.csv.sessionDirs
import com.opentdi.shared.csv.sessionStartMs
import com.opentdi.shared.csv.sizeOnDisk
import com.opentdi.shared.csv.watchSessionDurationMs
import com.opentdi.shared.csv.watchSessionSummary
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.logEvent
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One filesDir/sessions directory (§12.1) as the Sessions screens show it. */
private class WatchSession(val id: String, val startMs: Long, val bytes: Long, val durationMs: Long?)

/**
 * The logging sessions on the watch, newest first, to delete test sessions: tap one for its details and Delete, or
 * Delete all. The running session ([runningId]) is listed but cannot be opened or deleted.
 */
@Composable
fun SessionsScreen(runningId: String?) {
    val context = LocalContext.current
    val root = remember { File(context.filesDir, "sessions") }
    var reload by remember { mutableIntStateOf(0) }
    val sessions by produceState<List<WatchSession>?>(null, reload, runningId) {
        value = withContext(Dispatchers.IO) { loadSessions(root) }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = sessions?.firstOrNull { it.id == openId }
    val scope = rememberCoroutineScope()
    fun delete(ids: List<String>) {
        scope.launch {
            val state = SensingStateHolder.state.value
            val count = withContext(Dispatchers.IO) {
                deleteSessionDirs(root, ids, runningId = state.sessionId.takeIf { state.running })
            }
            logEvent("SESSIONS_DELETED", "count" to count)
            openId = null
            reload++
        }
    }
    BackHandler(enabled = open != null) { openId = null }
    SwipeToDismissBox(onDismissed = { openId = null }, userSwipeEnabled = open != null) { isBackground ->
        if (open != null && !isBackground) {
            SessionDetails(open, File(root, open.id), onDelete = { delete(listOf(open.id)) })
        } else {
            SessionList(sessions, runningId, onOpen = { openId = it }, onDeleteAll = ::delete)
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<WatchSession>?,
    runningId: String?,
    onOpen: (String) -> Unit,
    onDeleteAll: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val deletable = sessions.orEmpty().map { it.id }.filter { it != runningId }
    var confirm by remember { mutableStateOf(false) }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Sessions") } }
            if (sessions == null) item { Line("Loading…") }
            if (sessions?.isEmpty() == true) item { Line("No sessions yet") }
            items(sessions.orEmpty(), key = { it.id }) { s ->
                val running = s.id == runningId
                Card(onClick = { onOpen(s.id) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    Text(startText(context, s))
                    Text(if (running) "Running" else listOfNotNull(duration(s), size(context, s)).joinToString(" · "))
                }
            }
            if (deletable.isNotEmpty()) {
                item {
                    FilledTonalButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete all")
                    }
                }
            }
        }
    }
    ConfirmDelete(
        visible = confirm,
        title = if (deletable.size == 1) "Delete 1 session?" else "Delete ${deletable.size} sessions?",
        onDismiss = { confirm = false },
        onConfirm = { onDeleteAll(deletable) },
    )
}

@Composable
private fun SessionDetails(s: WatchSession, dir: File, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    // Counted for the opened session only: it reads the whole file.
    val packets by produceState("…", dir) {
        value = withContext(Dispatchers.IO) {
            readPackets(dir) { it.useLines(block = ::watchSessionSummary).packets.toString() } ?: "—"
        }
    }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(startText(context, s)) } }
            item { Line("Duration ${duration(s) ?: "—"}") }
            item { Line("Packets $packets") }
            item { Line("Size ${size(context, s)}") }
            item { Line(s.id) }
            item {
                FilledTonalButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            }
        }
    }
    ConfirmDelete(confirm, "Delete this session?", onDismiss = { confirm = false }, onConfirm = onDelete)
}

@Composable
private fun ConfirmDelete(visible: Boolean, title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            )
        },
        title = { Text(title) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        text = { Text("This cannot be undone.") },
    )
}

private fun loadSessions(root: File): List<WatchSession> = sessionDirs(root).map { dir ->
    val durationMs = readPackets(dir, ::watchSessionDurationMs)
    WatchSession(dir.name, sessionStartMs(dir.name) ?: dir.lastModified(), sizeOnDisk(dir), durationMs)
}

/** [read] of the session's w_packets.csv; null if the session stopped before its CSVs existed. */
private fun <T> readPackets(dir: File, read: (File) -> T): T? = try {
    read(File(dir, CsvFiles.W_PACKETS.name))
} catch (e: IOException) {
    null
}

private fun startText(context: Context, s: WatchSession) = DateUtils.formatDateTime(
    context, s.startMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
)

private fun duration(s: WatchSession) = s.durationMs?.let { DateUtils.formatElapsedTime(it / 1000) }

private fun size(context: Context, s: WatchSession): String = Formatter.formatShortFileSize(context, s.bytes)
