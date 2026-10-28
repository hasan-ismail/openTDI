package com.opentdi.shared.csv

import com.opentdi.shared.model.SensorSource
import java.io.StringReader
import java.io.StringWriter
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    private fun write(columns: List<String>, vararg rows: List<Any?>): String {
        val out = StringWriter()
        CsvWriter(out, columns).use { csv -> rows.forEach { csv.row(*it.toTypedArray()) } }
        return out.toString()
    }

    @Test
    fun floats_roundTripBitExact() {
        val random = Random(20261027)
        val specials = listOf(0f, -0f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, 1.17549435E-38f, 0.1f, 65.1f)
        val floats = specials + List(20_000) { Float.fromBits(random.nextInt()) }.filterNot { it.isNaN() }
        val text = write(listOf("f", "d"), *floats.map { listOf(it, it.toDouble() * 1.000001) }.toTypedArray())
        val rows = readCsv(StringReader(text)).drop(1)
        assertEquals(floats.size, rows.size)
        floats.forEachIndexed { i, f ->
            assertEquals(f.toRawBits(), rows[i][0]!!.toFloat().toRawBits())
            val d = f.toDouble() * 1.000001
            assertEquals(d.toRawBits(), rows[i][1]!!.toDouble().toRawBits())
        }
        val nonFinite = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        val special = readCsv(StringReader(write(listOf("a", "b", "c"), nonFinite)))[1]
        assertTrue(special[0]!!.toFloat().isNaN())
        assertEquals(Float.POSITIVE_INFINITY, special[1]!!.toFloat())
        assertEquals(Float.NEGATIVE_INFINITY, special[2]!!.toFloat())
    }

    @Test
    fun dialect_nullsBooleansListsEnums() {
        val text = write(
            listOf("ts_ms", "hr_bpm", "ibi_ms", "jerk", "limp", "source", "detail"),
            listOf(1_793_000_000_000L, null, listOf(812, 830, 845), true, false, SensorSource.SAMSUNG_SDK, ""),
            listOf(1_793_000_001_000L, 65.1f, emptyList<Int>(), false, true, SensorSource.ANDROID_SENSORS, null),
        )
        assertEquals(
            "ts_ms,hr_bpm,ibi_ms,jerk,limp,source,detail\n" +
                "1793000000000,,812;830;845,1,0,SAMSUNG_SDK,\n" +
                "1793000001000,65.1,,0,1,ANDROID_SENSORS,\n",
            text,
        )
        val rows = readCsv(StringReader(text))
        assertEquals(listOf("ts_ms", "hr_bpm", "ibi_ms", "jerk", "limp", "source", "detail"), rows[0])
        assertEquals(listOf("1793000000000", null, "812;830;845", "1", "0", "SAMSUNG_SDK", null), rows[1])
        assertEquals(listOf(812, 830, 845), rows[1][2]!!.split(";").map { it.toInt() })
        assertEquals(listOf("1793000001000", "65.1", null, "0", "1", "ANDROID_SENSORS", null), rows[2])
    }

    @Test
    fun quoting_rfc4180() {
        val tricky = listOf("a,b", "say \"hi\"", "line1\nline2", "cr\rhere", "\"", ",", "plain", "semi;colon")
        val text = write(tricky.indices.map { "c$it" }, tricky)
        assertEquals(
            "\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\",\"cr\rhere\",\"\"\"\",\",\",plain,semi;colon\n",
            text.substringAfter('\n'),
        )
        assertEquals(tricky, readCsv(StringReader(text))[1])
    }

    @Test
    fun reader_edgeCases() {
        assertEquals(listOf(listOf(null, "x", null)), readCsv(StringReader(",x,\n")))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), readCsv(StringReader("a,b\r\n1,2")))
        assertEquals(listOf(listOf("")), readCsv(StringReader("\"\"\n"))) // a quoted empty field is not null
        assertEquals(emptyList<List<String?>>(), readCsv(StringReader("")))
        assertThrows(IllegalArgumentException::class.java) { readCsv(StringReader("\"open")) }
    }

    @Test
    fun writer_lfOnlyAndChecksWidth() {
        val text = write(listOf("a", "b"), listOf(1, 2), listOf(3, 4))
        assertFalse(text.contains('\r'))
        assertEquals("a,b\n1,2\n3,4\n", text)
        val csv = CsvWriter(StringWriter(), listOf("a", "b"))
        assertThrows(IllegalArgumentException::class.java) { csv.row(1) }
    }

    @Test
    fun csvFiles_namesAndHeaders() {
        val all = listOf(
            CsvFiles.W_CONFIG, CsvFiles.W_ACCEL_RAW, CsvFiles.W_SENSOR_RAW, CsvFiles.W_PACKETS, CsvFiles.W_ACCEL_EVENTS,
            CsvFiles.W_ENGINE, CsvFiles.W_INPUTS, CsvFiles.W_TRANSITIONS, CsvFiles.W_LINK, CsvFiles.P_CONFIG,
            CsvFiles.P_FRAMES, CsvFiles.P_TRANSITIONS, CsvFiles.P_AUDIO, CsvFiles.P_LINK, CsvFiles.W_SKIN_TEMP,
            CsvFiles.W_SPO2, CsvFiles.W_PPG_RAW, CsvFiles.W_ALARM, CsvFiles.W_MORNING,
        )
        assertEquals(19, all.map { it.name }.toSet().size)
        all.forEach { f ->
            assertEquals(f.name, f.columns.size, f.columns.toSet().size)
            assertTrue(f.name, f.columns.all { it.matches(Regex("[a-z0-9_]+")) })
        }
        assertEquals(
            listOf("arrival_wall_ms", "sdk_ts_ms", "tracker", "hr_bpm", "hr_status", "ibi_ms", "ibi_status", "eda_us",
                "eda_status", "batch_size", "late"),
            CsvFiles.W_SENSOR_RAW.columns,
        )
        // New packet columns go at the end of w_packets.csv, so older column positions never move.
        assertEquals(listOf("emitted_wall_ms", "skin_temp_c", "spo2_pct"), CsvFiles.W_PACKETS.columns.takeLast(3))
        assertEquals("event", CsvFiles.W_SPO2.columns[5])
        // The watch's DreamReportScreen writes one column per question, in this order.
        assertEquals(
            "wall_ms,after,recording,recalled,insight,control,nightmare,false_awakening,cue_in_dream,cue_woke," +
                "refreshed,sleep_paralysis,unusual_night",
            CsvFiles.W_MORNING.columns.joinToString(","),
        )
        assertEquals(
            "rx_wall_ms,batch_seq,frame_seq,ts_ms,source,hr_bpm,hr_status,ibi_ms,rmssd_ms,eda_us,eda_status," +
                "accel_mean_g,accel_rms_g,accel_peak_g,stillness,jerk,limp,skin_temp_c,spo2_pct,state,state_reason," +
                "completed_cycles,confidence,gate,hr_sig,rmssd_sig,eda_bonus,jerk_bonus,limp_bonus,hold_s,hold_req_s",
            CsvFiles.P_FRAMES.columns.joinToString(","),
        )
        assertEquals("driver", CsvFiles.P_TRANSITIONS.columns.last())
        assertEquals(CsvFiles.W_CONFIG.columns, CsvFiles.P_CONFIG.columns)
        assertEquals(CsvFiles.W_LINK.columns, CsvFiles.P_LINK.columns)
    }
}
