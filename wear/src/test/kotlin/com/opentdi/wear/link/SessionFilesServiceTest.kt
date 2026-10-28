package com.opentdi.wear.link

import com.opentdi.shared.model.readChunks
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionFilesServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val id = "20261028T033928Z-bc77"

    @Test
    fun chunksToSend_sendsOnlyWhatThePhoneLacks() {
        val files = mapOf(
            "$id/w_packets.csv" to 500L, // grew: the new bytes only
            "$id/w_engine.csv" to 300L, // the phone has all of it
            "$id/w_alarm.csv" to 40L, // shorter than the phone's copy: whole
            "$id/w_morning.csv" to 80L, // new to the phone: whole
            "$id/w_dream_064512.m4a" to 9000L, // a recording that changed: whole, its header was rewritten
        )
        val have = mapOf(
            "$id/w_packets.csv" to 200L,
            "$id/w_engine.csv" to 300L,
            "$id/w_alarm.csv" to 90L,
            "$id/w_dream_064512.m4a" to 4000L,
        )
        assertEquals(
            listOf(
                FileChunk("$id/w_packets.csv", 200, 300),
                FileChunk("$id/w_alarm.csv", 0, 40),
                FileChunk("$id/w_morning.csv", 0, 80),
                FileChunk("$id/w_dream_064512.m4a", 0, 9000),
            ),
            chunksToSend(files, have),
        )
    }

    @Test
    fun sendSessionFiles_sendsTheNewBytesOfSyncedFilesThenTheEnd() {
        val dir = File(tmp.root, id).apply { mkdirs() }
        File(dir, "w_packets.csv").writeText("header\nrow1\nrow2\n")
        File(dir, "w_accel_raw.csv").writeText("stays on the watch")
        File(tmp.root, "reports").mkdirs() // not a session folder
        val bytes = ByteArrayOutputStream()
        sendSessionFiles(tmp.root, mapOf("$id/w_packets.csv" to 7L), DataOutputStream(bytes))
        val got = mutableListOf<String>()
        readChunks(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))) { session, file, offset, length, data ->
            got += "$session/$file@$offset+$length:${data.readBytes().decodeToString()}"
        }
        assertEquals(listOf("$id/w_packets.csv@7+10:row1\nrow2\n"), got)
    }
}
