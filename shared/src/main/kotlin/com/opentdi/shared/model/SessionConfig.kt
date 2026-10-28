package com.opentdi.shared.model

import kotlinx.serialization.Serializable

// Session configuration, docs/ARCHITECTURE_AND_THRESHOLDS.md §9.5. Ranges are in the comments and enforced by
// SessionConfig.validate(); every default is labelled SOURCED or GUESSED in the spec (§6, §8).

@Serializable
data class SessionConfig(
    val revision: Long = 0, // wall ms of the last edit; ConfigSync keeps the higher revision
    val mode: SessionMode = SessionMode.DETECTION,
    val theme: String = "lucid dreaming", // 1..60 chars; the default cue words are about lucidity (§7.1)
    val maxCycles: Int = 4, // 1..10
    val sessionMaxMin: Int = 180, // 15..180; the Lucid night preset
    val countdownS: Int = 180, // 60..300 step 15; also the TIMED dwell (15 only when simulation == FAST_HYPNAGOGIA)
    val detection: DetectionConfig = DetectionConfig(),
    val accel: AccelConfig = AccelConfig(),
    val timed: TimedCueConfig = TimedCueConfig(),
    val audio: AudioConfig = AudioConfig(),
    val recording: RecordingConfig = RecordingConfig(),
    val link: LinkConfig = LinkConfig(),
    val sensors: SensorsConfig = SensorsConfig(),
    val alarms: AlarmConfig = AlarmConfig(),
    val simulation: SimulationMode? = null, // set only by the simulation receiver
)

@Serializable
data class DetectionConfig(
    val baselineWindowS: Int = 180, // 60..600 step 30
    val sensitivity: SensitivityPreset = SensitivityPreset.MIT_STANDARD,
    val hrDropFraction: Float? = null, // 0.04..0.12; null = preset (§8.11)
    val rmssdRiseFraction: Float? = null, // 0.05..0.50; null = preset
    val stillThreshold: Float? = null, // 0.60..0.95; null = preset
    val holdS: Int? = null, // 5..30, <= stillWindowS; null = preset
    val holdShortS: Int? = null, // 3..holdS; null = preset
    val hrMaWindowS: Int = 15, // 5..60
    val stillWindowS: Int = 30, // 10..120, >= the resolved holdS
    val edaSlopeWindowS: Int = 30, // 15..120
    val bonusLookbackS: Int = 120, // 30..300
    val refractoryS: Int = 120, // 0..600
    val missedCycleGraceS: Int = 15, // 5..60
    val cycleStageTimeoutS: Int = 180, // 90..600
    val hrLostS: Int = 300, // 60..900
    val accelLostS: Int = 60, // 10..300
    val hrHoldMaxS: Int = 5, // 1..15 (watch aggregator)
    val rmssdWindowS: Int = 60, // 30..300 (watch aggregator)
    val priorHrSource: PriorHrSource = PriorHrSource.NONE,
    val priorRestingHrBpm: Float? = null, // 35..120
    val priorHrWeight: Float = 0.3f, // 0.0..0.5
)

/** Watch-side 25 Hz accelerometer processing (§8.1); all GUESSED. */
@Serializable
data class AccelConfig(
    val noiseFloorG: Float = 0.003f,
    val moveRmsG: Float = 0.05f,
    val moveStillness: Float = 0.5f,
    val gravityAlpha: Float = 0.04f,
    val jerkThresholdG: Float = 0.30f,
    val jerkMaxBurstMs: Int = 500,
    val jerkPreStillS: Int = 10,
    val jerkPostStillS: Int = 3,
    val limpDriftDeg: Float = 3.0f,
    val limpRefStillS: Int = 10,
    val limpRefMaxAgeS: Int = 60,
    val logRawAccel: Boolean = true, // writes w_accel_raw.csv for AccelConfig replay tuning
)

@Serializable
data class TimedCueConfig(
    val firstSleepMinMin: Int = 10, // 1..60
    val firstSleepMaxMin: Int = 15, // firstSleepMinMin..90
    val betweenSleepS: Int = 420, // 60..1200
    val phase1ReportWindowS: Int = 60, // 30..90 (Phase 1 only)
    val autoFallbackToTimed: Boolean = true,
    val watchLinkTimeoutS: Int = 120, // 30..600
    val waitForStillness: Boolean = true, // owner 2026-10-28: a loop also waits for the watch to show you still
    val stillForS: Int = 300, // 60..1800, GUESSED from the home TLR study's 5 min after movement stops
)

