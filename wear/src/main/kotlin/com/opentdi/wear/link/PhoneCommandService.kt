package com.opentdi.wear.link

import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.TdiCommand
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingStateHolder

/**
 * The phone's `/opentdi/state_command` requests: Start session starts the recording, the phone's "Stop watch
 * recording" ends it, and every request is answered with the watch's [WatchStatus].
 */
class PhoneCommandService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != DataLayerPaths.STATE_COMMAND) return null
        val command = try {
            OpenTdiJson.decodeFromString(TdiCommand.serializer(), request.decodeToString())
        } catch (e: IllegalArgumentException) { // SerializationException: another app version
            Log.w(TAG, "PHONE_COMMAND_UNREADABLE")
            null
        }
        val state = SensingStateHolder.state.value
        var now = when {
            state.running -> WatchState.RECORDING
            state.waiting -> WatchState.WAITING
            else -> WatchState.IDLE
        }
        try {
            if (command is TdiCommand.StartSession && now == WatchState.WAITING) {
                HealthTrackingForegroundService.startFromPhone(this)
                now = WatchState.RECORDING
            } else if (command is TdiCommand.AbortSession && now != WatchState.IDLE) {
                HealthTrackingForegroundService.stop(this)
                now = WatchState.IDLE
            }
        } catch (e: IllegalStateException) { // the service is not running, so the app counts as in the background
            Log.w(TAG, "PHONE_COMMAND_REFUSED command=${command?.javaClass?.simpleName}")
        }
        Log.i(TAG, "PHONE_COMMAND command=${command?.javaClass?.simpleName} state=$now")
        val battery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return Tasks.forResult(
            OpenTdiJson.encodeToString(WatchStatus.serializer(), WatchStatus(now, battery)).encodeToByteArray(),
        )
    }

    private companion object {
        const val TAG = "OTDI.Link"
    }
}
