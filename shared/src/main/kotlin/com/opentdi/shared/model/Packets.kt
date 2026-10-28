package com.opentdi.shared.model

import kotlinx.serialization.Serializable

// Telemetry and engine-output contracts, docs/ARCHITECTURE_AND_THRESHOLDS.md §9.2–9.3. All timestamps are epoch ms.

/** One second of watch sensing (§8.2). No raw accelerometer data ever goes over the wire. */
@Serializable
data class BiosignalPacket(
    val timestamp: Long, // start of the 1 s bin (watch clock)
    val hr: Float?, // bpm; sample-and-hold ≤ hrHoldMaxS; null = no valid HR
    val ibiList: List<Int>, // ms, IBIs accepted in this bin (may be empty)
    val rmssd: Float?, // ms, rolling window; null with < 20 pairs
    val skinConductance: Float?, // µS, EdaSet.SKIN_CONDUCTANCE with STATUS == 0
    val accelRms: Float?, // g, RMS of the gravity-removed dynamic magnitude over the second
    val edaStatus: Int?, // raw EdaSet.STATUS (0, -5, -10)
    val hrStatus: Int?, // raw HEART_RATE_STATUS (1, 0, -2, -3, -8, -10, -999)
    val accelMean: Float?, // g, mean |a| over the second (≈1 with gravity in the data, ≈0 without)
    val accelPeak: Float?, // g, max dynamic magnitude in the second
    val jerkEvent: Boolean, // a hypnic jerk was confirmed in this second (§8.1)
    val limpEvent: Boolean, // a limp-wrist drift was confirmed in this second (§8.1)
    val stillnessScore: Float?, // 0..1; null with < 20 accel samples
    val source: SensorSource,
)

/** Gate status plus the detection confidence, emitted every frame (§8.9). */
@Serializable
data class DetectionScore(
    val confidence: Float,
    val detectionActive: Boolean,
    val stillMean: Float?,
    val movingSeconds: Int?,
    val gate: Boolean,
    val hrMa: Float?,
    val hrDropFraction: Float?,
    val hrSignal: Boolean,
    val rmssdRiseFraction: Float?,
    val rmssdSignal: Boolean,
    val edaSlopeUsPerS: Float?,
    val edaBonus: Boolean,
    val jerkBonus: Boolean,
    val limpBonus: Boolean,
    val holdS: Int,
    val holdRequiredS: Int,
    val refractory: Boolean,
)

@Serializable
data class BaselineStats(
    val startMs: Long,
    val endMs: Long,
    val windowS: Int, // W, or 2W after an extension
    val framesTotal: Int,
    val framesMotionDiscarded: Int,
    val hrMeanLive: Float,
    val hrMean: Float, // μ_HR,eff (§8.5)
    val hrSd: Float,
    val hrN: Int,
    val priorRestingHrBpm: Float?,
    val priorHrWeightApplied: Float, // 0 if no prior or the prior was ignored
    val rmssdMean: Float?,
    val rmssdSd: Float?,
    val rmssdN: Int,
    val edaMean: Float?,
    val edaSd: Float?,
    val edaN: Int,
    val accelNoiseFloorP95G: Float?,
)

/** `/opentdi/hypnagogia_trigger`, watch → phone. */
@Serializable
data class HypnagogiaTrigger(
    val sessionId: String,
    val cycle: Int, // 1-based number of the cycle this onset starts
    val onsetTimestampMs: Long, // packet time of the trigger frame
    val countdownMs: Long,
    val confidence: Float,
    val evidence: DetectionScore,
    val baseline: BaselineStats,
    val preset: SensitivityPreset,
    val source: SensorSource,
    val sentAtWallMs: Long, // watch wall clock, latency log only
)

@Serializable
data class TelemetryFrame(
    val seq: Long, // per-session frame counter (gap detection)
    val packet: BiosignalPacket,
    val state: TdiState,
    val stateReason: StateReason?, // set on the frame where the state changed
    val completedCycles: Int,
    val score: DetectionScore?, // null in IDLE / CALIBRATING_BASELINE
)

/** `/opentdi/sensor_stream`, watch → phone, one batch per telemetry flush period. */
@Serializable
data class TelemetryBatch(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val sessionId: String,
    val batchSeq: Long,
    val sensorStatus: SensorStatus,
    val gravityMode: GravityMode,
    val frames: List<TelemetryFrame>,
    val sentAtWallMs: Long,
)

/** Reply to every `sendRequest`. */
@Serializable
data class Ack(val ok: Boolean, val code: String = "OK")
