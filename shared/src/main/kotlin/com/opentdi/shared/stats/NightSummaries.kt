package com.opentdi.shared.stats

import com.opentdi.shared.config.StructuralConstants
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.readCsv
import com.opentdi.shared.model.EDA_STATUS_DETACHED
import com.opentdi.shared.model.HR_STATUS_DETACHED
import com.opentdi.shared.model.LateCueMode
import com.opentdi.shared.model.TdiState
import java.io.Reader
import java.io.StringReader
import java.time.ZoneId

private const val WAKE = "WAKE"
private const val WBTB = "WBTB"
private const val CUES = "CUES"
private const val SLEEP = "SLEEP"
private const val REPORT_MS = 5 * 60_000L // GUESSED (§B7 F5): awake for this long before saving a morning report
private const val HR_GOOD = 1 // HEART_RATE_STATUS: a successful measurement
private const val EDA_NORMAL = 0 // EdaSet STATUS: normal
// shortcut: a recording spanning over 3 days (a clock jump: no watch battery lasts that long) is not summarised;
// split it at the jump if that ever happens.
private const val MAX_RECORDING_MS = 3 * 24 * 3_600_000L
private val RING_ENDS = setOf("RING", "SNOOZE", "STOPPED", "TIMED_OUT")
private val PROMPT_STATES = listOf(TdiState.WAKING_USER, TdiState.RECORDING_DREAM, TdiState.RE_INCUBATING).map { it.name }
// GUESSED: longer than a real prompt (cue, report window, re-incubation cue), each stage cut off after 180 s.
private const val DANGLING_PROMPT_MS = 10 * 60_000L
private val RECORDING = Regex("""w_dream_\d{6}\.m4a""") // DreamReportScreen: w_dream_HHmmss.m4a

/**
 * Summarises one watch session folder for the night that began in [zone] (owner, 2026-10-28; the method is
 * docs/references/sleep_stats.md). [open] gives a file by name, null when it is missing: the watch's w_packets.csv,
 * w_alarm.csv and w_morning.csv, and p_transitions.csv, which is the phone's: the rows of every phone session that
 * overlaps the recording, header once. Null when w_packets.csv is missing or has no whole row. No clock and no
 * randomness, so the same files always give an equal summary; a reader's IOException is the caller's.
 */
fun summarizeNight(sessionId: String, zone: ZoneId, open: (fileName: String) -> Reader?): NightSummary? {
    val sec = open(CsvFiles.W_PACKETS.name)?.use(::readPackets) ?: return null
    val alarms = open(CsvFiles.W_ALARM.name)?.use(::readTable).orEmpty().mapNotNull(::alarmRow).sortedBy { it.wallMs }
    val reports = open(CsvFiles.W_MORNING.name)?.use(::readTable).orEmpty().mapNotNull(::dreamReport)
        .sortedBy { it.atMs }
    val prompted = dormioWakings(open(CsvFiles.P_TRANSITIONS.name)?.use(::readTable).orEmpty(), sec.startMs, sec.endMs)
    val wake = wakeOutcome(alarms)
    val wbtb = wbtbOutcome(alarms)
    // §B7: the times the app knows the sleeper was awake. The WBTB break depends on the estimate, which places it.
    val knownWake = answeredRings(alarms) +
        listOfNotNull(wake?.takeIf { it.stoppedAtMs != null }?.let { Span(it.ringAtMs, sec.endMs) }) +
        prompted +
        reports.map { Span(it.atMs - REPORT_MS, it.atMs) }
    val estimate = estimate(sec, knownWake, wbtb)
    return NightSummary(
        sessionId = sessionId,
        version = SUMMARY_VERSION,
        nightOf = nightDate(sec.startMs, zone).toString(),
        zone = zone.id,
        recordStartMs = sec.startMs,
        recordEndMs = sec.endMs,
        sleep = estimate.sleep,
        epochs = estimate.epochs,
        wake = wake,
        wbtb = wbtb,
        lateCues = lateCueOutcome(alarms),
        reports = reports,
        promptedWakings = prompted.size,
        liveOnsetMs = liveOnset(alarms),
    )
}

/** One w_packets.csv row: the fields the summary uses. */
private class Packet(
    val tsMs: Long,
    val hr: Float?,
    val detached: Boolean,
    val stillness: Float?,
    val angle: Float?,
    val hrGood: Float?,
    val eda: Float?,
    val rmssd: Float?,
    val skinTemp: Float?,
    val accelPeak: Float?,
)

