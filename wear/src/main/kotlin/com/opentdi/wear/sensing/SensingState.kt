package com.opentdi.wear.sensing

import android.app.Activity
import com.opentdi.shared.model.GravityMode
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.EmittedPacket
import com.opentdi.shared.sensing.Tracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the watch UI shows of the sensing layer. Written on the sensing thread, read by the UI. */
data class SensingState(
    val running: Boolean = false,
    val waiting: Boolean = false, // the service runs with the sensors off until the phone's Start session
    val alarmRinging: String? = null, // "WAKE" or "WBTB" while an alarm rings (WakeAlarms)
    val wakeAgainAtMs: Long? = null, // a snoozed or rung-out wake-up rings again then, epoch ms
    val backupAtMs: Long? = null, // the system's backup for the wake-up (WakeBackup) fires then, epoch ms; null: none
    val wbtbSkip: String? = null, // why tonight's early wake-up won't ring: MIN_SLEEP_BEFORE_WBTB or PASSED
    // Tonight's early wake-up once decided, epoch ms; null while the watch waits to see you fall asleep, or it is off.
    val wbtbAtMs: Long? = null,
    // Tonight's early wake-up still to ring, epoch ms (while the watch waits: the set time, the latest it rings); null:
    // none, done, skipped.
    val wbtbAheadAtMs: Long? = null,
    val wbtbNowFromMs: Long? = null, // "I'm up now" is offered from then, epoch ms: 4.5 h into the night
    val wbtbSkippedTonight: Boolean = false, // "Skip the early wake-up" was tapped tonight
    val wbtbBackToSleepAtMs: Long? = null, // after a WBTB alarm: when the back-to-sleep buzz comes, epoch ms
    val lastWakeText: String? = null, // how long the last morning wake-up took
    val snoozesLeft: Int = 0, // while the wake-up alarm rings
    val lateCuesTraining: Boolean = false, // after a WBTB alarm with late cues: the cue is being trained
    val guideOffered: Boolean = false, // after the back-to-sleep buzz: "I'm in bed" starts the guided back-to-sleep
    val guideRunning: Boolean = false, // the guide plays; a long press or the back key stops it
    // "WAKE", "WBTB" or "NIGHT" (saved in the night, only the recording): a dream report is due (DreamReport). Saved
    // reports also have "LATER": from last night, by hand.
    val reportAfter: String? = null,
    val sessionId: String? = null,
    val startedElapsedMs: Long = 0L,
    val logRawAccel: Boolean = false,
    val source: SensorSource? = null,
    val status: SensorStatus? = null, // null until the first status of the session
    val fallbackCause: SensorStatus? = null, // why this session uses basic watch sensors (AAR, HSS, unsupported)
    val unsupported: Tracker? = null, // the tracker behind TRACKER_UNSUPPORTED
    val edaAvailable: Boolean = true,
    val resolve: ((Activity) -> Boolean)? = null, // HealthTrackerException.resolve; false when the Store did not open
    val lastPacket: EmittedPacket? = null,
    val packetsWritten: Long = 0,
    val ibisAccepted: Long = 0,
    val gravityMode: GravityMode = GravityMode.UNKNOWN,
    val gravityProgress: Int = 0, // calm seconds collected toward the gravity decision
    val extrasOff: Map<ExtraTracker, String> = emptyMap(), // why a logging-only tracker is not running
    val lastSpo2: Int? = null, // %, the last completed spot check
    val lastSpo2AtMs: Long? = null, // wall time its check ended, epoch ms
    val ppgRate: Float? = null, // PPG samples delivered per second over the last 10 s
)

/** Process-wide holder of [SensingState]: the service writes it, the activity collects it. */
object SensingStateHolder {
    private val mutableState = MutableStateFlow(SensingState())
    val state: StateFlow<SensingState> = mutableState.asStateFlow()

    fun update(transform: (SensingState) -> SensingState) = mutableState.update(transform)
}
