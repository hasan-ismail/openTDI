package com.opentdi.mobile

import android.app.Application
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentdi.mobile.audio.AudioEvent
import com.opentdi.mobile.audio.CuePlayer
import com.opentdi.mobile.audio.DndEffect
import com.opentdi.mobile.audio.toLogLine
import com.opentdi.mobile.cues.CueException
import com.opentdi.mobile.cues.CueSet
import com.opentdi.mobile.cues.loadCueSet
import com.opentdi.mobile.cues.loadPreparedCues
import com.opentdi.mobile.cues.renderCues
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The session screen's own state; the running session's state is in SessionMonitor. */
data class UiState(
    val config: SessionConfig,
    val cues: CueSet?,
    val preparing: Boolean = false,
    val testing: Boolean = false,
    val cueMessage: String? = null,
    val dnd: DndEffect = DndEffect.PLAYS, // as checked at the last Start tap
    val calibratedIndex: Int? = null, // media volume step of the last full test cue (§7.7 calibration)
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val mutableUi =
        MutableStateFlow(UiState(loadConfig(app), loadCueSet(app), calibratedIndex = loadCalibratedIndex(app)))
    val ui: StateFlow<UiState> = mutableUi.asStateFlow()

    /** Applies and saves a settings change. */
    fun edit(transform: (SessionConfig) -> SessionConfig) {
        val next = transform(ui.value.config).copy(revision = System.currentTimeMillis())
        mutableUi.update { it.copy(config = next) }
        saveConfig(app, next)
    }

    fun showDnd(effect: DndEffect) = mutableUi.update { it.copy(dnd = effect) }

    /** The Prepare step: renders the cues for the current theme (§7.3), never during a session. */
    fun prepareCues() {
        if (ui.value.preparing) return
        val theme = ui.value.config.theme
        mutableUi.update { it.copy(preparing = true, cueMessage = null) }
        viewModelScope.launch {
            val error = try {
                renderCues(app, theme)
                null
            } catch (e: CueException) {
                e.message
            } catch (e: IOException) {
                "Could not store the cues: ${e.message}"
            }
            mutableUi.update { it.copy(preparing = false, cues = loadCueSet(app), cueMessage = error) }
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
                val audio = withContext(Dispatchers.IO) { loadPreparedCues(app, config.theme) }.audio.getValue(cue)
                val index = app.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)
                var last: AudioEvent? = null
                val played = CuePlayer(app).play(cue, audio, config.audio, calibratedIndex = index) { event ->
                    last = event
                    Log.i(TAG_AUDIO, event.toLogLine(config.audio.route, cycle = null))
                }
                if (played) {
                    if (index > 0) { // a muted test is no calibration: it would scale every later cue to silence
                        saveCalibratedIndex(app, index)
                        mutableUi.update { it.copy(calibratedIndex = index) }
                    }
                    null
                } else {
                    "The test cue did not play: ${last?.event} ${last?.detail.orEmpty()}".trim()
                }
            } catch (e: CueException) {
                e.message
            }
            mutableUi.update { it.copy(testing = false, cueMessage = message) }
        }
    }
}