/** w_packets.csv, read line by line (a night is about 25,000 rows), on a 1-s grid; null without a whole row. */
private fun readPackets(reader: Reader): Seconds? {
    val lines = reader.buffered().lineSequence().iterator()
    if (!lines.hasNext()) return null
    val header = lines.next().split(',') // columns by name: older files lack later ones
    val ts = header.indexOf("ts_ms")
    val hr = header.indexOf("hr_bpm")
    val hrStatus = header.indexOf("hr_status")
    val edaStatus = header.indexOf("eda_status")
    val stillness = header.indexOf("stillness")
    val angle = header.indexOf("angle_z_deg")
    val edaUs = header.indexOf("eda_us")
    val rmssd = header.indexOf("rmssd_ms")
    val skinTemp = header.indexOf("skin_temp_c")
    val accelPeak = header.indexOf("accel_peak_g")
    val packets = ArrayList<Packet>()
    for (line in lines) {
        val f = line.split(',') // numbers and enum names only, never a quoted field
        if (f.size != header.size) continue // cut short when the watch died
        val tsMs = f.getOrNull(ts)?.toLongOrNull() ?: continue
        val hrStat = f.getOrNull(hrStatus)?.toIntOrNull()
        val edaStat = f.getOrNull(edaStatus)?.toIntOrNull()
        val bpm = f.float(hr)
        packets += Packet(
            tsMs = tsMs,
            hr = bpm,
            detached = hrStat == HR_STATUS_DETACHED || edaStat == EDA_STATUS_DETACHED,
            stillness = f.float(stillness),
            angle = f.float(angle),
            hrGood = bpm.takeIf { hrStat == null || hrStat == HR_GOOD },
            eda = f.float(edaUs).takeIf { edaStat == EDA_NORMAL },
            rmssd = f.float(rmssd),
            skinTemp = f.float(skinTemp),
            accelPeak = f.float(accelPeak),
        )
    }
    if (packets.isEmpty()) return null
    packets.sortBy { it.tsMs } // stable: of rows with one ts_ms, the first in the file is kept below
    val startMs = packets.first().tsMs
    val lastMs = packets.last().tsMs
    if (lastMs - startMs >= MAX_RECORDING_MS) return null
    val size = ((lastMs - startMs) / 1000 + 1).toInt()
    fun none() = FloatArray(size) { Float.NaN }
    val sec = Seconds(
        startMs, lastMs + 1000, none(), none(), if (angle < 0) null else none(), BooleanArray(size), none(), none(),
    )
    val filled = BooleanArray(size)
    for (p in packets) {
        val s = ((p.tsMs - startMs) / 1000).toInt()
        if (filled[s]) continue
        filled[s] = true
        sec.hr[s] = p.hr ?: Float.NaN
        sec.stillness[s] = p.stillness ?: Float.NaN
        sec.angle?.set(s, p.angle ?: Float.NaN)
        sec.detached[s] = p.detached
        sec.hrGood[s] = p.hrGood ?: Float.NaN
        sec.eda[s] = p.eda ?: Float.NaN
        sec.rmssd[s] = p.rmssd ?: Float.NaN
        sec.skinTemp[s] = p.skinTemp ?: Float.NaN
        sec.accelPeak[s] = p.accelPeak ?: Float.NaN
    }
    return sec
}

private fun List<String>.float(i: Int) = getOrNull(i)?.toFloatOrNull()?.takeIf { it.isFinite() }

/** A small CSV ([readCsv]) as one map per row, by header name; a row of another width is dropped. */
private fun readTable(reader: Reader): List<Map<String, String?>> {
    val records = records(reader.readText())
    val header = records.firstOrNull()?.map { it.orEmpty() } ?: return emptyList()
    return records.drop(1).filter { it.size == header.size }.map { header.zip(it).toMap() }
}

/** [readCsv], dropping a last row cut off inside a quoted field when the watch died. */
private fun records(text: String): List<List<String?>> = try {
    readCsv(StringReader(text))
} catch (e: IllegalArgumentException) {
    if ('\n' in text) records(text.substringBeforeLast('\n')) else emptyList()
}

/** One w_alarm.csv row; [detail] split into its `key=value` pairs. */
private class AlarmRow(val wallMs: Long, val alarm: String?, val event: String?, val detail: Map<String, String>)

private fun alarmRow(row: Map<String, String?>): AlarmRow? = AlarmRow(
    wallMs = row["wall_ms"]?.toLongOrNull() ?: return null,
    alarm = row["alarm"],
    event = row["event"],
    detail = row["detail"].orEmpty().split(';')
        .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap(),
)

