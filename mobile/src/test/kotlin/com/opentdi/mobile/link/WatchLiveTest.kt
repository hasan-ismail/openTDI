package com.opentdi.mobile.link

import com.opentdi.shared.model.BiosignalPacket
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.TelemetryBatch
import com.opentdi.shared.model.TelemetryFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchLiveTest {
    private fun frame(seq: Long, jerk: Boolean = false, limp: Boolean = false) = TelemetryFrame(
        seq = seq,
        packet = BiosignalPacket(
            timestamp = 1_793_000_000_000L + seq * 1000, hr = 60f, ibiList = emptyList(), rmssd = null,
            skinConductance = 0.4f, accelRms = 0.002f, edaStatus = 0, hrStatus = 1, accelMean = 0.01f,
            accelPeak = 0.004f, jerkEvent = jerk, limpEvent = limp, stillnessScore = 1f,
            source = SensorSource.SAMSUNG_SDK,
        ),
        state = TdiState.IDLE, stateReason = null, completedCycles = 0, score = null,
    )

    private fun batch(sessionId: String, frames: List<TelemetryFrame>) = TelemetryBatch(
        sessionId = sessionId, batchSeq = 0, sensorStatus = SensorStatus.CONNECTED_SAMSUNG,
        gravityMode = GravityMode.LINEAR_ONLY, frames = frames, sentAtWallMs = 0L,
    )

    @Test
    fun batches_keepTheLast180Frames_andCountEventsForTheSession() {
        var live = WatchLive()
        for (b in 0L until 100L) {
            live = live.plus(batch("s1", listOf(frame(2 * b, jerk = b == 3L), frame(2 * b + 1, limp = b == 5L))), b * 2000)
        }
        assertEquals(KEEP_FRAMES, live.frames.size)
        assertEquals((20L until 200L).toList(), live.frames.map { it.seq })
        assertEquals(1, live.jerkEvents) // still counted after its frame left the window
        assertEquals(1, live.limpEvents)
        assertEquals(198_000L, live.receivedAtMs)
        assertEquals(SensorStatus.CONNECTED_SAMSUNG, live.sensorStatus)
        assertEquals(GravityMode.LINEAR_ONLY, live.gravityMode)
    }

    @Test
    fun repeatedFrames_areSkipped_andAnotherSessionStartsOver() {
        val first = WatchLive().plus(batch("s1", listOf(frame(0, jerk = true), frame(1))), 10L)
        val again = first.plus(batch("s1", listOf(frame(1, jerk = true), frame(2))), 20L)
        assertEquals(listOf(0L, 1L, 2L), again.frames.map { it.seq })
        assertEquals(1, again.jerkEvents)
        val next = again.plus(batch("s2", listOf(frame(0))), 30L)
        assertEquals("s2", next.sessionId)
        assertEquals(listOf(0L), next.frames.map { it.seq })
        assertEquals(0, next.jerkEvents)
        assertEquals(30L, next.receivedAtMs)
    }
}
