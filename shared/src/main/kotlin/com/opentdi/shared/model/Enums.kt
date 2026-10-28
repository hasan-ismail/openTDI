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
enum class PromptCue { INITIAL_INCUBATION, WAKE_PROMPT, SLEEP_STATE_QUESTION, RE_INCUBATION, SESSION_END }

@Serializable
enum class AudioRouteMode {
    NIGHTSTAND_SPEAKER,
    BLUETOOTH_SLEEP_AUDIO,
    USB_WIRED_DAC, // deferred, disabled in the UI
    WATCH_WRIST_SPEAKER, // deferred, disabled in the UI
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

@Serializable
enum class PriorHrSource { NONE, USER_ENTERED, PREVIOUS_SESSION }
