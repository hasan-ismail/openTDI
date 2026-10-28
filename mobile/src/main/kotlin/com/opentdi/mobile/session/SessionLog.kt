package com.opentdi.mobile.session

import android.os.SystemClock
import android.util.Log
import com.opentdi.mobile.TAG_SESSION
import com.opentdi.mobile.audio.AudioEvent
import com.opentdi.mobile.logLine
import com.opentdi.shared.csv.CsvFile
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.timed.TimedEvent
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * The phone CSVs of one session in [dir] (§12.1, §12.3): p_config.csv, p_transitions.csv and p_audio.csv. Thread-safe.
 * The owner flushes it every 5 s; a transition flushes at once. After [close] or a write error, rows are dropped.
 */
class SessionLog(private val dir: File, private val startRealtimeMs: Long, private val route: AudioRouteMode) :
    Closeable {
    private val transitions = open(CsvFiles.P_TRANSITIONS)
    private val audio = open(CsvFiles.P_AUDIO)
    private var closed = false

    /** p_config.csv, written whole and closed. */
    fun writeConfig(rows: List<Pair<String, String>>) {
        try {
            open(CsvFiles.P_CONFIG).use { csv -> rows.forEach { (key, value) -> csv.row(key, value) } }
        } catch (e: IOException) {
            failed(e)
        }
    }

    @Synchronized
    fun transition(e: TimedEvent.StateChanged) = write {
        transitions.row(System.currentTimeMillis(), e.atMs, e.from, e.to, e.reason, e.cycle, "TIMED")
        transitions.flush()
        audio.flush()
    }

    @Synchronized
    fun audio(e: AudioEvent, cycle: Int) = write {
        audio.row(
            System.currentTimeMillis(), SystemClock.elapsedRealtime() - startRealtimeMs, cycle, e.event, e.cue, route,
            e.routedType, e.gain, e.fadeMs, e.focus, e.dndFilter, e.dndCategories, e.streamIndex, e.detail,
        )
    }

    @Synchronized
    fun flush() = write {
        transitions.flush()
        audio.flush()
    }

    @Synchronized
    override fun close() = write {
        closed = true
        transitions.close()
        audio.close()
    }

    private inline fun write(block: () -> Unit) {
        if (closed) return
        try {
            block()
        } catch (e: IOException) {
            closed = true // a full disk ends the logging, never the nap
            failed(e)
        }
    }

    private fun failed(e: IOException) = Log.w(TAG_SESSION, logLine("CSV_FAILED", "dir" to dir.name, "error" to e.message))

    private fun open(file: CsvFile) = CsvWriter(File(dir, file.name).bufferedWriter(), file.columns)
}
