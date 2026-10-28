package com.opentdi.mobile.stats

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.opentdi.mobile.TAG_LINK
import com.opentdi.mobile.TAG_SESSION
import com.opentdi.mobile.logLine
import com.opentdi.mobile.session.SESSIONS_DIR
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.sessionDirs
import com.opentdi.shared.csv.sessionStartMs
import com.opentdi.shared.csv.watchSessionDurationMs
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.SessionFilesRequest
import com.opentdi.shared.model.readChunks
import com.opentdi.shared.model.writeRequest
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.stats.SUMMARY_VERSION
import com.opentdi.shared.stats.nightDate
import com.opentdi.shared.stats.summarizeNight
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Everything the Stats screens show about one night (noon to noon), from the watch, the phone and the routine. */
data class NightRow(
    val night: LocalDate,
    /** From the phone's copy of the watch session that recorded the night; null when it recorded too little or none. */
    val watch: NightSummary?,
    /** The phone's sleep-onset cue session(s) that night; null when none ran. */
    val phone: PhoneNight?,
    /**
     * Bedtime-routine steps ticked that evening, and how many there were, as the night's last phone session logged
     * them (RoutineStore keeps only tonight); null when none was ticked or no phone session ran.
     */
    val routineDone: Int?,
    val routineTotal: Int?,
    /** The night's dream recordings copied from the watch (w_dream_*.m4a), oldest first. */
    val recordings: List<File> = emptyList(),
)

/** The phone's own sessions that night (p_transitions.csv, p_audio.csv). */
data class PhoneNight(
    val sessionIds: List<String>,
    val startMs: Long,
    val endMs: Long,
    val loopsCompleted: Int,
    /** Cues that began to play (CUE_START). */
    val cuesPlayed: Int,
)

/** The result of one copy from the watch. */
data class SyncResult(val files: Int, val bytes: Long, val error: String? = null)

/**
 * The nights the phone knows, newest first, and copying new watch data to the phone (owner, 2026-10-28: "nice graphs
 * ... of your sleep time and dream state over months and weeks"). Watch copies live in filesDir/watch/<sessionId>/
 * (NightFiles.kt), the phone's own sessions in filesDir/sessions.
 */
