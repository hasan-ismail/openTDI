package com.opentdi.shared.audio

import com.opentdi.shared.config.StructuralConstants
import com.opentdi.shared.model.AudioConfig
import kotlin.math.abs
import kotlin.math.pow

/** No sample gain above unity, ever (§7.7 step 2). */
const val HARD_MAX_GAIN = 1.0f

/** G_target = min(userGain, routeCapGain, HARD_MAX_GAIN): the one gain rule used everywhere (§7.4, §7.7 step 3). */
fun targetGain(userGain: Float, routeCapGain: Float): Float =
    minOf(userGain, routeCapGain, HARD_MAX_GAIN).coerceAtLeast(0f)

/** [targetGain] on the selected route; a route without a calibrated cap plays silent (validate() rejects it). */
fun AudioConfig.targetGain(): Float = targetGain(userGain, routeCapGain[route] ?: 0f)

/** §7.7 step 5: the factor on G_target when the speaker volume (getStreamVolumeDb) is above its calibrated level. */
fun volumeRaisedCorrection(dbNow: Float, dbCal: Float): Float =
    if (dbNow > dbCal) 10f.pow(-(dbNow - dbCal) / 20f) else 1f

/** A copy of [samples] scaled to a peak of [peakDbfs] (§7.3: every cue at −3 dBFS); silence stays silent. */
fun normalisePeak(samples: FloatArray, peakDbfs: Float = StructuralConstants.CUE_PEAK_DBFS): FloatArray {
    val peak = samples.maxOfOrNull { abs(it) } ?: 0f
    if (peak == 0f) return samples.copyOf()
    val scale = 10f.pow(peakDbfs / 20f) / peak
    return FloatArray(samples.size) { samples[it] * scale }
}

// NaN becomes silence, never full scale.
internal fun clampSample(x: Float): Float = if (x.isNaN()) 0f else x.coerceIn(-1f, 1f)
