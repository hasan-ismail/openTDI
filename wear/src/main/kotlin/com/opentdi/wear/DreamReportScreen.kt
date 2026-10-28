package com.opentdi.wear

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
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
    "Remember a dream?" to listOf("YES", "VAGUE", "NO"),
    "Lucid?" to listOf("YES", "ALMOST", "NO"),
    "Woke up inside a dream (false awakening)?" to listOf("YES", "NO"),
    "A cue in the dream, even disguised?" to listOf("YES", "NO"),
    "Did a cue wake you?" to listOf("YES", "NO"),
    "Refreshed? (1 = not at all)" to listOf("1", "2", "3", "4", "5"),
    "Sleep paralysis?" to listOf("YES", "NO"),
    "Unusual night (ill, alcohol, very late)?" to listOf("YES", "NO"),
)
private const val SLEEP_PARALYSIS = 6 // its index in QUESTIONS

/**
 * The morning report (owner, 2026-10-28; lucid_induction.md §5.3), also after WBTB: say the dream into the watch, then
 * one tap per question. The recording and a w_morning.csv row go to the night's session folder ([dir]), so cue and
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
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(if (after == "WBTB") "Before you go back to sleep" else "Your dream") } }
            item {
                val r = recorder
                when {
                    r != null -> Button(
                        onClick = {
                            stopRecorder(r)
                            recorder = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Stop · ${WakeAlarms.clock(nowMs - startedMs)}") },
                    )
                    else -> Button(
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
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (recording == null) "Tell me your dream" else "Record again") },
                    )
                }
            }
            for ((i, question) in QUESTIONS.withIndex()) {
                item { Text(question.first) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (option in question.second) {
                            Button(
                                onClick = { answers[i] = option },
                                colors = if (answers[i] == option) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            }
            if (answers[SLEEP_PARALYSIS] == "YES") {
                item {
                    Text(
                        "Sleep paralysis is harmless and passes: stay calm and move one finger or toe. Sleeping on " +
                            "your side and WBTB at most once a week make it less likely. Talk to a doctor if it keeps " +
                            "happening or frightens you.",
                    )
                }
            }
            item {
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
