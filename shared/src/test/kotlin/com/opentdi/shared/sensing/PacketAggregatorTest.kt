package com.opentdi.shared.sensing

import com.opentdi.shared.model.BiosignalPacket
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorsConfig
import com.opentdi.shared.model.SessionConfig
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T = 1_761_000_000_000L // a second boundary

class PacketAggregatorTest {
    private fun aggregator(vararg trackers: Tracker) =
        PacketAggregator(SessionConfig(), SensorSource.SAMSUNG_SDK, trackers.toSet())

    private fun hr(ts: Long, bpm: Int = 60, status: Int = 1, ibis: List<Int>? = null) =
        HrDataPoint(ts, bpm, status, ibis, ibis?.map { 0 })

    private fun stillSecond(start: Long) = AccelSecond(start, 25, 1f, 0f, 0f, 1f, null, null, false, false)

    /**
     * One single-point HR batch per second (screen on), beat k at T + k s + 500 ms (null: a point without IBIs);
     * returns one packet per entry.
     */
    private fun packetsForBeats(ibis: List<Int?>): List<BiosignalPacket> {
        val agg = aggregator(Tracker.HR)
        ibis.forEachIndexed { k, ibi ->
            val ts = T + k * 1000L + 500
            agg.addHrBatch(listOf(hr(ts, ibis = ibi?.let(::listOf))), ts + 50)
        }
        val closing = T + ibis.size * 1000L + 500 // completes the last beat's bin
        agg.addHrBatch(listOf(hr(closing)), closing + 50)
        return agg.poll(closing + 50).map { it.packet }
    }

    @Test
    fun hr_invalidStatus_holds5s_thenNull() {
        val agg = aggregator(Tracker.HR)
        val points = listOf(hr(T + 200, bpm = 70)) + (1..7).map { hr(T + it * 1000L + 200, bpm = 0, status = -8) }
        agg.addHrBatch(points, T + 7300)
        val packets = agg.poll(T + 7300).map { it.packet }
        assertEquals((0..6).map { T + it * 1000L }, packets.map { it.timestamp })
        assertEquals(listOf(70f, 70f, 70f, 70f, 70f, 70f, null), packets.map { it.hr })
        assertEquals(listOf(1, -8, -8, -8, -8, -8, -8), packets.map { it.hrStatus })
    }

    @Test
    fun ibi_batchInFirstPoint_timedBackwards() {
        val agg = aggregator(Tracker.HR)
        val ibis = listOf(810, 820, 830, 840, 850, 860)
        val batch = (0..4).map { k -> hr(T + 10_000 + k * 1000L, ibis = if (k == 0) ibis else null) }
        agg.addHrBatch(batch, T + 14_050)
        agg.addHrBatch(listOf(hr(T + 15_000)), T + 15_050)
        // From the last point (T + 14 000) backwards: 860 at 14 000, 850 at 13 140, 840 at 12 290, 830 at 11 450,
        // 820 at 10 620, 810 at 9 800.
        val packets = agg.poll(T + 15_050).map { it.packet }
        assertEquals((9..14).map { T + it * 1000L }, packets.map { it.timestamp })
        assertEquals(ibis.map { listOf(it) }, packets.map { it.ibiList })
    }

    @Test
    fun ibi_rejects20PercentOutlier() {
        val packets = packetsForBeats(List(5) { 1000 } + 1300 + List(21) { if (it % 2 == 0) 1000 else 1020 })
        assertTrue(packets.none { 1300 in it.ibiList })
        assertEquals(26, packets.sumOf { it.ibiList.size })
        // 4 zero differences before the outlier and 20 of ±20 ms after it; a pair across 1300 would add a 25th.
        assertEquals(sqrt(20 * 400.0 / 24).toFloat(), packets.last().rmssd!!, 1e-4f)
    }

    @Test
    fun ibi_medianOfLast7Accepted() {
        // The 1000s pass against a 900 median; the last 7 are then all 1000, so 1190 passes (an all-accepted median
        // of 900 would reject it).
        val packets = packetsForBeats(List(8) { 900 } + List(7) { 1000 } + 1190)
        assertEquals(16, packets.sumOf { it.ibiList.size })
        assertEquals(listOf(1190), packets.last().ibiList)
    }

