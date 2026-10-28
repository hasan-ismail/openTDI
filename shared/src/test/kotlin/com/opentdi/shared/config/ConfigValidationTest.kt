package com.opentdi.shared.config

import com.opentdi.shared.audio.targetGain
import com.opentdi.shared.config.StructuralConstants.nEdaMin
import com.opentdi.shared.config.StructuralConstants.nHrMin
import com.opentdi.shared.config.StructuralConstants.nStillMin
import com.opentdi.shared.model.AudioConfig
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.model.DetectionConfig
import com.opentdi.shared.model.LinkConfig
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.PpgChannel
import com.opentdi.shared.model.RecordingConfig
import com.opentdi.shared.model.SensitivityPreset
import com.opentdi.shared.model.SensorsConfig
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SimulationMode
import com.opentdi.shared.model.TimedCueConfig
import com.opentdi.shared.model.TtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidationTest {
    private fun assertRejected(config: SessionConfig, field: String) {
        val errors = config.validate()
        assertTrue("expected an error naming $field, got $errors", errors.any { it.startsWith("$field ") })
    }

    private fun assertValid(config: SessionConfig) = assertEquals("$config", emptyList<String>(), config.validate())

    private fun detection(d: DetectionConfig) = SessionConfig(detection = d)

    @Test
    fun defaults_valid() {
        assertValid(SessionConfig())
        assertEquals(emptyList<String>(), SessionConfig().warnings())
    }

    @Test
    fun outOfRange_rejectedWithFieldNames() {
        val d = DetectionConfig()
        mapOf(
            SessionConfig(theme = "") to "theme",
            SessionConfig(theme = "x".repeat(61)) to "theme",
            SessionConfig(maxCycles = 0) to "maxCycles",
            SessionConfig(maxCycles = 11) to "maxCycles",
            SessionConfig(sessionMaxMin = 14) to "sessionMaxMin",
            SessionConfig(sessionMaxMin = 181) to "sessionMaxMin",
            SessionConfig(countdownS = 45) to "countdownS",
            SessionConfig(countdownS = 315) to "countdownS",
            SessionConfig(countdownS = 65) to "countdownS",
            detection(d.copy(baselineWindowS = 30)) to "detection.baselineWindowS",
            detection(d.copy(baselineWindowS = 75)) to "detection.baselineWindowS",
            detection(d.copy(hrDropFraction = 0.03f)) to "detection.hrDropFraction",
            detection(d.copy(hrDropFraction = 0.13f)) to "detection.hrDropFraction",
            detection(d.copy(hrDropFraction = Float.NaN)) to "detection.hrDropFraction",
            detection(d.copy(rmssdRiseFraction = 0.6f)) to "detection.rmssdRiseFraction",
            detection(d.copy(stillThreshold = 0.5f)) to "detection.stillThreshold",
            detection(d.copy(holdS = 4)) to "detection.holdS",
            detection(d.copy(holdS = 31)) to "detection.holdS",
            detection(d.copy(holdShortS = 2)) to "detection.holdShortS",
            detection(d.copy(hrMaWindowS = 4)) to "detection.hrMaWindowS",
            detection(d.copy(stillWindowS = 121)) to "detection.stillWindowS",
            detection(d.copy(edaSlopeWindowS = 14)) to "detection.edaSlopeWindowS",
            detection(d.copy(bonusLookbackS = 301)) to "detection.bonusLookbackS",
            detection(d.copy(refractoryS = -1)) to "detection.refractoryS",
            detection(d.copy(missedCycleGraceS = 4)) to "detection.missedCycleGraceS",
            detection(d.copy(cycleStageTimeoutS = 601)) to "detection.cycleStageTimeoutS",
            detection(d.copy(hrLostS = 59)) to "detection.hrLostS",
            detection(d.copy(accelLostS = 301)) to "detection.accelLostS",
            detection(d.copy(hrHoldMaxS = 0)) to "detection.hrHoldMaxS",
            detection(d.copy(rmssdWindowS = 29)) to "detection.rmssdWindowS",
            detection(d.copy(priorRestingHrBpm = 34f)) to "detection.priorRestingHrBpm",
            detection(d.copy(priorHrWeight = 0.6f)) to "detection.priorHrWeight",
            SessionConfig(timed = TimedCueConfig(firstSleepMinMin = 0)) to "timed.firstSleepMinMin",
            SessionConfig(timed = TimedCueConfig(firstSleepMaxMin = 91)) to "timed.firstSleepMaxMin",
            SessionConfig(timed = TimedCueConfig(firstSleepMinMin = 20)) to "timed.firstSleepMaxMin", // above max 15
            SessionConfig(timed = TimedCueConfig(betweenSleepS = 59)) to "timed.betweenSleepS",
            SessionConfig(timed = TimedCueConfig(phase1ReportWindowS = 91)) to "timed.phase1ReportWindowS",
            SessionConfig(timed = TimedCueConfig(watchLinkTimeoutS = 29)) to "timed.watchLinkTimeoutS",
            SessionConfig(audio = AudioConfig(userGain = 1.1f)) to "audio.userGain",
            SessionConfig(audio = AudioConfig(userGain = -0.1f)) to "audio.userGain",
            SessionConfig(audio = AudioConfig(fadeInMs = 5001)) to "audio.fadeInMs",
            SessionConfig(audio = AudioConfig(carrierLevelDbfs = -95)) to "audio.carrierLevelDbfs",
            SessionConfig(audio = AudioConfig(carrierLevelDbfs = -77)) to "audio.carrierLevelDbfs",
            SessionConfig(audio = AudioConfig(carrierLeadS = 61)) to "audio.carrierLeadS",
            SessionConfig(audio = AudioConfig(endChimeCount = 0)) to "audio.endChimeCount",
            SessionConfig(audio = AudioConfig(ttsSpeechRate = 0.45f)) to "audio.ttsSpeechRate",
            SessionConfig(audio = AudioConfig(ttsSpeechRate = 1.25f)) to "audio.ttsSpeechRate",
            SessionConfig(audio = AudioConfig(phrasePauseMs = -1)) to "audio.phrasePauseMs",
            SessionConfig(audio = AudioConfig(phrasePauseMs = 2001)) to "audio.phrasePauseMs",
            SessionConfig(audio = AudioConfig(cueTexts = mapOf(PromptCue.WAKE_PROMPT to "x".repeat(301)))) to
                "audio.cueTexts.WAKE_PROMPT",
            SessionConfig(audio = AudioConfig(neuralSpeaker = "heart")) to "audio.neuralSpeaker",
            SessionConfig(recording = RecordingConfig(marginDb = 2f)) to "recording.marginDb",
            SessionConfig(recording = RecordingConfig(hangoverMs = 999)) to "recording.hangoverMs",
            SessionConfig(recording = RecordingConfig(noSpeechTimeoutMs = 60_001)) to "recording.noSpeechTimeoutMs",
            SessionConfig(recording = RecordingConfig(maxMs = 9_999)) to "recording.maxMs",
            SessionConfig(recording = RecordingConfig(seedMs = 501)) to "recording.seedMs",
            SessionConfig(link = LinkConfig(telemetryFlushS = 0)) to "link.telemetryFlushS",
            SessionConfig(link = LinkConfig(sdkFlushS = 31)) to "link.sdkFlushS",
            SessionConfig(sensors = SensorsConfig(ppgChannels = emptySet())) to "sensors.ppgChannels",
            SessionConfig(sensors = SensorsConfig(spo2IntervalMin = 4)) to "sensors.spo2IntervalMin",
            SessionConfig(sensors = SensorsConfig(spo2IntervalMin = 121)) to "sensors.spo2IntervalMin",
            SessionConfig(sensors = SensorsConfig(spo2TimeoutS = 29)) to "sensors.spo2TimeoutS",
            SessionConfig(sensors = SensorsConfig(spo2TimeoutS = 121)) to "sensors.spo2TimeoutS",
        ).forEach { (config, field) -> assertRejected(config, field) }
    }

    @Test
    fun sensors_boundariesAndChannels() {
        assertValid(SessionConfig(sensors = SensorsConfig(spo2IntervalMin = 5, spo2TimeoutS = 30)))
        assertValid(SessionConfig(sensors = SensorsConfig(spo2IntervalMin = 120, spo2TimeoutS = 120)))
        assertValid(SessionConfig(sensors = SensorsConfig(ppgChannels = PpgChannel.entries.toSet())))
        assertValid(SessionConfig(sensors = SensorsConfig(ppgRawEnabled = false, ppgChannels = emptySet())))
    }

    @Test
    fun boundaryValues_accepted() {
        assertValid(SessionConfig(theme = "x".repeat(60), maxCycles = 10, sessionMaxMin = 180, countdownS = 300))
        assertValid(SessionConfig(maxCycles = 1, countdownS = 60))
        assertValid(SessionConfig(detection = DetectionConfig(baselineWindowS = 600), sessionMaxMin = 30))
        assertValid(detection(DetectionConfig(hrDropFraction = 0.04f, rmssdRiseFraction = 0.5f, stillThreshold = 0.6f)))
        assertValid(detection(DetectionConfig(holdS = 30, holdShortS = 30, stillWindowS = 30)))
        assertValid(SessionConfig(timed = TimedCueConfig(firstSleepMinMin = 60, firstSleepMaxMin = 90)))
        assertValid(SessionConfig(audio = AudioConfig(userGain = 0f, fadeInMs = 0, carrierLevelDbfs = -50)))
        assertValid(SessionConfig(audio = AudioConfig(ttsSpeechRate = 0.5f, phrasePauseMs = 0)))
        assertValid(SessionConfig(audio = AudioConfig(ttsEngine = TtsEngine.NEURAL, neuralSpeaker = "bf_emma")))
        assertValid(
            SessionConfig(
                audio = AudioConfig(
                    ttsSpeechRate = 1.2f,
                    phrasePauseMs = 2000,
                    cueTexts = mapOf(PromptCue.INITIAL_INCUBATION to "x".repeat(300), PromptCue.SESSION_END to ""),
                ),
            ),
        )
    }

    @Test
    fun countdown15_onlyWithFastSimulation() {
        assertRejected(SessionConfig(countdownS = 15), "countdownS")
        assertRejected(SessionConfig(countdownS = 15, simulation = SimulationMode.REALTIME_HYPNAGOGIA), "countdownS")
        assertValid(SessionConfig(countdownS = 15, simulation = SimulationMode.FAST_HYPNAGOGIA))
    }

    @Test
    fun crossRules_rejected() {
        // HIGH's holdS 15 does not fit a 10 s stillness window, nor does a 25 s override fit 20 s.
        val high = SensitivityPreset.HIGH
        assertRejected(detection(DetectionConfig(sensitivity = high, stillWindowS = 10)), "detection.holdS")
        assertRejected(detection(DetectionConfig(holdS = 25, stillWindowS = 20)), "detection.holdS")
        // MIT_STANDARD's holdS is 20, so a holdShortS of 25 is too long; and its holdShortS 10 exceeds a holdS of 8.
        assertRejected(detection(DetectionConfig(holdShortS = 25)), "detection.holdShortS")
        assertRejected(detection(DetectionConfig(holdS = 8)), "detection.holdShortS")
        val longBaseline = DetectionConfig(baselineWindowS = 600)
        assertRejected(SessionConfig(detection = longBaseline, sessionMaxMin = 15), "sessionMaxMin") // 900 < 1380 s
        val speaker = AudioRouteMode.NIGHTSTAND_SPEAKER
        val bluetooth = AudioRouteMode.BLUETOOTH_SLEEP_AUDIO
        assertRejected(
            SessionConfig(audio = AudioConfig(routeCapGain = mapOf(speaker to 1.5f))),
            "audio.routeCapGain.NIGHTSTAND_SPEAKER",
        )
        assertRejected(
            SessionConfig(audio = AudioConfig(routeCapGain = mapOf(speaker to 0.6f, bluetooth to -0.1f))),
            "audio.routeCapGain.BLUETOOTH_SLEEP_AUDIO",
        )
        val usb = AudioRouteMode.USB_WIRED_DAC
        assertRejected(SessionConfig(audio = AudioConfig(route = usb)), "audio.routeCapGain.USB_WIRED_DAC")
        assertRejected(
            detection(DetectionConfig(missedCycleGraceS = 100, cycleStageTimeoutS = 90)),
            "detection.missedCycleGraceS",
        )
    }

    @Test
    fun stageTimeout_mustCoverTheLongestRecording() {
        fun config(timeoutS: Int) = SessionConfig(
            recording = RecordingConfig(maxMs = 180_000),
            detection = DetectionConfig(cycleStageTimeoutS = timeoutS),
        )
        assertRejected(config(180), "detection.cycleStageTimeoutS")
        // 180 000 + 8000 + 60 000 ms needs at least 248 s.
        assertRejected(config(247), "detection.cycleStageTimeoutS")
        assertValid(config(248))
    }

    @Test
    fun userGainAboveRouteCap_acceptedClampedWithWarning() {
        val config = SessionConfig(audio = AudioConfig(route = AudioRouteMode.BLUETOOTH_SLEEP_AUDIO, userGain = 0.5f))
        assertValid(config)
        val warnings = config.warnings()
        assertEquals(1, warnings.size)
        assertTrue(warnings[0], warnings[0].startsWith("USER_GAIN_CLAMPED"))
        assertEquals(0.3f, config.audio.targetGain(), 0f)
    }

    @Test
    fun minCounts_withinEveryAllowedWindow() {
        for (w in 5..60) assertTrue("hrMaWindowS $w", nHrMin(w) in 1..w)
        for (w in 10..120) assertTrue("stillWindowS $w", nStillMin(w) in 1..w)
        for (w in 15..120) assertTrue("edaSlopeWindowS $w", nEdaMin(w) in 1..w)
        assertEquals(10, nHrMin(15))
        assertEquals(25, nStillMin(30))
        assertEquals(20, nEdaMin(30))
        assertEquals(4, nHrMin(5)) // ceil(3.33)
        assertEquals(9, nStillMin(10)) // ceil(8.33)
        assertEquals(10, nEdaMin(15))
    }

    @Test
    fun presets_matchTheTableAndTheConsistencyRules() {
        assertEquals(ResolvedThresholds(0.10f, 0.25f, 0.85f, 30, 15), SensitivityPreset.LOW.thresholds())
        assertEquals(ResolvedThresholds(0.07f, 0.15f, 0.80f, 20, 10), DetectionConfig().thresholds())
        assertEquals(ResolvedThresholds(0.05f, 0.10f, 0.75f, 15, 8), SensitivityPreset.HIGH.thresholds())
        for (preset in SensitivityPreset.entries) {
            val t = preset.thresholds()
            assertTrue("$preset θ_drop", t.dropFraction in 0.04f..0.12f)
            assertTrue("$preset θ_rmssd", t.rmssdRiseFraction in 0.05f..0.50f)
            assertTrue("$preset θ_still", t.stillThreshold in 0.60f..0.95f)
            assertTrue("$preset holdS", t.holdS in 5..30 && t.holdS <= DetectionConfig().stillWindowS)
            assertTrue("$preset holdShortS", t.holdShortS in 3..t.holdS)
            assertValid(detection(DetectionConfig(sensitivity = preset)))
        }
    }

    @Test
    fun overrides_beatThePreset() {
        val t = DetectionConfig(
            sensitivity = SensitivityPreset.LOW,
            hrDropFraction = 0.09f,
            stillThreshold = 0.7f,
            holdShortS = 12,
        ).thresholds()
        assertEquals(ResolvedThresholds(0.09f, 0.25f, 0.7f, 30, 12), t)
    }

    @Test
    fun configRows_flattenEveryField() {
        val rows = SessionConfig(theme = "a tree, at night").toConfigRows()
        val map = rows.toMap()
        assertEquals("keys are unique", rows.size, map.size)
        assertEquals("0", map["revision"])
        assertEquals("DETECTION", map["mode"])
        assertEquals("a tree, at night", map["theme"])
        assertEquals("180", map["detection.baselineWindowS"])
        assertEquals("MIT_STANDARD", map["detection.sensitivity"])
        assertEquals("", map["detection.hrDropFraction"]) // null = empty field
        assertEquals("0.3", map["detection.priorHrWeight"])
        assertEquals("0.003", map["accel.noiseFloorG"])
        assertEquals("1", map["accel.logRawAccel"])
        assertEquals("1", map["timed.autoFallbackToTimed"])
        assertEquals("0.6", map["audio.routeCapGain.NIGHTSTAND_SPEAKER"])
        assertEquals("0.3", map["audio.routeCapGain.BLUETOOTH_SLEEP_AUDIO"])
        assertEquals("", map["audio.carrierMode"])
        assertEquals("-75", map["audio.carrierLevelDbfs"])
        assertEquals("0.75", map["audio.ttsSpeechRate"])
        assertEquals("700", map["audio.phrasePauseMs"])
        assertEquals("SYSTEM", map["audio.ttsEngine"])
        assertEquals("af_bella", map["audio.neuralSpeaker"])
        assertEquals("", map["audio.cueTexts"]) // no overrides: one empty row
        assertEquals("en-US", map["recording.sttLanguage"])
        assertEquals("2", map["link.telemetryFlushS"])
        assertEquals("1", map["link.watchWakeLock"])
        assertEquals("1", map["sensors.skinTempEnabled"])
        assertEquals("GREEN", map["sensors.ppgChannels"])
        assertEquals("15", map["sensors.spo2IntervalMin"])
        assertEquals("60", map["sensors.spo2TimeoutS"])
        assertEquals("", map["simulation"])
        // One row per leaf: 7 top-level, 21 detection, 12 accel, 8 timed, 17 audio (2 caps), 12 recording, 3 link,
        // 6 sensors, 15 alarms; audio.ownVoice is one empty row, plus audio.watchOutput.
        assertEquals(103, rows.size)

        val words = "You are dreaming. You are in control."
        val custom = SessionConfig(audio = AudioConfig(cueTexts = mapOf(PromptCue.INITIAL_INCUBATION to words)))
            .toConfigRows().toMap()
        assertEquals(words, custom["audio.cueTexts.INITIAL_INCUBATION"])
        assertEquals(null, custom["audio.cueTexts"])
        val channels = SessionConfig(sensors = SensorsConfig(ppgChannels = setOf(PpgChannel.GREEN, PpgChannel.IR)))
        assertEquals("GREEN;IR", channels.toConfigRows().toMap()["sensors.ppgChannels"])
    }

    @Test
    fun resolvedAndConstRows() {
        assertEquals(
            listOf(
                "resolved.dropFraction" to "0.07",
                "resolved.rmssdRiseFraction" to "0.15",
                "resolved.stillThreshold" to "0.8",
                "resolved.holdS" to "20",
                "resolved.holdShortS" to "10",
            ),
            DetectionConfig().thresholds().toConfigRows(),
        )
        val constRows = StructuralConstants.configRows()
        assertTrue(constRows.all { it.first.startsWith("const.") && it.second.isNotEmpty() })
        val consts = constRows.toMap()
        assertEquals(constRows.size, consts.size)
        assertEquals("2/3", consts["const.N_HR_MIN_FRACTION"])
        assertEquals("1500", consts["const.HOLD_RESET_GAP_MS"])
        assertEquals("0;2000;4000;8000", consts["const.TRIGGER_ATTEMPT_OFFSETS_MS"])
        assertEquals("-3.0", consts["const.CUE_PEAK_DBFS"])
        assertEquals("-50.0", consts["const.PHRASE_SILENCE_DBFS"])
        assertEquals("120", consts["const.SKIN_TEMP_HOLD_S"])
    }
}
