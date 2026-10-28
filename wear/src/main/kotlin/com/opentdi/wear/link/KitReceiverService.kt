package com.opentdi.wear.link

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.wear.sensing.SamsungKit
import java.io.DataInputStream
import java.io.File

/**
 * Receives the user's Samsung kit from the phone (bring your own kit): the SDK the phone converted to Android code.
 * It is stored as SamsungKit.USER_DEX and used from the next recording on. Replies 1 when stored, 0 when refused.
 */
class KitReceiverService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != DataLayerPaths.SAMSUNG_KIT) return
        val channels = Wearable.getChannelClient(this)
        val ok = try {
            // The phone sends the length first, so neither side relies on how a channel half-closes.
            val data = DataInputStream(Tasks.await(channels.getInputStream(channel)).buffered())
            val size = data.readInt()
            require(size in 9..MAX_BYTES) { "kit size $size" }
            val bytes = ByteArray(size).also { data.readFully(it) }
            // A dex file starts with "dex\n"; anything else is not what the phone converts.
            require(bytes.size > 8 && String(bytes, 0, 4, Charsets.US_ASCII) == "dex\n") { "not a dex file" }
            val target = SamsungKit.userDex(this)
            val partial = File(filesDir, "${target.name}.part")
            partial.writeBytes(bytes)
            target.delete() // read-only once loaded; the app still owns the folder
            check(partial.renameTo(target)) { "could not store the kit" }
            Log.i(TAG, "SAMSUNG_KIT_STORED bytes=${bytes.size}")
            true
        } catch (e: Exception) { // a closed channel, a wrong file: refused, the old kit stays
            Log.w(TAG, "SAMSUNG_KIT_REFUSED reason=${e.message}")
            false
        }
        try {
            Tasks.await(channels.getOutputStream(channel)).run {
                write(if (ok) 1 else 0)
                flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SAMSUNG_KIT_NO_REPLY reason=${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "OTDI.Link"
        const val MAX_BYTES = 4 * 1024 * 1024 // the 1.4.1 SDK is about 60 KB of Android code
    }
}