    @Test
    fun ibi_medianCheckFromThirdAccepted() {
        val packets = packetsForBeats(listOf(1000, 1000, 1500, 1500))
        assertEquals(listOf(listOf(1000), listOf(1000), listOf(1500), emptyList()), packets.map { it.ibiList })
    }

    @Test
    fun ibi_bounds300To2000Inclusive() {
        val packets = packetsForBeats(listOf(299, 300, 2000, 2001)) // under 3 accepted: only the bounds apply
        assertEquals(listOf(emptyList(), listOf(300), listOf(2000), emptyList()), packets.map { it.ibiList })
    }

    @Test
    fun rmssd_noPairAcrossIbiGap() {
        // 30 s of points without IBIs (e.g. a tracker reconnect), then a beat the median rule accepts.
        val packets = packetsForBeats(List(21) { if (it % 2 == 0) 1000 else 1020 } + List(30) { null } + 1150)
        assertEquals(listOf(1150), packets.last().ibiList)
        assertEquals(20f, packets.last().rmssd) // pairing it with the last 1000 before the gap would add a 150 ms diff
    }

    @Test
    fun hr_bounds30To220Inclusive() {
        val agg = aggregator(Tracker.HR)
        agg.addHrBatch(listOf(30, 29, 220, 221, 60).mapIndexed { k, bpm -> hr(T + k * 1000L + 200, bpm) }, T + 4300)
        assertEquals(listOf(30f, 30f, 220f, 220f), agg.poll(T + 4300).map { it.packet.hr }) // out of range: held
    }

    @Test
    fun eda_floor005Inclusive() {
        val agg = aggregator(Tracker.EDA)
        val points = listOf(0.049f, 0.05f, 2f).mapIndexed { k, us -> EdaDataPoint(T + k * 1000L + 100, us, 0) }
        agg.addEdaBatch(points, T + 2150)
        assertEquals(listOf(null, 0.05f), agg.poll(T + 2150).map { it.packet.skinConductance })
    }

    @Test
    fun rmssd_knownValue() {
        val rmssd = packetsForBeats(List(25) { if (it % 2 == 0) 1000 else 1020 }).map { it.rmssd }
        assertEquals(List(20) { null } + List(5) { 20f }, rmssd) // bin k holds k pairs
    }

    @Test
    fun rmssd_nullUnder20Pairs() {
        val packets = packetsForBeats(List(15) { if (it % 2 == 0) 1000 else 1020 })
        assertEquals(15, packets.size)
        assertTrue(packets.all { it.rmssd == null })
    }

    @Test
    fun eda_detachedStatus_nullNoHold() {
        val agg = aggregator(Tracker.EDA)
        val points = listOf(
            EdaDataPoint(T + 100, 2.0f, 0),
            EdaDataPoint(T + 1100, 2.1f, -5),
            EdaDataPoint(T + 2100, 2.2f, 0),
            EdaDataPoint(T + 3100, 2.3f, 0),
        )
        assertEquals(List(4) { false }, agg.addEdaBatch(points, T + 3150))
        val packets = agg.poll(T + 3150).map { it.packet }
        assertEquals(listOf(2.0f, null, 2.2f), packets.map { it.skinConductance })
        assertEquals(listOf(0, -5, 0), packets.map { it.edaStatus })
    }

    @Test
    fun skinTemp_heldUpTo120s_errorStatusIgnored() {
        val agg = aggregator(Tracker.HR)
        agg.addSkinTempBatch(
            listOf(SkinTempDataPoint(T + 200, 33.5f, 24f, 0), SkinTempDataPoint(T + 10_200, 20f, 24f, -1)),
        )
        agg.addHrBatch((0..130).map { hr(T + it * 1000L + 500) }, T + 131_000)
        val skin = agg.poll(T + 131_000).map { it.packet.skinTemperature }
        assertEquals(List(121) { 33.5f } + List(9) { null }, skin) // bins 0..120 hold it, 121..129 do not
    }

