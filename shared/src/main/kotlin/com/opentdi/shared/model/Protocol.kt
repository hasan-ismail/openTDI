package com.opentdi.shared.model

import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.serialization.json.Json

/**
 * Checked only on the telemetry stream (WatchStream). Phone → watch commands use [WatchStatus.understood] and
 * [WatchStatus.apk] instead: this number was never bumped as the enums grew.
 */
const val PROTOCOL_VERSION = 1

/** [apk]'s SHA-256 in lower-case hex, read in blocks: [WatchStatus.apk], compared with the phone's watch.apk. */
fun apkSha256(apk: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = apk.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** The one JSON configuration used for every Data Layer payload (§9). */
val OpenTdiJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Data Layer paths and capability names (§10.2). Phase 2 uses them; they are fixed here so both apps agree. */
object DataLayerPaths {
    const val SENSOR_STREAM = "/opentdi/sensor_stream"
    const val STATE_COMMAND = "/opentdi/state_command"
    const val HYPNAGOGIA_TRIGGER = "/opentdi/hypnagogia_trigger"
    const val SESSION_FILES = "/opentdi/session_files"
    const val WRIST_AUDIO_CHANNEL = "/opentdi/wrist_audio_channel" // wrist speaker cues (WristCue); the wrist mic is deferred
    const val SAMSUNG_KIT = "/opentdi/samsung_kit" // the user's own Samsung SDK, converted on the phone (dex)
    const val CAPABILITY_PHONE = "opentdi_phone"
    const val CAPABILITY_WATCH = "opentdi_watch"
}

private val SESSION_ID_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** `yyyyMMdd'T'HHmmss'Z'-xxxx` (UTC time + 4 random hex digits), made by the device that starts the session (§9.4). */
fun newSessionId(epochMs: Long, random: Random): String =
    SESSION_ID_TIME.format(Instant.ofEpochMilli(epochMs)) + "-" + "%04x".format(random.nextInt(0x10000))