object NightsRepository {
    private const val PHONE_CACHE = "phone_sessions.json"
    // GUESSED (stats plan, 2026-10-28): full copies for 120 nights; w_packets.csv alone is ~0.7 MB an hour (§12.1)
    private const val KEEP_CSV_NIGHTS = 120
    private const val IDLE_TIMEOUT_MS = 60_000L // GUESSED: a link that sent nothing for a minute is gone
    private val copying = Mutex()
    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w(TAG_LINK, logLine("WATCH_COPY_FAILED", "error" to e)) // best effort: never crash the caller
        },
    )

    /** Every night with a watch summary, a phone session or a recording, newest first. Call it off the main thread. */
    @Synchronized
    fun rows(context: Context): List<NightRow> {
        // shortcut: nights are dated in the phone's zone now, so after a trip an old night can move by a day; keep each
        // session's zone when that matters.
        val zone = ZoneId.systemDefault()
        val today = nightDate(System.currentTimeMillis(), zone)
        val phone = phoneSessions(context.filesDir)
        val sessionsDir = File(context.filesDir, SESSIONS_DIR)
        val watch = sessionDirs(File(context.filesDir, WATCH_DIR)).mapNotNull {
            watchNight(it, zone, today) { start, end -> transitionsDuring(phone, sessionsDir, start, end) }
        }
        return joinNights(watch, phone, zone)
    }

    /** Copies what the watch recorded since the last copy (SessionFiles.kt); one copy at a time. */
    suspend fun syncFromWatch(context: Context): SyncResult =
        copying.withLock { withContext(Dispatchers.IO) { copy(context.applicationContext) } }

    /** [syncFromWatch] without waiting for it, e.g. when a phone session ends. */
    fun syncInBackground(context: Context) {
        background.launch { syncFromWatch(context) }
    }

    private suspend fun copy(context: Context): SyncResult {
        val root = File(context.filesDir, WATCH_DIR).apply { mkdirs() }
        val copied = AtomicLong()
        val stalled = AtomicBoolean()
        var files = 0
        val error = try {
            val nodes = Wearable.getCapabilityClient(context)
                .getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes
            val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (node == null) {
                "The watch app is not reachable: install it and keep the watch connected."
            } else {
                val channels = Wearable.getChannelClient(context)
                val channel = channels.openChannel(node.id, DataLayerPaths.SESSION_FILES).await()
                try {
                    coroutineScope {
                        // A night is several MB over Bluetooth, so no total limit: only a link that stopped sending,
                        // or a cancelled copy, closes the channel, which ends a blocked read.
                        val watchdog = launch {
                            try {
                                var seen = -1L
                                while (copied.get() != seen) {
                                    seen = copied.get()
                                    delay(IDLE_TIMEOUT_MS)
                                }
                                stalled.set(true)
                            } finally {
                                channels.close(channel)
                            }
                        }
                        try {
                            val output = DataOutputStream(channels.getOutputStream(channel).await().buffered())
                            writeRequest(output, SessionFilesRequest(have(root)))
                            val input = DataInputStream(channels.getInputStream(channel).await().buffered())
                            readChunks(input) { sessionId, fileName, offset, _, data ->
                                if (writeChunk(root, sessionId, fileName, offset, data, copied)) files++
                            }
                        } finally {
                            watchdog.cancel()
                        }
                    }
                } finally {
                    channels.close(channel)
                }
                com.opentdi.mobile.journal.Transcriber.transcribeInBackground(context)
                null
            }
        } catch (e: IOException) {
            if (stalled.get()) {
                "The watch stopped sending. Keep it near the phone and try again."
            } else {
                "Could not copy from the watch: ${e.message}"
            }
        } catch (e: ApiException) {
            "Could not reach the watch: ${e.message}"
        }
        // What arrived before an error is kept: the next copy continues from there.
        return SyncResult(files, copied.get(), error).also {
            Log.i(TAG_LINK, logLine("WATCH_COPY", "files" to it.files, "bytes" to it.bytes, "error" to it.error))
        }
    }

    /**
     * A watch copy's night, summary and recordings; null for a folder whose name is no real time. [phone] gives the
     * p_transitions.csv files of the phone's sessions during a span: their Dormio wakings are part of the night.
     */
    private fun watchNight(
        dir: File,
        zone: ZoneId,
        today: LocalDate,
        phone: (startMs: Long, endMs: Long) -> List<File>,
    ): WatchNight? {
        val night = nightDate(sessionStartMs(dir.name) ?: return null, zone)
        val summary = try {
            val prune = ChronoUnit.DAYS.between(night, today) > KEEP_CSV_NIGHTS
            summary(dir, zone, prune, transitions(dir, phone))
        } catch (e: IOException) {
            failed(dir, e)
        } catch (e: RuntimeException) { // a damaged file must not hide the other nights; it is read again next time
            failed(dir, e)
        }
        return WatchNight(night, summary, dir.listFiles().orEmpty().filter { isRecording(it.name) })
    }

    private fun failed(dir: File, e: Exception): NightSummary? {
        Log.w(TAG_SESSION, logLine("NIGHT_SUMMARY_FAILED", "id" to dir.name, "error" to e))
        return null
    }

    /**
     * The [phone] sessions' p_transitions.csv files during [dir]'s watch recording, its first to last packet (read from
     * the ends of w_packets.csv only).
     */
    private fun transitions(dir: File, phone: (startMs: Long, endMs: Long) -> List<File>): List<File> {
        val packets = File(dir, CsvFiles.W_PACKETS.name).takeIf { it.isFile } ?: return emptyList()
        val startMs = packets.useLines { lines ->
            lines.drop(1).firstNotNullOfOrNull { it.substringBefore(',').toLongOrNull() }
        }
        val durationMs = watchSessionDurationMs(packets)
        return if (startMs == null || durationMs == null) emptyList() else phone(startMs, startMs + durationMs + 1000)
    }

    /**
     * [dir]'s cached summary, computed again when its CSVs, the phone sessions' [transitions] or SUMMARY_VERSION
     * changed; [prune] then drops the CSVs.
     */
    private fun summary(dir: File, zone: ZoneId, prune: Boolean, transitions: List<File>): NightSummary? {
        // Before reading, so a file that grows meanwhile is read again next time.
        val files = fingerprint(dir) + transitions.associate { "${it.parentFile?.name}/${it.name}" to it.length() }
        val cache = readCache(dir)?.takeIf { it.pruned || it.version == SUMMARY_VERSION && it.files == files }
            ?: SummaryCache(SUMMARY_VERSION, files, summarize(dir, zone, transitions)).also { writeCache(dir, it) }
        if (prune) {
            // shortcut: copies older than KEEP_CSV_NIGHTS keep only the summary, the recordings and their transcripts,
            // so a new SUMMARY_VERSION cannot recompute them; keep their CSVs (zipped) when old nights must be recomputed.
            if (!cache.pruned) writeCache(dir, cache.copy(pruned = true))
            for (file in dir.listFiles().orEmpty()) {
                val keep = file.name == SUMMARY_FILE || isRecording(file.name) ||
                    file.name.endsWith(".txt") && isRecording(file.name.removeSuffix(".txt") + ".m4a")
                if (!keep) file.delete()
            }
        }
        return cache.summary
    }

    /** summarizeNight over the copy in [dir] and the phone's [transitions], closing whatever it opened. */
    private fun summarize(dir: File, zone: ZoneId, transitions: List<File>): NightSummary? {
        val opened = mutableListOf<Reader>()
        try {
            return summarizeNight(dir.name, zone) { name ->
                if (name == CsvFiles.P_TRANSITIONS.name) {
                    joinTransitions(transitions)?.let(::StringReader)
                } else {
                    File(dir, name).takeIf { it.isFile }?.bufferedReader()?.also { opened += it }
                }
            }
        } finally {
            opened.forEach { it.close() }
        }
    }

    /** The phone's sessions, each read again only when its CSVs changed (filesDir/phone_sessions.json). */
    private fun phoneSessions(filesDir: File): List<PhoneSession> {
        val cacheFile = File(filesDir, PHONE_CACHE)
        val cached = try {
            OpenTdiJson.decodeFromString<List<PhoneSession>>(cacheFile.readText())
        } catch (e: IOException) {
            emptyList()
        } catch (e: IllegalArgumentException) { // SerializationException: damaged
            emptyList()
        }
        val known = cached.associateBy { it.id }
        val dirs = sessionDirs(File(filesDir, SESSIONS_DIR))
        // Retention (SessionRetention.kt) deletes only the oldest folders, so cached sessions older than every folder
        // left stay in the stats; a newer one is gone because the Sessions screen deleted it. shortcut: deleting the
        // oldest sessions there keeps them in the stats; log deletions if that matters.
        val oldestLeft = dirs.lastOrNull()?.name
        val retained = cached.filter { oldestLeft != null && it.id < oldestLeft }
        val sessions = dirs.mapNotNull { dir ->
            val files = fingerprint(dir)
            known[dir.name]?.takeIf { it.files == files } ?: readPhoneSession(dir)?.copy(files = files)
        } + retained
        if (sessions != cached) {
            try {
                writeWhole(cacheFile, OpenTdiJson.encodeToString(sessions))
            } catch (e: IOException) { // read again next time
                Log.w(TAG_SESSION, logLine("PHONE_NIGHTS_NOT_STORED", "error" to e.message))
            }
        }
        return sessions
    }

    private fun readPhoneSession(dir: File): PhoneSession? = try {
        fun text(name: String) = File(dir, name).takeIf { it.isFile }?.readText().orEmpty()
        phoneSession(
            dir.name, text(CsvFiles.P_TRANSITIONS.name), text(CsvFiles.P_AUDIO.name), text(CsvFiles.P_CONFIG.name),
        )
    } catch (e: IOException) {
        null
    }
}
