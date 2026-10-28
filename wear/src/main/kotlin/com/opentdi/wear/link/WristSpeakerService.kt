package com.opentdi.wear.link

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.WristCue
import com.opentdi.shared.model.DataLayerPaths

/**
 * The wrist speaker: Play Services starts this when the phone opens the cue channel. It plays the cue on the watch's
 * own speaker at the watch's media volume and answers with a [WristCue] status byte.
 */
class WristSpeakerService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != DataLayerPaths.WRIST_AUDIO_CHANNEL) return
        val channels = Wearable.getChannelClient(this)
        // Callbacks run on a background thread; anything thrown here would also stop the watch's recording.
        val status = try {
            play(WristCue.read(Tasks.await(channels.getInputStream(channel))))
        } catch (e: Exception) {
            Log.w(TAG, "WRIST_CUE_FAILED reason=${e.javaClass.simpleName}")
            WristCue.FAILED
        }
        try {
            Tasks.await(channels.getOutputStream(channel)).run {
                write(status)
                flush()
            }
        } catch (e: Exception) { // the phone gave up waiting and closed the channel
            Log.w(TAG, "WRIST_CUE_NO_REPLY reason=${e.javaClass.simpleName}")
        }
    }

    private fun play(audio: WavAudio): Int {
        val audioManager = getSystemService(AudioManager::class.java)
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Log.w(TAG, "WRIST_CUE_SKIPPED reason=media_volume_off")
            return WristCue.VOLUME_ZERO
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { track.setPreferredDevice(it) }
            track.play()
            // Trailing silence carries the last cue samples through the mixer, so the head position reaches them.
            val out = audio.samples.copyOf(audio.samples.size + audio.sampleRate / 4)
            track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
            val deadline = SystemClock.elapsedRealtime() + DRAIN_MS
            while (track.playbackHeadPosition < audio.samples.size && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(POLL_MS)
            }
            Log.i(
                TAG,
                "WRIST_CUE_PLAYED samples=${audio.samples.size} rate=${audio.sampleRate} " +
                    "routedType=${track.routedDevice?.type}",
            )
            return WristCue.PLAYED
        } finally {
            track.release()
        }
    }

    private companion object {
        const val TAG = "OTDI.Audio"
        const val DRAIN_MS = 2000L
        const val POLL_MS = 20L
    }
}