    @Test
    fun skinTemp_lateValueStillHoldsLaterBins_newerReplacesIt() {
        val agg = aggregator(Tracker.HR)
        agg.addHrBatch((0..4).map { hr(T + it * 1000L + 500) }, T + 5_000)
        assertEquals(List(4) { null }, agg.poll(T + 5_000).map { it.packet.skinTemperature })
        // Sampled in bin 1, which already went out, and in bin 5.
        agg.addSkinTempBatch(
            listOf(SkinTempDataPoint(T + 1_500, 33f, null, 0), SkinTempDataPoint(T + 5_200, 34f, null, 0)),
        )
        agg.addHrBatch((5..7).map { hr(T + it * 1000L + 500) }, T + 8_000)
        assertEquals(listOf(33f, 34f, 34f), agg.poll(T + 8_000).map { it.packet.skinTemperature })
    }

    @Test
    fun spo2_heldUntilTheNextCheckCouldHaveCompleted() {
        val config = SessionConfig(sensors = SensorsConfig(spo2IntervalMin = 5, spo2TimeoutS = 30))
        val agg = PacketAggregator(config, SensorSource.SAMSUNG_SDK, setOf(Tracker.HR))
        agg.addSpo2(T + 40_300, 96)
        agg.addHrBatch((0..372).map { hr(T + it * 1000L + 500) }, T + 373_000)
        val spo2 = agg.poll(T + 373_000).map { it.packet.spo2 }
        // Sampled in bin 40 and held 5 min + 30 s: bins 40..370.
        assertEquals(List(40) { null } + List(331) { 96 } + listOf(null), spo2)
    }

    @Test
    fun emission_lateTracker_nullAfterDeadline() {
        val agg = aggregator(Tracker.HR, Tracker.EDA, Tracker.ACCEL)
        val clockOffset = 3_600_000L // caller's clock minus sample clock (the timestamp base is unverified)
        for (k in 0..9) { // HR and accel on time, EDA silent
            val ts = T + k * 1000L
            agg.addHrBatch(listOf(hr(ts + 500)), ts + 500 + clockOffset)
            agg.addAccelSeconds(listOf(stillSecond(ts)), ts + 1000 + clockOffset)
            assertTrue(agg.poll(ts + 1000 + clockOffset).isEmpty())
        }
        val deadline = T + 1000 + clockOffset + 10_000 // bin end + clock offset + 2 × sdkFlushS
        assertTrue(agg.poll(deadline - 1).isEmpty())
        val emitted = agg.poll(deadline).single()
        assertEquals(T, emitted.packet.timestamp)
        assertEquals(deadline, emitted.emittedWallMs)
        assertNull(emitted.packet.skinConductance)
        assertNull(emitted.packet.edaStatus)
        assertEquals(60f, emitted.packet.hr)
        assertEquals(1f, emitted.packet.stillnessScore)

        // EDA wakes up: its point for the emitted bin is reported late and changes nothing; the next one is used.
        val late = agg.addEdaBatch(listOf(EdaDataPoint(T + 300, 2f, 0), EdaDataPoint(T + 1300, 2f, 0)), deadline + 100)
        assertEquals(listOf(true, false), late)
        val next = agg.poll(deadline + 1000).single().packet
        assertEquals(T + 1000, next.timestamp)
        assertEquals(2f, next.skinConductance)
    }

    @Test
    fun emission_allTrackersDelivered_noDeadlineWait() {
        val agg = PacketAggregator(SessionConfig(), SensorSource.ANDROID_SENSORS, setOf(Tracker.HR, Tracker.ACCEL))
        agg.addHrBatch(listOf(hr(T + 400, bpm = 61)), T + 1100)
        val second = AccelSecond(T, 24, 1.01f, 0.004f, 0.012f, 0.98f, 1.5f, -37.5f, true, false)
        agg.addAccelSeconds(listOf(second), T + 1100)
        assertTrue(agg.poll(T + 1100).isEmpty()) // no HR point at or after the bin end yet
        agg.addHrBatch(listOf(hr(T + 1400, bpm = 62)), T + 1500)
        val expected = BiosignalPacket(
            timestamp = T, hr = 61f, ibiList = emptyList(), rmssd = null, skinConductance = null, accelRms = 0.004f,
            edaStatus = null, hrStatus = 1, accelMean = 1.01f, accelPeak = 0.012f, jerkEvent = true, limpEvent = false,
            stillnessScore = 0.98f, source = SensorSource.ANDROID_SENSORS,
        )
        assertEquals(listOf(EmittedPacket(expected, 1.5f, -37.5f, 24, T + 1500)), agg.poll(T + 1500))
    }
}
