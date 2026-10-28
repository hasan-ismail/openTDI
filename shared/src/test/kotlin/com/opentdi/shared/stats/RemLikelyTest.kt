package com.opentdi.shared.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class RemLikelyTest {
    private val minutes = 360
    private val onset = 10
    private val asleep = List<Boolean?>(minutes) { m -> if (m == minutes - 1) null else m >= onset }

    /**
     * A night at 60 ± 0.5 bpm, RMSSD 50 ms, 34 °C and no twitches. [raised] minutes: 66 bpm, ± 3 when [variable], with
     * RMSSD 35 ms when [lowHrv]; [twitchy] minutes twitch every 10 s and [cool] ones are 0.3 °C cooler. Skin conductance
     * is flat at 1 μS but for a rise at [peaks].
     */
    private fun night(
        raised: Set<Int>,
        variable: Boolean = true,
        lowHrv: Boolean = true,
        twitchy: Set<Int> = emptySet(),
        cool: Set<Int> = emptySet(),
        peaks: Set<Int> = emptySet(),
    ): Seconds {
        val size = minutes * 60
        val hr = FloatArray(size) { s ->
            when {
                s / 60 !in raised -> 60f + (s % 3 - 1) * 0.5f
                variable -> 66f + ((s / 7) % 3 - 1) * 3f
                else -> 66f
            }
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
            rmssd = FloatArray(size) { if (lowHrv && it / 60 in raised) 35f else 50f },
            skinTemp = FloatArray(size) { if (it / 60 in cool) 33.7f else 34f },
            accelPeak = FloatArray(size) { if (it / 60 in twitchy && it % 10 == 0) 0.1f else 0.01f },
        )
    }

    private fun likely(rem: List<Boolean?>) = rem.indices.filter { rem[it] == true }

    @Test
    fun raisedHeartRateWithTwoMoreSignsAfterTheFirstHour() {
        // Minutes 30–49 look the same but come before onset + 60 min.
        val rem = remLikely(night((30..49).toSet() + (150..169)), asleep, onset)
        assertEquals((150..169).toList(), likely(rem))
        assertEquals(false, rem[0]) // awake stays not REM-likely
        assertEquals(null, rem[minutes - 1]) // and no estimate stays none
    }

    @Test
    fun twitchesAndACoolerWristCanBeTheTwoSigns() {
        val stretch = (150..169).toSet()
        val steady = night(stretch, variable = false, lowHrv = false)
        assertEquals(emptyList<Int>(), likely(remLikely(steady, asleep, onset))) // a raised heart rate alone is not enough
        val signs = night(stretch, variable = false, lowHrv = false, twitchy = stretch, cool = stretch)
        assertEquals((150..169).toList(), likely(remLikely(signs, asleep, onset)))
    }

    @Test
    fun anEdaStormRulesMinutesOutAndShortRunsAreDropped() {
        // One peak in each 30-s epoch of minutes 153–156: a storm; 150–152 is then too short to keep.
        val peaks = (2 * 153..2 * 156 + 1).map { 30 * it + 10 }.toSet()
        val rem = remLikely(night((150..169).toSet(), peaks = peaks), asleep, onset)
        assertEquals((157..169).toList(), likely(rem))
    }

    @Test
    fun steadyHeartRateIsNeverRemLikely() {
        assertEquals(emptyList<Int>(), likely(remLikely(night(emptySet()), asleep, onset)))
    }
}
