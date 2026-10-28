package com.opentdi.wear.alarm

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Where the watch plays sound (owner, 2026-10-28): its own speaker, or earbuds paired with the watch (e.g. Galaxy Buds,
 * in one or both ears). The choice comes from the phone with Start session; when the earbuds are not connected, the
 * speaker plays instead.
 */
object WatchOutputs {
    private const val PREFS = "watch_output"
    private const val KEY_EARBUDS = "earbuds"
    // Classic Bluetooth audio, and LE Audio earbuds and speakers (values from API 31; older watches report A2DP).
    private val EARBUD_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        26, // TYPE_BLE_HEADSET
        27, // TYPE_BLE_SPEAKER
    )

    fun earbuds(audioManager: AudioManager): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in EARBUD_TYPES }

    fun speaker(audioManager: AudioManager): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    /** The earbuds when [earbuds] is chosen and they are connected, otherwise the speaker. */
    fun pick(audioManager: AudioManager, earbuds: Boolean): AudioDeviceInfo? =
        (if (earbuds) earbuds(audioManager) else null) ?: speaker(audioManager)

    fun isEarbuds(type: Int) = type in EARBUD_TYPES

    fun earbudsChosen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_EARBUDS, false)

    fun saveChoice(context: Context, earbuds: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_EARBUDS, earbuds).apply()
    }
}
