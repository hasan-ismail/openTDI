package com.opentdi.mobile.cues

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.opentdi.mobile.TAG_AUDIO
import com.opentdi.mobile.logLine
import com.opentdi.shared.audio.GuideClip
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.WavIo
import com.opentdi.shared.audio.joinWithPauses
import com.opentdi.shared.audio.normalisePeak
import com.opentdi.shared.audio.trimSilence
import com.opentdi.shared.config.StructuralConstants
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TtsEngine
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * The cues Prepare renders; SLEEP_STATE_QUESTION arrives with recording in Phase 2. MORNING_WAKE is the spoken
 * wake-up alarm, sent to the watch (owner, 2026-10-28).
 */
val PHASE1_CUES = listOf(
    PromptCue.INITIAL_INCUBATION, PromptCue.WAKE_PROMPT, PromptCue.RE_INCUBATION, PromptCue.SESSION_END,
    PromptCue.MORNING_WAKE,
)

/**
 * OpenTDI's default words (§7.1): lucidity, telling the sleeper they are dreaming and leaving the rest to them
 * (owner decision 2026-10-27). [theme] is not used; MIT's theme incubation wording is [mitThemeCueText].
 * SESSION_END is the sentence only: the chime is prepended at playback.
 */
fun cueText(cue: PromptCue, @Suppress("UNUSED_PARAMETER") theme: String): String = when (cue) {
    PromptCue.INITIAL_INCUBATION -> "You are dreaming. You are in control."
    PromptCue.WAKE_PROMPT -> "You're falling asleep. You are dreaming now. What do you notice?"
    PromptCue.RE_INCUBATION -> "You are dreaming. You are in control. You can fall back asleep now."
    PromptCue.SESSION_END -> "The session is over. You can wake up now."
    // Calm and about recall first: being woken by name helped recall more than an alarm tone (§5.2).
    PromptCue.MORNING_WAKE -> "Good morning. Keep your eyes closed, and replay your dream before you move."
    PromptCue.SLEEP_STATE_QUESTION -> throw IllegalArgumentException("SLEEP_STATE_QUESTION is Phase 2")
}

/** MIT Dormio's theme incubation wording (P20 §2.4), with [theme] for [THEME]: the alternative to [cueText]. */
fun mitThemeCueText(cue: PromptCue, theme: String): String = when (cue) {
    PromptCue.INITIAL_INCUBATION -> "Remember to think of $theme."
    PromptCue.WAKE_PROMPT -> "You're falling asleep. Please tell me, what's going through your mind?"
    PromptCue.RE_INCUBATION -> "Remember to think of $theme. You can fall back asleep now."
    PromptCue.SESSION_END, PromptCue.SLEEP_STATE_QUESTION, PromptCue.MORNING_WAKE -> cueText(cue, theme)
}

/** Stands for the theme in the user's own cue words, so edited cues follow a later theme change (§7.1). */
const val THEME_PLACEHOLDER = "[THEME]"

/** The words [cue] is rendered with: the user's own (audio.cueTexts), or the §7.1 wording when missing or blank. */
fun cueText(cue: PromptCue, config: SessionConfig): String {
    val own = config.audio.cueTexts[cue]?.takeIf { it.isNotBlank() }?.replace(THEME_PLACEHOLDER, config.theme)
    val name = config.alarms.sleeperName.trim()
    return when {
        own != null -> own
        // Only the wake-up alarm says the name: in a night cue it woke a dreamer (lucid_induction.md §6).
        cue == PromptCue.MORNING_WAKE && name.isNotEmpty() ->
            cueText(cue, config.theme).replaceFirst("Good morning.", "Good morning, $name.")
        else -> cueText(cue, config.theme)
    }
}

/**
 * [text] as the phrases that are rendered one by one (§7.3): split after . ? ! ; : or a comma that a space follows,
 * and at line breaks. Each phrase keeps its punctuation for prosody; a piece without a letter or digit is dropped.
 */
fun splitPhrases(text: String): List<String> =
    text.split(PHRASE_BREAK).map { it.trim() }.filter { phrase -> phrase.any { it.isLetterOrDigit() } }

