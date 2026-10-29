package com.opentdi.shared.stats

import com.opentdi.shared.csv.CsvFiles.P_TRANSITIONS
import com.opentdi.shared.csv.CsvFiles.W_ALARM
import com.opentdi.shared.csv.CsvFiles.W_MORNING
import com.opentdi.shared.csv.CsvFiles.W_PACKETS
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.model.SensorSource
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L

// Synthetic nights only, built here: real recordings are personal data and never go into this repository.
class NightSummariesTest {
    private val zone = ZoneId.of("America/New_York")
    private val start = ms("2026-10-27T23:00")

    private fun ms(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun at(minute: Int) = start + minute * MIN

    /** One second of w_packets.csv. */
    private data class Row(
        val angle: Float?,
        val stillness: Float? = 1f,
        val hr: Float? = 60f,
        val hrStatus: Int? = 1,
        val edaStatus: Int? = 0,
    )

    private fun csv(columns: List<String>, rows: List<List<Any?>>): String {
        val out = StringWriter()
        CsvWriter(out, columns).use { csv -> rows.forEach { csv.row(*it.toTypedArray()) } }
        return out.toString()
    }

    /** w_packets.csv as LoggingSession writes it, one row a second from [startMs]; [row] null: no packet then. */
    private fun packets(startMs: Long, seconds: Int, angleColumn: Boolean = true, row: (s: Int) -> Row?): String {
        val columns = if (angleColumn) W_PACKETS.columns else W_PACKETS.columns - "angle_z_deg"
        return csv(
            columns,
            (0 until seconds).mapNotNull { s ->
                val r = row(s) ?: return@mapNotNull null
                val ts = startMs + s * 1000L
                listOf(
                    ts, s, SensorSource.SAMSUNG_SDK, r.hr, r.hrStatus, emptyList<Int>(), null, 1.2f, r.edaStatus, 1f,
                    0.003f, 0.01f, r.stillness, false, false, null, 25, ts + 1200, 33f, null, r.angle,
                ).take(columns.size)
            },
        )
    }

    private fun alarms(vararg rows: List<Any?>) = W_ALARM.name to csv(W_ALARM.columns, rows.toList())

    private fun summarize(files: Map<String, String>) =
        summarizeNight("20261028T030000Z-0001", zone) { name -> files[name]?.let(::StringReader) }

    private fun assertNear(expected: Long, actual: Long, within: Long) =
        assertTrue("$actual is not within $within of $expected", abs(actual - expected) <= within)

    /** An arm that keeps moving: 10° steps every 10 s. */
    private fun steps(s: Int) = if (s / 10 % 2 == 0) 0f else 10f

    /** An arm at rest: 0–3° jitter, so epoch means move by 0.2–0.6°. */
    private fun jitter(s: Int) = (s % 4).toFloat()

    /** §B12 test 4: 30 min moving, 6 h at rest with a 12-min movement 3 h in, 20 min moving. */
    private fun moving(s: Int) = s < 30 * 60 || s in 210 * 60 until 222 * 60 || s >= 390 * 60

    private fun restless(s: Int) = if (moving(s)) Row(steps(s), stillness = 0.2f) else Row(jitter(s))

    private fun night(row: (s: Int) -> Row? = ::restless) = mapOf(W_PACKETS.name to packets(start, 410 * 60, row = row))

    /** 2 h at rest: too short for an estimate, enough for the outcomes. */
    private val shortNight = mapOf(W_PACKETS.name to packets(start, 2 * 3600) { s -> Row(jitter(s)) })

    @Test
    fun basicNight_armAngle() {
        val sleep = summarize(night())!!.sleep!!
        assertEquals("arm-angle v1", sleep.method)
        assertNear(at(30), sleep.onsetMs, 5_000) // one epoch
        assertNear(at(390), sleep.finalWakeMs, 10_000)
        assertEquals(30, sleep.latencyMin)
        assertEquals(1, sleep.awakenings)
        assertEquals(12, sleep.wasoMin)
        assertEquals(348, sleep.sleepMin)
        assertEquals(89, sleep.efficiencyPct)
        assertEquals(60f, sleep.hrSleepMeanBpm)
        assertEquals(60f, sleep.hrSleepMinBpm)
    }

    @Test
    fun withoutTheAngle_stillness() {
        val still = { s: Int -> Row(null, stillness = if (moving(s)) 0.2f else 1f) }
        val withoutColumn = packets(start, 410 * 60, angleColumn = false, row = still)
        for (file in listOf(withoutColumn, packets(start, 410 * 60, row = still))) {
            val sleep = summarize(mapOf(W_PACKETS.name to file))!!.sleep!!
            assertEquals("stillness v1", sleep.method) // no angle column, or one without values
            assertNear(at(30), sleep.onsetMs, 5_000)
            assertEquals(1, sleep.awakenings)
            assertEquals(12, sleep.wasoMin)
            assertEquals(348, sleep.sleepMin)
        }
    }

    @Test
    fun parsing_byName_cutRows_crlf_order_duplicates_unknownEvents() {
        val files = night()
        val text = files.getValue(W_PACKETS.name)
        val expected = summarize(files)
        fun same(packets: String) = assertEquals(expected, summarize(mapOf(W_PACKETS.name to packets)))
        same(text.lines().filter { it.isNotEmpty() }.joinToString("\n", postfix = "\n") { line ->
            line.split(',').reversed().joinToString(",") // the columns in another order
        })
        same(text + "${at(410)},24600,SAMSUNG_SD") // the watch died mid-row
        same(text.replace("\n", "\r\n"))
        val lines = text.lines().filter { it.isNotEmpty() }
        same((lines.take(1) + lines.drop(1).reversed()).joinToString("\n"))
        val duplicate = packets(at(60), 1) { Row(jitter(3600), hr = 120f) }.lines()[1]
        same(text + duplicate + "\n") // the first row of a second is kept

        val alarm = csv(
            W_ALARM.columns,
            listOf(
                listOf(at(380), "WAKE", "RING", null, null, "tone=MELODY;alarm_volume=5"),
                listOf(at(385), "WAKE", "SOMETHING_NEW", null, null, null),
                listOf(at(390), "WAKE", "STOPPED", 0.4f, 70f, "after_ms=600000;first_movement_ms=12000"),
            ),
        ) + "${at(395)},WAKE,SNOOZE,,,\"after_ms=1" // cut inside a quoted field
        assertEquals(AlarmOutcome(at(380), at(390), 0, 600), summarize(files + (W_ALARM.name to alarm))!!.wake)
    }

    @Test
    fun noPackets_noSummary() {
        assertNull(summarize(emptyMap()))
        assertNull(summarize(mapOf(W_PACKETS.name to "")))
        assertNull(summarize(mapOf(W_PACKETS.name to csv(W_PACKETS.columns, emptyList()))))
    }

    @Test
    fun twoHours_noEstimate_butATimeline() {
        val s = summarize(shortNight)!!
        assertNull(s.sleep)
        assertEquals(SUMMARY_VERSION, s.version)
        assertEquals(120, s.epochs.asleep.size)
        assertTrue(s.epochs.asleep.all { it == null })
        assertEquals(List(120) { 60f }, s.epochs.hrBpm)
    }

    @Test
    fun angleChangeOf5Deg_keepsTheBout_5point1Deg_endsIt() {
        // At rest at 0°, except moving for the first 10 min and, 2 h in, 12 min moving, 8 min at rest with one step
        // halfway, 12 min moving: 4 min either side of the step is too short for a bout.
        fun stepNight(step: Float) = mapOf(
            W_PACKETS.name to packets(start, 5 * 3600) { s ->
                val m = s / 60
                Row(
                    when {
                        m < 10 || m in 120 until 132 || m in 140 until 152 -> steps(s)
                        m in 132 until 136 -> 20f
                        m in 136 until 140 -> 20f + step
                        else -> 0f
                    },
                )
            },
        )
        assertEquals(true, summarize(stepNight(5f))!!.epochs.asleep[138])
        assertEquals(false, summarize(stepNight(5.1f))!!.epochs.asleep[138])
    }

    @Test
    fun anHourOffTheWrist_isNoData() {
        val offWrist = 90 * 60 until 150 * 60
        val files = night { s -> if (s in offWrist) Row(jitter(s), hr = null, hrStatus = -3) else restless(s) }
        val sleep = summarize(files)!!.sleep!!
        assertEquals(288, sleep.sleepMin) // not in sleep ...
        assertEquals(12, sleep.wasoMin) // ... nor awake ...
        assertEquals(87, sleep.efficiencyPct) // ... nor in the share's denominator (74% with it)
    }

    @Test
    fun aHoleOf15Min_isNoData_oneOf8Min_isWake() {
        val holes = listOf(90 * 60 until 105 * 60, 300 * 60 until 308 * 60)
        val sleep = summarize(night { s -> restless(s).takeIf { holes.none { s in it } } })!!.sleep!!
        assertEquals(325, sleep.sleepMin)
        assertEquals(20, sleep.wasoMin) // the 12-min movement and the 8-min hole
        assertEquals(2, sleep.awakenings)
    }

    @Test
    fun hdcza_thresholdClampedTo013And050() {
        val n = 1000
        // A perfectly still arm: every rolling median is 0, so only the 0.13 floor lets it form a block.
        assertEquals(0 until n, hdczaWindow(DoubleArray(n), BooleanArray(n)))
        // A steady 1° change: 15 × the 10th percentile is 15°, clamped to 0.50, so nothing is below it.
        assertNull(hdczaWindow(DoubleArray(n) { 1.0 }, BooleanArray(n)))
    }

    /** §B12 test 9: 10 min moving, 3 h at rest, 70 min up for WBTB, 2 h at rest, 10 min moving. */
    private val breakNight = mapOf(
        W_PACKETS.name to packets(start, 390 * 60) { s ->
            if (s < 10 * 60 || s in 190 * 60 until 260 * 60 || s >= 380 * 60) Row(steps(s), 0.2f) else Row(jitter(s))
        },
    )
    private val wbtbRows = alarms(
        listOf(at(190), "WBTB", "RING", null, null, "tone=MELODY;alarm_volume=5"),
        listOf(at(191), "WBTB", "STOPPED", 0.3f, 72f, "after_ms=60000;first_movement_ms=5000"),
        listOf(at(246), "WBTB", "BACK_TO_SLEEP", 0.9f, 64f, null),
        listOf(at(246) + 1, "CUES", "ARMED", null, null, "night=CUE;start_after_onset_min=30"),
        listOf(at(270), "CUES", "RE_ONSET", 1f, 58f, "latency_min=14"),
    )

    @Test
    fun aWbtbBreakOf70Min_keepsOneWindow_withoutItsRows_theLongerBlockWins() {
        val one = summarize(breakNight + wbtbRows)!!.sleep!!
        assertNear(at(380), one.finalWakeMs, 10_000)
        assertEquals(300, one.sleepMin)
        val two = summarize(breakNight)!!.sleep!!
        assertNear(at(190), two.finalWakeMs, 10_000)
        assertEquals(180, two.sleepMin)
    }

    @Test
    fun wbtbFullSequence_theBreakIsNotWakingInTheNight() {
        val s = summarize(breakNight + wbtbRows)!!
        assertEquals(WbtbOutcome(at(190), at(191), at(246), at(270) - 600_000, 14), s.wbtb)
        assertEquals(LateCueOutcome("ON", cueNight = true), s.lateCues)
        val sleep = s.sleep!!
        assertEquals(0, sleep.wasoMin)
        assertEquals(0, sleep.awakenings)
    }

    @Test
    fun wbtbSkipped_orRungOut() {
        val skipped = alarms(listOf(at(120), "WBTB", "SKIPPED", 1f, 60f, "recording_ms=7200000"))
        assertEquals(WbtbOutcome(skipped = "MIN_SLEEP_BEFORE_WBTB"), summarize(night() + skipped)!!.wbtb)
        val rungOut = alarms(
            listOf(at(300), "WBTB", "RING", null, null, "tone=VIBRATE_ONLY;alarm_volume=5"),
            listOf(at(302), "WBTB", "ESCALATED", null, null, "tone=MELODY"),
            listOf(at(310), "WBTB", "TIMED_OUT", 1f, 60f, "after_ms=600000;first_movement_ms="),
        )
        val s = summarize(night() + rungOut)!!
        assertEquals(WbtbOutcome(ringAtMs = at(300)), s.wbtb) // still a WBTB night
        assertEquals(348, s.sleep!!.sleepMin) // maybe slept through: not known wake
    }

    @Test
    fun wakeAlarm_snoozes_stop_laterRingIgnored_orRungOut() {
        val stopped = alarms(
            listOf(at(380), "WAKE", "RING", null, null, null),
            listOf(at(381), "WAKE", "SNOOZE", null, null, "after_ms=60000;snooze=1"),
            listOf(at(390), "WAKE", "RING", null, null, null),
            listOf(at(391), "WAKE", "SNOOZE", null, null, "after_ms=60000;snooze=2"),
            listOf(at(400), "WAKE", "RING", null, null, null),
            listOf(at(401), "WAKE", "STOPPED", null, null, "after_ms=60000"),
            listOf(at(405), "WAKE", "RING", null, null, null), // the next morning's, on an over-long recording
        )
        val s = summarize(night() + stopped)!!
        assertEquals(AlarmOutcome(at(380), at(401), snoozes = 2, latencyS = 21 * 60), s.wake)
        assertTrue(s.sleep!!.finalWakeMs <= at(380))
        val rungOut = summarize(
            night() + alarms(
                listOf(at(380), "WAKE", "RING", null, null, null),
                listOf(at(390), "WAKE", "TIMED_OUT", 1f, 60f, "after_ms=600000;first_movement_ms="),
            ),
        )!!
        assertEquals(AlarmOutcome(at(380)), rungOut.wake)
        assertNear(at(390), rungOut.sleep!!.finalWakeMs, 10_000)
    }

    @Test
    fun lateCues_experiment_on_none() {
        val experiment = alarms(
            listOf(at(100), "CUES", "ARMED", null, null, "night=NO_CUE;pair=2/2;block=123;start_after_onset_min=30"),
            listOf(at(110), "CUES", "RE_ONSET", null, null, "latency_min=0"),
            listOf(at(111), "CUES", "WOULD_CUE", 1f, 60f, "bout_cue=1"),
            listOf(at(112), "CUES", "WOULD_CUE", 1f, 60f, "bout_cue=2"),
            listOf(at(113), "CUES", "PAUSE_MOVEMENT", 0.1f, 70f, "bout_cues=2"),
            listOf(at(115), "CUES", "ENDED", null, null, null),
            listOf(at(116), "CUES", "WOULD_CUE", 1f, 60f, "bout_cue=1"), // after ENDED
        )
        assertEquals(
            LateCueOutcome("EXPERIMENT", cueNight = false, pair = "2/2", wouldCue = 2, blockId = 123),
            summarize(shortNight + experiment)!!.lateCues,
        )
        val on = alarms(
            listOf(at(100), "CUES", "ARMED", null, null, "night=CUE;start_after_onset_min=30"),
            listOf(at(111), "CUES", "CUE", 1f, 60f, "bout_cue=1"),
            listOf(at(112), "CUES", "CUE", 1f, 60f, "bout_cue=2"),
        )
        assertEquals(LateCueOutcome("ON", cueNight = true, cuesPlayed = 2), summarize(shortNight + on)!!.lateCues)
        assertNull(summarize(shortNight + alarms(listOf(at(100), "WAKE", "RING", null, null, null)))!!.lateCues)
    }

    @Test
    fun reports_normalised() {
        val morning = W_MORNING.name to csv(
            W_MORNING.columns,
            listOf(
                listOf(at(400), "WAKE", "w_dream_064512.m4a", "NO", 4, 3, "YES", "YES", "YES", "NO", 6, "NO", "YES", null, "FORM", "YES"),
                listOf(at(300), "WBTB", "../w_dream_040000.m4a", "WHITE", 4, 3, "YES", "NO", "NO", "YES", 3, "YES", "NO", "UNSURE", "ACTION", "NO"),
                listOf(at(410), "WAKE", null, "VAGUE", 2, 2, null, null, null, null, 5, null, null, "MAYBE", null, null),
                listOf(at(420), "WAKE", "w_dream_070000.m4a", "YES", 5, 0, "NO", "NO", "YES", "NO", 4, "NO", "NO", "YES", "CONTEXT", "YES"),
            ),
        )
        assertEquals(
            listOf(
                DreamReport(
                    at(300), "WBTB", recalled = "WHITE", nightmare = true, falseAwakening = false, cueInDream = false,
                    cueWoke = true, refreshed = 3, sleepParalysis = true, unusualNight = false, guideSleep = "UNSURE",
                    signSeen = false,
                ),
                DreamReport(
                    at(400), "WAKE", recording = "w_dream_064512.m4a", recalled = "NO", cueWoke = false,
                    sleepParalysis = false, unusualNight = true,
                ),
                DreamReport(at(410), "WAKE", recalled = "WHITE", refreshed = 5),
                DreamReport(
                    at(420), "WAKE", "w_dream_070000.m4a", "YES", insight = 5, control = 0, nightmare = false,
                    falseAwakening = false, cueInDream = true, cueWoke = false, refreshed = 4, sleepParalysis = false,
                    unusualNight = false, guideSleep = "YES", odd = "CONTEXT", signSeen = true,
                ),
            ),
            summarize(shortNight + morning)!!.reports,
        )
        // A report saved before guide_sleep, odd and sign_seen were added (2026-10-28) still reads.
        val older = W_MORNING.name to csv(
            W_MORNING.columns.take(13),
            listOf(listOf(at(420), "WAKE", null, "YES", 3, 1, "NO", "NO", "NO", "NO", 4, "NO", "NO")),
        )
        assertEquals(3, summarize(shortNight + older)!!.reports.single().insight)
        val alarmsOnly = alarms(listOf(at(100), "WAKE", "RING", null, null, null))
        assertEquals(emptyList<DreamReport>(), summarize(shortNight + alarmsOnly)!!.reports)
    }

    @Test
    fun dormioPrompt_fromThePhone_isWake() {
        val transitions = P_TRANSITIONS.name to csv(
            P_TRANSITIONS.columns,
            listOf(
                listOf(start - 60 * MIN, 0, "HYPNAGOGIA_DETECTED", "WAKING_USER", "PHONE_STAGE", 1, "TIMED"), // before
                listOf(at(100), 0, "AWAKE_INCUBATING", "HYPNAGOGIA_DETECTED", "TIMED_DWELL", 1, "TIMED"),
                listOf(at(120), 0, "HYPNAGOGIA_DETECTED", "WAKING_USER", "PHONE_STAGE", 1, "TIMED"),
                listOf(at(121), 0, "WAKING_USER", "RECORDING_DREAM", "PHONE_STAGE", 1, "TIMED"),
                listOf(at(130), 0, "RECORDING_DREAM", "RE_INCUBATING", "PHONE_STAGE", 1, "TIMED"),
                listOf(at(135), 0, "RE_INCUBATING", "AWAKE_INCUBATING", "CYCLE_COMPLETED", 1, "TIMED"),
            ),
        )
        val s = summarize(night() + transitions)!!
        assertEquals(1, s.promptedWakings)
        assertEquals(List(15) { false }, s.epochs.asleep.subList(120, 135))
        val sleep = s.sleep!!
        assertEquals(348 - 15, sleep.sleepMin)
        assertEquals(2, sleep.awakenings)
        // The phone died in the report: 10 min awake, not the rest of the night.
        val died = P_TRANSITIONS.name to csv(
            P_TRANSITIONS.columns,
            listOf(
                listOf(at(120), 0, "HYPNAGOGIA_DETECTED", "WAKING_USER", "PHONE_STAGE", 1, "TIMED"),
                listOf(at(121), 0, "WAKING_USER", "RECORDING_DREAM", "PHONE_STAGE", 1, "TIMED"),
            ),
        )
        val dangling = summarize(night() + died)!!
        assertEquals(List(10) { false }, dangling.epochs.asleep.subList(120, 130))
        assertEquals(348 - 10, dangling.sleep!!.sleepMin)
    }

    @Test
    fun nightOf_noonToNoon_dstNightInRealMinutes() {
        val afterMidnight = packets(ms("2026-10-28T00:30"), 60) { Row(0f) }
        assertEquals("2026-10-27", summarize(mapOf(W_PACKETS.name to afterMidnight))!!.nightOf)
        // Clocks go back at 02:00 on 2026-11-01: 23:00 to 07:00 local is 9 h.
        val dst = packets(ms("2026-10-31T23:00"), 9 * 3600) { s -> if (s < 1800) Row(steps(s)) else Row(jitter(s)) }
        val s = summarize(mapOf(W_PACKETS.name to dst))!!
        assertEquals("2026-10-31", s.nightOf)
        assertEquals(ms("2026-11-01T07:00"), s.recordEndMs)
        assertEquals(540, s.epochs.asleep.size)
        assertEquals(510, s.sleep!!.sleepMin)
    }

    @Test
    fun epochs_minuteByMinute() {
        val hole = 100 * 60 until 115 * 60
        val e = summarize(night { s -> restless(s).takeIf { s !in hole } })!!.epochs
        assertEquals(start, e.startMs)
        assertEquals(listOf(410, 410, 410), listOf(e.movement.size, e.hrBpm.size, e.asleep.size))
        assertEquals(listOf(null, null, null), listOf(e.movement[105], e.hrBpm[105], e.asleep[105]))
        assertEquals(List(30) { false }, e.asleep.take(30)) // before onset
        assertEquals(true, e.asleep[60])
        assertEquals(1f, e.movement[10])
        assertEquals(0f, e.movement[60])
        assertEquals(60f, e.hrBpm[60])
    }

    @Test
    fun sameFiles_equalSummaries() {
        val files = breakNight + wbtbRows
        assertEquals(summarize(files), summarize(files))
    }
}
