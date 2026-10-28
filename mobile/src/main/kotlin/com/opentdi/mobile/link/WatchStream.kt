package com.opentdi.mobile.link

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.opentdi.mobile.TAG_LINK
import com.opentdi.mobile.logLine
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.PROTOCOL_VERSION
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.model.TelemetryBatch
import com.opentdi.shared.model.TelemetryFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Frames kept for the live card: the last 3 minutes at one per second. */
const val KEEP_FRAMES = 180

/** What arrived from the watch's telemetry stream (display only): the newest frames of one watch session. */
data class WatchLive(
    val sessionId: String? = null,
    val sensorStatus: SensorStatus? = null,
    val gravityMode: GravityMode = GravityMode.UNKNOWN,
    val frames: List<TelemetryFrame> = emptyList(), // oldest first, at most KEEP_FRAMES
    val jerkEvents: Int = 0, // in this session
    val limpEvents: Int = 0,
    val receivedAtMs: Long? = null, // SystemClock.elapsedRealtime() of the last batch
)

/** [this] with [batch] added: another session starts over, and frames already received (by seq) are skipped. */
fun WatchLive.plus(batch: TelemetryBatch, receivedAtMs: Long): WatchLive {
    val base = if (batch.sessionId == sessionId) this else WatchLive(sessionId = batch.sessionId)
    val lastSeq = base.frames.lastOrNull()?.seq ?: -1L
    val fresh = batch.frames.filter { it.seq > lastSeq }
    return base.copy(
        sensorStatus = batch.sensorStatus,
        gravityMode = batch.gravityMode,
        frames = (base.frames + fresh).takeLast(KEEP_FRAMES),
        jerkEvents = base.jerkEvents + fresh.count { it.packet.jerkEvent },
        limpEvents = base.limpEvents + fresh.count { it.packet.limpEvent },
        receivedAtMs = receivedAtMs,
    )
}

/** The process-wide [WatchLive]: [WatchStreamListener] writes it, the session screen reads it. */
object WatchTelemetry {
    private val mutableLive = MutableStateFlow(WatchLive())
    val live: StateFlow<WatchLive> = mutableLive.asStateFlow()

    fun add(batch: TelemetryBatch, receivedAtMs: Long) = mutableLive.update { it.plus(batch, receivedAtMs) }
}

/**
 * Receives the watch's /opentdi/sensor_stream batches while the app UI is visible (started): a live MessageClient
 * listener, so no WearableListenerService is needed (§10.2). Its callbacks run on the main thread.
 */
class WatchStreamListener(context: Context) : DefaultLifecycleObserver, MessageClient.OnMessageReceivedListener {
    private val messages = Wearable.getMessageClient(context)

    override fun onStart(owner: LifecycleOwner) {
        messages.addListener(this, STREAM_URI, MessageClient.FILTER_LITERAL)
            .addOnFailureListener { Log.w(TAG_LINK, logLine("RX_LISTENER_FAILED", "error" to it)) }
    }

    override fun onStop(owner: LifecycleOwner) {
        messages.removeListener(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        val batch = try {
            OpenTdiJson.decodeFromString<TelemetryBatch>(event.data.decodeToString())
        } catch (e: IllegalArgumentException) { // SerializationException included
            Log.w(TAG_LINK, logLine("RX_DECODE_FAILED", "path" to event.path, "bytes" to event.data.size, "error" to e))
            return
        }
        if (batch.protocolVersion != PROTOCOL_VERSION) {
            Log.w(TAG_LINK, logLine("RX_PROTOCOL_MISMATCH", "path" to event.path, "version" to batch.protocolVersion))
            return
        }
        WatchTelemetry.add(batch, SystemClock.elapsedRealtime())
    }

    private companion object {
        val STREAM_URI: Uri = "wear://*${DataLayerPaths.SENSOR_STREAM}".toUri()
    }
}