@Serializable
data class AudioConfig(
    val route: AudioRouteMode = AudioRouteMode.NIGHTSTAND_SPEAKER,
    val userGain: Float = 0.5f, // 0..1; playback clamps it to routeCapGain[route] (§7.7)
    val routeCapGain: Map<AudioRouteMode, Float> = mapOf(
        AudioRouteMode.NIGHTSTAND_SPEAKER to 0.6f,
        AudioRouteMode.BLUETOOTH_SLEEP_AUDIO to 0.3f,
    ), // 0..1, GUESSED
    val fadeInMs: Int = 2000, // 0..5000
    val carrierMode: CarrierMode? = null, // null = route default (§7.5)
    val carrierLevelDbfs: Int = -75, // -90..-50 step 5
    val carrierLeadS: Int = 10, // 0..60
    val focusMode: FocusMode = FocusMode.GAIN_TRANSIENT,
    val routeLossPolicy: RouteLossPolicy = RouteLossPolicy.PAUSE_CUES,
    val endAlarm: EndAlarm = EndAlarm.CHIME,
    val endChimeCount: Int = 3, // 1..3; "off" is endAlarm = NONE
    val ttsSpeechRate: Float = 0.75f, // 0.5..1.2; TextToSpeech.setSpeechRate when the cues are prepared, 1 = normal
    val phrasePauseMs: Int = 700, // 0..2000; silence between the phrases of a cue
    val cueTexts: Map<PromptCue, String> = emptyMap(), // the user's words per cue, <= 300 chars; missing or blank = §7.1
    val ttsEngine: TtsEngine = TtsEngine.SYSTEM, // NEURAL falls back to SYSTEM while the model is not on the phone
    val neuralSpeaker: String = "af_bella", // one of NEURAL_SPEAKERS; used by NEURAL only
    val ownVoice: Map<PromptCue, String> = emptyMap(), // cue → SHA-256 of the user's own recording, used instead of TTS
    val watchOutput: WatchOutput = WatchOutput.SPEAKER, // the watch's sound: speaker, or earbuds paired with the watch
)

@Serializable
data class RecordingConfig(
    val marginDb: Float = 10f, // 3..20
    val hangoverMs: Int = 4000, // 1000..10000
    val noSpeechTimeoutMs: Int = 15000, // 5000..60000
    val maxMs: Int = 45000, // 10000..180000
    val minSpeechFrames: Int = 5,
    val seedMs: Int = 400, // 300..500
    val askSleepState: Boolean = true,
    val answerMaxMs: Int = 8000,
    val answerHangoverMs: Int = 1500,
    val answerNoSpeechMs: Int = 6000,
    val sttEnabled: Boolean = true,
    val sttLanguage: String = "en-US",
)

@Serializable
data class LinkConfig(
    val telemetryFlushS: Int = 2, // 1..30
    val sdkFlushS: Int = 5, // 1..30
    val watchWakeLock: Boolean = true,
)

/** Samsung trackers that are only logged, never used for detection (watch, Samsung SDK path only). */
@Serializable
data class SensorsConfig(
    val skinTempEnabled: Boolean = true, // SKIN_TEMPERATURE_CONTINUOUS → w_skin_temp.csv
    val ppgRawEnabled: Boolean = true, // PPG_CONTINUOUS → w_ppg_raw.csv (raw signal for a later breathing estimate)
    val ppgChannels: Set<PpgChannel> = setOf(PpgChannel.GREEN), // not empty while ppgRawEnabled
    val spo2SpotChecks: Boolean = true, // SPO2_ON_DEMAND spot checks → w_spo2.csv
    val spo2IntervalMin: Int = 15, // 5..120
    val spo2TimeoutS: Int = 60, // 30..120
)

/**
 * Watch alarms (owner, 2026-10-28). They ring on the watch, which records all night beside the sleeper, while the
 * phone may be in another room. Times are minutes of the local day; the next occurrence after recording starts rings.
 */
@Serializable
data class AlarmConfig(
    val wakeEnabled: Boolean = false,
    val wakeAtMin: Int = 420, // 0..1439 (07:00)
    val wakeTone: AlarmTone = AlarmTone.MELODY,
    val wbtbEnabled: Boolean = false,
    val wbtbAtMin: Int = 330, // 0..1439 (05:30): about 6 h after a 23:30 sleep onset (lucid_induction.md §5)
    val wbtbTone: AlarmTone = AlarmTone.VIBRATE_ONLY, // rarely wakes anyone else; adds sound after 2 min
    val wbtbAwakeMin: Int = 30, // 5..60: 30+ min awake predicted success (LaBerge 2018); then a buzz says sleep
    val rampS: Int = 30, // 0..300: the tone rises from quiet to the watch's alarm volume
    val maxRingMin: Int = 10, // 1..30: then it stops by itself
    val maxSnoozes: Int = 3, // 0..5 (0 = no snooze button), wake-up alarm only
    val snoozeMin: Int = 9, // 1..30
    val sleeperName: String = "", // <= 30 chars; the spoken wake-up says it (being called by name helps recall)
    val lateCues: LateCueMode = LateCueMode.OFF, // soft tone cues after WBTB; needs wbtbEnabled
    // 10..120 after falling back asleep (still-bout start); no sooner than ~40 (lucid_induction.md §5.2), REM ~42 [12]
    val lateCueAfterOnsetMin: Int = 40,
    val lateCueVolume: Float = 0.15f, // 0.02..1.0 of the watch's media volume: "notice it, not wake to it"
)
