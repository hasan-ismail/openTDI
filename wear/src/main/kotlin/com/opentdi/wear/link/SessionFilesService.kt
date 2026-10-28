package com.opentdi.wear.link

import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.shared.csv.sessionDirs
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.isSyncedWatchFile
import com.opentdi.shared.model.readRequest
import com.opentdi.shared.model.writeChunk
import com.opentdi.shared.model.writeEnd
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Copies the night files to the phone when it asks, for the phone's stats over weeks and months (owner, 2026-10-28),
 * as SessionFiles.kt describes (spec §10.2 [EXT] /opentdi/session_files): the phone opens a channel and says what it
 * has, and the watch answers with what each synced file gained, then the end marker. Watch files are only read.
 */
class SessionFilesService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != DataLayerPaths.SESSION_FILES) return
        val channels = Wearable.getChannelClient(this)
        val startMs = SystemClock.elapsedRealtime()
        try {
            val request = readRequest(DataInputStream(Tasks.await(channels.getInputStream(channel)).buffered()))
            val sent = DataOutputStream(Tasks.await(channels.getOutputStream(channel)).buffered()).use { out ->
                sendSessionFiles(File(filesDir, "sessions"), request.have, out)
            }
            // The phone closes the channel once it has the end marker: closing it here could fail its last reads.
            Log.i(
                TAG,
                "SESSION_FILES_SENT files=${sent.size} bytes=${sent.sumOf { it.length }} " +
                    "ms=${SystemClock.elapsedRealtime() - startMs}",
            )
        } catch (e: Exception) { // a closed channel, a request from another app version, a file deleted meanwhile
            Log.w(TAG, "SESSION_FILES_FAILED ms=${SystemClock.elapsedRealtime() - startMs} reason=$e")
            channels.close(channel) // the phone's read fails now instead of waiting for bytes that will not come
        }
    }

    private companion object {
        const val TAG = "OTDI.Link"
    }
}

/** [length] bytes of [path] ("sessionId/fileName" in filesDir/sessions) from [offset]. */
internal data class FileChunk(val path: String, val offset: Long, val length: Long)

/**
 * What to send of [files] ("sessionId/fileName" to its length now) when the phone has [have] bytes of each: nothing
 * when the phone has the full length; a CSV that grew from the phone's count on (CSVs only grow); otherwise the whole
 * file, which covers a file now shorter than the phone's copy and a dream recording that changed (MediaRecorder
 * rewrites the MP4 header when it stops, so a copy taken while it recorded cannot just be extended).
 */
internal fun chunksToSend(files: Map<String, Long>, have: Map<String, Long>): List<FileChunk> =
    files.mapNotNull { (path, length) ->
        val had = have[path] ?: 0L
        when {
            had == length -> null
            had in 0L until length && path.endsWith(".csv") -> FileChunk(path, had, length - had)
            else -> FileChunk(path, 0, length)
        }
    }

/**
 * Writes what [chunksToSend] picks of the synced files in the session folders in [root] (filesDir/sessions), newest
 * session first, then the end marker, and returns the chunks. Each length is read once, before sending: the running
 * session keeps appending, and the rest goes next time.
 */
internal fun sendSessionFiles(root: File, have: Map<String, Long>, out: DataOutputStream): List<FileChunk> {
    val lengths = sessionDirs(root).flatMap { dir ->
        dir.listFiles().orEmpty().filter { it.isFile && isSyncedWatchFile(it.name) }
            .map { "${dir.name}/${it.name}" to it.length() }
    }.toMap()
    val chunks = chunksToSend(lengths, have)
    for (chunk in chunks) {
        val (sessionId, fileName) = chunk.path.split('/')
        FileInputStream(File(root, chunk.path)).use { data ->
            data.channel.position(chunk.offset)
            writeChunk(out, sessionId, fileName, chunk.offset, chunk.length, data)
        }
    }
    writeEnd(out)
    return chunks
}
