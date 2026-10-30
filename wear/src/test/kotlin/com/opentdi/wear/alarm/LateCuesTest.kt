package com.opentdi.wear.alarm

import com.opentdi.shared.config.StructuralConstants.RE_ONSET_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LateCuesTest {
    private val min = 60_000L
    private val buzz = 100 * min

    @Test
    fun reOnset_isTheStartOfTheFirstTenMinuteStillStretchFromTheBuzz() {
        assertNull(reOnsetAt(buzz, stillSinceMs = null, nowMs = buzz + 30 * min)) // still moving
        assertNull(reOnsetAt(buzz, buzz + 5 * min, buzz + 14 * min)) // still for 9 min only
        assertEquals(buzz + 5 * min, reOnsetAt(buzz, buzz + 5 * min, buzz + 15 * min)) // latency 5 min
    }

    @Test
    fun reOnset_stillnessBeforeTheBuzzCountsFromTheBuzz() {
        assertNull(reOnsetAt(buzz, buzz - 20 * min, buzz + 9 * min))
        assertEquals(buzz, reOnsetAt(buzz, buzz - 20 * min, buzz + RE_ONSET_MS)) // latency 0
    }

    @Test
    fun guide_lyingStillThroughIt_isNotFallingAsleep_theCuesArmAtItsEnd() {
        val end = buzz + 21 * min // "I'm in bed" 2 min after the buzz, then the 19-min guide
        val still = buzz + 2 * min // still from its first word on
        assertNull(reOnsetAt(end, still, end)) // 19 still minutes, none of them counted
        assertNull(reOnsetAt(end, still, end + 9 * min))
        assertEquals(end, reOnsetAt(end, still, end + RE_ONSET_MS)) // latency 0 from the guide's end
    }

    @Test
    fun guide_stoppedMidway_armsAtOnce() {
        var armedAt: Long? = null
        val guide = GuideRoutine(guideSteps({ 20_000L }, 650, 0.15f), {}, {}, { _, _, _, _ -> }) { armedAt = it }
        guide.start(buzz + 2 * min, null, null)
        guide.tick(buzz + 7 * min, null, null)
        assertNull(armedAt) // not while it plays
        guide.stop(buzz + 7 * min + 400, null, null)
        val at = armedAt!!
        assertEquals(buzz + 7 * min + 400, at)
        assertEquals(at, reOnsetAt(at, buzz + 3 * min, at + RE_ONSET_MS)) // still since before: counted from the stop
    }

    @Test
    fun movement_countsEveryPacketOfAScreenOffBurst() {
        // A burst of five seconds ending in a twitch: one moving second, no major movement.
        val twitch = ArrayDeque<Boolean>()
        assertFalse(listOf(0.9f, 0.9f, 0.9f, 0.9f, 0.1f).map { twitch.addSecond(it) }.any { it })
        // Rolling over for three seconds, then still: a major movement, though the burst's newest second is still.
        val rollOver = ArrayDeque<Boolean>()
        assertTrue(listOf(0.1f, 0.1f, 0.1f, 0.9f, 0.9f).map { rollOver.addSecond(it) }.any { it })
    }

    @Test
    fun withoutTheGuide_theBuzzArmsAsBefore() {
        // Guide off, or its clips not on the watch: armed at the buzz, so stillness from then on is falling asleep.
        assertNull(reOnsetAt(buzz, buzz + 2 * min, buzz + 11 * min))
        assertEquals(buzz + 2 * min, reOnsetAt(buzz, buzz + 2 * min, buzz + 12 * min))
    }
}
