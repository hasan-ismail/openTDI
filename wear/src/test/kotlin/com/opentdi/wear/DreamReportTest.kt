package com.opentdi.wear

import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DreamReportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun report(wallMs: Long, nightmare: String?) =
        listOf<Any?>(wallMs, "WAKE", null, "YES", 2, 0, nightmare, "NO", "NO", "NO", 3, "NO", "NO")

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
}
