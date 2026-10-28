package com.opentdi.mobile.session

import android.app.ApplicationExitInfo
import com.opentdi.shared.model.StateReason
import com.opentdi.shared.model.TdiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionRetentionTest {
    private fun id(day: Int, second: Int) = "202610%02dT0000%02dZ-%04x".format(day, second, day * 100 + second)

    @Test
    fun keepsTheNewest30_deletesTheOldest() {
        val names = (1..20).flatMap { day -> listOf(id(day, 1), id(day, 2)) }.shuffled() // 40 sessions
        val deleted = sessionDirsToDelete(names)
        assertEquals((1..5).flatMap { day -> listOf(id(day, 1), id(day, 2)) }, deleted)
    }

    @Test
    fun thirtyOrFewer_deletesNothing() {
        assertEquals(emptyList<String>(), sessionDirsToDelete((1..15).flatMap { listOf(id(it, 1), id(it, 2)) }))
        assertEquals(emptyList<String>(), sessionDirsToDelete(emptyList()))
    }

    @Test
    fun otherNames_areNeverChosen_andDoNotCount() {
        val sessions = (1..31).map { id(1 + it % 28, it % 60) }.distinct()
        val others = listOf("watch", "notes.txt", "20261001T000000Z-zzzz", "20261001T000000Z-0001-copy", "")
        val deleted = sessionDirsToDelete(others + sessions)
        assertEquals(sessions.sorted().take(sessions.size - 30), deleted)
        assertTrue(deleted.none { it in others })
    }

    private val transitions = "wall_ms,elapsed_ms,from,to,reason,cycle,driver\n" +
        "1000,0,IDLE,AWAKE_INCUBATING,SESSION_START,0,TIMED\n" +
        "601000,600000,AWAKE_INCUBATING,HYPNAGOGIA_DETECTED,TIMED_DWELL,1,TIMED\n"
    private val userKill = ApplicationExitInfo.REASON_USER_REQUESTED

    @Test
    fun userKillRow_closesTheKilledSession() {
        val expected = listOf<Any?>(
            700_000L, null, "HYPNAGOGIA_DETECTED", TdiState.SESSION_COMPLETE, StateReason.USER_ABORT, "1", "TIMED",
        )
        assertEquals(expected, userKillRow(transitions, 601_050, userKill, 700_000))
    }

    @Test
    fun userKillRow_nullUnlessAUserKillEndedAnOpenSession() {
        val closed = transitions + "650000,649000,HYPNAGOGIA_DETECTED,SESSION_COMPLETE,USER_ABORT,1,TIMED\n"
        assertNull(userKillRow(closed, 650_050, userKill, 700_000)) // already complete
        assertNull(userKillRow(transitions, 601_050, ApplicationExitInfo.REASON_CRASH, 700_000))
        assertNull(userKillRow(transitions, 701_000, userKill, 700_000)) // written after that exit: a later session
        assertNull(userKillRow(transitions.substringBefore('\n') + "\n", 1_000, userKill, 700_000)) // header only
        assertNull(userKillRow(transitions.dropLast(6), 601_050, userKill, 700_000)) // last row cut by the kill
    }

    @Test
    fun deleteOldSessions_removesOnlyOldSessionDirs() {
        val filesDir = Files.createTempDirectory("otdi").toFile()
        try {
            val root = File(filesDir, SESSIONS_DIR).apply { mkdirs() }
            val ids = (1..32).map { id(1 + (it - 1) / 2, it % 2) }
            ids.forEach { File(root, it).mkdirs(); File(root, "$it/p_audio.csv").writeText("x") }
            File(root, "keep-me").mkdirs()
            File(root, "20260101T000000Z-0000").writeText("a file, not a session directory")
            File(filesDir, "cues").mkdirs()

            deleteOldSessions(filesDir)

            val left = root.list()!!.toSet()
            assertEquals(ids.sorted().drop(2).toSet() + "keep-me" + "20260101T000000Z-0000", left)
            assertTrue(File(filesDir, "cues").isDirectory)
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
