package com.opentdi.wear

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.CsvWriter
import com.opentdi.shared.csv.csvLine
import com.opentdi.shared.csv.readCsv
import com.opentdi.wear.alarm.WakeAlarms
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The questions, in w_morning.csv's column order, and their answers as written there; a button shows [LABELS] or the
 * answer. Recall offers a white dream (sure you dreamed, no content) [38][49]; lucidity is two 0–5 ratings, insight
 * and control, as in the LuCiD scale [64]; nightmares are tagged [96][97] (sleep_and_dreams.md §8). Owner, 2026-10-28:
 * whether you fell asleep during the guided back-to-sleep (ILDIS asked it of MILD [67]); what was odd, in the lab
 * dreamwork's terms "odd form, action, context" [12]; and whether a dream sign turned up, the sleeper's own test
 * (no study has one). [shownQuestions] says when each of these three is asked.
 */
private val QUESTIONS = listOf(
    "Do you remember a dream?" to listOf("YES", "WHITE", "NO"),
    "Did you know you were dreaming?" to (0..5).map { "$it" },
    "Could you steer it?" to (0..5).map { "$it" },
    "Nightmare?" to listOf("YES", "NO"),
    "Did you dream that you woke up?" to listOf("YES", "NO"),
    "Did you hear the voice or a sound in your dream?" to listOf("YES", "NO"),
    "Did the voice or a sound wake you?" to listOf("YES", "NO"),
    "How refreshed do you feel?" to listOf("1", "2", "3", "4", "5"),
    "Any sleep paralysis (awake but unable to move)?" to listOf("YES", "NO"),
    "Unusual night (ill, alcohol, very late)?" to listOf("YES", "NO"),
    "Fell asleep during the guide?" to listOf("YES", "NO", "UNSURE"),
    "What was odd?" to listOf("FORM", "ACTION", "CONTEXT", "NONE"),
    "Did one of your signs show up?" to listOf("YES", "NO"),
)
private val LABELS = mapOf(
    "WHITE" to "I dreamed, but can't recall it",
    "FORM" to "How something looked",
    "ACTION" to "What happened",
    "CONTEXT" to "Where you were",
    "UNSURE" to "Not sure",
    "NONE" to "Nothing odd",
)
private const val RECALLED = 0 // its index in QUESTIONS, as the ones below
private val ABOUT_THE_DREAM = 1..5 // knew, steered, nightmare, dreamed of waking, a cue in it (owner, 2026-10-28)
private const val NIGHTMARE = 3
internal const val REFRESHED = 7
private const val SLEEP_PARALYSIS = 8
internal const val UNUSUAL_NIGHT = 9
internal const val GUIDE_SLEEP = 10
internal const val ODD = 11
internal const val SIGN_SEEN = 12

/**
 * The indices of the questions this report asks, in order: the guide's after a guided WBTB ([guided]); the questions
 * about the dream itself only about a remembered one (no dream, or one without content, has nothing to judge); the
 * dream-sign question only when the phone sent signs ([signs]); how refreshed and how unusual the night only in the
 * morning, not at the early wake-up ([after] "WBTB").
 */
internal fun shownQuestions(answers: Map<Int, String>, guided: Boolean, signs: Boolean, after: String): List<Int> {
    val dream = answers[RECALLED] == "YES"
    return QUESTIONS.indices.filter {
        when (it) {
            in ABOUT_THE_DREAM, ODD -> dream
            GUIDE_SLEEP -> guided
            SIGN_SEEN -> signs && dream
            REFRESHED, UNUSUAL_NIGHT -> after != "WBTB"
            else -> true
        }
    }
}

/**
 * A report being answered. It outlives the page, as SensingStateHolder does: back or the app closing keeps it, and
 * "Record your dream" goes on where it was left. Only Save, or the night ending ([flush]), writes its one row.
 */
internal class ReportDraft private constructor(
    val after: String,
    val dir: File,
    private val guided: Boolean,
    private val signs: Boolean,
) {
    val answers = mutableStateMapOf<Int, String>()
    var recording by mutableStateOf<String?>(null) // the saved file's name
    var recordedMs by mutableLongStateOf(0L) // its length
    var step by mutableIntStateOf(0) // 0 is the recording, 1..asked().size the questions asked, then Save
    private var saved = false

    fun asked() = shownQuestions(answers, guided, signs, after)

    /** Writes the w_morning.csv row, unanswered as skipped; false when it was written already. */
    fun write(): Boolean {
        if (saved) return false
        val asked = asked() // an answer to a question no longer asked is dropped
        save(dir, after, recording, QUESTIONS.indices.map { i -> answers[i]?.takeIf { i in asked } })
        saved = true
        return true
    }

    companion object {
        private var current: ReportDraft? = null // main thread only

        /** The unsaved report after [after] in [dir] to go on with, or a new one; any other is [flush]ed first. */
        fun open(after: String, dir: File, signs: Boolean): ReportDraft {
            current?.let { if (it.after == after && it.dir == dir && !it.saved) return it }
            flush()
            return ReportDraft(after, dir, guideStarted(dir), signs).also { current = it }
        }

        /** Writes an unsaved report with something said or answered as it stands; true when a row was written. */
        fun flush(): Boolean {
            val d = current ?: return false
            current = null
            return (d.recording != null || d.answers.isNotEmpty()) && d.write()
        }
    }
}

