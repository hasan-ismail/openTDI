package com.opentdi.shared.csv

import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionEndReason
import com.opentdi.shared.model.SessionMode
import com.opentdi.shared.model.StateReason
import com.opentdi.shared.model.TdiState
import com.opentdi.shared.model.TimedCueConfig
import com.opentdi.shared.model.newSessionId
import com.opentdi.shared.timed.TimedCueScheduler
import com.opentdi.shared.timed.TimedEvent
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test

class SessionDirsTest {
    @Test
    fun sessionIds_matchOnlyTheNewSessionIdForm() {
        assertTrue(isSessionId("20261028T011315Z-f935"))
        assertTrue(isSessionId(newSessionId(1_793_000_000_000L, Random(1))))
        val others = listOf(
            "", "watch", "20261028T011315Z-F935", "20261028T011315Z-f93", "20261028T011315Z-f9350",
            "20261028T011315-f935", "../20261028T011315Z-f935", "20261028T011315Z-f935/..", " 20261028T011315Z-f935",
        )
        others.forEach { assertFalse(it, isSessionId(it)) }
    }

    @Test
    fun startTime_isTheUtcTimeTheIdBeginsWith() {
        assertEquals(Instant.parse("2026-10-28T01:13:15Z").toEpochMilli(), sessionStartMs("20261028T011315Z-f935"))
        val ms = 1_793_000_123_456L
        assertEquals(ms / 1000 * 1000, sessionStartMs(newSessionId(ms, Random(7))))
        assertNull(sessionStartMs("20261328T011315Z-f935")) // month 13
        assertNull(sessionStartMs("20261028T251315Z-f935")) // hour 25
        assertNull(sessionStartMs("watch"))
    }

    @Test
    fun sessionDirs_newestFirst_sessionDirectoriesOnly() = withFilesDir { root ->
        val ids = listOf("20261027T220000Z-0001", "20261028T011315Z-f935", "20261026T000000Z-ffff")
        ids.forEach { File(root, "$it/w_packets.csv").apply { parentFile.mkdirs() }.writeText("12345") }
        File(root, "watch").mkdirs()
        File(root, "20261029T000000Z-0000").writeText("a file, not a session directory")
        assertEquals(ids.sortedDescending(), sessionDirs(root).map { it.name })
        assertEquals(5L, sizeOnDisk(File(root, ids[0])))
        assertEquals(emptyList<File>(), sessionDirs(File(root, "missing")))
    }

    @Test
    fun delete_onlySessionDirsDirectlyInRoot_neverTheRunningOne() = withFilesDir { root ->
        val (a, b, running) = listOf("20261027T220000Z-000a", "20261027T221500Z-000b", "20261028T011315Z-f935")
        listOf(a, b, running).forEach { File(root, "$it/sub/p_audio.csv").apply { parentFile.mkdirs() }.writeText("x") }
        File(root, "watch").mkdirs()
        val outside = File(root.parentFile, "20261027T220000Z-0ff5").apply { mkdirs() }
        val notADir = File(root, "20261027T230000Z-00ff").apply { writeText("a file") }
        val asked = listOf(a, b, a, running, "watch", "../${outside.name}", notADir.name, "20261027T235959Z-dead")

        assertEquals(2, deleteSessionDirs(root, asked, runningId = running))

        assertEquals(setOf(running, "watch", notADir.name), root.list()!!.toSet())
        assertTrue(File(root, "$running/sub/p_audio.csv").isFile)
        assertTrue(outside.isDirectory)
    }

