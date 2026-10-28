package com.opentdi.mobile.cues

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.WavIo
import com.opentdi.shared.audio.normalisePeak
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.PromptCue
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** The cues Phase 1 renders and plays; SLEEP_STATE_QUESTION arrives with recording in Phase 2. */
val PHASE1_CUES = listOf(PromptCue.INITIAL_INCUBATION, PromptCue.WAKE_PROMPT, PromptCue.RE_INCUBATION, PromptCue.SESSION_END)

/** §7.1 wording with [theme] for [THEME]. SESSION_END is the sentence only: the chime is prepended at playback. */
fun cueText(cue: PromptCue, theme: String): String = when (cue) {
    PromptCue.INITIAL_INCUBATION -> "Remember to think of $theme."
    PromptCue.WAKE_PROMPT -> "You're falling asleep. Please tell me, what's going through your mind?"
    PromptCue.RE_INCUBATION -> "Remember to think of $theme. You can fall back asleep now."
    PromptCue.SESSION_END -> "The session is over. You can wake up now."
    PromptCue.SLEEP_STATE_QUESTION -> throw IllegalArgumentException("SLEEP_STATE_QUESTION is Phase 2")
}

/** One rendered cue with the format onBeginSynthesis reported for it (logged in p_config.csv). */
@Serializable
data class RenderedCue(val cue: PromptCue, val ttsSampleRate: Int, val ttsEncoding: Int, val ttsChannels: Int)

/** filesDir/cues/cues.json: the theme, engine and voice the WAVs beside it were rendered with. */
@Serializable
data class CueSet(val theme: String, val engine: String, val voice: String, val cues: List<RenderedCue>)

/** The decoded cue WAVs of [set] and each file's SHA-256 (hex). */
class PreparedCues(val set: CueSet, val audio: Map<PromptCue, WavAudio>, val sha256: Map<PromptCue, String>)

/** A cue problem, with a message for the user. */
class CueException(message: String) : Exception(message)

private const val CUES_DIR = "cues"
private const val MANIFEST = "cues.json"
private const val INIT_TIMEOUT_MS = 15_000L
private const val SYNTHESIS_TIMEOUT_MS = 60_000L
private const val NO_OFFLINE_VOICE = "No offline English text-to-speech voice is installed. Download one in the " +
    "phone's text-to-speech settings. OpenTDI never uses a voice that needs the network."

private fun cueFile(context: Context, cue: PromptCue) = File(File(context.filesDir, CUES_DIR), "${cue.name}.wav")