/** The index of the first row from [from] on that passes [test]; unknown events never do. */
private inline fun List<AlarmRow>.next(from: Int, test: (AlarmRow) -> Boolean): Int? =
    (from until size).firstOrNull { test(this[it]) }

/**
 * The first wake-up alarm (§B4): its first ring until "I'm awake" or the ring timing out. Later rows belong to a later
 * morning of an over-long recording. Snoozes count as part of waking up, as in WakeAlarms.stop.
 */
private fun wakeOutcome(alarms: List<AlarmRow>): AlarmOutcome? {
    val ring = alarms.next(0) { it.alarm == WAKE && it.event == "RING" } ?: return null
    val end = alarms.next(ring + 1) { it.alarm == WAKE && (it.event == "STOPPED" || it.event == "TIMED_OUT") }
    val ringAtMs = alarms[ring].wallMs
    val stoppedAtMs = end?.takeIf { alarms[it].event == "STOPPED" }?.let { alarms[it].wallMs }
    val snoozes = alarms.subList(ring + 1, end ?: alarms.size).count { it.alarm == WAKE && it.event == "SNOOZE" }
    return AlarmOutcome(ringAtMs, stoppedAtMs, snoozes, stoppedAtMs?.let { ((it - ringAtMs) / 1000).toInt() })
}

/**
 * The first WBTB alarm (§B4): skipped, or its ring, getting up ("I'm awake"; none when it rang out), the
 * back-to-sleep buzz and the re-onset LateCues logged after it. "I'm up now" logs a silent RING (how=USER_NOW) and
 * STOPPED at once, so it reads as an early wake-up rung at that time.
 */
private fun wbtbOutcome(alarms: List<AlarmRow>): WbtbOutcome? {
    val first = alarms.next(0) { it.alarm == WBTB && (it.event == "RING" || it.event == "SKIPPED") } ?: return null
    val d = alarms[first].detail
    // reason= (MIN_SLEEP_BEFORE_WBTB or PASSED), or "Skip the early wake-up" (how=USER); before reason=, only too
    // little sleep recorded before it.
    if (alarms[first].event == "SKIPPED") {
        val why = d["reason"] ?: if (d["how"] == "USER") "USER" else "MIN_SLEEP_BEFORE_WBTB"
        return WbtbOutcome(skipped = why, timedFrom = d["from"])
    }
    val end = alarms.next(first + 1) { it.alarm == WBTB && (it.event == "STOPPED" || it.event == "TIMED_OUT") }
    val up = end?.takeIf { alarms[it].event == "STOPPED" }
    val buzz = up?.let { alarms.next(it + 1) { r -> r.alarm == WBTB && r.event == "BACK_TO_SLEEP" } }
    // LateCues logs RE_ONSET on the first one-second tick RE_ONSET_MS after the still stretch began: within about 1 s.
    val reOnset = buzz?.let { alarms.next(it + 1) { r -> r.alarm == CUES && r.event == "RE_ONSET" } }?.let(alarms::get)
    val buzzMs = buzz?.let { alarms[it].wallMs }
    val reOnsetMs = reOnset?.let { it.wallMs - StructuralConstants.RE_ONSET_MS }
    return WbtbOutcome(
        ringAtMs = alarms[first].wallMs,
        upAtMs = up?.let { alarms[it].wallMs },
        backToSleepAtMs = buzzMs,
        reOnsetMs = reOnsetMs,
        // From the buzz, not the row's latency_min: on a guided night the watch counts from the guide's end.
        reOnsetLatencyMin = if (buzzMs != null && reOnsetMs != null) ((reOnsetMs - buzzMs) / 60_000).toInt() else null,
        timedFrom = d["from"],
    )
}

/** SLEEP ONSET, or the onset an early wake-up was timed from when this folder lacks that row (a restart). */
private fun liveOnset(alarms: List<AlarmRow>): Long? =
    alarms.firstOrNull { it.alarm == SLEEP && it.event == "ONSET" }?.detail?.get("onset_ms")?.toLongOrNull()
        ?: alarms.firstNotNullOfOrNull { r -> r.detail["onset_ms"]?.toLongOrNull()?.takeIf { r.alarm == WBTB } }

