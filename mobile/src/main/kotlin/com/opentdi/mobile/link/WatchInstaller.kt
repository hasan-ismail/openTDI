package com.opentdi.mobile.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

/** A Wireless debugging service the watch advertises on the Wi-Fi: its pairing dialog, or its connection. */
data class AdbEndpoint(val host: String, val port: Int)

/** What the setup screen shows: the endpoints found so far. */
data class AdbFound(val pairing: AdbEndpoint? = null, val connect: Map<String, AdbEndpoint> = emptyMap())

/**
 * Installs the watch app from the phone (owner, 2026-10-28) over the watch's Wireless debugging, with Kadb (Apache-2.0):
 * pair once with the code the watch shows, connect, check that the device is a watch, install the copy of the watch
 * app this phone app carries (assets/watch.apk) and open it. Those are the only commands it sends. The adb key that
 * lets the watch recognise this phone lives in the app's private storage.
 */
class WatchInstaller(private val context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val mutableFound = MutableStateFlow(AdbFound())
    val found: StateFlow<AdbFound> = mutableFound.asStateFlow()
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        KadbCert.configure(OkioFilePrivateKeyStore(File(context.filesDir, "adb/adbkey.pem").path.toPath()))
    }

    /** Looks for the watch's pairing dialog and its connection on the local network until [stop]. */
    fun start() {
        if (listeners.isNotEmpty()) return
        for (type in listOf(PAIRING, CONNECT)) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) = resolve(info, type)
                override fun onServiceLost(info: NsdServiceInfo) {
                    mutableFound.update {
                        if (type == PAIRING) it.copy(pairing = null) else it.copy(connect = it.connect - info.serviceName)
                    }
                }
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            listeners += listener
        }
    }

    fun stop() {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
    }

    private fun resolve(info: NsdServiceInfo, type: String) {
        fun found(host: String?, port: Int) {
            if (host == null || port <= 0) return
            val endpoint = AdbEndpoint(host, port)
            mutableFound.update {
                if (type == PAIRING) it.copy(pairing = endpoint) else it.copy(connect = it.connect + (info.serviceName to endpoint))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsd.registerServiceInfoCallback(info, executor, object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(resolved: NsdServiceInfo) {
                    found(resolved.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress, resolved.port)
                }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            })
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) = found(resolved.host?.hostAddress, resolved.port)
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            })
        }
    }

    /** Pairs this phone with the watch showing [code]; throws with the reason when the watch refuses it. */
    suspend fun pair(endpoint: AdbEndpoint, code: String) =
        withContext(Dispatchers.IO) { Kadb.pair(endpoint.host, endpoint.port, code) }

    /**
     * Connects to [endpoint], refuses anything that is not a watch, installs the carried watch app (replacing an
     * older one, granting its permissions) and opens it. Returns null when it worked, or what went wrong.
     */
    suspend fun install(endpoint: AdbEndpoint): String? = withContext(Dispatchers.IO) {
        try {
            Kadb.create(endpoint.host, endpoint.port).use { kadb ->
                val kind = kadb.shell("getprop ro.build.characteristics").output
                if ("watch" !in kind) return@withContext "That device is not a watch, so nothing was installed."
                val apk = File(context.cacheDir, "watch.apk")
                context.assets.open(WATCH_APK).use { input -> apk.outputStream().use { input.copyTo(it) } }
                try {
                    kadb.install(apk, "-r", "-g")
                } finally {
                    apk.delete()
                }
                kadb.shell("am start -n com.opentdi/com.opentdi.wear.MainActivity")
                null
            }
        } catch (e: IOException) {
            "Could not reach the watch: ${e.message}"
        } catch (e: Exception) { // Kadb's auth and install failures are plain exceptions
            if (e is kotlinx.coroutines.CancellationException) throw e
            "The watch refused: ${e.message}"
        }
    }

    private companion object {
        const val PAIRING = "_adb-tls-pairing._tcp"
        const val CONNECT = "_adb-tls-connect._tcp"
        const val WATCH_APK = "watch.apk"
    }
}
