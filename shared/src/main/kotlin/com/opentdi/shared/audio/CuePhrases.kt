package com.opentdi.shared.audio

import com.opentdi.shared.config.StructuralConstants
import kotlin.math.abs
import kotlin.math.pow

// A cue is rendered one phrase at a time (§7.3): each phrase trimmed, then joined with exact pauses.

/** [samples] without the leading and trailing samples below [thresholdDbfs]; empty when every sample is below it. */
fun trimSilence(samples: FloatArray, thresholdDbfs: Float = StructuralConstants.PHRASE_SILENCE_DBFS): FloatArray {
    val level = 10f.pow(thresholdDbfs / 20f)
    val first = samples.indexOfFirst { abs(it) >= level }
    if (first < 0) return FloatArray(0)
    return samples.copyOfRange(first, samples.indexOfLast { abs(it) >= level } + 1)
}

/** [phrases] in order after [leadMs] of digital silence, with exactly [pauseMs] between each two and none after. */
fun joinWithPauses(phrases: List<FloatArray>, sampleRate: Int, pauseMs: Int, leadMs: Int = 0): FloatArray {
    val pause = (sampleRate.toLong() * pauseMs / 1000).toInt()
    var at = (sampleRate.toLong() * leadMs / 1000).toInt()
    val out = FloatArray(at + phrases.sumOf { it.size } + pause * (phrases.size - 1).coerceAtLeast(0))
    for (phrase in phrases) {
        phrase.copyInto(out, at)
        at += phrase.size + pause
    }
    return out
}
