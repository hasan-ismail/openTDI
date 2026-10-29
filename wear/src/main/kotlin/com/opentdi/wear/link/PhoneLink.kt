package com.opentdi.wear.link

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.DeviceRole
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.SessionEndReason
import com.opentdi.shared.model.TdiCommand
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.wear.sensing.logEvent

/** Watch → phone messages on /opentdi/state_command. */
object PhoneLink {
    /**
     * End the night on the watch: the phone stops its voice too, instead of playing on without the stillness check.
     * Fire and forget on the application context, so closing the activity does not cancel it; a phone out of reach
     * never hears it. The session id is the watch's own, not the phone's.
     */
    fun endNight(context: Context) {
        val app = context.applicationContext
        val command = TdiCommand.AbortSession(
            SensingStateHolder.state.value.sessionId ?: "watch", DeviceRole.WATCH, SessionEndReason.USER,
        )
        val bytes = OpenTdiJson.encodeToString(TdiCommand.serializer(), command).toByteArray()
        Wearable.getCapabilityClient(app)
            .getCapability(DataLayerPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                // A phone reached over Bluetooth beats one reached through the cloud, as for the telemetry.
                val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
                logEvent("END_NIGHT_TO_PHONE", "node" to node?.id)
                node?.let { Wearable.getMessageClient(app).sendMessage(it.id, DataLayerPaths.STATE_COMMAND, bytes) }
            }
            .addOnFailureListener { logEvent("END_NIGHT_TO_PHONE", "error" to it.javaClass.simpleName) }
    }
}
