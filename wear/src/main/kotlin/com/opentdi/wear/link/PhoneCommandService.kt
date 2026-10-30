package com.opentdi.wear.link

import com.opentdi.shared.model.WatchOutput
import com.opentdi.wear.alarm.WatchOutputs
import com.opentdi.wear.alarm.LateCues
import com.opentdi.shared.model.AlarmConfig
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.TdiCommand
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import com.opentdi.wear.alarm.AlarmPlayer
import com.opentdi.wear.alarm.WakeAlarms
import com.opentdi.wear.alarm.WakeBackup
import com.opentdi.wear.sensing.SamsungKit
import com.opentdi.wear.sensing.HealthTrackingForegroundService
import com.opentdi.wear.sensing.SensingStateHolder
import com.opentdi.shared.model.apkSha256
import java.io.File

/**
 * The phone's `/opentdi/state_command` requests: Start session starts the recording (and brings the night's alarm
 * settings), a ConfigSync brings alarm changes, the phone's "Stop watch recording" ends it, a tone test plays a few
 * seconds of an alarm, and every request is answered with the watch's [WatchStatus].
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
        // A ConfigSync is an alarm change made on the phone, in any watch state; a running night picks it up next tick.
        val sent = (command as? TdiCommand.StartSession)?.config ?: (command as? TdiCommand.ConfigSync)?.config
        if (sent != null) {
            WakeAlarms.saveConfig(this, sent.alarms)
            WatchOutputs.saveChoice(this, sent.audio.watchOutput == WatchOutput.EARBUDS)
        }
        if (command is TdiCommand.TestAlarm) {
            Thread {
                val player = AlarmPlayer(applicationContext)
                player.start(command.tone, TEST_VOLUME, gentle = false, earbuds = command.earbuds)
                Thread.sleep(TEST_MS)
                player.stop()
            }.start()
        }
        if (command is TdiCommand.TestLateCue) {
            LateCues(applicationContext, { AlarmConfig() }) { _, _, _, _ -> }.play(command.volume, command.earbuds)
        }
        // "Stop watch recording" in any state: a died night's backup wake-up must not ring after it.
        if (command is TdiCommand.AbortSession) WakeBackup.set(this, null, "PHONE_STOP")
        try {
            // While recording too: the service keeps tonight's recording and replaces one left from last night.
            if (command is TdiCommand.StartSession && now != WatchState.IDLE) {
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
        val audio = getSystemService(android.media.AudioManager::class.java)
        return Tasks.forResult(
            OpenTdiJson.encodeToString(
                WatchStatus.serializer(),
                WatchStatus(
                    now, battery, SamsungKit.source(this), WatchOutputs.earbuds(audio)?.productName?.toString(),
                    understood = command != null, apk = installedApk(packageCodePath),
                ),
            ).encodeToByteArray(),
        )
    }

    private companion object {
        const val TAG = "OTDI.Link"
        const val TEST_VOLUME = 0.6f
        const val TEST_MS = 6000L

        @Volatile private var apk: String? = null

        /** This app's own APK, hashed once per process (an update restarts it); null if unreadable. */
        fun installedApk(path: String): String? = apk ?: try {
            val started = SystemClock.elapsedRealtime()
            File(path).inputStream().use(::apkSha256).also {
                apk = it
                Log.i(TAG, "APK_HASHED ms=${SystemClock.elapsedRealtime() - started}")
            }
        } catch (e: Exception) { // a version check must never break the reply, or crash the night's tracking with it
            Log.w(TAG, "APK_UNREADABLE", e)
            null
        }
    }
}
