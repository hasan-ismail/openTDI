package com.opentdi.wear.link

import com.opentdi.wear.alarm.WatchOutputs
import com.opentdi.wear.alarm.guideFile
import java.io.File
import java.io.IOException
import com.opentdi.wear.alarm.AlarmPlayer
import com.opentdi.shared.audio.WavIo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.shared.audio.WristCue
import com.opentdi.shared.audio.volumeRaisedCorrection
import com.opentdi.shared.model.DataLayerPaths
import java.io.OutputStream

/**
 * The wrist speaker: Play Services starts this when the phone opens the cue channel. It plays the cue on the watch's
 * own speaker at the watch's media volume and answers with a [WristCue.Reply]. As on the phone (§7.7 step 5), the
 * test cue's volume is the calibration: a cue never plays louder than the last test cue, whatever the volume is now.
 */
class WristSpeakerService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != DataLayerPaths.WRIST_AUDIO_CHANNEL) return
        val channels = Wearable.getChannelClient(this)
        // Callbacks run one at a time on a background thread: a phone that never sends must not block later cues.
        val guard = Handler(Looper.getMainLooper())
        guard.postDelayed({ channels.close(channel) }, READ_DEADLINE_MS)
        // Anything thrown here would also stop the watch's recording, so every failure becomes a reply.
        var out: OutputStream? = null // a channel's output stream may only be opened once
        try {
            val cue = try {
                WristCue.read(Tasks.await(channels.getInputStream(channel)))
            } finally {
                guard.removeCallbacksAndMessages(null)
            }
            val stream = Tasks.await(channels.getOutputStream(channel)).also { out = it }
            val name = cue.storeAs
            if (name != null) { // kept, not played now: the spoken wake-up alarm or a clip of the guided back-to-sleep
                val file =
                    if (name == WristCue.WAKE_VOICE) File(filesDir, AlarmPlayer.WAKE_VOICE_FILE) else guideFile(this, name)
                val part = File(file.path + ".part").apply { parentFile?.mkdirs() } // a crash never leaves half a clip
                part.writeBytes(WavIo.writePcm16Mono(cue.audio.samples, cue.audio.sampleRate))
                if (!part.renameTo(file)) throw IOException("could not keep $name")
                Log.i(TAG, "CLIP_STORED name=$name samples=${cue.audio.samples.size}")
                WristCue.writeReply(stream, WristCue.Reply(WristCue.PLAYED))
            } else {
                WristCue.writeReply(stream, play(cue, stream))
            }
        } catch (e: Exception) { // a closed channel, a bad header, a track the system refused
            Log.w(TAG, "WRIST_CUE_FAILED reason=${e.javaClass.simpleName}")
            try {
                WristCue.writeReply(out ?: Tasks.await(channels.getOutputStream(channel)), WristCue.Reply(WristCue.FAILED))
            } catch (e: Exception) { // the phone gave up waiting and closed the channel
                Log.w(TAG, "WRIST_CUE_NO_REPLY reason=${e.javaClass.simpleName}")
            }
        }
    }

    private fun play(cue: WristCue.Cue, out: OutputStream): WristCue.Reply {
        val audioManager = getSystemService(AudioManager::class.java)
        val index = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Earbuds chosen and connected: media goes to them, and the volume index read above is theirs.
        val target = WatchOutputs.pick(audioManager, cue.earbuds)
        val toEarbuds = target != null && WatchOutputs.isEarbuds(target.type)
        // getStreamVolume is the current media device's index, so it is the speaker's only while media goes there.
        // Before API 33 there is no such query; the routed device after the cue still reports a headset.
        if (!toEarbuds && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            audioManager.getAudioDevicesForAttributes(ATTRIBUTES).none { it.type == SPEAKER }
        ) {
            Log.w(TAG, "WRIST_CUE_SKIPPED reason=media_not_on_speaker")
            return WristCue.Reply(WristCue.NOT_ON_SPEAKER, index)
        }
        if (index == 0) {
            Log.w(TAG, "WRIST_CUE_SKIPPED reason=media_volume_off")
            return WristCue.Reply(WristCue.VOLUME_ZERO, index)
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        // Speaker and earbuds have their own volume steps, so each keeps its own test-cue calibration.
        val calibrationKey = if (toEarbuds) KEY_CALIBRATED_INDEX_EARBUDS else KEY_CALIBRATED_INDEX
        val device = target?.type ?: SPEAKER
        // The output whose volume curve converts the steps to dB (classic or LE Audio earbuds, else the speaker).
        val curve = when (device) {
            AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceInfo.TYPE_BLE_HEADSET
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioDeviceInfo.TYPE_BLE_SPEAKER
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            else -> SPEAKER
        }
        val calibrated = prefs.getInt(calibrationKey, 0)
        val factor = if (!cue.calibrate && calibrated in 1 until index) {
            volumeRaisedCorrection(
                audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, curve),
                audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, calibrated, curve),
            )
        } else {
            1f
        }
        val samples = cue.audio.samples
        val track = AudioTrack.Builder()
            .setAudioAttributes(ATTRIBUTES)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(cue.audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            target?.let { track.setPreferredDevice(it) }
            // Trailing silence carries the last cue samples through the mixer, so the head position reaches them.
            val pcm = FloatArray(samples.size + cue.audio.sampleRate / 4) { n ->
                if (n < samples.size) samples[n] * factor else 0f
            }
            WristCue.writeStarted(out)
            track.play()
            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            val deadline = SystemClock.elapsedRealtime() + DRAIN_MS
            while (track.playbackHeadPosition < samples.size && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(POLL_MS)
            }
            val routed = track.routedDevice?.type ?: -1
            val status = when {
                written != pcm.size -> WristCue.FAILED
                routed != device -> WristCue.NOT_ON_SPEAKER // not on the chosen output
                else -> WristCue.PLAYED
            }
            if (status == WristCue.PLAYED && cue.calibrate) prefs.edit().putInt(calibrationKey, index).apply()
            Log.i(
                TAG,
                "WRIST_CUE_END status=$status samples=${samples.size} rate=${cue.audio.sampleRate} index=$index " +
                    "calibrated=$calibrated factor=$factor written=$written routedType=$routed",
            )
            return WristCue.Reply(status, index, routed, factor)
        } finally {
            track.release()
        }
    }

    private companion object {
        const val TAG = "OTDI.Audio"
        const val SPEAKER = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        const val PREFS = "wrist_speaker"
        const val KEY_CALIBRATED_INDEX = "calibratedIndex"
        const val KEY_CALIBRATED_INDEX_EARBUDS = "calibratedIndexEarbuds"
        const val READ_DEADLINE_MS = 60_000L
        const val DRAIN_MS = 2000L
        const val POLL_MS = 20L
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
