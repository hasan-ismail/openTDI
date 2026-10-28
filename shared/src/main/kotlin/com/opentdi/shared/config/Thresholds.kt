package com.opentdi.shared.config

import com.opentdi.shared.model.DetectionConfig
import com.opentdi.shared.model.SensitivityPreset

/** θ_drop, θ_rmssd, θ_still, holdS and holdShortS after the preset and the overrides are applied (§8.11). */
data class ResolvedThresholds(
    val dropFraction: Float,
    val rmssdRiseFraction: Float,
    val stillThreshold: Float,
    val holdS: Int,
    val holdShortS: Int,
)

/** The §8.11 preset table (all GUESSED). */
fun SensitivityPreset.thresholds(): ResolvedThresholds = when (this) {
    SensitivityPreset.LOW -> ResolvedThresholds(0.10f, 0.25f, 0.85f, holdS = 30, holdShortS = 15)
    SensitivityPreset.MIT_STANDARD -> ResolvedThresholds(0.07f, 0.15f, 0.80f, holdS = 20, holdShortS = 10)
    SensitivityPreset.HIGH -> ResolvedThresholds(0.05f, 0.10f, 0.75f, holdS = 15, holdShortS = 8)
}

/** The preset of [DetectionConfig.sensitivity] with every non-null override applied. */
fun DetectionConfig.thresholds(): ResolvedThresholds {
    val preset = sensitivity.thresholds()
    return ResolvedThresholds(
        dropFraction = hrDropFraction ?: preset.dropFraction,
        rmssdRiseFraction = rmssdRiseFraction ?: preset.rmssdRiseFraction,
        stillThreshold = stillThreshold ?: preset.stillThreshold,
        holdS = holdS ?: preset.holdS,
        holdShortS = holdShortS ?: preset.holdShortS,
    )
}
