package com.opentdi.mobile.journal

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.opentdi.mobile.TAG_STT
import com.opentdi.mobile.logLine
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.mobile.stats.WATCH_DIR
import com.opentdi.mobile.stats.isRecording
import com.opentdi.mobile.stats.readCache
import com.opentdi.shared.csv.sessionDirs
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What transcription is doing, for the Stats screen. */
sealed interface TranscriptionStatus {
    data object Idle : TranscriptionStatus

    /** "Transcribing [current] of [total]": the recording being turned into text, and how many this run has. */
    data class Transcribing(val current: Int, val total: Int) : TranscriptionStatus

    /** "Transcription model not installed": a normal state, not an error (docs/SETUP.md §13). */
    data object ModelNotInstalled : TranscriptionStatus

    /** [message] is for the user. */
    data class Failed(val message: String) : TranscriptionStatus
}

/**
 * Offline transcription of the dream recordings copied from the watch (the owner's spec, CLAUDE.md §3: the dream
 * journal's on-device speech-to-text; journal design of 2026-10-28, part B3, with its evidence and engineering facts in
 * docs/references/dream_journal_and_guidance.md A5 and A9). After each successful copy (NightsRepository), every
 * filesDir/watch/<sessionId>/w_dream_HHmmss.m4a without a current transcript is decoded to 16 kHz mono, cut into speech
 * by the Silero VAD and recognised by sherpa-onnx, all on the phone; the text goes to w_dream_HHmmss.txt beside it (an
 * empty file: no speech found). A transcript is a written copy of the report, never checked for accuracy. It never runs
 * during a phone session or alongside the natural voice ([withoutTranscription]), and it logs lengths and timings,
 * never text.
 */
object Transcriber {
    private val mutableStatus = MutableStateFlow<TranscriptionStatus>(TranscriptionStatus.Idle)
    val status: StateFlow<TranscriptionStatus> = mutableStatus.asStateFlow()