    @Test
    fun delete_neverFollowsASymbolicLinkOutOfRoot() = withFilesDir { root ->
        val outside = File(root.parentFile, "outside").apply { mkdirs() }
        File(outside, "keep.csv").writeText("x")
        val linked = File(root, "20261027T220000Z-1111")
        val session = File(root, "20261027T221500Z-2222").apply { mkdirs() }
        try {
            Files.createSymbolicLink(linked.toPath(), outside.toPath())
            Files.createSymbolicLink(File(session, "link").toPath(), outside.toPath())
        } catch (e: Exception) { // Windows creates links only in Developer Mode or as administrator
            assumeNoException(e)
        }
        val resolvesLinks = linked.canonicalFile == outside.canonicalFile // Linux and Android do; Windows may not

        val deleted = deleteSessionDirs(root, listOf(linked.name, session.name), runningId = null)

        assertFalse(session.exists())
        assertTrue(File(outside, "keep.csv").isFile) // through neither link
        if (resolvesLinks) assertEquals(1, deleted) // the linked "session" resolves outside root: refused
    }

    @Test
    fun phoneSummary_maxCycles_countsTheCompletedCyclesAsTheSchedulerDoes() {
        val s = TimedCueScheduler("any", timedConfig)
        val text = transitionsCsv(s, failFirstWakePrompt = true) // one aborted cycle on the way

        val summary = phoneSessionSummary(text.lineSequence())

        assertEquals(PhoneSessionSummary(lastElapsedMs(text), StateReason.MAX_CYCLES, 3), summary)
        assertEquals(s.completedCycles, summary.completedCycles)
    }

    @Test
    fun phoneSummary_timeLimitAndStop() {
        val limited = TimedCueScheduler("any", timedConfig.copy(maxCycles = 10, sessionMaxMin = 15))
        val limitedText = transitionsCsv(limited)
        assertEquals(
            PhoneSessionSummary(lastElapsedMs(limitedText), StateReason.TIME_LIMIT, limited.completedCycles),
            phoneSessionSummary(limitedText.lineSequence()),
        )
        assertTrue(limited.completedCycles in 2..9)

        val stopped = TimedCueScheduler("any", timedConfig)
        val stoppedText = transitionsCsv(stopped, stopAtMs = 250_000) // in cycle 2
        assertEquals(
            PhoneSessionSummary(250_000, StateReason.USER_ABORT, 1),
            phoneSessionSummary(stoppedText.lineSequence()),
        )
        assertEquals(1, stopped.completedCycles)
    }

    @Test
    fun phoneSummary_afterACrash_hasNoEnd_andSkipsACutRow() {
        val text = transitionsCsv(TimedCueScheduler("any", timedConfig))
        val lines = text.lines().filter { it.isNotEmpty() && !it.contains(TdiState.SESSION_COMPLETE.name) }
        val crashed = lines + "${WALL_START_MS + 9_999_999},9999999,AWAKE_INC" // the app died mid-row

        val summary = phoneSessionSummary(crashed.asSequence())

        assertEquals(lines.last().split(',')[1].toLong(), summary.durationMs)
        assertNull(summary.endReason)
        assertEquals(PhoneSessionSummary(null, null, 0), phoneSessionSummary(lines.take(1).asSequence()))
        assertEquals(PhoneSessionSummary(null, null, 0), phoneSessionSummary(emptySequence()))
    }

    @Test
    fun watchSummary_wholeRowsOnly_fromTheFirstToTheLastPacket() {
        val text = packetsCsv(3600)

        assertEquals(WatchSessionSummary(3_599_000, 3600), watchSessionSummary(text.lineSequence()))
        // The app died mid-row: a cut ts_ms would otherwise become the "last" packet.
        assertEquals(WatchSessionSummary(3_599_000, 3600), watchSessionSummary((text + "17930").lineSequence()))
        assertEquals(WatchSessionSummary(null, 0), watchSessionSummary(text.lineSequence().take(1)))
        assertEquals(WatchSessionSummary(null, 0), watchSessionSummary(emptySequence()))
        val old = packetsCsv(3600, old = true) // a night logged before angle_z_deg: one column fewer
        assertEquals(WatchSessionSummary(3_599_000, 3600), watchSessionSummary(old.lineSequence()))
    }

