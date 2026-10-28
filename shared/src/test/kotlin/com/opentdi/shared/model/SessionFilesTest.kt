package com.opentdi.shared.model

import com.opentdi.shared.stats.nightDate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFilesTest {
    private val id = "20261028T033928Z-bc77"

    @Test
    fun requestRoundTrips() {
        val bytes = ByteArrayOutputStream()
        val request = SessionFilesRequest(mapOf("$id/w_packets.csv" to 1234L))
        writeRequest(DataOutputStream(bytes), request)
        assertEquals(request, readRequest(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
    }

    @Test
    fun chunksArriveInOrderAndUnreadBytesAreSkipped() {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        writeChunk(out, id, "w_packets.csv", 10, 5, ByteArrayInputStream("hello".toByteArray()))
        writeChunk(out, id, "w_dream_064512.m4a", 0, 3, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        writeEnd(out)
        val seen = mutableListOf<String>()
        readChunks(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))) { session, file, offset, length, data ->
            // Reads only part of the first chunk: the rest must be skipped, not read as the next header.
            val first = data.read()
            seen += "$session/$file@$offset+$length:$first"
        }
        assertEquals(listOf("$id/w_packets.csv@10+5:${'h'.code}", "$id/w_dream_064512.m4a@0+3:1"), seen)
    }

    @Test(expected = IOException::class)
    fun aPathOutsideTheSessionFilesIsRefused() {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        writeChunk(out, id, "../../shared_prefs/x.xml", 0, 1, ByteArrayInputStream(byteArrayOf(0)))
        writeEnd(out)
        readChunks(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))) { _, _, _, _, _ -> }
    }

    @Test
    fun onlyNightFilesAndRecordingsAreSynced() {
        assertTrue(isSyncedWatchFile("w_packets.csv"))
        assertTrue(isSyncedWatchFile("w_morning.csv"))
        assertTrue(isSyncedWatchFile("w_dream_235901.m4a"))
        assertFalse(isSyncedWatchFile("w_accel_raw.csv"))
        assertFalse(isSyncedWatchFile("w_ppg_raw.csv"))
        assertFalse(isSyncedWatchFile("w_dream_x.m4a"))
    }

    @Test
    fun aNightRunsNoonToNoon() {
        val zone = ZoneId.of("America/New_York")
        // 2026-10-28 03:39 UTC is 23:39 on the 27th in New York; 06:15 the next morning belongs to the same night.
        assertEquals(LocalDate.of(2026, 10, 27), nightDate(1_793_158_740_000, zone))
        assertEquals(LocalDate.of(2026, 10, 27), nightDate(1_793_182_500_000, zone))
    }
}
