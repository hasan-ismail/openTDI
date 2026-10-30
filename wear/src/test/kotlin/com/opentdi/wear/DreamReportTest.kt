package com.opentdi.wear

import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.csv.csvLine
import com.opentdi.shared.csv.readCsv
import com.opentdi.shared.model.AlarmConfig
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DreamReportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun report(wallMs: Long, nightmare: String?) =
        listOf<Any?>(wallMs, "WAKE", null, "YES", 2, 0, nightmare, "NO", "NO", "NO", 3, "NO", "NO", null, "FORM", null)

    @Test
    fun nightmaresSince_countsYesRowsOfTheLastWeekInEverySessionFolder() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val a = File(tmp.root, "a").apply { mkdirs() }
        val b = File(tmp.root, "b").apply { mkdirs() }
        File(tmp.root, "empty").mkdirs()
        CsvWriter(File(a, CsvFiles.W_MORNING.name).bufferedWriter(), CsvFiles.W_MORNING.columns).use {
            it.row(*report(now - 9 * day, "YES").toTypedArray()) // older than a week
            it.row(*report(now - 2 * day, "YES").toTypedArray())
            it.row(*report(now - 1 * day, "NO").toTypedArray())
        }
        CsvWriter(File(b, CsvFiles.W_MORNING.name).bufferedWriter(), CsvFiles.W_MORNING.columns).use {
            it.row(*report(now - day / 2, "YES").toTypedArray())
            it.row(*report(now - day / 4, null).toTypedArray()) // skipped
        }
        // A file written before the nightmare column counts none.
        File(tmp.root, "old").apply { mkdirs() }.resolve(CsvFiles.W_MORNING.name)
            .writeText("wall_ms,after,recording,recalled,lucid\n${now - day},WAKE,,YES,YES\n")
        assertEquals(2, nightmaresSince(tmp.root, now - 7 * day))
        assertEquals(0, nightmaresSince(File(tmp.root, "missing"), now - 7 * day))
    }

    @Test
    fun shownQuestions_askTheNewOnesOnlyWhenTheyApply() {
        val noDream = listOf(0, 6, 7, 8, 9) // recall, a cue woke you, refreshed, sleep paralysis, unusual night
        val dream = (0..9).toList()
        assertEquals(noDream, shownQuestions(emptyMap(), guided = false, signs = false, after = "WAKE"))
        // no content to judge
        assertEquals(noDream, shownQuestions(mapOf(0 to "WHITE"), guided = false, signs = false, after = "WAKE"))
        assertEquals(dream + ODD, shownQuestions(mapOf(0 to "YES"), guided = false, signs = false, after = "WAKE"))
        assertEquals(noDream + GUIDE_SLEEP, shownQuestions(emptyMap(), guided = true, signs = false, after = "WAKE"))
        // no dream, no sign in it
        assertEquals(noDream, shownQuestions(mapOf(0 to "NO"), guided = false, signs = true, after = "WAKE"))
        assertEquals(
            dream + listOf(GUIDE_SLEEP, ODD, SIGN_SEEN),
            shownQuestions(mapOf(0 to "YES"), guided = true, signs = true, after = "WAKE"),
        )
        // At the early wake-up, not how refreshed or how unusual the night was: it isn't over yet.
        assertEquals(
            listOf(0, 6, 8, GUIDE_SLEEP),
            shownQuestions(emptyMap(), guided = true, signs = false, after = "WBTB"),
        )
        assertEquals(
            dream - listOf(REFRESHED, UNUSUAL_NIGHT) + ODD,
            shownQuestions(mapOf(0 to "YES"), guided = false, signs = false, after = "WBTB"),
        )
    }

    @Test
    fun reportDraft_goesOnUntilSaved_andIsWrittenOnceWhenANewReportOrTheNightEndsIt() {
        ReportDraft.flush() // left by another test
        val night = tmp.newFolder("night")
        val wbtb = ReportDraft.open("WBTB", night, signs = false)
        assertSame(wbtb, ReportDraft.open("WBTB", night, signs = false)) // left, then opened again
        wbtb.answers[0] = "NO"
        val wake = ReportDraft.open("WAKE", night, signs = false) // the WBTB one is written as it stands
        assertNotSame(wbtb, wake)
        assertFalse(ReportDraft.isCurrent(wbtb)) // a page still showing it closes instead of saving nothing
        assertTrue(ReportDraft.isCurrent(wake))
        wake.answers[0] = "YES"
        assertTrue(wake.write())
        assertFalse(wake.write()) // one row per report
        val next = ReportDraft.open("WAKE", night, signs = false) // a saved report is not reopened
        assertNotSame(wake, next)
        assertFalse(ReportDraft.flush()) // nothing said or answered: no row
        assertFalse(ReportDraft.isCurrent(next)) // dropped by the night ending all the same
        val rows = File(night, CsvFiles.W_MORNING.name).reader().use(::readCsv)
        val after = rows[0].indexOf("after")
        assertEquals(listOf("WBTB", "WAKE"), rows.drop(1).map { it[after] })
    }

    @Test
    fun save_putsTheNewAnswersLast_andAFileFromTheOlderAppStillReads() {
        val now = System.currentTimeMillis()
        val nights = tmp.newFolder("nights")
        val answers = listOf("YES", "2", "0", "YES", "NO", "NO", "NO", "3", "NO", "NO", "UNSURE", "CONTEXT", "YES")
        val fresh = File(nights, "fresh")
        save(fresh, "WAKE", "w_dream_061500.m4a", answers)
        val rows = File(fresh, CsvFiles.W_MORNING.name).reader().use(::readCsv)
        assertEquals(CsvFiles.W_MORNING.columns, rows[0])
        val row = rows[0].zip(rows[1]).toMap()
        assertEquals(listOf("UNSURE", "CONTEXT", "YES"), listOf(row["guide_sleep"], row["odd"], row["sign_seen"]))
        assertEquals("w_dream_061500.m4a", row["recording"])
        // A night begun by the previous app: its header lacks the new columns, and the older ones keep their places.
        val old = File(nights, "old").apply { mkdirs() }
        val oldColumns = CsvFiles.W_MORNING.columns.take(13)
        File(old, CsvFiles.W_MORNING.name)
            .writeText(oldColumns.joinToString(",") + "\n${now - 1000},WBTB,,YES,1,0,YES,NO,NO,NO,3,NO,NO\n")
        save(old, "WAKE", null, answers)
        val oldRows = File(old, CsvFiles.W_MORNING.name).reader().use(::readCsv)
        assertEquals(oldColumns, oldRows[0])
        assertEquals(listOf("WBTB", "WAKE"), oldRows.drop(1).map { it[oldColumns.indexOf("after")] })
        assertEquals(3, nightmaresSince(nights, now - 60_000)) // one new row, both rows of the older file
    }

    @Test
    fun alarmsLeft_listsASnoozedWakeUpAtItsNextRing_andNoEarlyWakeUpThatWillBeSkipped() {
        val zone = ZoneId.systemDefault()
        fun at(hour: Int, minute: Int) = LocalDate.of(2026, 10, 29).atTime(hour, minute).atZone(zone).toInstant()
            .toEpochMilli()
        val config = AlarmConfig(wakeAtMin = 420, wbtbEnabled = true, wbtbAtMin = 270)
        assertEquals(
            "the early wake-up at 4:30 AM and the wake-up alarm at 7 AM",
            alarmsLeft(config, wakeAgainAtMs = null, wbtbWontRing = false, nowMs = at(1, 0)),
        )
        assertEquals(
            "the wake-up alarm at 7 AM", // in bed at 1 AM: too soon before 4:30
            alarmsLeft(config, wakeAgainAtMs = null, wbtbWontRing = true, nowMs = at(1, 0)),
        )
        // Snoozed at 7: tomorrow's alarms are more than 12 h away, the next ring is not.
        assertEquals(
            "the wake-up alarm at 7:09 AM",
            alarmsLeft(config, wakeAgainAtMs = at(7, 9), wbtbWontRing = false, nowMs = at(7, 0)),
        )
    }

    @Test
    fun alarmsLeft_sleepTimingSaysByUntilDecided_thenTheTime() {
        val zone = ZoneId.systemDefault()
        fun at(hour: Int, minute: Int) = LocalDate.of(2026, 10, 29).atTime(hour, minute).atZone(zone).toInstant()
            .toEpochMilli()
        val config = AlarmConfig(wakeAtMin = 420, wbtbEnabled = true, wbtbAtMin = 270, wbtbAfterSleepMin = 360)
        assertEquals(
            "the early wake-up by 4:30 AM and the wake-up alarm at 7 AM", // still waiting to see you fall asleep
            alarmsLeft(config, wakeAgainAtMs = null, wbtbWontRing = false, nowMs = at(1, 0)),
        )
        assertEquals(
            "the early wake-up at 3:40 AM and the wake-up alarm at 7 AM",
            alarmsLeft(config, wakeAgainAtMs = null, wbtbWontRing = false, nowMs = at(1, 0), wbtbAtMs = at(3, 40)),
        )
    }

    @Test
    fun guideStarted_readsTheNightsAlarmLog() {
        val night = tmp.newFolder("night")
        assertFalse(guideStarted(night)) // no alarm rang
        val alarm = File(night, CsvFiles.W_ALARM.name)
        CsvWriter(alarm.bufferedWriter(), CsvFiles.W_ALARM.columns).use {
            it.row(1_000L, "WBTB", "BACK_TO_SLEEP", 0.9f, 58f, null)
        }
        assertFalse(guideStarted(night)) // the guide was not played
        alarm.appendText(csvLine(listOf(2_000L, "WBTB", "GUIDE_START", 0.9f, 58f, "steps=47;total_s=1133")))
        assertTrue(guideStarted(night))
    }
}
