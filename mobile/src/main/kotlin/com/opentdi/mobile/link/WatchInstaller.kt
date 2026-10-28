package com.opentdi.mobile.link

import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import java.io.File
import java.io.IOException
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
    private val infoCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()

    init {
        configureIdentity(context)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // registered only there
            infoCallbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
        infoCallbacks.clear()
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
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(resolved: NsdServiceInfo) {
                    found(resolved.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress, resolved.port)
                }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            infoCallbacks += callback
            nsd.registerServiceInfoCallback(info, context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) = found(resolved.host?.hostAddress, resolved.port)
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            })
        }
    }

    /**
     * Pairs this phone with the watch showing [code]: null when it worked, otherwise why not. A watch whose Wi-Fi
     * dozes never answers, so it gives up after 30 s.
     */
    suspend fun pair(endpoint: AdbEndpoint, code: String): String? = try {
        withTimeoutOrNull(PAIR_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { Kadb.pair(endpoint.host, endpoint.port, code) }
            null
        } ?: "The watch did not answer. Tap its screen to keep it awake and try again."
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        "Pairing failed: ${e.message}. Check the code and try once more."
    }

    /**
     * Connects to [endpoint], refuses anything that is not a watch, installs the carried watch app (replacing an
     * older one, granting its permissions) and opens it. Returns null when it worked, or what went wrong.
     */
    suspend fun install(endpoint: AdbEndpoint): String? = withContext(Dispatchers.IO) {
        try {
            // connect within 10 s; then up to 2 min per read, for the watch's install and optimisation
            Kadb.create(endpoint.host, endpoint.port, CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS).use { kadb ->
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

    companion object {
        private const val PAIRING = "_adb-tls-pairing._tcp"
        private const val CONNECT = "_adb-tls-connect._tcp"
        private const val WATCH_APK = "watch.apk"
        private const val PAIR_TIMEOUT_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SOCKET_TIMEOUT_MS = 120_000
        private var identityReady = false

        /** Once per process: the key that lets the watch recognise this phone, in the app's private storage. */
        @Synchronized
        fun configureIdentity(context: Context) {
            if (identityReady) return
            KadbCert.configure(OkioFilePrivateKeyStore(File(context.filesDir, "adb/adbkey.pem").path.toPath()))
            identityReady = true
        }
    }
}
