package com.opentdi.wear.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.opentdi.shared.audio.alarmLoop
import com.opentdi.shared.model.AlarmTone

/**
 * Plays an alarm tone on the watch speaker in a loop at the watch's alarm volume, with a vibration pattern, until
 * [stop]. USAGE_ALARM: it plays through Do Not Disturb and bedtime mode, as an alarm clock does.
 */
class AlarmPlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var track: AudioTrack? = null

    /** Starts [tone] at [volume] (0..1 of the alarm stream); a [gentle] vibration is softer and sparser. */
    fun start(tone: AlarmTone, volume: Float, gentle: Boolean) {
        stop()
        val loop = alarmLoop(tone, SAMPLE_RATE)
        if (loop.isNotEmpty()) {
            track = AudioTrack.Builder()
                .setAudioAttributes(ATTRIBUTES)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(loop.size * Float.SIZE_BYTES)
                .build()
                .apply {
                    write(loop, 0, loop.size, AudioTrack.WRITE_BLOCKING)
                    setLoopPoints(0, loop.size, -1)
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { setPreferredDevice(it) }
                    setVolume(volume)
                    play()
                }
        }
        val effect = if (gentle) {
            VibrationEffect.createWaveform(longArrayOf(0, 300, 2700), intArrayOf(0, 90, 0), 0)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 500, 700), intArrayOf(0, 255, 0), 0)
        }
        vibrate(effect)
    }

    fun setVolume(volume: Float) {
        track?.setVolume(volume)
    }

    /** One short buzz, e.g. WBTB's "go back to sleep". */
    fun buzz() = vibrate(VibrationEffect.createOneShot(400, 120))

    /** The alarm stream's volume step, 0 = the tone is silent (the vibration still runs). */
    fun alarmVolumeIndex(): Int = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    fun stop() {
        track?.run {
            try {
                stop()
            } catch (e: IllegalStateException) { // never started
                Unit
            }
            release()
        }
        track = null
        vibrator.cancel()
    }

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, ATTRIBUTES)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
