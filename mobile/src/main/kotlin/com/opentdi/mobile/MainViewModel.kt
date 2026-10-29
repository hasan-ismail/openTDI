package com.opentdi.mobile

import com.opentdi.shared.model.WatchOutput
import java.util.concurrent.atomic.AtomicBoolean
import com.opentdi.mobile.cues.saveOwnVoice
import com.opentdi.mobile.cues.recordMic
import com.opentdi.mobile.cues.removeOwnVoice
import com.opentdi.mobile.cues.OWN_VOICE_RATE
import com.opentdi.shared.model.AlarmTone
import android.app.Application
import android.app.NotificationManager
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentdi.shared.model.WatchState
import com.opentdi.shared.model.WatchStatus
import com.opentdi.shared.model.TdiCommand
import com.opentdi.shared.model.SessionEndReason
import com.opentdi.shared.model.DeviceRole
import com.opentdi.mobile.link.watchRequest
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.mobile.audio.AudioEvent
import com.opentdi.mobile.audio.CuePlayer
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.audio.dndEffect
import com.opentdi.mobile.audio.dndState
import com.opentdi.mobile.audio.toLogLine
import com.opentdi.mobile.cues.CueException
import com.opentdi.mobile.cues.CueSet
import com.opentdi.mobile.cues.guideOn
import com.opentdi.mobile.cues.loadCueSet
import com.opentdi.mobile.cues.loadGuideClip
import com.opentdi.mobile.cues.loadPreparedCues
import com.opentdi.mobile.cues.renderCues
import com.opentdi.mobile.journal.Transcriber
import com.opentdi.shared.audio.FadeEnvelope
import com.opentdi.shared.audio.GuideClip
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.targetGain
import com.opentdi.mobile.session.SESSIONS_DIR
import com.opentdi.mobile.session.SessionMonitor
import com.opentdi.mobile.stats.NightsRepository
import com.opentdi.shared.csv.CsvFiles
import com.opentdi.shared.csv.PhoneSessionSummary
import com.opentdi.shared.csv.deleteSessionDirs
import com.opentdi.shared.csv.phoneSessionSummary
import com.opentdi.shared.csv.sessionDirs
import com.opentdi.shared.csv.sessionStartMs
import com.opentdi.shared.csv.sizeOnDisk
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The session screen's own state; the running session's state is in SessionMonitor. */
data class UiState(
    val config: SessionConfig,
    val cues: CueSet?,
    val preparing: Boolean = false,
    val prepareStatus: String = PREPARING, // what Prepare cues is doing, while [preparing]
    val testing: Boolean = false,
    val cueMessage: String? = null,
    val calibratedIndex: Int? = null, // media volume step of the last full test cue (§7.7 calibration)
    val watchTested: Boolean = false, // a test cue played on the watch in this app run
    val recordingCue: PromptCue? = null, // the cue whose own voice is being recorded
    val batteryUnrestricted: Boolean = true, // as checked at the last resume
    val batteryManual: Boolean = false, // no battery settings screen opened: show the path instead
)

const val PREPARING = "Getting the voice ready…"
private const val GUIDE_NOT_SENT =
    "The watch didn't get the sleep guide. Keep it near the phone and tap Get the voice ready again."
private const val ALARMS_NOT_SENT =
    "The watch didn't get the new alarm settings. Keep it near the phone and change them again."
private const val WATCH_NO_ANSWER = "The watch didn't answer. End the night on the watch itself."
private const val WATCH_NOT_STOPPED = "The watch didn't stop. End the night on the watch itself."

/** Why Test the voice did not play, in plain words, from the [last] audio event it logged. */
internal fun testFailure(last: AudioEvent?): String = when (last?.detail ?: last?.event) {
    "dnd_muted" -> "Do Not Disturb is silencing it. In Do Not Disturb, allow Media."
    "stream_device_not_speaker" -> "The phone's sound is going to headphones or a speaker. Disconnect them."
    "VOLUME_ZERO" -> "The phone's volume is off. Turn it up."
    "watch_media_volume_off" -> "The watch's volume is off. Turn it up on the watch."
    "watch_media_not_on_speaker" -> "The watch's sound is going to earbuds. Disconnect them, or choose Earbuds in Settings."
    "watch_unreachable" -> "The watch didn't answer. Open OpenTDI on the watch and keep it near the phone."
    else -> "The voice didn't play. Try again."
}

