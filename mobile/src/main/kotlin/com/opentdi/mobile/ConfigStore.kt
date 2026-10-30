package com.opentdi.mobile

import android.content.Context
import com.opentdi.mobile.cues.CUE_WORDS_VERSION
import com.opentdi.mobile.cues.dropOldBuiltInWords
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionMode

private const val PREFS = "opentdi"
private const val KEY_CONFIG = "session_config"
private const val KEY_WORDS_VERSION = "cue_words_version"
private const val KEY_CALIBRATED_INDEX = "calibrated_stream_index"
private const val KEY_ONBOARDED = "onboarded"

/** Phase 1 runs TIMED_CUES only (spec §0); every other default is the spec's. */
val PHASE1_DEFAULT_CONFIG = SessionConfig(mode = SessionMode.TIMED_CUES)

/** The saved SessionConfig, or the Phase 1 default when nothing (readable) is saved. */
fun loadConfig(context: Context): SessionConfig {
    val prefs = prefs(context)
    val json = prefs.getString(KEY_CONFIG, null) ?: return PHASE1_DEFAULT_CONFIG
    val config = try {
        OpenTdiJson.decodeFromString<SessionConfig>(json)
    } catch (e: IllegalArgumentException) { // SerializationException included
        return PHASE1_DEFAULT_CONFIG
    }
    // Once per change of the built-in words (saveConfig stamps the version), so old words typed later on purpose stay.
    if (prefs.getInt(KEY_WORDS_VERSION, 1) >= CUE_WORDS_VERSION) return config
    return dropOldBuiltInWords(config).also { saveConfig(context, it) }
}

fun saveConfig(context: Context, config: SessionConfig) {
    prefs(context).edit().putString(KEY_CONFIG, OpenTdiJson.encodeToString(config))
        .putInt(KEY_WORDS_VERSION, CUE_WORDS_VERSION).apply()
}

/** §7.7 step 5 "the index saved at calibration": STREAM_MUSIC's speaker index of the last full test cue, or null. */
fun loadCalibratedIndex(context: Context): Int? = prefs(context).getInt(KEY_CALIBRATED_INDEX, -1).takeIf { it >= 0 }

fun saveCalibratedIndex(context: Context, index: Int) {
    prefs(context).edit().putInt(KEY_CALIBRATED_INDEX, index).apply()
}

/** True once the set-up guide was finished or skipped. */
fun isOnboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)

fun setOnboarded(context: Context, done: Boolean) {
    prefs(context).edit().putBoolean(KEY_ONBOARDED, done).apply()
}

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
