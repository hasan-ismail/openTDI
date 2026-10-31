package com.opentdi.mobile.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.opentdi.mobile.session.SESSIONS_DIR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The folders an export holds, under filesDir: the phone's voice nights and the nights copied from the watch. */
private val EXPORT_DIRS = listOf(SESSIONS_DIR, "watch")

/**
 * Zips every file under [dirs] (names relative to [base]) into [out], with the paths kept, e.g.
 * "watch/<night>/w_packets.csv". Returns the number of files and their bytes.
 */
internal fun writeExport(base: File, dirs: List<String>, out: OutputStream): Pair<Int, Long> {
    var files = 0
    var bytes = 0L
    ZipOutputStream(out.buffered()).use { zip ->
        for (dir in dirs) {
            File(base, dir).walk().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(base).invariantSeparatorsPath).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                files++
                bytes += f.length()
            }
        }
    }
    return files to bytes
}

/** Settings › More: saves every night (CSV files and dream recordings) as one zip file where the user picks. */
@Composable
fun ExportData() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // cancelled
        result = "Saving…"
        scope.launch {
            result = try {
                val (files, bytes) = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { writeExport(context.filesDir, EXPORT_DIRS, it) }
                        ?: error("the file couldn't be opened")
                }
                if (files == 0) "Saved, but there are no nights yet."
                else "Saved $files files (${Formatter.formatShortFileSize(context, bytes)})."
            } catch (e: Exception) {
                "Couldn't save: ${e.message ?: "unknown error"}. Try again, or pick another place."
            }
        }
    }
    Section("Export my data") {
        Help(
            "Saves every night as one zip file: the watch's sensor data, alarms and dream answers (CSV files you can " +
                "open in a spreadsheet) and your dream recordings. You pick where it goes, for example Downloads or Drive.",
        )
        OutlinedButton(onClick = { save.launch("OpenTDI-${LocalDate.now()}.zip") }, enabled = result != "Saving…") {
            Text("Export my data")
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
