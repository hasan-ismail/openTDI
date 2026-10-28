package com.opentdi.shared.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.serialization.json.Json

/** Bumped whenever a wire contract changes incompatibly; a mismatch is answered `Ack(false, "PROTOCOL_MISMATCH")`. */
const val PROTOCOL_VERSION = 1

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
    const val CAPABILITY_PHONE = "opentdi_phone"
    const val CAPABILITY_WATCH = "opentdi_watch"
}

private val SESSION_ID_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** `yyyyMMdd'T'HHmmss'Z'-xxxx` (UTC time + 4 random hex digits), made by the device that starts the session (§9.4). */
fun newSessionId(epochMs: Long, random: Random): String =
    SESSION_ID_TIME.format(Instant.ofEpochMilli(epochMs)) + "-" + "%04x".format(random.nextInt(0x10000))