/** The late cues' first arming (§B4) and its cues until they ended. */
private fun lateCueOutcome(alarms: List<AlarmRow>): LateCueOutcome? {
    val armed = alarms.next(0) { it.alarm == CUES && it.event == "ARMED" } ?: return null
    val ended = alarms.next(armed + 1) { it.alarm == CUES && it.event == "ENDED" } ?: alarms.size
    val cues = alarms.subList(armed + 1, ended).filter { it.alarm == CUES }
    val detail = alarms[armed].detail
    return LateCueOutcome(
        mode = if ("pair" in detail) LateCueMode.EXPERIMENT.name else LateCueMode.ON.name,
        cueNight = detail["night"] == "CUE",
        pair = detail["pair"],
        cuesPlayed = cues.count { it.event == "CUE" },
        wouldCue = cues.count { it.event == "WOULD_CUE" },
        blockId = detail["block"]?.toLongOrNull(),
    )
}

/** §B7 F1: each ring someone snoozed or stopped; a ring that timed out may have been slept through. */
private fun answeredRings(alarms: List<AlarmRow>): List<Span> = alarms.indices.mapNotNull { i ->
    val ring = alarms[i].takeIf { it.event == "RING" } ?: return@mapNotNull null
    val end = alarms.next(i + 1) { it.alarm == ring.alarm && it.event in RING_ENDS } ?: return@mapNotNull null
    Span(ring.wallMs, alarms[end].wallMs).takeIf { alarms[end].event == "SNOOZE" || alarms[end].event == "STOPPED" }
}

/**
 * §B5: the Dormio prompts among the phone's p_transitions.csv rows in [startMs, endMs), each from WAKING_USER until a
 * state other than the prompt, the report and the re-incubation. One never followed by such a state (the phone process
 * died) lasts at most [DANGLING_PROMPT_MS], not to [endMs]: the watch went on recording the sleep after it.
 */
private fun dormioWakings(rows: List<Map<String, String?>>, startMs: Long, endMs: Long): List<Span> {
    val states = rows.mapNotNull { r -> r["wall_ms"]?.toLongOrNull()?.takeIf { it in startMs until endMs }?.to(r["to"]) }
        .sortedBy { it.first }
    return states.indices.filter { states[it].second == TdiState.WAKING_USER.name }.map { i ->
        val end = states.drop(i + 1).firstOrNull { it.second !in PROMPT_STATES }?.first
        Span(states[i].first, end ?: minOf(endMs, states[i].first + DANGLING_PROMPT_MS))
    }
}

/** One w_morning.csv row (§B6); an answer out of range reads as skipped. */
private fun dreamReport(row: Map<String, String?>): DreamReport? {
    val atMs = row["wall_ms"]?.toLongOrNull() ?: return null
    val recalled = when (val answer = row["recalled"]) {
        "YES", "WHITE", "NO" -> answer
        "VAGUE" -> "WHITE" // its name before 2026-10-28
        else -> null
    }
    // Answers about a dream that was not recalled mean nothing; a white dream has no content to rate.
    val dreamt = recalled == "YES" || recalled == "WHITE"
    fun yesNo(key: String) = when (row[key]) {
        "YES" -> true
        "NO" -> false
        else -> null
    }
    fun scale(key: String, range: IntRange) = row[key]?.toIntOrNull()?.takeIf { it in range }
    return DreamReport(
        atMs = atMs,
        after = row["after"] ?: WAKE, // a report opened by hand is a WAKE report (MainActivity)
        recording = row["recording"]?.takeIf { RECORDING.matches(it) },
        recalled = recalled,
        insight = scale("insight", 0..5)?.takeIf { recalled == "YES" },
        control = scale("control", 0..5)?.takeIf { recalled == "YES" },
        nightmare = yesNo("nightmare")?.takeIf { dreamt },
        falseAwakening = yesNo("false_awakening")?.takeIf { dreamt },
        cueInDream = yesNo("cue_in_dream")?.takeIf { dreamt },
        cueWoke = yesNo("cue_woke"),
        refreshed = scale("refreshed", 1..5),
        sleepParalysis = yesNo("sleep_paralysis"),
        unusualNight = yesNo("unusual_night"),
        guideSleep = row["guide_sleep"]?.takeIf { it in GUIDE_SLEEP },
        odd = row["odd"]?.takeIf { it in ODD && recalled == "YES" },
        signSeen = yesNo("sign_seen")?.takeIf { dreamt },
    )
}

private val GUIDE_SLEEP = setOf("YES", "NO", "UNSURE") // DreamReportScreen: "Fell asleep during the guide?"
private val ODD = setOf("FORM", "ACTION", "CONTEXT", "NONE") // "What was odd?", asked only after a recalled dream
