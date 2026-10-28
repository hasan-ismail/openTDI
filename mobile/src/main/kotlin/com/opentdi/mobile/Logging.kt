package com.opentdi.mobile

// Logcat tags of spec §13.5.
const val TAG_SESSION = "OTDI.Session"
const val TAG_AUDIO = "OTDI.Audio"
const val TAG_LINK = "OTDI.Link"
const val TAG_STT = "OTDI.Stt"

/** A §13.5 log message, `EVENT key=value …`; null values are left out. */
fun logLine(event: String, vararg fields: Pair<String, Any?>): String = buildString {
    append(event)
    for ((key, value) in fields) if (value != null) append(' ').append(key).append('=').append(value)
}