    /** Held while a speech model is in memory: a transcription run's or, via [withoutTranscription], the voice's. */
    private val modelSlot = Mutex()
    private val voiceWaiting = AtomicInteger()
    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.w(TAG_STT, logLine("TRANSCRIBE_FAILED", "error" to e)) // never crash the app; the next copy retries
            mutableStatus.value = TranscriptionStatus.Failed(STOPPED)
        },
    )

    /**
     * In order of preference: the first one installed whose files are intact and which loads is used. Parakeet's CTC
     * head has no decoder that can run on through a pause: an INFERENCE from the architecture, with mixed support in
     * WhisperX (A5, [14]). No accuracy figure exists for either on sleepy speech: the owner's bake-off decides.
     */
    private val models = listOf(
        // NVIDIA Parakeet TDT-CTC 110M int8, CTC head (the AAR's model type 33), CC-BY-4.0: punctuates; 132 MB.
        SttModel(
            "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8",
            mapOf(
                "model.int8.onnx" to "9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a",
                "tokens.txt" to "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10",
            ),
        ) { dir ->
            OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = File(dir, "model.int8.onnx").path),
                tokens = File(dir, "tokens.txt").path,
                numThreads = NUM_THREADS,
            )
        },
        // The fallback: Moonshine tiny English, quantized 2026-02-27 (the AAR's model type 53), MIT; 44 MB.
        SttModel(
            "sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27",
            mapOf(
                "encoder_model.ort" to "94e90a4654fc45cdfedb77c4c08e1739f48862998e58fada384b25118134f221",
                "decoder_model_merged.ort" to "cf524c4862d36e9e5ab032eddc73637efd822d70e868ac575cf1a46e1e4708a0",
                "tokens.txt" to "2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049",
            ),
        ) { dir ->
            OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = File(dir, "encoder_model.ort").path,
                    mergedDecoder = File(dir, "decoder_model_merged.ort").path,
                ),
                tokens = File(dir, "tokens.txt").path,
                numThreads = NUM_THREADS,
            )
        },
    )

    /** Transcribes, in the background, the copied recordings that have no current transcript; one run at a time. */
    fun transcribeInBackground(context: Context) {
        val app = context.applicationContext
        background.launch { modelSlot.withLock { transcribeAll(app) } }
    }

    /**
     * Runs [block] with no transcription model in memory, for Prepare cues, which may load the natural voice
     * (MainViewModel.prepareCues): a running transcription stops after its current stretch of speech, and the
     * recordings it did not reach wait for the next copy from the watch. Anything else that loads a speech model must
     * run in here.
     */
    suspend fun <T> withoutTranscription(block: suspend () -> T): T {
        voiceWaiting.incrementAndGet()
        try {
            modelSlot.lock()
        } finally {
            voiceWaiting.decrementAndGet() // the slot is ours, or the wait was cancelled: no run left to stop
        }
        try {
            return block()
        } finally {
            modelSlot.unlock()
        }
    }

    // A phone session needs the CPU, and the natural voice the memory.
    private fun paused() = SessionMonitor.status.value.running || voiceWaiting.get() > 0

    private fun transcribeAll(context: Context) {
        if (paused()) return
        val pending = pendingRecordings(File(context.filesDir, WATCH_DIR))
        if (pending.isEmpty()) {
            mutableStatus.value = TranscriptionStatus.Idle
            return
        }
        val root = File(context.filesDir, STT_DIR)
        val installed = models.filter { model -> model.digests.keys.all { File(root, "${model.dir}/$it").isFile } }
        if (!File(root, VAD_FILE).isFile || installed.isEmpty()) {
            mutableStatus.value = TranscriptionStatus.ModelNotInstalled
            return
        }
        mutableStatus.value = TranscriptionStatus.Transcribing(1, pending.size)
        val recognizer = installed.takeIf { intact(root, VAD_FILE, mapOf(VAD_FILE to VAD_SHA256)) }
            ?.firstNotNullOfOrNull { load(root, it) }
        if (recognizer == null) {
            mutableStatus.value = TranscriptionStatus.Failed(LOAD_FAILED)
            return
        }
        try {
            for ((i, recording) in pending.withIndex()) {
                if (paused()) break // the rest waits for the next copy
                mutableStatus.value = TranscriptionStatus.Transcribing(i + 1, pending.size)
                transcribe(recognizer, File(root, VAD_FILE), recording)
            }
        } finally {
            recognizer.release()
        }
        mutableStatus.value = TranscriptionStatus.Idle
    }

    /** Whether [digests]' files in [dir] are intact; logs a push cut short, which the native loader may not survive. */
    private fun intact(dir: File, name: String, digests: Map<String, String>): Boolean =
        filesIntact(dir, digests).also { if (!it) Log.w(TAG_STT, logLine("STT_MODEL_DAMAGED", "model" to name)) }

    /** [model]'s recognizer; null when one of its files is damaged or it does not load. */
    private fun load(root: File, model: SttModel): OfflineRecognizer? {
        val dir = File(root, model.dir)
        if (!intact(dir, model.dir, model.digests)) return null
        val start = SystemClock.elapsedRealtime()
        return try {
            OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model.config(dir))).also {
                val loadMs = SystemClock.elapsedRealtime() - start
                Log.i(TAG_STT, logLine("STT_MODEL", "model" to model.dir, "loadMs" to loadMs))
            }
        } catch (e: Exception) {
            Log.w(TAG_STT, logLine("STT_MODEL_FAILED", "model" to model.dir, "error" to e))
            null
        } catch (e: LinkageError) { // its native library is missing for this phone's CPU
            Log.w(TAG_STT, logLine("STT_MODEL_FAILED", "model" to model.dir, "error" to e))
            null
        }
    }

    /** Writes [recording]'s transcript; a recording that cannot be read is logged and left for the next run. */
    private fun transcribe(recognizer: OfflineRecognizer, vad: File, recording: File) {
        val start = SystemClock.elapsedRealtime()
        try {
            val audio = decodeMono16k(recording)
            val decoded = SystemClock.elapsedRealtime()
            val speech = speechSpans(audio, vad)
            val chunks = mergeSpeech(speech, MIN_SPEECH_SAMPLES, MAX_CHUNK_SAMPLES)
            val texts = mutableListOf<String>()
            for (chunk in chunks) {
                if (paused()) return // no .txt: done again next time
                texts += guardRepeats(recognise(recognizer, audio.sliceArray(chunk)))
            }
            val text = texts.filter { it.isNotEmpty() }.joinToString(" ")
            writeTranscript(recording, text)
            val end = SystemClock.elapsedRealtime()
            val audioMs = audio.size * 1000L / SAMPLE_RATE
            Log.i(
                TAG_STT,
                logLine(
                    "TRANSCRIBED", "file" to recording.name, "audioMs" to audioMs, "decodeMs" to decoded - start,
                    "speech" to speech.size, "chunks" to chunks.size, "recogniseMs" to end - decoded,
                    "rtf" to String.format(Locale.ROOT, "%.3f", (end - start).toDouble() / audioMs.coerceAtLeast(1)),
                    "chars" to text.length,
                ),
            )
        } catch (e: Exception) { // a copy cut short, a damaged file or a native error: no .txt, so the next run retries
            Log.w(TAG_STT, logLine("TRANSCRIBE_SKIPPED", "file" to recording.name, "error" to e))
        }
    }

    private fun recognise(recognizer: OfflineRecognizer, samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    /** Where [audio] (16 kHz) has speech by the Silero VAD in [model], as sample ranges in order; one VAD per file. */
    private fun speechSpans(audio: FloatArray, model: File): List<IntRange> {
        val vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = model.path,
                    threshold = VAD_THRESHOLD,
                    minSilenceDuration = VAD_MIN_SILENCE_S,
                    minSpeechDuration = VAD_MIN_SPEECH_S,
                    windowSize = VAD_WINDOW,
                    maxSpeechDuration = VAD_MAX_SPEECH_S,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
            ),
        )
        try {
            val spans = mutableListOf<IntRange>()
            fun take() {
                while (!vad.empty()) {
                    val segment = vad.front() // start counts samples from the beginning of this VAD's input
                    spans += segment.start until segment.start + segment.samples.size
                    vad.pop()
                }
            }
            for (offset in 0..audio.size - VAD_WINDOW step VAD_WINDOW) {
                vad.acceptWaveform(audio.copyOfRange(offset, offset + VAD_WINDOW))
                take()
            }
            vad.flush()
            take()
            return spans
        } finally {
            vad.release()
        }
    }
}