/**
 * The morning report (owner, 2026-10-28; lucid_induction.md §5.3), also after WBTB: say the dream into the watch, then
 * one question per screen, one tap each; back goes to the previous screen. The recording and a w_morning.csv row go to
 * the night's session folder ([dir]), so cue and no-cue nights can be compared. Dream recall is the strongest personal
 * predictor of lucid dreams. Leaving keeps the unsaved report ([ReportDraft]) for "Record your dream".
 */
@Composable
fun DreamReportScreen(
    after: String,
    dir: File,
    requestMic: (then: () -> Unit) -> Unit,
    onSaved: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val draft = remember {
        ReportDraft.open(after, dir, WakeAlarms.parse(WakeAlarms.loadConfigJson(context)).dreamSigns.isNotEmpty())
    }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var micFailed by remember { mutableStateOf(false) }
    var startedMs by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(0L) }
    if (recorder != null) {
        LaunchedEffect(startedMs) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
    }
    fun stopRecording() {
        stopRecorder(recorder ?: return)
        recorder = null
        draft.recordedMs = SystemClock.elapsedRealtime() - startedMs // the length shown on the Save page
    }
    // Left without Save (back, the app closing): the recording is kept, and the draft waits for the next opening.
    DisposableEffect(Unit) { onDispose { stopRecording() } }
    val awake = rememberInteractive()
    // A watch screen that times out can take the microphone from a recording app: keep it on while recording.
    val view = LocalView.current
    DisposableEffect(recorder != null) {
        view.keepScreenOn = recorder != null
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = draft.step > 0) { draft.step-- }
    BackHandler(enabled = recorder != null) { stopRecording() } // back while recording stops it and stays here
    val listState = rememberTransformingLazyColumnState()
    LaunchedEffect(draft.step) { listState.scrollToItem(0) }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(if (after == "WBTB") "Before you go back to sleep" else "Your dream") } }
            item {
                AnimatedContent(
                    draft.step,
                    label = "step",
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 6 }) togetherWith fadeOut(tween(150))
                    },
                ) { s ->
                    // Only the settled page takes taps: a double tap must not also answer the page sliding in.
                    fun settled() = draft.step == s && transition.currentState == transition.targetState
                    fun next() {
                        if (settled()) draft.step = s + 1
                    }
                    val asked = draft.asked()
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            s == 0 -> {
                                val r = recorder
                                if (r != null) {
                                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                                        if (awake) BreathingRing(MaterialTheme.colorScheme.primary, breathMs = 1600)
                                        Text(
                                            WakeAlarms.clock((nowMs - startedMs).coerceAtLeast(0)),
                                            style = MaterialTheme.typography.displaySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Button(
                                        onClick = ::stopRecording,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Stop") },
                                    )
                                } else {
                                    val record = {
                                        micFailed = true // stays when the microphone is refused (no callback) or busy
                                        requestMic {
                                            val name = "w_dream_${LocalTime.now().format(FILE_TIME)}.m4a"
                                            recorder = startRecorder(context, File(dir.apply { mkdirs() }, name))
                                            if (recorder != null) {
                                                micFailed = false
                                                draft.recording = name
                                                startedMs = SystemClock.elapsedRealtime()
                                            }
                                        }
                                    }
                                    if (draft.recording == null) {
                                        if (after == "WAKE") {
                                            Line(
                                                "Before you move: pinch your nose and try to breathe. If you can, " +
                                                    "you're dreaming.",
                                            )
                                        }
                                        Line("Replay the dream with your eyes closed. Then tap, say it, and tap Stop.")
                                        Button(
                                            onClick = record,
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Tell me your dream") },
                                        )
                                        if (micFailed) {
                                            Line(
                                                "The microphone didn't start. Tap Skip recording to answer the " +
                                                    "questions.",
                                            )
                                        }
                                        Button(
                                            onClick = ::next,
                                            colors = ButtonDefaults.filledTonalButtonColors(),
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Skip recording") },
                                        )
                                    } else {
                                        // Next first: a tap on Start over replaces the dream just said.
                                        Button(
                                            onClick = ::next,
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Next") },
                                        )
                                        Button(
                                            onClick = record,
                                            colors = ButtonDefaults.filledTonalButtonColors(),
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Start over") },
                                        )
                                    }
                                }
                            }
                            s <= asked.size -> {
                                val i = asked[s - 1]
                                val (question, options) = QUESTIONS[i]
                                Line("$s of ${asked.size}", style = MaterialTheme.typography.labelSmall)
                                Line(question, style = MaterialTheme.typography.titleMedium)
                                val scale = options.first().toIntOrNull() != null
                                if (scale) {
                                    Line(
                                        if (options.first() == "0") {
                                            "0 = not at all · 5 = completely"
                                        } else {
                                            "1 = not at all · 5 = very"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                // A scale in rows of three (1 to 5: three and two; 0 to 5: three and three) of
                                // 52 dp buttons; the others one full-width button each.
                                for (row in options.chunked(if (scale) 3 else 1)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for (option in row) {
                                            Button(
                                                onClick = {
                                                    if (settled()) draft.answers[i] = option
                                                    next()
                                                },
                                                colors = if (draft.answers[i] == option) {
                                                    ButtonDefaults.buttonColors()
                                                } else {
                                                    ButtonDefaults.filledTonalButtonColors()
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = {
                                                    Text(
                                                        LABELS[option]
                                                            ?: option.lowercase().replaceFirstChar { it.uppercase() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Center,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = ::next) { Text("Skip") }
                                }
                            }
                            else -> {
                                Line(
                                    draft.recording?.let { "Recorded ${WakeAlarms.clock(draft.recordedMs)}" }
                                        ?: "No recording",
                                )
                                if (draft.answers[SLEEP_PARALYSIS] == "YES") {
                                    // Wellness wording: no condition sent to a doctor, nothing called abnormal [100].
                                    Line(
                                        "If you wake unable to move again, stay calm: it passes. Move one finger or toe. " +
                                            "Sleeping on your side and fewer early wake-up nights may make it less " +
                                            "likely. Talking to a healthcare professional may be helpful.",
                                    )
                                }
                                if (draft.answers[NIGHTMARE] == "YES") {
                                    // This report's nightmare and the watch's saved reports of the last 7 days.
                                    // GUESSED: 2 in 7 days; [96][97] give no number, only "if frequent, suggest pausing"
                                    // (sleep_and_dreams.md §6).
                                    val nightmares = remember {
                                        1 + nightmaresSince(dir.parentFile ?: dir, System.currentTimeMillis() - WEEK_MS)
                                    }
                                    if (nightmares >= 2) {
                                        Line(
                                            "${if (nightmares == 2) "Two" else nightmares} nightmares this week. " +
                                                "Consider a few nights off.",
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        draft.write()
                                        onSaved() // the report is done: "Record your dream" goes away
                                        onDone()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Save") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
private const val WEEK_MS = 7 * 24 * 3_600_000L

private fun startRecorder(context: Context, file: File): MediaRecorder? {
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }
    return try {
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setOutputFile(file.path)
            prepare()
            start()
        }
    } catch (e: IOException) {
        recorder.release()
        null
    } catch (e: RuntimeException) { // the microphone is busy or the permission was refused
        recorder.release()
        null
    }
}

private fun stopRecorder(recorder: MediaRecorder) {
    try {
        recorder.stop()
    } catch (e: RuntimeException) { // stopped right after starting: no audio, the file is empty
        Unit
    }
    recorder.release()
}

/** Appends one w_morning.csv row, writing the header first in a new file. */
internal fun save(dir: File, after: String, recording: String?, answers: List<String?>) {
    dir.mkdirs()
    val file = File(dir, CsvFiles.W_MORNING.name)
    val row: List<Any?> = listOf<Any?>(System.currentTimeMillis(), after, recording) + answers
    if (file.exists()) {
        file.appendText(csvLine(row))
    } else {
        CsvWriter(file.bufferedWriter(), CsvFiles.W_MORNING.columns).use { it.row(*row.toTypedArray<Any?>()) }
    }
}

/** True when the night in [dir] played the guided back-to-sleep: its w_alarm.csv has a GUIDE_START row. */
internal fun guideStarted(dir: File): Boolean {
    val rows = try {
        File(dir, CsvFiles.W_ALARM.name).reader().use(::readCsv)
    } catch (e: IOException) { // no alarm rang in this recording
        return false
    } catch (e: IllegalArgumentException) { // a row cut short when the watch died
        return false
    }
    val event = rows.firstOrNull().orEmpty().indexOf("event")
    return event >= 0 && rows.drop(1).any { it.getOrNull(event) == "GUIDE_START" }
}

/**
 * The nightmare=YES rows written since [sinceMs] in the w_morning.csv of each folder in [root] (filesDir/sessions);
 * a file from before the nightmare column, or one that cannot be read, counts none.
 */
internal fun nightmaresSince(root: File, sinceMs: Long): Int {
    var n = 0
    for (folder in root.listFiles().orEmpty()) {
        val file = File(folder, CsvFiles.W_MORNING.name)
        if (file.lastModified() < sinceMs) continue // a missing file reads 0; an older one holds no newer row
        val rows = try {
            file.reader().use(::readCsv)
        } catch (e: IOException) {
            continue
        } catch (e: IllegalArgumentException) { // a row cut short when the watch died
            continue
        }
        val column = rows.firstOrNull().orEmpty().indexOf("nightmare")
        if (column < 0) continue
        n += rows.drop(1).count { it.getOrNull(column) == "YES" && (it[0]?.toLongOrNull() ?: 0L) >= sinceMs }
    }
    return n
}
