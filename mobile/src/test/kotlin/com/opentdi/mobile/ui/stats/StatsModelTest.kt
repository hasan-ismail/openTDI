package com.opentdi.mobile.ui.stats

import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.stats.SyncResult
import com.opentdi.shared.stats.AlarmOutcome
import com.opentdi.shared.stats.DreamReport
import com.opentdi.shared.stats.Epochs
import com.opentdi.shared.stats.LateCueOutcome
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.stats.SUMMARY_VERSION
import com.opentdi.shared.stats.SleepEstimate
import com.opentdi.shared.stats.WbtbOutcome
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsModelTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 28)

    private fun ms(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun summary(
        night: LocalDate,
        sleep: SleepEstimate? = null,
        wbtb: WbtbOutcome? = null,
        wake: AlarmOutcome? = null,
        lateCues: LateCueOutcome? = null,
        reports: List<DreamReport> = emptyList(),
    ) = NightSummary(
        "s-$night", SUMMARY_VERSION, night.toString(), zone.id, 0, 0, sleep,
        Epochs(0, 60, emptyList(), emptyList(), emptyList()), wake, wbtb, lateCues, reports,
    )

    private fun row(night: LocalDate, s: NightSummary? = summary(night)) = NightRow(night, s, null)

    private fun report(after: String = "WAKE", recalled: String? = null, insight: Int? = null, nightmare: Boolean? = null) =
        DreamReport(0, after, recalled = recalled, insight = insight, nightmare = nightmare)

    private fun sleep(onsetMs: Long, finalWakeMs: Long, min: Int = 420) =
        SleepEstimate(onsetMs, finalWakeMs, min, 10, 20, 2, 90, method = "test")

    @Test
    fun ranges_keepTheLastNights_oldestFirst_andBinsGrowWithTheSpan() {
        val rows = listOf(LocalDate.of(2026, 9, 1), today.plusDays(1), today, today.minusDays(6), today.minusDays(7)).map { row(it) }
        val week = rangeStart(StatsRange.WEEK, today, rows)
        assertEquals(today.minusDays(6), week)
        assertEquals(listOf(today.minusDays(6), today), inRange(rows, week, today).map { it.night })
        assertEquals(LocalDate.of(2026, 9, 1), rangeStart(StatsRange.ALL, today, rows))
        assertEquals(today, rangeStart(StatsRange.ALL, today, emptyList()))

        val nights = bins(inRange(rows, week, today), week, today, DayOfWeek.MONDAY)
        assertEquals(7, nights.size) // empty nights stay as gaps
        assertTrue(nights.all { it.unit == BinUnit.NIGHT })
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 1), nights.map { it.nights.size })

        val sunday = LocalDate.of(2026, 9, 27) // 32 nights to today: weeks
        val mondays = bins(listOf(row(today.minusDays(1))), sunday, today, DayOfWeek.MONDAY)
        assertTrue(mondays.all { it.unit == BinUnit.WEEK })
        assertEquals(LocalDate.of(2026, 9, 21), mondays.first().start)
        assertEquals(LocalDate.of(2026, 10, 26), mondays.last().start)
        assertEquals(1, mondays.last().nights.size) // Tuesday the 27th is in the week of Monday the 26th
        assertEquals(LocalDate.of(2026, 9, 27), bins(emptyList(), sunday, today, DayOfWeek.SUNDAY).first().start)

        val months = bins(emptyList(), LocalDate.of(2026, 3, 1), today, DayOfWeek.MONDAY)
        assertEquals(8, months.size)
        assertTrue(months.all { it.unit == BinUnit.MONTH })

        val day = LocalDate.of(2026, 10, 22) // a Thursday
        assertEquals(0 to 3, calendarSlot(day, day, DayOfWeek.MONDAY))
        assertEquals(1 to 2, calendarSlot(today, day, DayOfWeek.MONDAY))
        assertEquals(1 to 3, calendarSlot(today, day, DayOfWeek.SUNDAY))
    }

    @Test
    fun clockAxis_runsFromNoonToNoon_acrossMidnightAndDaylightSaving() {
        val night = LocalDate.of(2026, 10, 27)
        assertEquals(660, clockMin(ms(night, 23), night, zone))
        assertEquals(810, clockMin(ms(night.plusDays(1), 1, 30), night, zone))
        assertEquals(1440, clockMin(ms(night.plusDays(1), 12), night, zone))
        val spring = LocalDate.of(2026, 3, 7) // clocks go forward at 2:00 the next morning
        assertEquals(930, clockMin(ms(spring.plusDays(1), 3, 30), spring, zone)) // wall clock, not elapsed time

        assertEquals("11 PM", clockLabel(660))
        assertEquals("1:30 AM", clockLabel(810))
        assertEquals("12 PM", clockLabel(1440))
        assertEquals("11:30 AM", clockLabel(-30))

        assertEquals(600..1260, clockAxis(listOf(670, 1150))) // 23:10 to 7:10 gets 22:00 to 9:00
        assertEquals(540..1200, clockAxis(emptyList())) // 22:00 to 7:00 with an hour to spare
        assertEquals(0..1440, clockAxis(listOf(30, 1430)))
    }

    @Test
    fun window_isOnsetToFinalWake_withTheWbtbTimeAwakeCutOut() {
        val night = LocalDate.of(2026, 10, 27)
        val next = night.plusDays(1)
        val s = summary(
            night,
            sleep = sleep(ms(night, 23, 10), ms(next, 7, 5)),
            wbtb = WbtbOutcome(ms(next, 4, 30), ms(next, 4, 31), ms(next, 4, 50), ms(next, 5, 2), 12),
            wake = AlarmOutcome(ms(next, 7)),
        )
        assertEquals(Window(670, 1145, 990..1022, listOf(990, 1140)), window(row(night, s)))
        val noOnset = s.copy(wbtb = WbtbOutcome(ms(next, 4, 30), backToSleepAtMs = ms(next, 4, 50)), wake = null)
        assertEquals(990..1010, window(row(night, noOnset))?.awake)
        assertNull(window(row(night, summary(night))))
        assertNull(window(row(night, null)))

        val other = summary(night.plusDays(1), sleep = sleep(ms(next, 23, 50), ms(next.plusDays(1), 7, 25)))
        val week = Bin(BinUnit.WEEK, night, listOf(row(night, s), row(next, other)))
        assertEquals(Window(690, 1155), window(week)) // means, without cut-out or alarms
        assertEquals(Window(670, 1145, 990..1022, listOf(990, 1140)), window(Bin(BinUnit.NIGHT, night, listOf(row(night, s)))))
    }

    @Test
    fun recallLucidAndOutcomes_countMorningsThatAnswered() {
        val day = { n: Long -> today.minusDays(n) }
        val nights = listOf(
            row(day(1), summary(day(1), reports = listOf(report(recalled = "YES", insight = 1)))),
            row(day(2), summary(day(2), reports = listOf(report(recalled = "WHITE")))),
            row(day(3), summary(day(3), reports = listOf(report(recalled = "NO", insight = 0)))),
            row(day(4), summary(day(4), reports = listOf(report("WBTB", "NO"), report(recalled = "YES", insight = 4, nightmare = true)))),
            row(day(5)),
            row(day(6), summary(day(6), reports = listOf(report()))), // every question skipped
        )
        assertEquals(2 to 4, recall(nights))
        assertEquals(1 to 4, lucid(nights)) // the WHITE morning counts as not lucid
        assertEquals(
            listOf(Outcome.DREAM, Outcome.WHITE, Outcome.NO_DREAM, Outcome.LUCID, Outcome.NO_REPORT, Outcome.NO_REPORT),
            nights.map(::outcome),
        )
        assertEquals(listOf(false, false, false, true, false, false), nights.map { it.nightmare })
        assertEquals(4, nights[3].insight)
        assertEquals(
            "Mornings: 1 lucid moment, 1 dream, 1 dreamed but can't recall it, 1 with no dream, 2 not answered.",
            outcomeTakeaway(nights.groupingBy(::outcome).eachCount()),
        )
        assertEquals("No recorded nights in this range yet.", outcomeTakeaway(emptyMap()))
        assertEquals(Mean(2.5f, 2), nights.mean { it.insight?.toFloat()?.takeIf { v -> v > 0 } })
        assertNull(emptyList<NightRow>().mean { 1f })
    }

    /** An EXPERIMENT night [daysBack] before today: [cue] arm, pair [pair], and the WAKE report's [insight]. */
    private fun arm(daysBack: Long, cue: Boolean, pair: String?, insight: Int?, after: String = "WAKE"): NightRow {
        val night = today.minusDays(daysBack)
        val reports = if (insight == null) emptyList() else listOf(report(after, "YES", insight))
        return row(night, summary(night, lateCues = LateCueOutcome("EXPERIMENT", cue, pair, if (cue) 3 else 0), reports = reports))
    }

    @Test
    fun experiment_pairsCueAndNoCueNights_andSaysNoMoreThanTheNumbers() {
        val two = listOf(arm(10, true, "1/2", 4), arm(9, false, "2/2", 0), arm(8, false, "1/2", 1), arm(7, true, "2/2", 1))
        val early = experiment(two, today)
        assertEquals(Experiment(pairs = 2, cueLucid = 1, noCueLucid = 0, onlyCue = 1, onlyNoCue = 0, waiting = 0), early)
        assertEquals("2 of 5 pairs so far: results show after 5 pairs.", verdict(early))
        assertEquals("1 of 2", armValue(early.cueLucid, early.pairs))

        val odd = listOf(
            arm(20, false, "2/2", 3), // no first night to pair with
            arm(19, true, "1/2", 3), arm(18, true, "2/2", 0), // the same arm twice: not a pair
            arm(17, true, "1/2", 3, after = "WBTB"), arm(16, false, "2/2", 0), // no morning (WAKE) report
            arm(15, true, "1/2", 0),
        )
        assertEquals(Experiment(0, 0, 0, 0, 0, waiting = 6), experiment(odd, today))

        val clear = (0 until 6).flatMap { listOf(arm(40L - 2 * it, true, "1/2", 4), arm(39L - 2 * it, false, "2/2", 0)) }
        val shown = experiment(clear, today)
        assertEquals(6, shown.pairs)
        assertEquals(0.03125, shown.p, 1e-9)
        assertTrue(shown.clear)
        assertEquals(
            "More lucid moments on nights with soft sounds so far. Small numbers can mislead: keep going.",
            verdict(shown),
        )
        assertEquals("100% (6 of 6)", armValue(shown.cueLucid, shown.pairs))
        assertEquals("0% (0 of 6)", armValue(shown.noCueLucid, shown.pairs))

        val close = (0 until 5).flatMap { i ->
            listOf(arm(60L - 2 * i, true, "1/2", if (i < 4) 4 else 0), arm(59L - 2 * i, false, "2/2", if (i < 4) 0 else 5))
        }
        val notYet = experiment(close, today)
        assertEquals(5, notYet.pairs)
        assertFalse(notYet.clear) // 4 to 1: p = 0.375
        assertEquals("No clear difference yet.", verdict(notYet))
    }

    @Test
    fun experiment_countsAMorningWithoutARememberedDreamAsNotLucid() {
        fun night(daysBack: Long, cue: Boolean, pair: String, morning: DreamReport): NightRow {
            val night = today.minusDays(daysBack)
            return row(night, summary(night, lateCues = LateCueOutcome("EXPERIMENT", cue, pair), reports = listOf(morning)))
        }
        val noDream = listOf(night(4, true, "1/2", report("WAKE", "NO")), night(3, false, "2/2", report("WAKE", "YES", 4)))
        assertEquals(
            Experiment(pairs = 1, cueLucid = 0, noCueLucid = 1, onlyCue = 0, onlyNoCue = 1, waiting = 0),
            experiment(noDream, today),
        )
        // A remembered dream whose "Did you know you were dreaming?" was skipped is unrated.
        val unrated = listOf(night(2, true, "1/2", report("WAKE", "YES")), night(1, false, "2/2", report("WAKE", "WHITE")))
        assertEquals(Experiment(0, 0, 0, 0, 0, waiting = 2), experiment(unrated, today))
    }

    @Test
    fun armShown_hidesOnlyLastNightsExperimentArm_untilItsMorningReport() {
        val pending = arm(0, false, "1/2", null)
        assertFalse(armShown(pending, today))
        assertTrue(armShown(arm(0, false, "1/2", 2), today))
        assertFalse(armShown(arm(0, false, "1/2", 2, after = "WBTB"), today)) // the WBTB report comes before the cues
        assertTrue(armShown(pending, today.plusDays(1))) // past noon the morning is over
        assertTrue(armShown(row(today, summary(today, lateCues = LateCueOutcome("ON", true))), today))
        assertTrue(armShown(row(today, null), today))
        // A hidden night counts nowhere, not even as waiting.
        assertEquals(0, experiment(listOf(pending), today).waiting)
    }

    @Test
    fun signTest_isTheExactTwoSidedBinomial() {
        assertEquals(1.0, signTestP(0, 0), 0.0)
        assertEquals(0.03125, signTestP(6, 0), 1e-12)
        assertEquals(0.0625, signTestP(0, 5), 1e-12)
        assertEquals(0.375, signTestP(4, 1), 1e-12)
        assertEquals(1.0, signTestP(10, 10), 0.0)
        assertTrue(signTestP(100, 40) < 1e-4)
    }

    @Test
    fun spreadTicksAndWords() {
        assertNull(spread(listOf(660, 690)))
        assertEquals(690 to 24, spread(listOf(660, 690, 720)))
        assertEquals(listOf(0f, 2f, 4f, 6f, 8f, 10f), niceTicks(0f, 8.5f))
        assertEquals(listOf(45f, 50f, 55f, 60f, 65f, 70f), niceTicks(48f, 66f, 4))
        assertEquals(listOf(0f, 1f), niceTicks(0f, 0f, minStep = 1f))
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f, 5f), niceTicks(0f, 5f, 5, 1f))

        assertEquals("Copying from the watch…", syncWords(true, SyncResult(1, 1, "old")))
        assertNull(syncWords(false, null))
        assertEquals("watch not reachable", syncWords(false, SyncResult(0, 0, "watch not reachable")))
        assertEquals("Up to date with the watch", syncWords(false, SyncResult(0, 0)))
        assertEquals("Copied 1 new file from the watch", syncWords(false, SyncResult(1, 10)))
        assertEquals("Copied 3 new files from the watch", syncWords(false, SyncResult(3, 10)))

        assertEquals("45 min", hm(45))
        assertEquals("8 h", hm(480))
        assertEquals("7 h 5 min", hm(425))
        assertEquals("Insight: nothing in this range yet.", takeaway("Insight", emptyList()) { "$it" })
        assertEquals(
            "Minutes averaged 20 over 3 nights, from 10 to 30.",
            takeaway("Minutes", listOf(10f, 20f, 30f)) { num(it) },
        )
        assertEquals("2.5", num(2.5f))
        assertEquals("3", num(3f))

        val made = listOf(DreamReport(0, "WAKE", recording = "w_dream_064512.m4a"))
        assertEquals("Recorded 6:45 AM, after the wake-up alarm", recordingLabel("w_dream_064512.m4a", made))
        assertEquals("Recorded 11:59 PM", recordingLabel("w_dream_235901.m4a", made))
        assertEquals("Recorded 12:15 AM", recordingLabel("w_dream_001500.m4a", made))
        assertEquals("odd.m4a", recordingLabel("odd.m4a", made))
    }

    @Test
    fun timeline_bucketsSkipGaps_runsAndMarksAreInOrder() {
        val e = Epochs(
            startMs = 0,
            movement = listOf(0.1f, null, 0.3f, 0.5f, null),
            hrBpm = listOf(50f, 52f, null, 60f, null),
            asleep = listOf(true, true, null, false, true),
        )
        assertEquals(
            listOf(Bucket(0, 0.1f, 51f, 2), Bucket(120_000, 0.4f, 60f, 0), Bucket(240_000, null, null, 1)),
            buckets(e, 2),
        )
        assertEquals(listOf(0..1, 4..4), asleepRuns(e.asleep))
        assertEquals(emptyList<IntRange>(), asleepRuns(emptyList()))

        val night = LocalDate.of(2026, 10, 27)
        val next = night.plusDays(1)
        val s = summary(
            night,
            wbtb = WbtbOutcome(ms(next, 4, 30), ms(next, 4, 31), ms(next, 4, 50), ms(next, 5, 2)),
            wake = AlarmOutcome(ms(next, 7), ms(next, 7, 3)),
            reports = listOf(DreamReport(ms(next, 7, 5), "WAKE"), DreamReport(ms(next, 4, 33), "WBTB")),
        )
        assertEquals(
            listOf("Early", "Up", "Questions", "Bed", "Asleep", "Alarm", "Awake", "Questions"),
            marks(s).map { it.short },
        )
    }

    @Test
    fun sampleNights_areTheSameEveryTime_andPlausible() {
        val a = sampleNights(today, zone)
        assertEquals(a, sampleNights(today, zone))
        assertTrue(a.size in 90..120)
        assertEquals(a.sortedByDescending { it.night }, a)
        assertTrue(a.all { it.night < today })
        for (n in a) {
            val s = n.watch ?: continue
            assertEquals(s.epochs.movement.size, s.epochs.hrBpm.size)
            assertEquals(s.epochs.movement.size, s.epochs.asleep.size)
            val sleep = s.sleep ?: continue
            assertTrue("${n.night}: ${sleep.sleepMin} min", sleep.sleepMin in 3 * 60..11 * 60)
            assertTrue(sleep.onsetMs < sleep.finalWakeMs)
            assertTrue(sleep.hrSleepMinBpm!! <= sleep.hrSleepMeanBpm!!)
        }
        assertEquals(Outcome.entries.toSet(), a.map(::outcome).toSet())
        assertTrue(experiment(a, today).pairs > 0)
        assertTrue(a.any { it.phone != null } && a.any { it.watch?.wbtb?.skipped != null })
    }
}