/**
 * What a rendering depends on, hashed: theme, voice, speed, pause, fade-in (the lead), the neural engine and speaker
 * when chosen, every cue's words, and the guided back-to-sleep's words when it is on.
 */
fun cueFingerprint(config: SessionConfig, voice: String): String {
    val audio = config.audio
    // Only NEURAL adds inputs, so cues prepared with the system voice before the engine choice existed stay ready.
    val engine = if (audio.ttsEngine == TtsEngine.NEURAL) listOf("NEURAL", audio.neuralSpeaker) else emptyList()
    // Own recordings likewise: only present ones add inputs. And the guide: a new dream sign asks for Prepare cues.
    val own = audio.ownVoice.toSortedMap().map { (cue, sha) -> "own:$cue:$sha" }
    val guide = if (guideOn(config)) GuideClip.entries.map { guideText(it, config) } else emptyList()
    val inputs = listOf(config.theme, voice, "${audio.ttsSpeechRate}", "${audio.phrasePauseMs}", "${audio.fadeInMs}") +
        engine + PHASE1_CUES.map { cueText(it, config) } + own + guide
    return sha256Hex(OpenTdiJson.encodeToString(inputs).toByteArray())
}

/** Why [set] cannot play for [config], as a message for the user; null when it was prepared for exactly this. */
fun cueSetProblem(set: CueSet?, config: SessionConfig): String? = when {
    set == null -> NO_CUES
    set.theme != config.theme -> "The cues are for \"${set.theme}\". Tap Prepare cues for \"${config.theme}\"."
    set.fingerprint != cueFingerprint(config, set.voice) ->
        "The cue words, voice, speaking speed, pause or fade-in have changed. Tap Prepare cues."
    else -> null
}

/** One rendered cue with the format onBeginSynthesis reported for it (logged in p_config.csv). */
@Serializable
data class RenderedCue(val cue: PromptCue, val ttsSampleRate: Int, val ttsEncoding: Int, val ttsChannels: Int)

/** The format a text-to-speech engine reported for one utterance. */
private data class TtsFormat(val sampleRate: Int, val encoding: Int, val channels: Int)

/**
 * filesDir/cues/cues.json: the theme, engine and voice the WAVs beside it were rendered with, and the rendering's
 * [cueFingerprint] ("" in a set from before it existed, which therefore counts as changed).
 */
@Serializable
data class CueSet(
    val theme: String,
    val engine: String,
    val voice: String,
    val cues: List<RenderedCue>,
    val fingerprint: String = "",
)

/**
 * One WAV in filesDir/cues/ as [RENDER_CACHE] records it: the [key] of the words, voice and settings it was rendered
 * from, and the format the engine reported, so Prepare cues renders only what changed (owner, 2026-10-28: the guide
 * took minutes on every Prepare, and closing the app lost all of it).
 */
@Serializable
private data class CachedRender(val key: String, val sampleRate: Int, val encoding: Int, val channels: Int)

/** The decoded cue WAVs of [set] and each file's SHA-256 (hex). */
class PreparedCues(val set: CueSet, val audio: Map<PromptCue, WavAudio>, val sha256: Map<PromptCue, String>)

/** A cue problem, with a message for the user. */
class CueException(message: String) : Exception(message)

private const val CUES_DIR = "cues"
private const val MANIFEST = "cues.json"
private const val RENDER_CACHE = "rendered.json"
private const val RENDER_VERSION = 1 // bump when trimming, joining or normalising changes, so every WAV renders anew
private const val INIT_TIMEOUT_MS = 15_000L
private const val SYNTHESIS_TIMEOUT_MS = 60_000L
private const val NO_CUES = "No cues are prepared yet. Tap Prepare cues."
private const val NO_OFFLINE_VOICE = "No offline English text-to-speech voice is installed. Download one in the " +
    "phone's text-to-speech settings. OpenTDI never uses a voice that needs the network."
private val PHRASE_BREAK = Regex("""(?<=[.?!;:,])\s+|\n""")

