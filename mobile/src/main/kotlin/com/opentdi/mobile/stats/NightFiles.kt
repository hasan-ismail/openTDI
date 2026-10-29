package com.opentdi.mobile.stats

import com.opentdi.mobile.session.NapSessionService
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.phoneSessionSummary
import com.opentdi.shared.csv.sessionDirs
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.isSyncedWatchFile
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.stats.nightDate
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable

// The files behind NightsRepository (owner, 2026-10-28: "nice graphs ... over months and weeks"): the phone's copies of
// the watch sessions in filesDir/watch/<sessionId>/ (SessionFiles.kt), each with its cached night summary, and the
// phone's own sessions in filesDir/sessions read into PhoneSessions.

internal const val WATCH_DIR = "watch"
internal const val SUMMARY_FILE = "night_summary.json"

/** filesDir/watch/<sessionId>/night_summary.json: the night's summary (null = too little to summarise), its input. */
@Serializable
internal data class SummaryCache(
    val version: Int,
    /**
     * The [fingerprint] it was computed from, plus "<phone sessionId>/p_transitions.csv" of the overlapping phone
     * sessions; once [pruned], what the phone had of each CSV.
     */
    val files: Map<String, Long>,
    val summary: NightSummary? = null,
    /** The CSVs were deleted to save space: the summary is kept as it is. */
    val pruned: Boolean = false,
)

/** One phone session as the stats count it, cached with the [fingerprint] of its CSVs. */
@Serializable
internal data class PhoneSession(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val loops: Int,
    val cues: Int,
    val files: Map<String, Long> = emptyMap(),
)

/** One watch copy's night, ready to join with the phone's sessions. */
internal data class WatchNight(val night: LocalDate, val summary: NightSummary?, val recordings: List<File>)

/** The CSVs in [dir] by name and byte count. They only grow, so a changed count means new data to read. */
internal fun fingerprint(dir: File): Map<String, Long> =
    dir.listFiles()?.filter { it.isFile && it.name.endsWith(".csv") }?.associate { it.name to it.length() }.orEmpty()

internal fun isRecording(name: String): Boolean = name.endsWith(".m4a") && isSyncedWatchFile(name)

private val AUDIO_EVENT = CsvFiles.P_AUDIO.columns.indexOf("event")

/**
 * A phone session from its CSV texts, headers included: the first to the last p_transitions.csv row, loops completed as
 * the Sessions screen counts them and cues that began to play (p_audio.csv CUE_START). Null when p_transitions.csv has
 * no complete row, and for a Quick test, which is no night.
 */
internal fun phoneSession(id: String, transitions: String, audio: String, config: String): PhoneSession? {
    if (config.lineSequence().any { it == "${NapSessionService.QUICK_TEST_ROW},1" }) return null
    val rows = transitions.lineSequence()
    val summary = phoneSessionSummary(rows)
    val durationMs = summary.durationMs ?: return null
    val startMs = rows.drop(1).firstNotNullOf { it.substringBefore(',').toLongOrNull() }
    return PhoneSession(
        id, startMs, startMs + durationMs, summary.completedCycles,
        cues = audio.lineSequence().count { it.split(',').getOrNull(AUDIO_EVENT) == "CUE_START" },
    )
}

/**
 * One row per night (noon to noon) with a summary, a phone session or a recording, newest first. Of several watch
 * copies in one night (a restart), the longest recording's sleep estimate shows, with every copy's reports and alarms.
 */
internal fun joinNights(watch: List<WatchNight>, phone: List<PhoneSession>, zone: ZoneId): List<NightRow> {
    val watchByNight = watch.groupBy { it.night }
    val phoneByNight = phone.sortedBy { it.startMs }.groupBy { nightDate(it.startMs, zone) }
    return (watchByNight.keys + phoneByNight.keys).sortedDescending().mapNotNull { night ->
        val copies = watchByNight[night].orEmpty()
        val sessions = phoneByNight[night].orEmpty()
        val parts = copies.mapNotNull { it.summary }
        // A restart writes the later alarms and reports to a new folder, so they come from every part.
        val summary = parts.maxByOrNull { it.recordEndMs - it.recordStartMs }?.let { s ->
            s.copy(
                reports = parts.flatMap { it.reports }.sortedBy { it.atMs },
                wake = s.wake ?: parts.mapNotNull { it.wake }.minByOrNull { it.ringAtMs },
                wbtb = s.wbtb ?: parts.firstNotNullOfOrNull { it.wbtb },
                lateCues = s.lateCues ?: parts.firstNotNullOfOrNull { it.lateCues },
            )
        }
        val recordings = copies.flatMap { it.recordings }.sortedBy { fromNoon(it.name) }
        val phoneNight = sessions.takeIf { it.isNotEmpty() }?.let { s ->
            PhoneNight(s.first().startMs, s.maxOf { it.endMs }, s.sumOf { it.loops }, s.sumOf { it.cues })
        }
        NightRow(night, summary, phoneNight, recordings)
            .takeIf { summary != null || phoneNight != null || recordings.isNotEmpty() }
    }
}

