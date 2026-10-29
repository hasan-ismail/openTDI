package com.opentdi.mobile.cues

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.opentdi.shared.model.NEURAL_SPEAKERS
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The neural voice: Kokoro v0.19 for sherpa-onnx in filesDir/voice/<NEURAL_MODEL>/ (tools/voice/push_voice_model.sh). */
const val NEURAL_MODEL = "kokoro-int8-en-v0_19"

/** The engine name stored in [CueSet.engine] for a neural rendering. */
const val NEURAL_ENGINE = "sherpa-onnx/$NEURAL_MODEL"

/** The voices offered in the settings (owner's pick: two US women, one UK woman, three men), the default first. */
val NEURAL_VOICE_CHOICES = listOf("af_bella", "af_sarah", "bf_emma", "am_adam", "am_michael", "bm_george")

/** "af_bella" as "Bella (US)", "bf_emma" as "Emma (UK)". */
fun speakerLabel(speaker: String): String =
    speaker.substringAfter('_').replaceFirstChar { it.uppercase() } + if (speaker.startsWith("b")) " (UK)" else " (US)"

/** The model's speaker id for [speaker] (its place in voices.bin); throws [CueException] for a name it lacks. */
fun neuralSpeakerId(speaker: String): Int =
    NEURAL_SPEAKERS.indexOf(speaker).takeIf { it >= 0 }
        ?: throw CueException("The natural voice has no speaker \"$speaker\". Choose one in Settings.")

private const val MODEL_FILE = "model.int8.onnx"
private const val VOICES_FILE = "voices.bin"
private const val TOKENS_FILE = "tokens.txt"
private const val DATA_DIR = "espeak-ng-data"

/**
 * Loads the model off the main thread; the caller must [OfflineTts.release] it. Throws [CueException] when the model is
 * not installed or does not load. The files are checked first because the native loader does not fail cleanly on a
 * missing one.
 */
suspend fun loadNeuralTts(context: Context): OfflineTts = withContext(Dispatchers.Default) {
    val dir = File(context.filesDir, "voice/$NEURAL_MODEL")
    if (listOf(MODEL_FILE, VOICES_FILE, TOKENS_FILE, "$DATA_DIR/phontab").any { !File(dir, it).isFile }) {
        throw CueException("The natural voice is not installed on this phone.")
    }
    val kokoro = OfflineTtsKokoroModelConfig(
        model = File(dir, MODEL_FILE).path,
        voices = File(dir, VOICES_FILE).path,
        tokens = File(dir, TOKENS_FILE).path,
        dataDir = File(dir, DATA_DIR).path,
    )
    try {
        OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = 4)))
    } catch (e: Exception) {
        throw CueException("The natural voice did not load (${e.message}).")
    } catch (e: LinkageError) { // its native library is missing for this phone's CPU
        throw CueException("The natural voice did not load (${e.message}).")
    }
}
