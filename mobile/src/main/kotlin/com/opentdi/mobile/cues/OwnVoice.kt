package com.opentdi.mobile.cues

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.WavIo
import com.opentdi.shared.model.PromptCue
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The user's own recorded voice for a cue (owner, 2026-10-28): an unfamiliar voice stirred sleepers more than a
 * familiar one (Ameen et al. 2022, lucid_induction.md §5.2). Kept in filesDir/own_voice/<CUE>.wav; the settings hold
 * each recording's SHA-256 (AudioConfig.ownVoice), so a new recording asks for Prepare cues again.
 */
private const val OWN_DIR = "own_voice"
const val OWN_VOICE_RATE = 24_000
const val OWN_VOICE_MAX_MS = 20_000L

private fun ownFile(context: Context, cue: PromptCue) = File(File(context.filesDir, OWN_DIR), "${cue.name}.wav")

/**
 * Records the phone's microphone until [stop] is set or [OWN_VOICE_MAX_MS] pass, on the calling (IO) thread. The
 * caller has RECORD_AUDIO; null when the microphone could not be opened.
 */
@SuppressLint("MissingPermission")
fun recordMic(stop: AtomicBoolean): FloatArray? {
    val minBuffer = AudioRecord.getMinBufferSize(
        OWN_VOICE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
    )
    val record = try {
        AudioRecord(
            MediaRecorder.AudioSource.MIC, OWN_VOICE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, OWN_VOICE_RATE / 2),
        )
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        return null
    }
    val all = ShortArray((OWN_VOICE_RATE * OWN_VOICE_MAX_MS / 1000).toInt())
    var total = 0
    try {
        record.startRecording()
        while (!stop.get() && total < all.size) {
            val n = record.read(all, total, minOf(OWN_VOICE_RATE / 10, all.size - total))
            if (n < 0) break
            total += n
        }
    } finally {
        record.stop()
        record.release()
    }
    return FloatArray(total) { all[it] / 32768f }
}

/** Stores [samples] as [cue]'s recording; returns its SHA-256 for AudioConfig.ownVoice. */
fun saveOwnVoice(context: Context, cue: PromptCue, samples: FloatArray): String {
    val bytes = WavIo.writePcm16Mono(samples, OWN_VOICE_RATE)
    ownFile(context, cue).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    return sha256(bytes)
}

fun removeOwnVoice(context: Context, cue: PromptCue) {
    ownFile(context, cue).delete()
}

/** [cue]'s recording when it is still the one whose hash the settings hold; otherwise a [CueException]. */
fun loadOwnVoice(context: Context, cue: PromptCue, sha: String): WavAudio {
    val file = ownFile(context, cue)
    val bytes = if (file.exists()) file.readBytes() else null
    if (bytes == null || sha256(bytes) != sha) {
        throw CueException("One of your own recordings is missing. Record it again, or tap Use the app's voice.")
    }
    return WavIo.read(bytes)
}

private fun sha256(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