private fun cueFile(context: Context, cue: PromptCue) = File(File(context.filesDir, CUES_DIR), "${cue.name}.wav")

private fun guideFile(context: Context, clip: GuideClip) =
    File(File(context.filesDir, CUES_DIR), "guide_${clip.file}.wav")

/** A guide clip that Prepare cues rendered, to send to the watch; throws [CueException] when it is gone or damaged. */
fun loadGuideClip(context: Context, clip: GuideClip): WavAudio = try {
    WavIo.read(guideFile(context, clip).readBytes())
} catch (e: IOException) {
    throw CueException("The guide's ${clip.file} clip is missing. Tap Prepare cues.")
} catch (e: IllegalArgumentException) {
    throw CueException("The guide's ${clip.file} clip is damaged. Tap Prepare cues.")
}

/** The manifest of the last complete rendering, or null. */
fun loadCueSet(context: Context): CueSet? = try {
    OpenTdiJson.decodeFromString<CueSet>(File(File(context.filesDir, CUES_DIR), MANIFEST).readText())
} catch (e: IOException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

/** Reads the cues prepared for [config]; throws [CueException] when they are missing, stale or unreadable. */
fun loadPreparedCues(context: Context, config: SessionConfig): PreparedCues {
    val set = loadCueSet(context) ?: throw CueException(NO_CUES)
    cueSetProblem(set, config)?.let { throw CueException(it) }
    val audio = mutableMapOf<PromptCue, WavAudio>()
    val sha256 = mutableMapOf<PromptCue, String>()
    for (rendered in set.cues) {
        val cue = rendered.cue
        try {
            val bytes = cueFile(context, cue).readBytes()
            audio[cue] = WavIo.read(bytes)
            sha256[cue] = sha256Hex(bytes)
        } catch (e: IOException) {
            throw CueException("The ${cue.name} cue file is missing. Tap Prepare cues.")
        } catch (e: IllegalArgumentException) {
            throw CueException("The ${cue.name} cue file is damaged. Tap Prepare cues.")
        }
    }
    return PreparedCues(set, audio, sha256)
}

/**
 * Renders [PHASE1_CUES] with [config]'s words, speaking speed and pause into filesDir/cues/ (§7.3): one utterance per
 * phrase, each trimmed of leading and trailing silence, joined with exactly phrasePauseMs of silence after a lead of
 * ¾ fadeInMs, peak-normalised to −3 dBFS and stored as PCM16 mono WAVs; with [guideOn], the guide's clips likewise
 * ([loadGuideClip]). NEURAL renders with the on-device Kokoro model; when it is not installed, does not load or fails
 * while rendering, [onFallback] gets the reason and the system voice renders instead. A WAV already rendered from the
 * same words, voice and settings is kept, and [onProgress] counts the cues and clips done. Call from the main thread,
 * never during a session. Throws [CueException] or IOException.
 */
suspend fun renderCues(
    context: Context,
    config: SessionConfig,
    onFallback: (String) -> Unit = {},
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
): CueSet {
    if (config.audio.ttsEngine == TtsEngine.NEURAL) {
        val speaker = config.audio.neuralSpeaker
        val sid = neuralSpeakerId(speaker)
        val tts = try {
            loadNeuralTts(context)
        } catch (e: CueException) {
            Log.w(TAG_AUDIO, logLine("NEURAL_TTS_UNAVAILABLE", "reason" to e.message))
            onFallback("${e.message} The cues use the phone's own voice instead.")
            null
        }
        if (tts != null) {
            try {
                return storeCues(context, config, NEURAL_ENGINE, speaker, onProgress) { _, text ->
                    // Kokoro's speed and setSpeechRate both take 1 as normal, so the rate passes through unchanged.
                    val audio = withContext(Dispatchers.Default) { tts.generate(text, sid, config.audio.ttsSpeechRate) }
                    TtsFormat(audio.sampleRate, AudioFormat.ENCODING_PCM_FLOAT, 1) to audio.samples
                }
            } catch (e: Exception) {
                // A native error arrives as a RuntimeException; a storage failure would stop the system voice too.
                if (e is CancellationException || e is IOException) throw e
                Log.w(TAG_AUDIO, logLine("NEURAL_TTS_FAILED", "reason" to e.message))
                onFallback("The natural voice could not prepare the cues. The cues use the phone's own voice instead.")
            } finally {
                tts.release() // directly: a cancelled coroutine would skip a withContext here
            }
        }
    }
    return renderSystemCues(context, config, onProgress)
}

/** [renderCues] with an offline English Android TextToSpeech voice; formats from onBeginSynthesis. */
private suspend fun renderSystemCues(
    context: Context,
    config: SessionConfig,
    onProgress: (done: Int, total: Int) -> Unit,
): CueSet {
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
        if (tts.setSpeechRate(config.audio.ttsSpeechRate) != TextToSpeech.SUCCESS) {
            throw CueException("The text-to-speech engine refused the speaking speed ${config.audio.ttsSpeechRate}.")
        }
        val formats = ConcurrentHashMap<String, TtsFormat>() // by utterance id, "<cue or clip>-<phrase index>"
        val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            fun finish(utteranceId: String?, ok: Boolean) {
                if (utteranceId != null) pending[utteranceId]?.complete(ok)
            }

            override fun onStart(utteranceId: String?) = Unit

            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                formats[utteranceId ?: return] = TtsFormat(sampleRateInHz, audioFormat, channelCount)
            }

            override fun onDone(utteranceId: String?) = finish(utteranceId, true)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId, false)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
        })
        return storeCues(context, config, tts.defaultEngine.orEmpty(), voice.name, onProgress) { id, text ->
            val raw = File(File(context.filesDir, CUES_DIR), "$id.tts.wav")
            val done = CompletableDeferred<Boolean>().also { pending[id] = it }
            val queued = tts.synthesizeToFile(text, Bundle(), raw, id) == TextToSpeech.SUCCESS
            if (!queued || withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) { done.await() } != true) {
                throw CueException("Text-to-speech could not render \"$text\".")
            }
            val format = formats[id] ?: throw CueException("Text-to-speech reported no format for \"$text\".")
            format to withContext(Dispatchers.IO) { readTts(raw, format, id) }
        }
    } finally {
        tts.shutdown()
    }
}

