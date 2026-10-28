package com.opentdi.mobile.link

import android.content.Context
import android.net.Uri
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.opentdi.shared.model.DataLayerPaths
import java.io.ByteArrayInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Bring your own Samsung kit (owner, 2026-10-28). OpenTDI's published apps carry no Samsung code: Samsung's license

 * picks the download from Samsung (its zip, or the .aar inside), the phone turns the SDK's Java classes into Android
 * code with D8 (the same step every Android build does) and sends that to the watch, which loads it (SamsungKit).
 */
object SamsungKitTransfer {
    private const val KIT_FILE = "samsung-sdk.dex"
    private const val PREFS = "samsung_kit"
    private const val KEY_NAME = "name"
    private const val MAX_DOWNLOAD = 20 * 1024 * 1024
    private const val SDK_CLASS = "com/samsung/android/service/health/tracking/HealthTrackingService.class"
    private const val SEND_TIMEOUT_MS = 60_000L

    /** The kit's file name (e.g. samsung-health-sensor-api-1.4.1.aar), or null before one was added. */
    fun name(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_NAME, null)?.takeIf { File(context.filesDir, KIT_FILE).exists() }

    /** Reads Samsung's download from [uri], converts the SDK and keeps it; returns its name or throws with the reason. */
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val download = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytes(MAX_DOWNLOAD + 1)
            if (bytes.size > MAX_DOWNLOAD) throw IOException("That file is too large to be Samsung's sensor kit.")
            bytes
        } ?: throw IOException("Could not read the file.")
        // Samsung's zip holds 1.4.1/libs/samsung-health-sensor-api-1.4.1.aar; the .aar itself holds classes.jar.
        val (aarName, aar) = zipEntry(download) { it.endsWith(".aar") } ?: ("the .aar file" to download)
        val classes = zipEntry(aar) { it == "classes.jar" }?.second
            ?: throw IOException("That is not Samsung's sensor kit: pick the zip or the .aar from Samsung.")
        if (zipEntry(classes) { it == SDK_CLASS } == null) {
            throw IOException("That library is not Samsung's Health Sensor SDK.")
        }
        val dir = File(context.cacheDir, "samsung_kit").apply { deleteRecursively(); mkdirs() }
        try {
            val jar = File(dir, "classes.jar").apply { writeBytes(classes) }
            val out = File(dir, "out").apply { mkdirs() }
            D8.run(
                D8Command.builder()
                    .addProgramFiles(jar.toPath())
                    .setMinApiLevel(WATCH_MIN_SDK)
                    .setOutput(out.toPath(), OutputMode.DexIndexed)
                    .build(),
            )
            File(out, "classes.dex").copyTo(File(context.filesDir, KIT_FILE), overwrite = true)
        } finally {
            dir.deleteRecursively() // Samsung's classes stay only in their converted form, in private storage
        }
        val name = aarName.substringAfterLast('/')
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name).apply()
        name
    }

    /** Sends the converted kit to the watch app; null when the watch stored it, else what went wrong. */
    suspend fun send(context: Context): String? = withContext(Dispatchers.IO) {
        val dex = File(context.filesDir, KIT_FILE).takeIf { it.exists() }?.readBytes()
            ?: return@withContext "Add Samsung's kit on this phone first."
        try {
            val nodes = Wearable.getCapabilityClient(context)
                .getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes
            val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                ?: return@withContext "The watch app is not reachable: install it and keep the watch connected."
            val channels = Wearable.getChannelClient(context)
            val channel = channels.openChannel(node.id, DataLayerPaths.SAMSUNG_KIT).await()
            try {
                coroutineScope {
                    val watchdog = launch {
                        try {
                            delay(SEND_TIMEOUT_MS)
                        } finally {
                            channels.close(channel) // ends a blocked read, so the answer below is null
                        }
                    }
                    try {
                        DataOutputStream(channels.getOutputStream(channel).await().buffered()).run {
                            writeInt(dex.size)
                            write(dex)
                            flush()
                        }
                        when (channels.getInputStream(channel).await().read()) {
                            1 -> null
                            0 -> "The watch refused the kit."
                            else -> "The watch did not answer."
                        }
                    } finally {
                        watchdog.cancel()
                    }
                }
            } finally {
                channels.close(channel)
            }
        } catch (e: IOException) {
            "Could not send the kit: ${e.message}"
        } catch (e: ApiException) {
            "Could not reach the watch: ${e.message}"
        }
    }

    /** The first entry of the zip [bytes] whose name matches, as (name, contents); null when none does or not a zip. */
    private fun zipEntry(bytes: ByteArray, matches: (String) -> Boolean): Pair<String, ByteArray>? =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { !it.isDirectory && matches(it.name) }
                ?.let { it.name to zip.readBytes() }
        }

    private const val WATCH_MIN_SDK = 30 // the watch app's minSdk: the dex must load there
}