/** The manifest of the last complete rendering, or null. */
fun loadCueSet(context: Context): CueSet? = try {
    OpenTdiJson.decodeFromString<CueSet>(File(File(context.filesDir, CUES_DIR), MANIFEST).readText())
} catch (e: IOException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

/** Reads the cues prepared for [theme]; throws [CueException] when they are missing, stale or unreadable. */
fun loadPreparedCues(context: Context, theme: String): PreparedCues {
    val set = loadCueSet(context) ?: throw CueException("No cues are prepared yet. Tap Prepare cues.")
    if (set.theme != theme) throw CueException("The cues are for \"${set.theme}\". Tap Prepare cues for \"$theme\".")
    val audio = mutableMapOf<PromptCue, WavAudio>()
    val sha256 = mutableMapOf<PromptCue, String>()
    for (rendered in set.cues) {
        val cue = rendered.cue
        try {
            val bytes = cueFile(context, cue).readBytes()
            audio[cue] = WavIo.read(bytes)
            sha256[cue] = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        } catch (e: IOException) {
            throw CueException("The ${cue.name} cue file is missing. Tap Prepare cues.")
        } catch (e: IllegalArgumentException) {
            throw CueException("The ${cue.name} cue file is damaged. Tap Prepare cues.")
        }
    }
    return PreparedCues(set, audio, sha256)
}

/**
 * Renders [PHASE1_CUES] for [theme] with an offline English TTS voice into filesDir/cues/ (§7.3): format from
 * onBeginSynthesis, peak-normalised to −3 dBFS, stored as PCM16 mono WAVs. Call from the main thread, never during
 * a nap. Throws [CueException] or IOException.
 */
suspend fun renderCues(context: Context, theme: String): CueSet {
    val ready = CompletableDeferred<Int>()
    val tts = TextToSpeech(context) { ready.complete(it) }
    try {
        if (withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } != TextToSpeech.SUCCESS) {
            throw CueException("The text-to-speech engine did not start.")
        }
        val voice = offlineEnglishVoice(tts) ?: throw CueException(NO_OFFLINE_VOICE)
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            throw CueException("The text-to-speech engine refused the voice ${voice.name}.")
        }
        val formats = ConcurrentHashMap<String, RenderedCue>()
        val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            fun finish(utteranceId: String?, ok: Boolean) {
                if (utteranceId != null) pending[utteranceId]?.complete(ok)
            }

            override fun onStart(utteranceId: String?) = Unit

            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                val cue = PHASE1_CUES.firstOrNull { it.name == utteranceId } ?: return
                formats[cue.name] = RenderedCue(cue, sampleRateInHz, audioFormat, channelCount)
            }

            override fun onDone(utteranceId: String?) = finish(utteranceId, true)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId, false)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
        })
        val dir = File(context.filesDir, CUES_DIR)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, MANIFEST).delete() // until the new set is complete, no cues count as prepared
        }
        val rendered = PHASE1_CUES.map { cue ->
            val raw = File(dir, "${cue.name}.tts.wav")
            val done = CompletableDeferred<Boolean>().also { pending[cue.name] = it }
            val queued = tts.synthesizeToFile(cueText(cue, theme), Bundle(), raw, cue.name) == TextToSpeech.SUCCESS
            if (!queued || withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) { done.await() } != true) {
                throw CueException("Text-to-speech could not render the ${cue.name} cue.")
            }
            val format = formats[cue.name] ?: throw CueException("Text-to-speech reported no format for ${cue.name}.")
            withContext(Dispatchers.IO) { storeCue(cueFile(context, cue), raw, format) }
            format
        }
        val set = CueSet(theme, tts.defaultEngine.orEmpty(), voice.name, rendered)
        withContext(Dispatchers.IO) { File(dir, MANIFEST).writeText(OpenTdiJson.encodeToString(set)) }
        return set
    } finally {
        tts.shutdown()
    }
}

/** AudioFormat encoding constant as its name, for p_config.csv. */
fun encodingName(encoding: Int): String = when (encoding) {
    AudioFormat.ENCODING_PCM_8BIT -> "ENCODING_PCM_8BIT"
    AudioFormat.ENCODING_PCM_16BIT -> "ENCODING_PCM_16BIT"
    AudioFormat.ENCODING_PCM_FLOAT -> "ENCODING_PCM_FLOAT"
    else -> encoding.toString()
}

// English because the §7.1 wording is English; a network voice is never a candidate (§7.3).
private fun offlineEnglishVoice(tts: TextToSpeech): Voice? {
    val offline = tts.voices.orEmpty().filter { voice ->
        !voice.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty() &&
            runCatching { voice.locale.isO3Language }.getOrNull() == "eng"
    }
    return tts.defaultVoice?.takeIf { it in offline } ?: offline.maxByOrNull { it.quality }
}

private fun storeCue(target: File, raw: File, format: RenderedCue) {
    val samples = try {
        // FileSynthesisCallback writes format tag 1 even for float data, so the reported encoding decides.
        WavIo.read(raw.readBytes(), ttsFloat = format.ttsEncoding == AudioFormat.ENCODING_PCM_FLOAT).samples
    } catch (e: IllegalArgumentException) {
        throw CueException("Text-to-speech wrote an unreadable ${format.cue.name} file: ${e.message}")
    } finally {
        raw.delete()
    }
    if (samples.all { it == 0f }) throw CueException("Text-to-speech rendered silence for ${format.cue.name}.")
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeBytes(WavIo.writePcm16Mono(normalisePeak(samples), format.ttsSampleRate))
    if (!tmp.renameTo(target)) throw IOException("Could not store ${target.name}")
}
