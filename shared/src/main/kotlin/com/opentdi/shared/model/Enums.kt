package com.opentdi.shared.model

import kotlinx.serialization.Serializable

// Wire enums of docs/ARCHITECTURE_AND_THRESHOLDS.md §9.1. Renaming a value changes the wire format.

@Serializable
enum class TdiState {
    IDLE, CALIBRATING_BASELINE, AWAKE_INCUBATING, HYPNAGOGIA_DETECTED,
    WAKING_USER, RECORDING_DREAM, RE_INCUBATING, PAUSED, SESSION_COMPLETE,
}

@Serializable
enum class SessionMode { DETECTION, TIMED_CUES }

@Serializable
enum class PromptCue { INITIAL_INCUBATION, WAKE_PROMPT, SLEEP_STATE_QUESTION, RE_INCUBATION, SESSION_END, MORNING_WAKE }

@Serializable
enum class AudioRouteMode {
    NIGHTSTAND_SPEAKER,
    BLUETOOTH_SLEEP_AUDIO,
    USB_WIRED_DAC, // deferred, disabled in the UI
    WATCH_WRIST_SPEAKER, // the phone sends each cue to the watch, which plays it (WristCue)
}

/** `MIT_STANDARD` keeps its name for the wire format; the UI label is "Standard" (no value in it is MIT's). */
@Serializable
enum class SensitivityPreset { LOW, MIT_STANDARD, HIGH }

@Serializable
enum class SensorSource { SAMSUNG_SDK, ANDROID_SENSORS, SIMULATION }

@Serializable
enum class SimulationMode { FAST_HYPNAGOGIA, REALTIME_HYPNAGOGIA, AWAKE_CONTROL, STOP }

@Serializable
enum class CycleStageType { DWELL_START, WAKING_USER, RECORDING_DREAM, RE_INCUBATING, CYCLE_COMPLETE }

@Serializable
enum class CycleOutcome { COMPLETED, ABORTED_AUDIO }

@Serializable
enum class CountdownAbortReason { MAJOR_MOVEMENT }

@Serializable
enum class DeviceRole { WATCH, PHONE }

@Serializable
enum class SessionEndReason { MAX_CYCLES, TIME_LIMIT, USER, SENSOR_FAILURE, ERROR }

@Serializable
enum class StateReason {
    SESSION_START, BASELINE_READY, BASELINE_EXTENDED,
    BASELINE_INSUFFICIENT_MOTION, BASELINE_INSUFFICIENT_HR, ONSET, TIMED_DWELL, MAJOR_MOVEMENT, MISSED_CYCLE,
    PHONE_REJECTED, PHONE_STAGE, CYCLE_COMPLETED, CYCLE_ABORTED, STAGE_TIMEOUT, HR_LOST, ACCEL_LOST, NO_SENSORS,
    PAUSED, RESUMED, MAX_CYCLES, TIME_LIMIT, USER_ABORT, FALLBACK_TO_TIMED,
}

@Serializable
enum class SensorStatus {
    CONNECTED_SAMSUNG, FALLBACK_ANDROID_SENSORS, SIMULATION, AAR_NOT_BUNDLED,
    HSS_NOT_INSTALLED, HSS_TOO_OLD, SDK_POLICY_ERROR, PERMISSION_MISSING, TRACKER_UNSUPPORTED, CONNECTION_FAILED,
    NO_SENSORS,
}

@Serializable
enum class GravityMode { UNKNOWN, WITH_GRAVITY, LINEAR_ONLY, AMBIGUOUS }

@Serializable
enum class SleepStateAnswer { AWAKE, HALFWAY, ASLEEP, UNKNOWN }

@Serializable
enum class CarrierMode { OFF, NOISE, ZERO }

@Serializable
enum class FocusMode { GAIN_TRANSIENT, GAIN_TRANSIENT_MAY_DUCK }

@Serializable
enum class RouteLossPolicy { PAUSE_CUES, SPEAKER_FALLBACK }

@Serializable
enum class EndAlarm { CHIME, NONE }

/** What renders the cues at Prepare cues (§7.3): Android's TextToSpeech, or the on-device Kokoro model. */
@Serializable
enum class TtsEngine { SYSTEM, NEURAL }

/** Watch alarm sounds (AlarmTones); VIBRATE_ONLY plays none; SPOKEN is the MORNING_WAKE cue the phone sent. */
@Serializable
enum class AlarmTone { MELODY, GENTLE_CHIMES, RISING_TONE, CLASSIC_BEEPS, VIBRATE_ONLY, SPOKEN }

/** Late-night lucidity cues after WBTB; EXPERIMENT cues one night of each pair, picked at random, unseen (blinded). */
@Serializable
enum class LateCueMode { OFF, ON, EXPERIMENT }

/** Where the watch plays sound: its speaker, or earbuds paired with the watch (speaker when they are not connected). */
@Serializable
enum class WatchOutput { SPEAKER, EARBUDS }

/** The Kokoro v0.19 voices in voices.bin order, so a name's index is its speaker id; af_ and bf_ are female. */
val NEURAL_SPEAKERS = listOf(
    "af", "af_bella", "af_nicole", "af_sarah", "af_sky", "am_adam", "am_michael",
    "bf_emma", "bf_isabella", "bm_george", "bm_lewis",
)

@Serializable
enum class PriorHrSource { NONE, USER_ENTERED, PREVIOUS_SESSION }

/** A PPG_CONTINUOUS LED channel; the names are those of Samsung's `PpgType` constants. */
@Serializable
enum class PpgChannel { GREEN, IR, RED }