/** The p_transitions.csv in [sessionsDir] (filesDir/sessions) of each [phone] session that overlaps [startMs, endMs). */
internal fun transitionsDuring(phone: List<PhoneSession>, sessionsDir: File, startMs: Long, endMs: Long): List<File> =
    phone.filter { it.startMs < endMs && it.endMs >= startMs }
        .map { File(sessionsDir, "${it.id}/${CsvFiles.P_TRANSITIONS.name}") }.filter { it.isFile }

/** [files] as one p_transitions.csv, the header once, for summarizeNight's Dormio wakings; null without a file. */
internal fun joinTransitions(files: List<File>): String? = files.takeIf { it.isNotEmpty() }?.let {
    (listOf(CsvFiles.P_TRANSITIONS.columns.joinToString(",")) + it.flatMap { f -> f.readLines().drop(1) })
        .joinToString("\n", postfix = "\n")
}

/** w_dream_HHmmss.m4a's local time as HHmmss from noon, so a night's evening recordings sort before its morning's. */
private fun fromNoon(name: String): Int =
    ((name.removePrefix("w_dream_").take(6).toIntOrNull() ?: 0) + 120_000) % 240_000

/** What the phone has of each watch session in [root] (filesDir/watch), as SessionFilesRequest.have. */
internal fun have(root: File): Map<String, Long> = buildMap {
    for (dir in sessionDirs(root)) {
        // A pruned night still counts its CSVs at their cached size, so the watch does not send them again.
        readCache(dir)?.takeIf { it.pruned }?.files?.forEach { (name, bytes) -> put("${dir.name}/$name", bytes) }
        for (file in dir.listFiles().orEmpty()) {
            if (file.isFile && isSyncedWatchFile(file.name)) put("${dir.name}/${file.name}", file.length())
        }
    }
}

/**
 * Writes one chunk of [sessionId]/[fileName] at [offset] under [root] (filesDir/watch), adding the bytes to [copied] as
 * they arrive; offset 0 starts the file over. False, with the chunk unread, when [offset] lies past the end of the
 * phone's file: a pruned night's CSV has nothing to continue.
 */
internal fun writeChunk(
    root: File,
    sessionId: String,
    fileName: String,
    offset: Long,
    data: InputStream,
    copied: AtomicLong,
): Boolean {
    val target = File(root, "$sessionId/$fileName")
    // readChunks already refuses other names; this also refuses a link that leads out of filesDir/watch.
    if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
        throw IOException("refused $sessionId/$fileName")
    }
    if (offset > target.length()) return false
    target.parentFile?.mkdirs()
    RandomAccessFile(target, "rw").use { file ->
        file.setLength(offset)
        file.seek(offset)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = data.read(buffer)
            if (n < 0) break
            file.write(buffer, 0, n)
            copied.addAndGet(n.toLong())
        }
    }
    return true
}

internal fun readCache(dir: File): SummaryCache? = try {
    OpenTdiJson.decodeFromString<SummaryCache>(File(dir, SUMMARY_FILE).readText())
} catch (e: IOException) {
    null
} catch (e: IllegalArgumentException) { // SerializationException: damaged
    null
}

internal fun writeCache(dir: File, cache: SummaryCache) =
    writeWhole(File(dir, SUMMARY_FILE), OpenTdiJson.encodeToString(cache))

/** Replaces [file] whole or not at all: a pruned night's summary cannot be computed again. */
internal fun writeWhole(file: File, text: String) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) throw IOException("Could not store ${file.name}")
}
