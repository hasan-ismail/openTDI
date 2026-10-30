package com.opentdi.mobile.stats

import com.opentdi.mobile.session.SESSIONS_DIR
import com.opentdi.shared.stats.AlarmOutcome
import com.opentdi.shared.stats.DreamReport
import com.opentdi.shared.stats.Epochs
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.stats.SUMMARY_VERSION
import com.opentdi.shared.stats.WbtbOutcome
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightsTest {
    private val zone = ZoneId.of("America/New_York")

    private fun ms(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("otdi").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fingerprint_isEveryCsvNameAndLength() = withDir { dir ->
        File(dir, "w_packets.csv").writeText("12345")
        File(dir, "w_alarm.csv").writeText("")
        File(dir, "w_dream_010203.m4a").writeBytes(ByteArray(3))
        File(dir, SUMMARY_FILE).writeText("{}")
        assertEquals(mapOf("w_packets.csv" to 5L, "w_alarm.csv" to 0L), fingerprint(dir))
        File(dir, "w_packets.csv").appendText("6")
        assertEquals(6L, fingerprint(dir)["w_packets.csv"])
        assertEquals(emptyMap<String, Long>(), fingerprint(File(dir, "missing")))
    }

    private val transitions = "wall_ms,elapsed_ms,from,to,reason,cycle,driver\n" +
        "1000,0,IDLE,AWAKE_INCUBATING,SESSION_START,0,TIMED\n" +
        "601000,600000,AWAKE_INCUBATING,HYPNAGOGIA_DETECTED,TIMED_DWELL,1,TIMED\n" +
        "700000,699000,RE_INCUBATING,AWAKE_INCUBATING,CYCLE_COMPLETED,1,TIMED\n" +
        "1300000,1299000,RE_INCUBATING,SESSION_COMPLETE,MAX_CYCLES,2,TIMED\n" +
        "1300050,13" // cut short when the app died
    private val audio = "wall_ms,elapsed_ms,cycle,event,cue,route,routed_type,gain,fade_ms,focus,dnd_filter," +
        "dnd_categories,stream_index,detail\n" +
        "1000,0,0,DND_STATE,,NIGHTSTAND_SPEAKER,,,,,1,0,7,\n" +
        "2000,1000,0,CUE_START,INITIAL_INCUBATION,NIGHTSTAND_SPEAKER,2,0.5,2000,GAIN_TRANSIENT,,,7,\n" +
        "9000,8000,0,CUE_END,INITIAL_INCUBATION,NIGHTSTAND_SPEAKER,2,0.5,2000,,,,7,\n" +
        "602000,601000,1,CUE_START,WAKE_PROMPT,NIGHTSTAND_SPEAKER,2,0.5,2000,GAIN_TRANSIENT,,,7,\n" +
        "650000,649000,1,CUE_SKIPPED,RE_INCUBATION,NIGHTSTAND_SPEAKER,,,,,,,7,dnd_muted\n"
    private val config = "key,value\napp.version,0.2.0\ntheme,\"a tree, at night\"\n"

    @Test
    fun phoneSession_fromItsCsvs() {
        val s = phoneSession("20261028T030000Z-0001", transitions, audio, config)!!
        assertEquals(1000L, s.startMs)
        assertEquals(1_300_000L, s.endMs)
        assertEquals(2, s.loops) // one CYCLE_COMPLETED, and the last loop ends the session (MAX_CYCLES)
        assertEquals(2, s.cues) // a skipped cue never began to play
    }

    @Test
    fun phoneSession_nullWithoutACompleteRow_orForAQuickTest() {
        assertNull(phoneSession("x", transitions.substringBefore('\n') + "\n", audio, config))
        val bare = phoneSession("x", transitions, "", "key,value\napp.version,0.2.0\n")!!
        assertEquals(0, bare.cues)
        assertNull(phoneSession("x", transitions, audio, "key,value\nsession.quickTest,1\n")) // no night
        assertEquals(0, phoneSession("x", transitions, "", "key,value\nsession.quickTest,0\n")!!.cues)
    }

    @Test
    fun transitions_ofThePhoneSessionsDuringARecording_joinedWithOneHeader() = withDir { sessions ->
        val header = "wall_ms,elapsed_ms,from,to,reason,cycle,driver\n"
        val rows = "9000,0,AWAKE_INCUBATING,HYPNAGOGIA_DETECTED,TIMED_DWELL,1,TIMED\n12000,3000,HYPNA" // died mid-row
        fun session(id: String, startMs: Long, endMs: Long, written: Boolean = true) =
            PhoneSession(id, startMs, endMs, 0, 0).also {
                if (written) File(sessions, "$id/p_transitions.csv").apply { parentFile?.mkdirs() }.writeText(header + rows)
            }
        val before = session("20261028T020000Z-0001", 1_000, 5_000)
        val during = session("20261028T030000Z-0002", 9_000, 12_000)
        val after = session("20261028T040000Z-0003", 20_000, 25_000)
        val deleted = session("20261028T035000Z-0004", 11_000, 13_000, written = false) // deleted on the Sessions screen

        val files = transitionsDuring(listOf(before, during, after, deleted), sessions, 10_000, 20_000)

        assertEquals(listOf(File(sessions, "${during.id}/p_transitions.csv")), files)
        assertEquals("$header$rows\n$rows\n", joinTransitions(files + files)) // the header once
        assertNull(joinTransitions(emptyList()))
    }

    private fun summary(id: String, from: String, to: String) = NightSummary(
        sessionId = id, version = SUMMARY_VERSION, nightOf = from.take(10), zone = zone.id,
        recordStartMs = ms(from), recordEndMs = ms(to),
        epochs = Epochs(ms(from), movement = emptyList(), hrBpm = emptyList(), asleep = emptyList()),
    )

    private fun session(id: String, at: String, minutes: Int, loops: Int, cues: Int) =
        PhoneSession(id, ms(at), ms(at) + minutes * 60_000L, loops, cues)

    @Test
    fun nights_joinNoonToNoon_newestFirst() {
        val oct27 = LocalDate.of(2026, 10, 27)
        val longer = summary("a", "2026-10-27T22:30", "2026-10-28T04:30")
        val earlyReport = DreamReport(ms("2026-10-28T04:35"), "WBTB")
        val wholeNight = longer.copy(reports = listOf(earlyReport), wbtb = WbtbOutcome(ms("2026-10-28T04:30")))
        // Restarted after 4:30: the wake-up alarm and the morning report went to the new folder.
        val morningReport = DreamReport(ms("2026-10-28T07:05"), "WAKE", recalled = "YES")
        val wake = AlarmOutcome(ms("2026-10-28T07:00"))
        val restart = summary("b", "2026-10-28T04:40", "2026-10-28T07:30")
            .copy(reports = listOf(morningReport), wake = wake)
        val morning = File("w_dream_064512.m4a")
        val evening = File("w_dream_235901.m4a")
        val copies = listOf(
            WatchNight(oct27, restart, emptyList()),
            WatchNight(oct27, wholeNight, listOf(morning, evening)),
            WatchNight(LocalDate.of(2026, 10, 25), null, emptyList()), // too little to summarise: no row
            WatchNight(LocalDate.of(2026, 10, 24), null, listOf(File("w_dream_070000.m4a"))),
        )
        val sessions = listOf(
            session("p2", "2026-10-28T00:30", 30, loops = 1, cues = 2), // after midnight: same night
            session("p1", "2026-10-27T22:50", 50, loops = 2, cues = 5),
            session("p0", "2026-10-26T23:00", 40, loops = 3, cues = 7),
        )

        val rows = joinNights(copies, sessions, zone)

        assertEquals(listOf(oct27, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 24)), rows.map { it.night })
        with(rows[0]) {
            // The longest recording's sleep, with every part's reports and alarms.
            assertEquals(wholeNight.copy(reports = listOf(earlyReport, morningReport), wake = wake), watch)
            assertEquals(PhoneNight(ms("2026-10-27T22:50"), ms("2026-10-28T01:00"), 3, 7), phone)
            assertEquals(listOf(evening, morning), recordings) // 23:59 before 06:45
        }
        assertNull(rows[1].watch)
        assertNull(rows[2].phone)
        assertEquals(1, rows[2].recordings.size)
    }

    @Test
    fun nights_keepTheLiveOnsetFromAnyPart() {
        val oct27 = LocalDate.of(2026, 10, 27)
        val onset = ms("2026-10-27T23:20")
        // A crash and restart: the onset was logged in the shorter first folder only.
        val first = summary("a", "2026-10-27T23:00", "2026-10-28T00:30").copy(liveOnsetMs = onset)
        val longer = summary("b", "2026-10-28T00:40", "2026-10-28T07:30")
        val copies = listOf(WatchNight(oct27, longer, emptyList()), WatchNight(oct27, first, emptyList()))
        assertEquals(onset, joinNights(copies, emptyList(), zone).single().watch!!.liveOnsetMs)
    }

    @Test
    fun phoneSessions_keepThoseWhoseFolderIsGone() = withDir { filesDir ->
        val id = "20261028T030000Z-0001"
        val dir = File(filesDir, "$SESSIONS_DIR/$id").apply { mkdirs() }
        File(dir, "p_transitions.csv").writeText(transitions)
        File(dir, "p_audio.csv").writeText(audio)
        File(dir, "p_config.csv").writeText(config)
        val read = NightsRepository.phoneSessions(filesDir)
        assertEquals(listOf(id), read.map { it.id })

        dir.deleteRecursively() // no folder left at all, as after deleting every listed one
        assertEquals(read, NightsRepository.phoneSessions(filesDir))
    }

    @Test
    fun have_listsCopiedFiles_andPrunedCsvsAtTheirCachedSize() = withDir { root ->
        val old = File(root, "20260601T030000Z-0001").apply { mkdirs() }
        writeCache(old, SummaryCache(SUMMARY_VERSION, mapOf("w_packets.csv" to 500L), pruned = true))
        File(old, "w_dream_064512.m4a").writeBytes(ByteArray(3))
        val recent = File(root, "20261028T030000Z-0002").apply { mkdirs() }
        writeCache(recent, SummaryCache(SUMMARY_VERSION, mapOf("w_packets.csv" to 2L)))
        File(recent, "w_packets.csv").writeText("12345")
        File(recent, "w_accel_raw.csv").writeText("stays on the watch")
        assertEquals(
            mapOf(
                "${old.name}/w_packets.csv" to 500L,
                "${old.name}/w_dream_064512.m4a" to 3L,
                "${recent.name}/w_packets.csv" to 5L,
            ),
            have(root),
        )
    }

    @Test
    fun chunks_continueAtTheirOffset_andStartOverAtZero() = withDir { root ->
        val id = "20261028T033928Z-bc77"
        val copied = AtomicLong()
        fun write(name: String, offset: Long, text: String) =
            writeChunk(root, id, name, offset, ByteArrayInputStream(text.toByteArray()), copied)
        val file = File(root, "$id/w_packets.csv")
        assertTrue(write("w_packets.csv", 0, "abc"))
        assertTrue(write("w_packets.csv", 3, "def"))
        assertEquals("abcdef", file.readText())
        assertTrue(write("w_packets.csv", 0, "xy")) // shorter on the watch: sent whole
        assertEquals("xy", file.readText())
        assertFalse(write("w_packets.csv", 5, "zz")) // past the end, as for a pruned CSV: left unread
        assertFalse(write("w_alarm.csv", 9, "z"))
        assertEquals("xy", file.readText())
        assertFalse(File(root, "$id/w_alarm.csv").exists()) // no empty file for a summary to misread
        assertEquals(8L, copied.get())
    }

    @Test(expected = IOException::class)
    fun aChunkOutsideTheWatchFolder_isRefused() = withDir { dir ->
        val root = File(dir, WATCH_DIR).apply { mkdirs() }
        writeChunk(root, "..", "w_packets.csv", 0, ByteArrayInputStream(byteArrayOf(1)), AtomicLong())
    }
}
