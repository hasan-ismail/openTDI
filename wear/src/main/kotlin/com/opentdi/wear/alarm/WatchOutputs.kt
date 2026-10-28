package com.opentdi.wear.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

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

/**
 * Short sounds on the watch's output: a late cue, or a clip of the guided back-to-sleep. Each plays once on a static
 * track that frees itself when done; [stop] silences them all at once.
 */
class OneShots(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val playing = mutableSetOf<AudioTrack>() // held until done: an unreferenced track may be collected

    /**
     * [samples] once at [volume] of the media stream, on the speaker or, with [earbuds], the earbuds paired with the
     * watch. A track the system refuses is skipped: a missed sound must not stop the night's recording.
     */
    fun play(samples: FloatArray, sampleRate: Int, volume: Float, earbuds: Boolean) {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                .build()
        } catch (e: RuntimeException) { // the builder's UnsupportedOperationException or IllegalArgumentException
            return
        }
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            WatchOutputs.pick(audioManager, earbuds)?.let { track.setPreferredDevice(it) }
            track.setVolume(volume)
            track.setNotificationMarkerPosition(samples.size)
            playing += track
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    t.release()
                    playing -= t
                }

                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
        } catch (e: IllegalStateException) { // a track that did not initialise
            playing -= track
            track.release()
        }
    }

    fun stop() {
        playing.forEach { it.release() }
        playing.clear()
    }
}