/** One speech model for sherpa-onnx in filesDir/stt/<dir>/, with each file's pinned SHA-256. */
private class SttModel(val dir: String, val digests: Map<String, String>, val config: (File) -> OfflineModelConfig)

// tools/stt/push_stt_model.sh installs the models and the VAD here; fetch_stt_model.sh checks the same digests
// (dream_journal_and_guidance.md A9; Moonshine's files from its archive, whose digest GitHub lists, 2026-10-28).
private const val STT_DIR = "stt"
private const val VAD_FILE = "silero_vad.onnx"
private const val VAD_SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"

private const val LOAD_FAILED = "The transcription model did not load. Install it again."
private const val STOPPED = "Transcription stopped. It tries again after the next copy from the watch."

// SOURCED: the watch records 16 kHz (DreamReportScreen.kt), and sherpa's silero_vad.onnx accepts only 16 kHz (A9).
private const val SAMPLE_RATE = 16_000
private const val VAD_WINDOW = 512 // SOURCED: the Silero default window (A9)
// GUESSED (journal design B3; Silero's defaults are 0.5, 0.25, 0.25 and 5 s, A9): tuned in the bake-off.
private const val VAD_THRESHOLD = 0.4f
private const val VAD_MIN_SILENCE_S = 0.5f
private const val VAD_MIN_SPEECH_S = 0.25f
private const val VAD_MAX_SPEECH_S = 20f
// GUESSED: Moonshine repeats tokens on clips under 1 s and was trained on 4-30 s clips ([15], A5), so speech under
// 0.5 s is dropped and neighbours merge up to 20 s.
private const val MIN_SPEECH_SAMPLES = SAMPLE_RATE / 2
private const val MAX_CHUNK_SAMPLES = 20 * SAMPLE_RATE
private const val NUM_THREADS = 4 // GUESSED (journal design B3)
// GUESSED: about 58 MB of samples, well inside an app's heap; a longer recording (a recorder left on) is skipped.
private const val MAX_AUDIO_MIN = 15
private const val MAX_SAMPLES = MAX_AUDIO_MIN * 60 * SAMPLE_RATE
// GUESSED: each wait for a decoder buffer; 200 waits in a row (2 to 4 s) with no buffer in or out mean it is stuck.
private const val CODEC_TIMEOUT_US = 10_000L
private const val CODEC_STALL_TRIES = 200
// GUESSED (journal design B3): the repeat guard.
private const val MIN_REPEATS = 3
private const val MAX_REPEAT_WORDS = 4
private const val MIN_WORDS_FOR_MAJORITY = 4
private val WHITESPACE = Regex("""\s+""")

