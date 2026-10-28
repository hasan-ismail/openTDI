package com.opentdi.shared.stats

import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The estimate of one real night, printed to check it by eye (owner, 2026-10-28): OPENTDI_NIGHT_DIR names a watch
 * session folder, OPENTDI_NIGHT_ZONE optionally its zone. Skipped when unset. Nights are personal data: run it locally
 * and never commit the folder or what it prints.
 */
class RealNightTest {
    @Test
    fun summariseTheNightInOpentdiNightDir() {
        val dir: String? = System.getenv("OPENTDI_NIGHT_DIR")
        assumeTrue("OPENTDI_NIGHT_DIR is not set", dir != null)
        val folder = File(dir!!)
        val zone = System.getenv("OPENTDI_NIGHT_ZONE")?.let(ZoneId::of) ?: ZoneId.systemDefault()
        val s = summarizeNight(folder.name, zone) { name -> File(folder, name).takeIf { it.isFile }?.bufferedReader() }
        assertNotNull("no whole w_packets.csv row in $folder", s)
        fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().withNano(0)
        println("night of ${s!!.nightOf} ($zone), recorded ${clock(s.recordStartMs)}–${clock(s.recordEndMs)}")
        val sleep = s.sleep ?: return println("no sleep estimate")
        println("${sleep.method}: onset ${clock(sleep.onsetMs)}, final wake ${clock(sleep.finalWakeMs)}")
        println("asleep ${sleep.sleepMin / 60} h ${sleep.sleepMin % 60} min, to sleep ${sleep.latencyMin} min")
        println("awake ${sleep.wasoMin} min, ${sleep.awakenings} long wakings, share asleep ${sleep.efficiencyPct}%")
        println("heart rate asleep: mean ${sleep.hrSleepMeanBpm}, lowest minute mean ${sleep.hrSleepMinBpm}")
        val asleep = s.epochs.asleep
        val last = ((sleep.finalWakeMs - s.recordStartMs) / 60_000).toInt()
        var m = ((sleep.onsetMs - s.recordStartMs) / 60_000).toInt()
        while (m < last) {
            val from = m
            while (m < last && asleep[m] == false) m++
            if (m - from >= 5) println("awake from ${clock(s.recordStartMs + from * 60_000L)} for ${m - from} min")
            if (m == from) m++
        }
        println("wake ${s.wake}, wbtb ${s.wbtb}, cues ${s.lateCues}, ${s.reports.size} reports, ${s.promptedWakings} prompts")
    }
}
