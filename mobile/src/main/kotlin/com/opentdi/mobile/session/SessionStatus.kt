package com.opentdi.mobile.session

import com.opentdi.shared.model.TdiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the session screen shows. [nextEventAtRealtimeMs] is on the SystemClock.elapsedRealtime() clock. */
data class SessionStatus(
    val running: Boolean = false,
    val sessionId: String? = null,
    val state: TdiState = TdiState.IDLE,
    val cycle: Int = 0,
    val completedCycles: Int = 0,
    val maxCycles: Int = 0,
    val nextEventAtRealtimeMs: Long? = null,
    val lastEvent: String? = null,
    val error: String? = null,
)

/** The process-wide session state: [NapSessionService] writes it, the UI reads it. */
object SessionMonitor {
    private val mutableStatus = MutableStateFlow(SessionStatus())
    val status: StateFlow<SessionStatus> = mutableStatus.asStateFlow()

    fun update(transform: (SessionStatus) -> SessionStatus) = mutableStatus.update(transform)
}
