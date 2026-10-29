package com.opentdi.mobile.session

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.opentdi.mobile.TAG_SESSION
import com.opentdi.mobile.logLine
import com.opentdi.mobile.stats.KEEP_CSV_NIGHTS
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.csvLine
import com.opentdi.shared.csv.readCsv
import com.opentdi.shared.csv.sessionStartMs
import com.opentdi.shared.model.StateReason
import com.opentdi.shared.model.TdiState
import java.io.File
import java.io.IOException
import java.io.StringReader

/** Session directory names are session IDs, `yyyyMMdd'T'HHmmss'Z'-xxxx`, so they sort by start time. */
private val SESSION_DIR_NAME = Regex("""\d{8}T\d{6}Z-[0-9a-f]{4}""")

/**
 * §12.1 retention: the session directories to delete so the newest [keep] and those younger than [keepDays] remain;
 * other names are never chosen. The stats re-read a night's p_transitions.csv until its watch copy is pruned.
 */
fun sessionDirsToDelete(
    names: Collection<String>,
    nowMs: Long,
    keep: Int = 30,
    keepDays: Long = KEEP_CSV_NIGHTS + 1L,
): List<String> = names.filter { SESSION_DIR_NAME.matches(it) }.sorted().dropLast(keep)
    .filter { (sessionStartMs(it) ?: 0L) < nowMs - keepDays * 86_400_000L }

/** Deletes old session directories in filesDir/sessions ([sessionDirsToDelete]) and nothing else (run at app start). */
fun deleteOldSessions(filesDir: File, nowMs: Long = System.currentTimeMillis()) {
    val root = File(filesDir, SESSIONS_DIR)
    val names = root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: return
    val running = SessionMonitor.status.value.takeIf { it.running }?.sessionId // sorts oldest after a clock change
    (sessionDirsToDelete(names, nowMs) - setOfNotNull(running)).forEach { File(root, it).deleteRecursively() }
}

/**
 * §14.3 "Killed by the user (Task Manager Stop)": the p_transitions.csv row that marks the session aborted, or null.
 * Due only when the newest process exit was REASON_USER_REQUESTED, after the file's last write, and the session never
 * reached SESSION_COMPLETE. elapsed_ms is unknown after the kill, so it stays empty.
 */
fun userKillRow(transitionsCsv: String, lastModifiedMs: Long, exitReason: Int, exitTimestampMs: Long): List<Any?>? {
    if (exitReason != ApplicationExitInfo.REASON_USER_REQUESTED || exitTimestampMs <= lastModifiedMs) return null
    if (!transitionsCsv.endsWith("\n")) return null // empty, or the kill cut its last row: left as it is
    val last = readCsv(StringReader(transitionsCsv)).drop(1).lastOrNull() ?: return null
    fun field(name: String) = last.getOrNull(CsvFiles.P_TRANSITIONS.columns.indexOf(name))
    if (field("to") == TdiState.SESSION_COMPLETE.name) return null
    return listOf(
        exitTimestampMs, null, field("to"), TdiState.SESSION_COMPLETE, StateReason.USER_ABORT, field("cycle"),
        field("driver"),
    )
}

/** Closes the newest session with [userKillRow] and says so on the screen (run at app start, off the main thread). */
fun closeKilledSession(context: Context) {
    if (SessionMonitor.status.value.running) return
    val exit = context.getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
    val root = File(context.filesDir, SESSIONS_DIR)
    val id = root.list()?.filter { SESSION_DIR_NAME.matches(it) }?.maxOrNull() ?: return
    val file = File(root, "$id/${CsvFiles.P_TRANSITIONS.name}")
    try {
        if (!file.isFile) return
        val row = userKillRow(file.readText(), file.lastModified(), exit.reason, exit.timestamp) ?: return
        file.appendText(csvLine(row))
    } catch (e: IOException) {
        Log.w(TAG_SESSION, logLine("CSV_FAILED", "dir" to id, "error" to e.message))
        return
    } catch (e: IllegalArgumentException) { // a damaged file is left as it is
        return
    }
    Log.w(TAG_SESSION, logLine("SESSION_KILLED", "id" to id, "exit" to "USER_REQUESTED", "exitMs" to exit.timestamp))
    // Start asks about battery only while it is restricted; otherwise the user closed or stopped the app.
    val power = context.getSystemService(PowerManager::class.java)
    val message = if (power.isIgnoringBatteryOptimizations(context.packageName)) {
        "Last time OpenTDI was closed while the voice was on (swiped away, or stopped in Active apps). " +
            "Leave it running until the voice ends."
    } else {
        "Last time the voice was stopped from outside OpenTDI, for example by battery saving. " +
            "Tap Allow when Start asks about battery."
    }
    SessionMonitor.update { if (it.running) it else it.copy(error = message) }
}

const val SESSIONS_DIR = "sessions"
