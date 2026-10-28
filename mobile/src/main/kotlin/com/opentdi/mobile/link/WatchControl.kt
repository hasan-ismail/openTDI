package com.opentdi.mobile.link

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.TdiCommand
import com.opentdi.shared.model.WatchStatus
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends [command] to the watch on `/opentdi/state_command` and returns its [WatchStatus]; null when no watch answers
 * within 5 s (not connected, or an app version without the command service).
 */
suspend fun watchRequest(context: Context, command: TdiCommand): WatchStatus? = withTimeoutOrNull(5_000) {
    try {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return@withTimeoutOrNull null
        val request = OpenTdiJson.encodeToString(TdiCommand.serializer(), command).encodeToByteArray()
        val reply = Wearable.getMessageClient(context).sendRequest(node.id, DataLayerPaths.STATE_COMMAND, request).await()
        OpenTdiJson.decodeFromString(WatchStatus.serializer(), reply.decodeToString())
    } catch (e: ApiException) {
        null
    } catch (e: IllegalArgumentException) { // SerializationException: another app version
        null
    }
}
