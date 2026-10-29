package com.opentdi.wear

import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.csv.csvLine
import com.opentdi.shared.csv.readCsv
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(noDream, shownQuestions(emptyMap(), guided = false, signs = false))
        assertEquals(noDream, shownQuestions(mapOf(0 to "WHITE"), guided = false, signs = false)) // no content to judge
        assertEquals(dream + ODD, shownQuestions(mapOf(0 to "YES"), guided = false, signs = false))
        assertEquals(noDream + GUIDE_SLEEP, shownQuestions(emptyMap(), guided = true, signs = false))
        assertEquals(noDream, shownQuestions(mapOf(0 to "NO"), guided = false, signs = true)) // no dream, no sign in it
        assertEquals(
            dream + listOf(GUIDE_SLEEP, ODD, SIGN_SEEN),
            shownQuestions(mapOf(0 to "YES"), guided = true, signs = true),
        )
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
