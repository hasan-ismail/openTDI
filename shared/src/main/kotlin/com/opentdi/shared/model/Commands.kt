package com.opentdi.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/opentdi/state_command` (§9.4). The `type` discriminator values below are part of the wire format. */
@Serializable
sealed class TdiCommand {
    @Serializable
    @SerialName("start")
    data class StartSession( // both directions
        val sessionId: String,
        val config: SessionConfig,
        val origin: DeviceRole,
        val startedAtWallMs: Long,
        val protocolVersion: Int = PROTOCOL_VERSION,
    ) : TdiCommand()

    /** Phone → watch: asks for the watch's [WatchStatus]; every request on the path is answered with one. */
    @Serializable
    @SerialName("status")
    data class StatusRequest(val origin: DeviceRole) : TdiCommand()

    /** Phone → watch: plays [tone] on the watch for a few seconds, for choosing one in the settings. */
    @Serializable
    @SerialName("test_alarm")
    data class TestAlarm(val tone: AlarmTone) : TdiCommand()

    /** Phone → watch: plays the late-night lucidity cue once at [volume], for setting its loudness in bed. */
    @Serializable
    @SerialName("test_late_cue")
    data class TestLateCue(val volume: Float) : TdiCommand()

    @Serializable
    @SerialName("pause")
    data class PauseSession(val sessionId: String, val origin: DeviceRole) : TdiCommand()

    @Serializable
    @SerialName("resume")
    data class ResumeSession(val sessionId: String, val origin: DeviceRole) : TdiCommand()

    @Serializable
    @SerialName("abort")
    data class AbortSession(
        val sessionId: String,
        val origin: DeviceRole,
        val reason: SessionEndReason,
    ) : TdiCommand()

    @Serializable
    @SerialName("config")
    data class ConfigSync(val sessionId: String?, val config: SessionConfig) : TdiCommand() // sessionId null outside a session

    @Serializable
    @SerialName("abort_countdown")
    data class AbortCountdown( // watch → phone
        val sessionId: String,
        val cycle: Int,
        val reason: CountdownAbortReason,
        val atMs: Long,
    ) : TdiCommand()

    @Serializable
    @SerialName("cycle_stage")
    data class CycleStage( // phone → watch
        val sessionId: String,
        val cycle: Int,
        val stage: CycleStageType,
        val outcome: CycleOutcome = CycleOutcome.COMPLETED, // only meaningful with CYCLE_COMPLETE
        val atWallMs: Long,
    ) : TdiCommand()
}

/** The watch app: not running, waiting for the phone's Start session (sensors off), or recording. */
@Serializable
enum class WatchState { IDLE, WAITING, RECORDING }

/** The watch's answer to every `/opentdi/state_command` request (`MessageClient.sendRequest`). */
@Serializable
data class WatchStatus(
    val state: WatchState,
    val batteryPct: Int? = null,
    val samsungKit: String? = null, // "built in", "your kit", or null: basic sensors only
)
