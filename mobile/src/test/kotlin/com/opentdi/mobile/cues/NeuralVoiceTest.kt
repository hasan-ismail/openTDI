package com.opentdi.mobile.cues

import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.NEURAL_SPEAKERS
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.TtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralVoiceTest {
    @Test
    fun speakers_mapToTheModelsIds_andOnlyFemaleVoicesAreOffered() {
        // The model's own metadata: 0->af, 1->af_bella, ..., 7->bf_emma, 10->bm_lewis.
        assertEquals(0, neuralSpeakerId("af"))
        assertEquals(1, neuralSpeakerId("af_bella"))
        assertEquals(7, neuralSpeakerId("bf_emma"))
        assertEquals(10, neuralSpeakerId("bm_lewis"))
        assertThrows(CueException::class.java) { neuralSpeakerId("af_heart") }
        assertEquals(AudioConfig().neuralSpeaker, FEMALE_NEURAL_SPEAKERS.first())
        assertTrue(FEMALE_NEURAL_SPEAKERS.all { it in NEURAL_SPEAKERS && it.substring(1, 3) == "f_" })
        assertEquals("Bella (US)", speakerLabel("af_bella"))
        assertEquals("Isabella (UK)", speakerLabel("bf_isabella"))
    }

    @Test
    fun fingerprint_coversEngineAndSpeaker_onlyWhenNeural() {
        val system = SessionConfig(theme = "a tree")
        val neural = system.copy(audio = AudioConfig(ttsEngine = TtsEngine.NEURAL))
        // The speaker does not change a system rendering, so system cues stay ready when it changes.
        assertEquals(
            cueFingerprint(system, "en-us-x-voice"),
            cueFingerprint(system.copy(audio = AudioConfig(neuralSpeaker = "bf_emma")), "en-us-x-voice"),
        )
        assertNotEquals(cueFingerprint(system, "af_bella"), cueFingerprint(neural, "af_bella"))
        val emma = neural.copy(audio = neural.audio.copy(neuralSpeaker = "bf_emma"))
        assertNotEquals(cueFingerprint(neural, "af_bella"), cueFingerprint(emma, "af_bella"))

        val prepared = CueSet("a tree", NEURAL_ENGINE, "af_bella", emptyList(), cueFingerprint(neural, "af_bella"))
        assertNull(cueSetProblem(prepared, neural))
        assertTrue(cueSetProblem(prepared, emma)!!.startsWith("The cue words, voice,"))
        assertTrue(cueSetProblem(prepared, system) != null)
        // Kokoro gets the speaking speed as it is (1 = normal in both engines), so a speed change still counts.
        val slower = neural.copy(audio = neural.audio.copy(ttsSpeechRate = 0.6f))
        assertTrue(cueSetProblem(prepared, slower) != null)
        // A system-voice fallback for a NEURAL config is ready until the speaker or the engine changes.
        val fallback = prepared.copy(engine = "com.google.android.tts", voice = "en-us-x-voice",
            fingerprint = cueFingerprint(neural, "en-us-x-voice"))
        assertNull(cueSetProblem(fallback, neural))
        assertTrue(cueSetProblem(fallback, emma) != null)
    }
}
