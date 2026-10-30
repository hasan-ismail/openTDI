package com.opentdi.shared.csv

import com.opentdi.shared.model.StateReason
import com.opentdi.shared.model.TdiState
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.io.UncheckedIOException
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// The session directories of §12.1, filesDir/sessions/<sessionId>/, as both apps' Sessions screens list and delete
// them, and what those screens show of each session's CSVs.

/** `yyyyMMdd'T'HHmmss'Z'-xxxx` (newSessionId), the only names a session directory has. */
private val SESSION_ID = Regex("""\d{8}T\d{6}Z-[0-9a-f]{4}""")
private val SESSION_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

fun isSessionId(name: String): Boolean = SESSION_ID.matches(name)

/** The UTC start time a session ID begins with, in epoch ms; null for anything else or an impossible date. */
fun sessionStartMs(id: String): Long? {
    if (!isSessionId(id)) return null
    return try {
        LocalDateTime.parse(id.take(15), SESSION_ID_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}

/** The session directories in [root] (filesDir/sessions), newest first: IDs begin with the UTC start time. */
fun sessionDirs(root: File): List<File> =
    root.listFiles()?.filter { it.isDirectory && isSessionId(it.name) }.orEmpty().sortedByDescending { it.name }

/** Bytes in the files under [dir]. */
fun sizeOnDisk(dir: File): Long = dir.walk().filter { it.isFile }.sumOf { it.length() }

/**
 * Deletes the session directories [ids] in [root] (filesDir/sessions) and returns how many it deleted. It refuses
 * [runningId], names that are not session IDs and any directory whose canonical path is not directly in [root].
 */
fun deleteSessionDirs(root: File, ids: Collection<String>, runningId: String?): Int {
    val canonicalRoot = root.canonicalFile
    return ids.toSet().count { id ->
        val dir = File(canonicalRoot, id)
        id != runningId && isSessionId(id) && dir.isDirectory && dir.canonicalFile.parentFile == canonicalRoot &&
            deleteTree(dir)
    }
}

// Files.walk does not follow symbolic links (File.deleteRecursively does), and a child sorts after its parent.
private fun deleteTree(dir: File): Boolean = try {
    Files.walk(dir.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    true
} catch (e: IOException) {
    false
} catch (e: UncheckedIOException) { // the walk's own read errors
    false
}

/** A phone session from its p_transitions.csv: first to last row, and how it ended (null if it never did). */
data class PhoneSessionSummary(val durationMs: Long?, val endReason: StateReason?, val completedCycles: Int)

// Both files hold only numbers and enum names, never a quoted field, so a row splits on commas; the time is column 0.
private val TRANSITIONS = CsvFiles.P_TRANSITIONS.columns
private val TO = TRANSITIONS.indexOf("to")
private val REASON = TRANSITIONS.indexOf("reason")

/** [lines] of p_transitions.csv, header first. A row cut short when the app died is skipped. */
fun phoneSessionSummary(lines: Sequence<String>): PhoneSessionSummary {
    var first: Long? = null
    var last: Long? = null
    var endReason: StateReason? = null
    var completed = 0
    for (line in lines.drop(1)) {
        val fields = line.split(',')
        val wallMs = fields[0].toLongOrNull()
        if (fields.size != TRANSITIONS.size || wallMs == null) continue
        if (first == null) first = wallMs
        last = wallMs
        val reason = StateReason.entries.firstOrNull { it.name == fields[REASON] }
        if (reason == StateReason.CYCLE_COMPLETED) completed++
        if (fields[TO] == TdiState.SESSION_COMPLETE.name) {
            endReason = reason
            if (reason == StateReason.MAX_CYCLES) completed++ // the last cycle goes straight to SESSION_COMPLETE
        }
    }
    return PhoneSessionSummary(if (first != null && last != null) last - first else null, endReason, completed)
}

/**
 * A watch session's length, first to last ts_ms of w_packets.csv [file], from its first rows and last [TAIL_BYTES]
 * only: a night's file is several MB (§12.1), too much to read for every session in the watch's list. A row cut short
 * when the app died is skipped.
 */
fun watchSessionDurationMs(file: File): Long? {
    val first = file.useLines { lines -> lines.drop(1).firstNotNullOfOrNull(::packetTsMs) }
    val last = RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        val start = (length - TAIL_BYTES).coerceAtLeast(0)
        raf.seek(start)
        val tail = ByteArray((length - start).toInt()).also(raf::readFully)
        String(tail).lines().drop(if (start > 0) 1 else 0) // the read may begin mid-row
    }.asReversed().firstNotNullOfOrNull(::packetTsMs)
    return if (first != null && last != null) last - first else null
}

private const val TAIL_BYTES = 4096 // about 20 rows of ~200 B (§12.1)

// Nights logged before angle_z_deg, the last column since 2026-10-28, have rows one field shorter.
private val PACKET_COMMAS = CsvFiles.W_PACKETS.columns.size.let { setOf(it - 2, it - 1) }

/** A w_packets.csv row's ts_ms; null for the header and for a row cut short when the app died. */
private fun packetTsMs(line: String): Long? =
    if (line.count { it == ',' } in PACKET_COMMAS) line.substringBefore(',').toLongOrNull() else null
