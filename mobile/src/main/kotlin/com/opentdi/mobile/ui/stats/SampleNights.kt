package com.opentdi.mobile.ui.stats

import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.stats.PhoneNight
import com.opentdi.shared.stats.AlarmOutcome
import com.opentdi.shared.stats.DreamReport
import com.opentdi.shared.stats.Epochs
import com.opentdi.shared.stats.LateCueOutcome
import com.opentdi.shared.stats.NightSummary
import com.opentdi.shared.stats.SUMMARY_VERSION
import com.opentdi.shared.stats.SleepEstimate
import com.opentdi.shared.stats.WbtbOutcome
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Made-up nights that fill every Stats chart before real nights exist (DEBUG builds only; owner, 2026-10-28). The same
 * [today] and [seed] always give the same nights, newest first like NightsRepository.rows. Plausible, not data:
 * mornings remember a dream 58 %, without content 14 %, none 28 % (sleep_and_dreams.md §3 [49]); cue and no-cue
 * nights share one insight distribution, so the sample never shows the cues working.
 */
fun sampleNights(today: LocalDate, zone: ZoneId, count: Int = 120, seed: Int = 20261028): List<NightRow> {
    val r = Random(seed)
    fun gauss() = r.nextFloat() + r.nextFloat() + r.nextFloat() + r.nextFloat() - 2f // mean 0, SD 0.58
    fun chance(p: Float) = r.nextFloat() < p
    var pendingArm: Boolean? = null // the experiment pair's second night: true = cue
    val rows = mutableListOf<NightRow>()
    for (back in count downTo 1) {
        val night = today.minusDays(back.toLong())
        if (chance(0.1f)) continue // the watch stayed off
        val noon = night.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        fun at(minFromNoon: Int) = noon + minFromNoon * 60_000L
        val bed = 650 + (gauss() * 50).roundToInt() // about 22:50
        val onset = bed + 6 + (abs(gauss()) * 30).roundToInt()
        val wbtbOn = back <= 75 && chance(0.4f)
        val skipped = wbtbOn && chance(0.1f)
        val up = 991 + r.nextInt(3) // the WBTB alarm rings at 4:30
        val backToBed = up + 15 + r.nextInt(16)
        val reOnset = backToBed + 10 + (abs(gauss()) * 40).roundToInt() // near the lab's 31 ± 26 min ([12])
        val alarm = chance(0.85f)
        val finalWake = maxOf(if (alarm) 1141 + r.nextInt(6) else 1170 + (gauss() * 45).roundToInt(), reOnset + 30)
        val wbtb = when {
            !wbtbOn -> null
            skipped -> WbtbOutcome(skipped = "MIN_SLEEP_BEFORE_WBTB") // as NightSummaries stores it
            else -> WbtbOutcome(at(990), at(up), at(backToBed), at(reOnset), reOnset - backToBed)
        }
        val lateCues = if (wbtb?.reOnsetMs == null) null else if (back <= 60) {
            val cue = pendingArm ?: r.nextBoolean()
            val pair = if (pendingArm == null) "1/2" else "2/2"
            pendingArm = if (pendingArm == null) !cue else null
            LateCueOutcome("EXPERIMENT", cue, pair, if (cue) 1 + r.nextInt(5) else 0, if (cue) 0 else 1 + r.nextInt(5))
        } else {
            LateCueOutcome("ON", true, cuesPlayed = 1 + r.nextInt(5))
        }

        val start = bed - 5
        val end = finalWake + 15
        val brief = List(2 + r.nextInt(3)) { onset + r.nextInt((finalWake - onset).coerceAtLeast(1)) }
        val hrBase = 54f + gauss() * 6
        val movement = ArrayList<Float?>()
        val hr = ArrayList<Float?>()
        val asleep = ArrayList<Boolean?>()
        val remLikely = ArrayList<Boolean?>()
        for (m in start until end) {
            if (chance(0.01f)) { // the watch sent nothing that minute
                movement += null
                hr += null
                asleep += null
                remLikely += null
                continue
            }
            val awakeAtWbtb = wbtb?.reOnsetMs != null && m in 990 until reOnset
            val sleeping = m in onset until finalWake && !awakeAtWbtb && brief.none { m in it until it + 5 }
            asleep += sleeping
            // At the heart rate's peaks below, from an hour after onset: about a quarter of each 95-min cycle.
            remLikely += sleeping && m - onset >= 60 && sin(2 * PI.toFloat() * (m - onset) / 95f) > 0.7f
            movement += when {
                !sleeping -> 0.15f + r.nextFloat() * 0.45f
                chance(0.04f) -> 0.1f + r.nextFloat() * 0.2f // a twitch
                else -> r.nextFloat() * 0.02f
            }
            hr += if (sleeping) {
                hrBase + 2.5f * sin(2 * PI.toFloat() * (m - onset) / 95f) + (m - onset) / 240f + gauss() * 2
            } else {
                hrBase + 9 + gauss() * 5
            }
        }
        val asleepHr = hr.filterIndexed { i, _ -> asleep[i] == true }.filterNotNull()
        val night0 = (onset - start until finalWake - start).map { asleep.getOrNull(it) == true }
        val sleepMin = night0.count { it }
        val sleep = if (chance(0.03f)) null else SleepEstimate(
            onsetMs = at(onset),
            finalWakeMs = at(finalWake),
            sleepMin = sleepMin,
            latencyMin = onset - start,
            wasoMin = night0.size - sleepMin,
            awakenings = night0.zipWithNext().count { (a, b) -> a && !b },
            efficiencyPct = sleepMin * 100 / (finalWake - start),
            hrSleepMeanBpm = asleepHr.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            hrSleepMinBpm = asleepHr.minOrNull(),
            method = "sample",
        )

        fun report(atMin: Int, after: String): DreamReport {
            val u = r.nextFloat()
            val recalled = when {
                u < 0.05f -> null
                u < 0.6f -> "YES"
                u < 0.73f -> "WHITE"
                else -> "NO"
            }
            val v = r.nextFloat()
            val insight = when {
                v < 0.1f -> null
                recalled != "YES" -> if (v < 0.8f) 0 else 1
                else -> listOf(0.5f, 0.75f, 0.87f, 0.94f, 0.98f).indexOfFirst { v < it }.let { if (it < 0) 5 else it }
            }
            val cueNight = lateCues?.cueNight.takeIf { after == "WAKE" }
            return DreamReport(
                atMs = at(atMin),
                after = after,
                recalled = recalled,
                insight = insight,
                control = insight?.let { r.nextInt(it + 1) },
                nightmare = chance(0.04f),
                falseAwakening = chance(0.05f),
                cueInDream = cueNight?.let { it && chance(0.15f) },
                cueWoke = cueNight?.let { it && chance(0.1f) },
                refreshed = 1 + r.nextInt(5),
                sleepParalysis = chance(0.01f),
                unusualNight = chance(0.08f),
            )
        }
        val reports = listOfNotNull(
            report(up + 4, "WBTB").takeIf { wbtb?.upAtMs != null && chance(0.7f) },
            report(finalWake + 3, "WAKE").takeIf { chance(if (alarm) 0.85f else 0.7f) },
        )
        val stopped = at(finalWake) + r.nextInt(60) * 1000L
        val summary = NightSummary(
            sessionId = "sample-$night",
            version = SUMMARY_VERSION,
            nightOf = night.toString(),
            zone = zone.id,
            recordStartMs = at(start),
            recordEndMs = at(end),
            sleep = sleep,
            epochs = Epochs(at(start), 60, movement, hr, asleep, remLikely),
            wake = if (alarm) {
                AlarmOutcome(at(1140), stopped, if (chance(0.2f)) 1 else 0, ((stopped - at(1140)) / 1000).toInt())
            } else {
                null
            },
            wbtb = wbtb,
            lateCues = lateCues,
            reports = reports,
        )
        val phone = if (back <= 100 && chance(0.3f)) {
            val loops = 2 + r.nextInt(3)
            PhoneNight(at(bed), at(bed + 50 + r.nextInt(50)), loops, loops * 3)
        } else {
            null
        }
        rows += NightRow(night, summary, phone)
    }
    return rows.reversed()
}