/**
 * Renders every phrase of every cue, and of the guide's clips when it is on, with [phrase] (its format and samples for
 * an utterance id "<cue or clip>-<phrase index>"), one utterance per phrase so each one's silence can be trimmed and
 * every pause is exact, then stores the cues, the clips and the cues' manifest. A WAV whose [RENDER_CACHE] key still
 * matches is kept; the cache is written after each WAV, so an interrupted Prepare resumes where it stopped.
 */
private suspend fun storeCues(
    context: Context,
    config: SessionConfig,
    engine: String,
    voice: String,
    onProgress: (done: Int, total: Int) -> Unit,
    phrase: suspend (id: String, text: String) -> Pair<TtsFormat, FloatArray>,
): CueSet {
    val dir = File(context.filesDir, CUES_DIR)
    val cacheFile = File(dir, RENDER_CACHE)
    val cache = withContext(Dispatchers.IO) {
        dir.mkdirs()
        File(dir, MANIFEST).delete() // until the new set is complete, no cues count as prepared
        try {
            OpenTdiJson.decodeFromString<Map<String, CachedRender>>(cacheFile.readText())
        } catch (e: IOException) {
            emptyMap()
        } catch (e: IllegalArgumentException) {
            emptyMap()
        }
    }.toMutableMap()
    val total = PHASE1_CUES.size + if (guideOn(config)) GuideClip.entries.size else 0
    var done = 0
    onProgress(done, total)
    // Trimmed speech would start at sample 0, where the §7.4 fade swallows a short first phrase.
    val leadMs = (config.audio.fadeInMs * StructuralConstants.SPEECH_LEAD_FADE_FRACTION).toInt()

    suspend fun remember(name: String, entry: CachedRender?) = withContext(Dispatchers.IO) {
        if (entry == null) cache.remove(name) else cache[name] = entry
        cacheFile.writeText(OpenTdiJson.encodeToString(cache.toMap()))
    }

    suspend fun speak(name: String, text: String): Pair<TtsFormat, FloatArray> {
        val phrases = splitPhrases(text).mapIndexed { i, words ->
            val (format, raw) = phrase("$name-$i", words)
            val samples = trimSilence(raw)
            if (samples.isEmpty()) throw CueException("Text-to-speech rendered silence for \"$words\".")
            format to samples
        }
        val format = phrases.firstOrNull()?.first ?: throw CueException("The $name cue has no words to say.")
        if (phrases.any { it.first.sampleRate != format.sampleRate }) {
            throw CueException("Text-to-speech changed its sample rate within the $name cue.")
        }
        val joined = joinWithPauses(phrases.map { it.second }, format.sampleRate, config.audio.phrasePauseMs, leadMs)
        return format to joined
    }

    /** [text] spoken into [file] as [name], unless the cache shows it was rendered from exactly these inputs. */
    suspend fun render(name: String, file: File, text: String): TtsFormat {
        val inputs = listOf("$RENDER_VERSION", engine, voice, "${config.audio.ttsSpeechRate}",
            "${config.audio.phrasePauseMs}", "$leadMs", text)
        val key = sha256Hex(OpenTdiJson.encodeToString(inputs).toByteArray())
        val kept = cache[name]?.takeIf { it.key == key && withContext(Dispatchers.IO) { file.isFile } }
        val format = if (kept != null) {
            TtsFormat(kept.sampleRate, kept.encoding, kept.channels)
        } else {
            val (format, samples) = speak(name, text)
            withContext(Dispatchers.IO) { storeCue(file, samples, format.sampleRate) }
            remember(name, CachedRender(key, format.sampleRate, format.encoding, format.channels))
            format
        }
        onProgress(++done, total)
        return format
    }

    val rendered = PHASE1_CUES.map { cue ->
        val ownSha = config.audio.ownVoice[cue]
        if (ownSha != null) { // the user's own recording, trimmed and led in like speech (OwnVoice.kt)
            val own = withContext(Dispatchers.IO) { loadOwnVoice(context, cue, ownSha) }
            val samples = trimSilence(own.samples)
            if (samples.isEmpty()) throw CueException("Your recording for \"${cue.name}\" is silent. Record it again.")
            val padded = joinWithPauses(listOf(samples), own.sampleRate, config.audio.phrasePauseMs, leadMs)
            withContext(Dispatchers.IO) { storeCue(cueFile(context, cue), padded, own.sampleRate) }
            remember(cue.name, null) // the WAV is the recording now, not a rendering
            onProgress(++done, total)
            return@map RenderedCue(cue, own.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
        }
        val format = render(cue.name, cueFile(context, cue), cueText(cue, config))
        RenderedCue(cue, format.sampleRate, format.encoding, format.channels)
    }
    if (guideOn(config)) {
        for (clip in GuideClip.entries) render(clip.file, guideFile(context, clip), guideText(clip, config))
    }
    val set = CueSet(config.theme, engine, voice, rendered, cueFingerprint(config, voice))
    withContext(Dispatchers.IO) { File(dir, MANIFEST).writeText(OpenTdiJson.encodeToString(set)) }
    return set
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

private fun readTts(raw: File, format: TtsFormat, id: String): FloatArray = try {
    // FileSynthesisCallback writes format tag 1 even for float data, so the reported encoding decides.
    WavIo.read(raw.readBytes(), ttsFloat = format.encoding == AudioFormat.ENCODING_PCM_FLOAT).samples
} catch (e: IllegalArgumentException) {
    throw CueException("Text-to-speech wrote an unreadable $id file: ${e.message}")
} finally {
    raw.delete()
}

private fun storeCue(target: File, samples: FloatArray, sampleRate: Int) {
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeBytes(WavIo.writePcm16Mono(normalisePeak(samples), sampleRate))
    if (!tmp.renameTo(target)) throw IOException("Could not store ${target.name}")
}

private fun sha256Hex(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