    @Test
    fun watchDuration_readsOnlyTheFileEnds_asTheFullSummary() = withFilesDir { root ->
        val file = File(root, CsvFiles.W_PACKETS.name)
        fun durationOf(text: String) = watchSessionDurationMs(file.apply { writeText(text) })

        assertEquals(3_599_000L, durationOf(packetsCsv(3600))) // far longer than the tail it reads
        assertEquals(3_599_000L, durationOf(packetsCsv(3600) + "17930")) // the app died mid-row
        assertEquals(2_000L, durationOf(packetsCsv(3))) // shorter than the tail: the header is in it
        assertEquals(0L, durationOf(packetsCsv(1)))
        assertNull(durationOf(packetsCsv(0)))
        assertEquals(3_599_000L, durationOf(packetsCsv(3600, old = true)))
    }

    /** w_packets.csv as LoggingSession writes it, one packet a second; [old]: before its last column, angle_z_deg. */
    private fun packetsCsv(rows: Int, old: Boolean = false): String {
        val columns = CsvFiles.W_PACKETS.columns.let { if (old) it.dropLast(1) else it }
        val out = StringWriter()
        CsvWriter(out, columns).use { csv ->
            for (i in 0 until rows) {
                val ts = 1_793_000_000_000L + i * 1000L
                val hr = if (i % 7 == 0) null else 61.5f // lost beats leave empty fields, still whole rows
                val fields = listOf(
                    ts, i, SensorSource.SAMSUNG_SDK, hr, 1, listOf(812, 830), 31.2f, 0.42f, 0, 1.0f, 0.003f, 0.01f,
                    0.97f, false, false, 0.5f, 25, ts + 1200, 33.1f, null, -12.5f,
                )
                csv.row(*fields.take(columns.size).toTypedArray())
            }
        }
        return out.toString()
    }

    private val timedConfig = SessionConfig(
        mode = SessionMode.TIMED_CUES,
        maxCycles = 3,
        countdownS = 60,
        timed = TimedCueConfig(
            firstSleepMinMin = 1, firstSleepMaxMin = 1, betweenSleepS = 60, phase1ReportWindowS = 30,
        ),
    )

    /** p_transitions.csv as NapSessionService writes it, every cue ending 1 s after it starts. */
    private fun transitionsCsv(
        s: TimedCueScheduler,
        failFirstWakePrompt: Boolean = false,
        stopAtMs: Long? = null,
    ): String {
        val out = StringWriter()
        CsvWriter(out, CsvFiles.P_TRANSITIONS.columns).use { csv ->
            var playing: PromptCue? = null
            var failWake = failFirstWakePrompt
            fun take(events: List<TimedEvent>) = events.forEach { e ->
                if (e is TimedEvent.StateChanged) {
                    csv.row(WALL_START_MS + e.atMs, e.atMs, e.from, e.to, e.reason, e.cycle, "TIMED")
                }
                if (e is TimedEvent.PlayCue) playing = e.cue
            }
            take(s.start())
            var now = 0L
            while (s.state != TdiState.SESSION_COMPLETE) {
                now += 1000
                val cue = playing
                playing = null
                take(
                    when {
                        stopAtMs != null && now >= stopAtMs -> s.abort(SessionEndReason.USER, now)
                        cue == null -> s.onTick(now)
                        cue == PromptCue.WAKE_PROMPT && failWake -> s.onCueFailed(cue, now).also { failWake = false }
                        else -> s.onCueFinished(cue, now)
                    },
                )
            }
        }
        return out.toString()
    }

    private fun lastElapsedMs(text: String) = readCsv(StringReader(text)).last()[1]!!.toLong()

    private fun withFilesDir(test: (root: File) -> Unit) {
        val filesDir = Files.createTempDirectory("otdi").toFile()
        try {
            test(File(filesDir, "sessions").apply { mkdirs() })
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private companion object {
        const val WALL_START_MS = 1_793_000_000_000L
    }
}
