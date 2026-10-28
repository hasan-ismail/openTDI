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
    val wbtbBackToSleepAtMs: Long? = null, // after a WBTB alarm: when the back-to-sleep buzz comes, epoch ms
    val lastWakeText: String? = null, // how long the last morning wake-up took
    val sessionId: String? = null,
    val startedElapsedMs: Long = 0L,
    val logRawAccel: Boolean = false,
    val source: SensorSource? = null,
    val status: SensorStatus? = null, // null until the first status of the session
    val fallbackCause: SensorStatus? = null, // why this session uses basic watch sensors (AAR, HSS, unsupported)
    val unsupported: Tracker? = null, // the tracker behind TRACKER_UNSUPPORTED
    val edaAvailable: Boolean = true,
    val statusMessage: String? = null, // the <message> of CONNECTION_FAILED
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
