package com.opentdi.mobile

import android.content.Context
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionMode

private const val PREFS = "opentdi"
private const val KEY_CONFIG = "session_config"
private const val KEY_CALIBRATED_INDEX = "calibrated_stream_index"

/** Phase 1 runs TIMED_CUES only (spec §0); every other default is the spec's. */
val PHASE1_DEFAULT_CONFIG = SessionConfig(mode = SessionMode.TIMED_CUES)

/** The saved SessionConfig, or the Phase 1 default when nothing (readable) is saved. */
fun loadConfig(context: Context): SessionConfig {
    val json = prefs(context).getString(KEY_CONFIG, null) ?: return PHASE1_DEFAULT_CONFIG
    return try {
        OpenTdiJson.decodeFromString<SessionConfig>(json)
    } catch (e: IllegalArgumentException) { // SerializationException included
        PHASE1_DEFAULT_CONFIG
    }
}

fun saveConfig(context: Context, config: SessionConfig) {
    prefs(context).edit().putString(KEY_CONFIG, OpenTdiJson.encodeToString(config)).apply()
}

/** §7.7 step 5 "the index saved at calibration": STREAM_MUSIC's speaker index of the last full test cue, or null. */
fun loadCalibratedIndex(context: Context): Int? = prefs(context).getInt(KEY_CALIBRATED_INDEX, -1).takeIf { it >= 0 }

fun saveCalibratedIndex(context: Context, index: Int) {
    prefs(context).edit().putInt(KEY_CALIBRATED_INDEX, index).apply()
}

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
