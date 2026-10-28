package com.opentdi.shared.model

import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.isSessionId
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.serialization.Serializable

// Copying the watch's session files to the phone (§10.2 [EXT] /opentdi/session_files), so the phone can keep and chart
// every night. The phone opens a channel on DataLayerPaths.SESSION_FILES and writes a [SessionFilesRequest]; the
// watch answers with one chunk per file that grew, then an empty path. CSVs only grow, so a chunk carries just the
// bytes after what the phone has; a file shorter on the watch than on the phone is sent whole (offset 0).

/** What the phone already has: "sessionId/fileName" to its byte count. */
@Serializable
data class SessionFilesRequest(val have: Map<String, Long> = emptyMap())

/**
 * The watch files the phone copies: the per-night CSVs and the dream recordings. The raw high-rate streams
 * (accelerometer, PPG, sensor batches) stay on the watch: they are large and nothing on the phone reads them.
 */
fun isSyncedWatchFile(name: String): Boolean = name in SYNCED_CSVS || DREAM_RECORDING.matches(name)

private val SYNCED_CSVS = with(CsvFiles) {
    listOf(W_CONFIG, W_PACKETS, W_ENGINE, W_TRANSITIONS, W_INPUTS, W_LINK, W_ALARM, W_MORNING, W_SPO2, W_SKIN_TEMP, W_ACCEL_EVENTS)
        .map { it.name }.toSet()
}
private val DREAM_RECORDING = Regex("""w_dream_\d{6}\.m4a""") // DreamReportScreen: w_dream_HHmmss.m4a

private const val MAX_REQUEST_BYTES = 4 * 1024 * 1024

fun writeRequest(out: DataOutputStream, request: SessionFilesRequest) {
    val json = OpenTdiJson.encodeToString(SessionFilesRequest.serializer(), request).toByteArray(Charsets.UTF_8)
    out.writeInt(json.size)
    out.write(json)
    out.flush()
}

fun readRequest(input: DataInputStream): SessionFilesRequest {
    val size = input.readInt()
    if (size !in 0..MAX_REQUEST_BYTES) throw IOException("request size $size")
    val json = ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8)
    return OpenTdiJson.decodeFromString(SessionFilesRequest.serializer(), json)
}

/** One chunk: [length] bytes of [sessionId]/[fileName] starting at [offset], read from [data]. */
fun writeChunk(out: DataOutputStream, sessionId: String, fileName: String, offset: Long, length: Long, data: InputStream) {
    out.writeUTF("$sessionId/$fileName")
    out.writeLong(offset)
    out.writeLong(length)
    val buffer = ByteArray(64 * 1024)
    var left = length
    while (left > 0) {
        val n = data.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
        if (n < 0) throw IOException("$fileName ended $left bytes early")
        out.write(buffer, 0, n)
        left -= n
    }
}

/** Ends the answer. */
fun writeEnd(out: DataOutputStream) {
    out.writeUTF("")
    out.flush()
}

/**
 * Reads chunks until the end marker; whatever [onChunk] leaves unread of a chunk is skipped. A path that is not a
 * session ID and a synced file name (e.g. "../x") ends the transfer with an IOException.
 */
fun readChunks(
    input: DataInputStream,
    onChunk: (sessionId: String, fileName: String, offset: Long, length: Long, data: InputStream) -> Unit,
) {
    while (true) {
        val path = input.readUTF()
        if (path.isEmpty()) return
        val sessionId = path.substringBefore('/')
        val fileName = path.substringAfter('/', "")
        if (!isSessionId(sessionId) || !isSyncedWatchFile(fileName)) throw IOException("refused path $path")
        val offset = input.readLong()
        val length = input.readLong()
        if (offset < 0 || length < 0) throw IOException("bad chunk $path $offset+$length")
        val chunk = BoundedInput(input, length)
        onChunk(sessionId, fileName, offset, length, chunk)
        chunk.skipRest()
    }
}

/** At most [left] bytes of [input], so a careless reader cannot run into the next chunk. */
private class BoundedInput(private val input: InputStream, private var left: Long) : InputStream() {
    override fun read(): Int {
        if (left <= 0) return -1
        return input.read().also { if (it >= 0) left-- }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = input.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) left -= n
        return n
    }

    fun skipRest() {
        val buffer = ByteArray(8 * 1024)
        while (left > 0) if (read(buffer, 0, buffer.size) < 0) throw IOException("chunk ended $left bytes early")
    }
}
