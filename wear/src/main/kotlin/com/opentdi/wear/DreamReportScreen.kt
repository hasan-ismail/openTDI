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
import com.opentdi.wear.alarm.WakeAlarms
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** The questions, their answers as written to w_morning.csv, and the button labels. */
private val QUESTIONS = listOf(
    "Do you remember a dream?" to listOf("YES", "VAGUE", "NO"),
    "Did you know you were dreaming?" to listOf("YES", "ALMOST", "NO"),
    "Did you dream that you woke up?" to listOf("YES", "NO"),
    "Did a cue turn up in a dream, even changed?" to listOf("YES", "NO"),
    "Did a cue wake you?" to listOf("YES", "NO"),
    "How refreshed do you feel?" to listOf("1", "2", "3", "4", "5"),
    "Any sleep paralysis (awake but unable to move)?" to listOf("YES", "NO"),
    "Unusual night (ill, alcohol, very late)?" to listOf("YES", "NO"),
)
private const val SLEEP_PARALYSIS = 6 // its index in QUESTIONS

/**
 * The morning report (owner, 2026-10-28; lucid_induction.md §5.3), also after WBTB: say the dream into the watch, then
 * one question per screen, one tap each; back goes to the previous screen. The recording and a w_morning.csv row go to the night's session folder ([dir]), so cue and
 * no-cue nights can be compared. Dream recall is the strongest personal predictor of lucid dreams.
 */
@Composable
fun DreamReportScreen(after: String, dir: File, requestMic: (then: () -> Unit) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val answers = remember { mutableStateMapOf<Int, String>() }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf<String?>(null) } // the saved file's name
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
    DisposableEffect(Unit) { onDispose { recorder?.let { stopRecorder(it) } } }
    // A watch screen that times out can take the microphone from a recording app: keep it on while recording.
    val view = LocalView.current
    DisposableEffect(recorder != null) {
        view.keepScreenOn = recorder != null
        onDispose { view.keepScreenOn = false }
    }
    // 0 is the recording, 1..8 the questions, then Save.
    var step by remember { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    val listState = rememberTransformingLazyColumnState()
    LaunchedEffect(step) { listState.scrollToItem(0) }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(if (after == "WBTB") "Before you go back to sleep" else "Your dream") } }
            item {
                AnimatedContent(
                    step,
                    label = "step",
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 6 }) togetherWith fadeOut(tween(150))
                    },
                ) { s ->
                    // A tap on the page that is fading out must not move on a second time.
                    fun next() {
                        if (step == s) step = s + 1
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            s == 0 -> {
                                val r = recorder
                                if (r != null) {
                                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                                        BreathingRing(MaterialTheme.colorScheme.primary, breathMs = 1600)
                                        Text(
                                            WakeAlarms.clock((nowMs - startedMs).coerceAtLeast(0)),
                                            style = MaterialTheme.typography.displaySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            stopRecorder(r)
                                            nowMs = SystemClock.elapsedRealtime() // the length shown on the Save page
                                            recorder = null
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Stop") },
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            requestMic {
                                                val name = "w_dream_${LocalTime.now().format(FILE_TIME)}.m4a"
                                                recorder = startRecorder(context, File(dir.apply { mkdirs() }, name))
                                                if (recorder != null) {
                                                    recording = name
                                                    startedMs = SystemClock.elapsedRealtime()
                                                }
                                            }
                                        },
                                        colors = if (recording == null) {
                                            ButtonDefaults.buttonColors()
                                        } else {
                                            ButtonDefaults.filledTonalButtonColors()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(if (recording == null) "Tell me your dream" else "Record again") },
                                    )
                                    Button(
                                        onClick = ::next,
                                        colors = if (recording == null) {
                                            ButtonDefaults.filledTonalButtonColors()
                                        } else {
                                            ButtonDefaults.buttonColors()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(if (recording == null) "Skip recording" else "Next") },
                                    )
                                }
                            }
                            s <= QUESTIONS.size -> {
                                val i = s - 1
                                val (question, options) = QUESTIONS[i]
                                Line("$s of ${QUESTIONS.size}", style = MaterialTheme.typography.labelSmall)
                                Line(question, style = MaterialTheme.typography.titleMedium)
                                val scale = options.first() == "1"
                                // 1 to 5 in two rows, three and two; the others one full-width button each.
                                for (row in options.chunked(if (scale) 3 else 1)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for (option in row) {
                                            Button(
                                                onClick = {
                                                    if (step == s) answers[i] = option
                                                    next()
                                                },
                                                colors = if (answers[i] == option) {
                                                    ButtonDefaults.buttonColors()
                                                } else {
                                                    ButtonDefaults.filledTonalButtonColors()
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = {
                                                    Text(
                                                        option.lowercase().replaceFirstChar { it.uppercase() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Center,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                                if (scale) Line("1 = not at all, 5 = very", style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = ::next) { Text("Skip") }
                                }
                            }
                            else -> {
                                Line(
                                    recording?.let { "Recorded ${WakeAlarms.clock((nowMs - startedMs).coerceAtLeast(0))}" }
                                        ?: "No recording",
                                )
                                if (answers[SLEEP_PARALYSIS] == "YES") {
                                    Line(
                                        "Sleep paralysis is harmless and passes: stay calm and move one finger or toe. " +
                                            "Sleeping on your side and WBTB at most once a week make it less likely. " +
                                            "Talk to a doctor if it keeps happening or frightens you.",
                                    )
                                }
                                Button(
                                    onClick = {
                                        recorder?.let { stopRecorder(it) }
                                        recorder = null
                                        save(dir, after, recording, QUESTIONS.indices.map { answers[it] })
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
private fun save(dir: File, after: String, recording: String?, answers: List<String?>) {
    dir.mkdirs()
    val file = File(dir, CsvFiles.W_MORNING.name)
    val row: List<Any?> = listOf<Any?>(System.currentTimeMillis(), after, recording) + answers
    if (file.exists()) {
        file.appendText(csvLine(row))
    } else {
        CsvWriter(file.bufferedWriter(), CsvFiles.W_MORNING.columns).use { it.row(*row.toTypedArray<Any?>()) }
    }
}
