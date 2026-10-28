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