/**
 * [file]'s first audio track as mono float samples at 16 kHz: MediaExtractor and MediaCodec, whose 16-bit output is
 * averaged over its channels. Throws IOException for a file without a readable audio track (a copy cut short has no
 * moov box yet), another rate or encoding, or one longer than [MAX_AUDIO_MIN] minutes.
 */
private fun decodeMono16k(file: File): FloatArray {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
        } ?: throw IOException("no audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs =
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs > MAX_AUDIO_MIN * 60_000_000L) throw IOException("longer than $MAX_AUDIO_MIN min")
        val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
        try {
            // The format carries csd-0, which configure passes on: it must not be queued again (MediaCodec docs).
            codec.configure(format, null, null, 0)
            codec.start()
            return decodeAll(extractor, codec, (durationUs * SAMPLE_RATE / 1_000_000 + SAMPLE_RATE).toInt())
        } finally {
            codec.release()
        }
    } finally {
        extractor.release()
    }
}

/** Runs [codec] over [extractor]'s selected track to the end of the stream; [expected] sizes the first buffer. */
private fun decodeAll(extractor: MediaExtractor, codec: MediaCodec, expected: Int): FloatArray {
    var out = FloatArray(expected.coerceAtMost(MAX_SAMPLES))
    var size = 0
    var channels = 0
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var idle = 0
    while (true) {
        var progress = false
        if (!inputDone) {
            val input = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (input >= 0) {
                progress = true
                val n = extractor.readSampleData(checkNotNull(codec.getInputBuffer(input)), 0)
                if (n < 0) {
                    codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(input, 0, n, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val output = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
        if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            progress = true
            channels = channelCount(codec.outputFormat)
        } else if (output >= 0) {
            progress = true
            if (channels == 0) channels = channelCount(codec.outputFormat)
            val buffer = checkNotNull(codec.getOutputBuffer(output))
            buffer.position(info.offset).limit(info.offset + info.size)
            val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            val frames = shorts.remaining() / channels
            if (size + frames > out.size) {
                if (size + frames > MAX_SAMPLES) throw IOException("longer than $MAX_AUDIO_MIN min")
                out = out.copyOf(minOf(MAX_SAMPLES, maxOf(out.size * 2, size + frames)))
            }
            repeat(frames) {
                var sum = 0
                repeat(channels) { sum += shorts.get() }
                out[size++] = sum / (channels * 32_768f)
            }
            codec.releaseOutputBuffer(output, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return out.copyOf(size)
        }
        if (progress) idle = 0 else if (++idle > CODEC_STALL_TRIES) throw IOException("decoder stalled")
    }
}

/** [format]'s channel count, after checking it is 16-bit PCM at 16 kHz. */
private fun channelCount(format: MediaFormat): Int {
    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    // shortcut: the watch records 16 kHz; add a resampler only if a recorder change makes this fire.
    if (rate != SAMPLE_RATE) throw IOException("decoded at $rate Hz")
    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
        format.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
    ) {
        throw IOException("decoded to encoding ${format.getInteger(MediaFormat.KEY_PCM_ENCODING)}")
    }
    return format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).also { if (it < 1) throw IOException("no channels") }
}

/** w_dream_HHmmss.m4a's transcript: w_dream_HHmmss.txt beside it. */
internal fun transcriptFile(recording: File) = File(recording.parentFile, recording.name.removeSuffix(".m4a") + ".txt")

/**
 * Whether [recording] needs a transcript: it has none, or only one older than the recording (a copy that was redone).
 * The .txt.part a killed run leaves does not count.
 */
internal fun needsTranscript(recording: File): Boolean {
    val transcript = transcriptFile(recording)
    return !transcript.isFile || transcript.lastModified() < recording.lastModified()
}

/**
 * The recordings in [root] (filesDir/watch) that need a transcript, newest night first. Nights older than
 * KEEP_CSV_NIGHTS are pruned (Nights.kt), which deletes their transcripts too, so transcribing them would repeat after
 * every copy. shortcut: a pruned night keeps no transcript; keep w_dream_*.txt in that prune, then drop this check.
 */
internal fun pendingRecordings(root: File): List<File> = sessionDirs(root).flatMap { dir ->
    val recordings =
        dir.listFiles().orEmpty().filter { isRecording(it.name) && needsTranscript(it) }.sortedBy { it.name }
    if (recordings.isEmpty() || readCache(dir)?.pruned == true) emptyList() else recordings
}

/** Replaces [recording]'s transcript whole: written to .txt.part, then renamed, so a killed run leaves no half text. */
internal fun writeTranscript(recording: File, text: String) {
    val target = transcriptFile(recording)
    val part = File(target.parentFile, "${target.name}.part")
    part.writeText(text)
    Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
}

/**
 * The stretches to recognise from the VAD's speech [spans] (sample ranges, in order): spans shorter than [minSamples]
 * are dropped, then neighbours merge while the merged stretch, pauses included, is at most [maxSamples] long
 * (WhisperX's cut and merge, A5 [14]).
 */
internal fun mergeSpeech(spans: List<IntRange>, minSamples: Int, maxSamples: Int): List<IntRange> {
    val merged = mutableListOf<IntRange>()
    for (span in spans) {
        if (span.last - span.first + 1 < minSamples) continue
        val last = merged.lastOrNull()
        if (last != null && span.last - last.first + 1 <= maxSamples) {
            merged[merged.lastIndex] = last.first..span.last
        } else {
            merged += span
        }
    }
    return merged
}

/**
 * One recognised stretch with runaway repeats removed: empty when a single word makes up more than half of four or
 * more words, otherwise every run of [MIN_REPEATS] or more back-to-back copies of the same 1 to [MAX_REPEAT_WORDS]
 * words cut to one copy. Words compare by their letters and digits, ignoring case. No phrase list: tuned to Whisper,
 * it deletes real phrases (journal design B3).
 */
internal fun guardRepeats(text: String): String {
    var words = text.split(WHITESPACE).filter { it.isNotEmpty() }
    val keys = words.map(::wordKey).filter { it.isNotEmpty() }
    val mostCommon = keys.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
    if (keys.size >= MIN_WORDS_FOR_MAJORITY && mostCommon * 2 > keys.size) return ""
    for (n in 1..MAX_REPEAT_WORDS) words = collapseRepeats(words, n)
    return words.joinToString(" ")
}

private fun collapseRepeats(words: List<String>, n: Int): List<String> {
    val keys = words.map(::wordKey)
    fun same(a: Int, b: Int) = (0 until n).all { keys[a + it] == keys[b + it] }
    val out = ArrayList<String>(words.size)
    var i = 0
    while (i < words.size) {
        var copies = 1
        while (i + (copies + 1) * n <= words.size && same(i, i + copies * n)) copies++
        if (copies >= MIN_REPEATS) {
            out += words.subList(i, i + n)
            i += copies * n
        } else {
            out += words[i++]
        }
    }
    return out
}

private fun wordKey(word: String) = word.filter { it.isLetterOrDigit() }.lowercase()

/** [file]'s SHA-256 in lower-case hex, read in blocks so a 132 MB model is never in memory at once. */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Whether each file named in [digests] is in [dir] with that SHA-256. */
internal fun filesIntact(dir: File, digests: Map<String, String>): Boolean =
    digests.all { (name, sha256) -> File(dir, name).let { it.isFile && sha256Hex(it) == sha256 } }