/** One row of Settings' Saved nights: a filesDir/sessions directory (§12.1) and its p_transitions.csv summary. */
data class SessionItem(val id: String, val startMs: Long, val bytes: Long, val summary: PhoneSessionSummary?)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val mutableUi =
        MutableStateFlow(UiState(loadConfig(app), loadCueSet(app), calibratedIndex = loadCalibratedIndex(app)))
    val ui: StateFlow<UiState> = mutableUi.asStateFlow()
    private val mutableSessions = MutableStateFlow<List<SessionItem>?>(null) // null until listed
    val sessions: StateFlow<List<SessionItem>?> = mutableSessions.asStateFlow()

    /** STREAM_MUSIC at index 0, which plays every cue silently; polled while the screen collects it. */
    val mediaVolumeOff: Flow<Boolean> = flow {
        val audioManager = app.getSystemService(AudioManager::class.java)
        while (true) {
            emit(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
            delay(1000)
        }
    }.distinctUntilChanged()

    /** How Do Not Disturb treats cues now (§7.7); polled while the screen collects it. */
    private val dndNow: Flow<Pair<Int, Int>> = flow {
        val notifications = app.getSystemService(NotificationManager::class.java)
        while (true) {
            emit(notifications.dndState())
            delay(1000)
        }
    }
    val dnd: Flow<DndEffect> = dndNow.map { (filter, categories) -> dndEffect(filter, categories) }.distinctUntilChanged()

    /** Do Not Disturb is off: notifications can buzz and light up the room (owner, 2026-10-29: they "got in the way"). */
    val dndOff: Flow<Boolean> =
        dndNow.map { it.first == NotificationManager.INTERRUPTION_FILTER_ALL }.distinctUntilChanged()

    /** Media goes to headphones or another speaker, not the phone's own: the phone route's voice would not play. */
    val speakerOff: Flow<Boolean> = flow {
        val player = CuePlayer(app)
        while (true) {
            emit(!player.streamOnSpeaker())
            delay(1000)
        }
    }.distinctUntilChanged()

    /** The watch app's state and battery, asked every 5 s while the screen collects it; null when it does not answer. */
    val watchStatus: Flow<WatchStatus?> = flow {
        while (true) {
            val status = watchRequest(app, TdiCommand.StatusRequest(DeviceRole.PHONE))
            if (status?.state == WatchState.IDLE) clearWatchStopError() // ended on the watch itself
            emit(status)
            delay(5000)
        }
    }.distinctUntilChanged()

    /** Ends the watch's night, which goes on after the voice ends; says so when the watch did not stop. */
    fun stopWatch() {
        viewModelScope.launch {
            val status = watchRequest(app, TdiCommand.AbortSession("watch", DeviceRole.PHONE, SessionEndReason.USER))
            Log.i(TAG_SESSION, logLine("WATCH_STOP", "state" to (status?.state ?: "NO_ANSWER")))
            when (status?.state) {
                WatchState.IDLE -> clearWatchStopError()
                null -> SessionMonitor.update { it.copy(error = WATCH_NO_ANSWER) }
                else -> SessionMonitor.update { it.copy(error = WATCH_NOT_STOPPED) }
            }
        }
    }

    private fun clearWatchStopError() = SessionMonitor.update {
        if (it.error == WATCH_NO_ANSWER || it.error == WATCH_NOT_STOPPED) it.copy(error = null) else it
    }

    private val stopRecording = AtomicBoolean(false)

    /** Records the user's own voice for [cue] until [stopOwnVoice] or 20 s; the caller has RECORD_AUDIO. */
    fun recordOwnVoice(cue: PromptCue) {
        if (ui.value.recordingCue != null) return
        stopRecording.set(false)
        mutableUi.update { it.copy(recordingCue = cue, cueMessage = null) }
        viewModelScope.launch {
            val samples = withContext(Dispatchers.IO) { recordMic(stopRecording) }
            if (samples == null || samples.size < OWN_VOICE_RATE / 2) {
                mutableUi.update { it.copy(recordingCue = null, cueMessage = "Nothing was recorded: try again.") }
                return@launch
            }
            val sha = withContext(Dispatchers.IO) { saveOwnVoice(app, cue, samples) }
            mutableUi.update { it.copy(recordingCue = null) }
            edit { it.copy(audio = it.audio.copy(ownVoice = it.audio.ownVoice + (cue to sha))) }
        }
    }

    fun stopOwnVoice() = stopRecording.set(true)

    /** Back to the synthetic voice for [cue]. */
    fun deleteOwnVoice(cue: PromptCue) {
        edit { it.copy(audio = it.audio.copy(ownVoice = it.audio.ownVoice - cue)) }
        viewModelScope.launch(Dispatchers.IO) { removeOwnVoice(app, cue) }
    }

    /** Plays the late-night cue once on the watch at [volume], for setting its loudness while lying in bed. */
    fun testLateCue(volume: Float) {
        viewModelScope.launch {
            val earbuds = ui.value.config.audio.watchOutput == WatchOutput.EARBUDS
            if (watchRequest(app, TdiCommand.TestLateCue(volume, earbuds)) == null) {
                mutableUi.update { it.copy(cueMessage = "The watch did not answer: is it connected?") }
            }
        }
    }

    /** Plays a few seconds of [tone] on the watch, for choosing an alarm sound; SPOKEN sends the voice first. */
    fun testAlarm(tone: AlarmTone) {
        viewModelScope.launch {
            if (tone == AlarmTone.SPOKEN) {
                val voice = try {
                    withContext(Dispatchers.IO) { loadPreparedCues(app, ui.value.config) }.audio[PromptCue.MORNING_WAKE]
                } catch (e: CueException) {
                    null
                }
                if (voice == null || !CuePlayer(app).storeOnWatch(voice)) {
                    mutableUi.update {
                        it.copy(
                            cueMessage = "Tap Get the voice ready first, with the watch connected, for the spoken wake-up.",
                        )
                    }
                    return@launch
                }
            }
            val earbuds = ui.value.config.audio.watchOutput == WatchOutput.EARBUDS
            val status = watchRequest(app, TdiCommand.TestAlarm(tone, earbuds))
            if (status == null) mutableUi.update { it.copy(cueMessage = "The watch did not answer: is it connected?") }
        }
    }

    private val mutableOnboarded = MutableStateFlow(isOnboarded(app))

    /** False while the set-up guide is shown. */
    val onboarded: StateFlow<Boolean> = mutableOnboarded.asStateFlow()

    fun finishOnboarding() {
        setOnboarded(app, true)
        mutableOnboarded.value = true
    }

    fun restartOnboarding() {
        setOnboarded(app, false)
        mutableOnboarded.value = false
    }

    private var watchSync: Job? = null

    /** Applies and saves a settings change; alarm and watch-output changes also go to the watch, even mid-night. */
    fun edit(transform: (SessionConfig) -> SessionConfig) {
        val old = ui.value.config
        val next = transform(old).copy(revision = System.currentTimeMillis())
        mutableUi.update { it.copy(config = next) }
        saveConfig(app, next)
        if (next.alarms == old.alarms && next.audio.watchOutput == old.audio.watchOutput) return
        // A slider drag sends one request with its last value, so an older request never arrives last.
        watchSync?.cancel()
        watchSync = viewModelScope.launch {
            delay(800)
            val sent = watchRequest(app, TdiCommand.ConfigSync(null, next)) != null
            mutableUi.update {
                when {
                    !sent -> it.copy(cueMessage = ALARMS_NOT_SENT)
                    it.cueMessage == ALARMS_NOT_SENT -> it.copy(cueMessage = null)
                    else -> it
                }
            }
        }
    }

    fun showBattery(unrestricted: Boolean) = mutableUi.update { it.copy(batteryUnrestricted = unrestricted) }

    fun showBatteryManual() = mutableUi.update { it.copy(batteryManual = true) }

    fun loadSessions() {
        viewModelScope.launch {
            mutableSessions.value = withContext(Dispatchers.IO) {
                sessionDirs(sessionsRoot()).map { dir ->
                    val summary = try {
                        File(dir, CsvFiles.P_TRANSITIONS.name).useLines(block = ::phoneSessionSummary)
                    } catch (e: IOException) { // a session refused before its CSVs existed
                        null
                    }
                    SessionItem(dir.name, sessionStartMs(dir.name) ?: dir.lastModified(), sizeOnDisk(dir), summary)
                }
            }
        }
    }

    /** Deletes the session directories [ids], never the running session's, then lists them again. */
    fun deleteSessions(ids: Collection<String>) {
        viewModelScope.launch {
            val status = SessionMonitor.status.value
            val count = withContext(Dispatchers.IO) {
                val running = status.sessionId.takeIf { status.running }
                deleteSessionDirs(sessionsRoot(), ids, runningId = running)
                    // Only once the folders are gone, or Stats would read them back into its cache.
                    .also { NightsRepository.forgetPhoneSessions(app, ids - setOfNotNull(running)) }
            }
            Log.i(TAG_SESSION, logLine("SESSIONS_DELETED", "count" to count))
            loadSessions()
        }
    }

    private fun sessionsRoot() = File(app.filesDir, SESSIONS_DIR)

    /**
     * The Prepare step: renders the cues for the current theme and cue settings (§7.3), never during a session. With
     * the guided back-to-sleep on, it also sends the guide's clips to the watch, which plays them after WBTB.
     */
    fun prepareCues() {
        if (ui.value.preparing) return
        val config = ui.value.config
        mutableUi.update { it.copy(preparing = true, prepareStatus = PREPARING, cueMessage = null) }
        fun status(text: String) = mutableUi.update { it.copy(prepareStatus = text) }
        viewModelScope.launch {
            var fallback: String? = null
            val error = try {
                Transcriber.withoutTranscription {
                    renderCues(app, config, { fallback = it }) { done, total ->
                        // The guide's clips are long: the first Prepare with the natural voice takes a few minutes.
                        status("Getting the voice ready: $done of $total…")
                    }
                }
                val guideMissed = guideOn(config) && !sendGuide(config) { sent, total ->
                    status("Sending the sleep guide to the watch: $sent of $total…")
                }
                listOfNotNull(fallback, GUIDE_NOT_SENT.takeIf { guideMissed }).joinToString(" ").ifEmpty { null }
            } catch (e: CueException) {
                e.message
            } catch (e: IOException) {
                Log.w(TAG_AUDIO, logLine("CUES_NOT_STORED", "error" to e.message))
                "The voice couldn't be saved. Free some space on the phone and try again."
            }
            mutableUi.update { it.copy(preparing = false, cues = loadCueSet(app), cueMessage = error) }
        }
    }

    /**
     * Sends the guide's clips to the watch, faded in at the cue volume as a cue sent there would be (the watch plays
     * them at full track volume); false as soon as the watch does not keep one.
     */
    private suspend fun sendGuide(config: SessionConfig, onSent: (sent: Int, total: Int) -> Unit): Boolean {
        val player = CuePlayer(app)
        return GuideClip.entries.withIndex().all { (i, clip) ->
            onSent(i, GuideClip.entries.size)
            val clipAudio = withContext(Dispatchers.IO) { loadGuideClip(app, clip) }
            val faded = FadeEnvelope(clipAudio.sampleRate, config.audio.fadeInMs)
                .applyTo(clipAudio.samples, config.audio.targetGain())
            player.storeOnWatch(WavAudio(faded, clipAudio.sampleRate), clip.file)
        }
    }

    /**
     * Plays INITIAL_INCUBATION once through the real player; only ever on this tap. A cue that plays to the end saves
     * the media volume as the calibration: later cues are scaled down when the volume is raised above it (§7.7).
     */
    fun testCue() {
        if (ui.value.testing) return
        val config = ui.value.config
        mutableUi.update { it.copy(testing = true, cueMessage = null) }
        viewModelScope.launch {
            val message = try {
                val cue = PromptCue.INITIAL_INCUBATION
                val audio = withContext(Dispatchers.IO) { loadPreparedCues(app, config) }.audio.getValue(cue)
                val index = app.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)
                var last: AudioEvent? = null
                val played = CuePlayer(app).play(cue, audio, config.audio, calibratedIndex = index, testCue = true) { event ->
                    last = event
                    Log.i(TAG_AUDIO, event.toLogLine(config.audio.route, cycle = null))
                }
                if (config.audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER) {
                    mutableUi.update { it.copy(watchTested = played) } // the watch keeps its own calibration
                }
                if (played) {
                    if (config.audio.route != AudioRouteMode.WATCH_WRIST_SPEAKER && index > 0) {
                        // A muted test is no calibration: it would scale every later cue to silence.
                        saveCalibratedIndex(app, index)
                        mutableUi.update { it.copy(calibratedIndex = index) }
                    }
                    null
                } else {
                    testFailure(last)
                }
            } catch (e: CueException) {
                e.message
            }
            mutableUi.update { it.copy(testing = false, cueMessage = message) }
        }
    }
}
