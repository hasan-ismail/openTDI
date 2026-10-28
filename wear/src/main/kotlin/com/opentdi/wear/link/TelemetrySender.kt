package com.opentdi.wear.link

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.TelemetryBatch
import com.opentdi.wear.sensing.logEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sends [TelemetryBatch]es to the phone on /opentdi/sensor_stream with MessageClient.sendMessage (§10.2), never
 * DataClient. Batches go out one at a time off the sensing thread; one that cannot be sent is dropped (the watch CSVs
 * keep everything), and each change of the link is logged once: LINK_DOWN with the reason, LINK_UP.
 */
class TelemetrySender(context: Context, scope: CoroutineScope) {
    private val messages = Wearable.getMessageClient(context)
    private val capabilities = Wearable.getCapabilityClient(context)
    private val queue = Channel<TelemetryBatch>(QUEUE_BATCHES, BufferOverflow.DROP_OLDEST)
    private var phoneNode: String? = null // cleared on a failure, so the next batch looks the phone up again
    private var linkUp: Boolean? = null
    private var dropped = 0

    init {
        scope.launch(Dispatchers.IO) { for (batch in queue) deliver(batch) }
    }

    /** Queues [batch] without blocking (called on the sensing thread). */
    fun send(batch: TelemetryBatch) {
        queue.trySend(batch)
    }

    private suspend fun deliver(batch: TelemetryBatch) {
        val failure = try {
            val target = phoneNode ?: reachablePhone()
            if (target == null) {
                "NO_PHONE_NODE"
            } else {
                val bytes = OpenTdiJson.encodeToString(batch).toByteArray()
                messages.sendMessage(target, DataLayerPaths.SENSOR_STREAM, bytes).await()
                phoneNode = target
                null
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive() // the service's own cancellation ends the loop
            (e as? ApiException)?.let { WearableStatusCodes.getStatusCodeString(it.statusCode) } ?: e.javaClass.simpleName
        }
        if (failure == null) {
            if (linkUp != true) logEvent("LINK_UP", "node" to phoneNode, "dropped" to dropped)
            linkUp = true
            dropped = 0
        } else {
            phoneNode = null
            dropped++
            if (linkUp != false) logEvent("LINK_DOWN", "reason" to failure)
            linkUp = false
        }
    }

    // A phone reached over Bluetooth beats one reached through the cloud.
    private suspend fun reachablePhone(): String? {
        val nodes = capabilities
            .getCapability(DataLayerPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await().nodes
        return (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
    }

    private companion object {
        const val QUEUE_BATCHES = 4 // the oldest waiting batch is dropped first
    }
}
