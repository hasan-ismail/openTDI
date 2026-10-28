package com.opentdi.shared.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class RemLikelyTest {
    private val minutes = 360
    private val onset = 10
    private val asleep = List<Boolean?>(minutes) { m -> if (m == minutes - 1) null else m >= onset }

    /** 60 ± 0.5 bpm, but 66 ± 3 bpm in [raised] minutes; skin conductance flat at 1 μS but for a rise at [peaks]. */
    private fun night(raised: Set<Int>, peaks: Set<Int> = emptySet()): Seconds {
        val size = minutes * 60
        val hr = FloatArray(size) { s ->
            if (s / 60 in raised) 66f + ((s / 7) % 3 - 1) * 3f else 60f + (s % 3 - 1) * 0.5f
        }
        return Seconds(
            startMs = 0,
            endMs = size * 1000L,
            hr = hr,
            stillness = FloatArray(size) { 1f },
            angle = null,
            detached = BooleanArray(size),
            hrGood = hr,
            eda = FloatArray(size) { if (it in peaks) 1.05f else 1f },
        )
    }

    private fun likely(rem: List<Boolean?>) = rem.indices.filter { rem[it] == true }

    @Test
    fun raisedVariableHeartRateWhileAsleepAfterTheFirstHour() {
        // Minutes 30–49 look the same but come before onset + 60 min.
        val rem = remLikely(night((30..49).toSet() + (150..169)), asleep, onset)
        assertEquals((150..169).toList(), likely(rem))
        assertEquals(false, rem[0]) // awake stays not REM-likely
        assertEquals(null, rem[minutes - 1]) // and no estimate stays none
    }

    @Test
    fun anEdaStormRulesMinutesOutAndShortRunsAreDropped() {
        // One peak in each 30-s epoch of minutes 153–156: a storm; 150–152 is then too short to keep.
        val peaks = (2 * 153..2 * 156 + 1).map { 30 * it + 10 }.toSet()
        val rem = remLikely(night((150..169).toSet(), peaks), asleep, onset)
        assertEquals((157..169).toList(), likely(rem))
    }

    @Test
    fun steadyHeartRateIsNeverRemLikely() {
        assertEquals(emptyList<Int>(), likely(remLikely(night(emptySet()), asleep, onset)))
    }
}
