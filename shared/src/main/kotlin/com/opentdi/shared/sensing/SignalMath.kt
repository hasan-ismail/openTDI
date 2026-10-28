package com.opentdi.shared.sensing

/** Start of the 1 s bin that holds [timestampMs] (floored to 1000 ms). */
internal fun binStartMs(timestampMs: Long): Long = timestampMs.floorDiv(1000L) * 1000L

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
