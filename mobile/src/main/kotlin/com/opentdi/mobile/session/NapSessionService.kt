package com.opentdi.mobile.session

import com.opentdi.shared.model.AlarmTone
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.TdiCommand
import com.opentdi.shared.model.DeviceRole
import com.opentdi.shared.model.WatchState
import com.opentdi.mobile.link.watchRequest
import com.opentdi.mobile.MainActivity
import com.opentdi.mobile.R
import com.opentdi.mobile.TAG_AUDIO
import com.opentdi.mobile.TAG_SESSION
import com.opentdi.mobile.audio.AudioEvent
import com.opentdi.mobile.link.WatchStreamListener
import com.opentdi.mobile.link.WatchTelemetry
import com.opentdi.mobile.audio.CuePlayer
import com.opentdi.mobile.audio.dndState
import com.opentdi.mobile.audio.toLogLine
import com.opentdi.mobile.cues.CueException
import com.opentdi.mobile.cues.PreparedCues
import com.opentdi.mobile.cues.encodingName
import com.opentdi.mobile.cues.loadPreparedCues
import com.opentdi.mobile.loadCalibratedIndex
import com.opentdi.mobile.loadConfig
import com.opentdi.mobile.logLine
import com.opentdi.mobile.stats.NightsRepository
import com.opentdi.shared.model.AudioRouteMode
import com.opentdi.shared.audio.WavAudio
import com.opentdi.shared.audio.renderEndChime
import com.opentdi.shared.audio.targetGain
import com.opentdi.shared.config.StructuralConstants
import com.opentdi.shared.config.thresholds
import com.opentdi.shared.config.toConfigRows
import com.opentdi.shared.config.validate
import com.opentdi.shared.model.PromptCue
import com.opentdi.shared.model.SessionConfig
import com.opentdi.shared.model.SessionEndReason
import com.opentdi.shared.model.TdiState
import com.opentdi.mobile.ui.TimingPreset
import com.opentdi.shared.model.newSessionId
import com.opentdi.shared.timed.StillnessGate
import com.opentdi.shared.timed.TimedCueScheduler
import com.opentdi.shared.timed.TimedEvent
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The phone's one foreground service (§14.3; type mediaPlayback in Phase 1), started from the visible activity. It
 * runs one TIMED_CUES session (§4.2–4.3): the shared TimedCueScheduler clocked from elapsed-realtime under a partial
 * wake lock, the cues it asks for, their end reported back, and the session CSVs (§12.3).
 */
class NapSessionService : LifecycleService() {
    private var session: Session? = null

    /** End the night on the watch stops the voice too; a Quick test never started the watch, so it plays on. */
    private val watchCommands = MessageClient.OnMessageReceivedListener { event ->
        val command = try {
            OpenTdiJson.decodeFromString(TdiCommand.serializer(), event.data.decodeToString())
        } catch (e: IllegalArgumentException) { // SerializationException: another app version
            return@OnMessageReceivedListener
        }
        val running = session
        if (command is TdiCommand.AbortSession && running != null && !running.quickTest) {
            Log.i(TAG_SESSION, logLine("WATCH_ENDED_NIGHT"))
            running.stop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The UI's listener stops with the screen; this one keeps the watch stream arriving for the whole session.
        lifecycle.addObserver(WatchStreamListener(applicationContext))
        Wearable.getMessageClient(this).addListener(watchCommands, COMMAND_URI, MessageClient.FILTER_LITERAL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            session?.stop() ?: stopSelf()
        } else {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            if (session == null) session = open(quickTest = intent?.getBooleanExtra(EXTRA_QUICK_TEST, false) == true)
        }
        return START_NOT_STICKY // a nap never restarts by itself after the process died
    }

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(watchCommands)
        session?.let {
            it.release()
            SessionMonitor.update { status -> status.copy(running = false, nextEventAtRealtimeMs = null) }
        }
        super.onDestroy()
    }

    /** [quickTest]: one run on Quick test timing, which is never saved, so a real night cannot start on it. */
    private fun open(quickTest: Boolean): Session? {
        val config = loadConfig(this).let { if (quickTest) TimingPreset.QUICK_TEST.applyTo(it) else it }
        val invalid = config.validate().firstOrNull()
        if (invalid != null) {
            Log.w(TAG_SESSION, logLine("INVALID_SETTING", "error" to invalid))
            return refuse(
                "Some settings don't fit together. Open Settings > More settings and tap Reset all of these to normal.",
            )
        }
        // The volume guard reads the media index of the current media device, which must be the cue's speaker.
        if (config.audio.route != AudioRouteMode.WATCH_WRIST_SPEAKER && !CuePlayer(this).streamOnSpeaker()) {
            return refuse(
                "The phone's sound is going to headphones or a speaker. " +
                    "Disconnect them, or turn on Play the voice on the watch in Settings.",
            )
        }
        val cues = try {
            loadPreparedCues(this, config)
        } catch (e: CueException) {
            return refuse(e.message.orEmpty())
        }
        val session = try {
            Session(config, cues, quickTest)
        } catch (e: IOException) { // the session folder or its CSVs could not be created
            Log.w(TAG_SESSION, logLine("SESSION_FILES_FAILED", "error" to e.message))
            return refuse("OpenTDI couldn't save tonight's files. Free some space on the phone and try again.")
        }
        return session.also { it.begin() }
    }

    private fun refuse(reason: String): Session? {
        Log.w(TAG_SESSION, logLine("START_REFUSED", "reason" to reason))
        SessionMonitor.update { SessionStatus(error = reason) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return null
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bedtime voice", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, NapSessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("OpenTDI: the voice is on")
            .setContentText(
                if (loadConfig(this).audio.route == AudioRouteMode.WATCH_WRIST_SPEAKER) {
                    "Playing on the watch."
                } else {
                    "Playing on the phone speaker."
                },
            )
            .setContentIntent(open)
            .addAction(0, "Stop the voice", stop)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** One session, from start to SESSION_COMPLETE. Every scheduler call runs on the main thread. */
    private inner class Session(
        private val config: SessionConfig,
        private val cues: PreparedCues,
        val quickTest: Boolean,
    ) {
        private val id = newSessionId(System.currentTimeMillis(), Random.Default)
        private val gate = if (config.timed.waitForStillness) StillnessGate(config) else null
        private val scheduler = TimedCueScheduler(id, config, gate)
        private val startRealtimeMs = SystemClock.elapsedRealtime()
        private val audioManager = getSystemService(AudioManager::class.java)
        // §7.7 step 5: the test cue's saved index; without a test cue yet, the index at Start.
        private val calibratedIndex = loadCalibratedIndex(this@NapSessionService)
            ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        private val log = SessionLog(
            File(filesDir, "$SESSIONS_DIR/$id").apply { mkdirs() }, startRealtimeMs, config.audio.route,
        )
        private val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTDI:session")
        private val player = CuePlayer(this@NapSessionService)
        private val endCue = cues.audio.getValue(PromptCue.SESSION_END).let { speech ->
            // A Quick test is not the night: "last cue for tonight" made it sound like the night had started.
            val words = if (quickTest) FloatArray(0) else speech.samples
            WavAudio(renderEndChime(speech.sampleRate, config.audio.endChimeCount) + words, speech.sampleRate)
        }
        private var tickJob: Job? = null
        private var flushJob: Job? = null
        private var gateJob: Job? = null
        private var cueJob: Job? = null
        private var ending = false
        private val watchMissing = "The watch isn't tracking your sleep" +
            (if (config.alarms.wakeEnabled || config.alarms.wbtbEnabled) ", so your alarms won't ring" else "") +
            ". Open OpenTDI on the watch and keep it near the phone."

        fun begin() {
            wakeLock.acquire((config.sessionMaxMin + 10) * 60_000L) // §14.4
            SessionMonitor.update {
                SessionStatus(running = true, sessionId = id, maxCycles = config.maxCycles, quickTest = quickTest)
            }
            // The waiting watch starts recording too (owner, 2026-10-28), but not for a Quick test: that would start
            // the night's recording and alarms hours early, often in the evening.
            if (!quickTest) lifecycleScope.launch {
                val start = TdiCommand.StartSession(id, config, DeviceRole.PHONE, System.currentTimeMillis())
                val alarms = config.alarms
                val spoken = (alarms.wakeEnabled && alarms.wakeTone == AlarmTone.SPOKEN) ||
                    (alarms.wbtbEnabled && alarms.wbtbTone == AlarmTone.SPOKEN)
                var voice = cues.audio[PromptCue.MORNING_WAKE]?.takeIf { spoken }
                var last: String? = null
                var misses = 0
                // A lost answer, a watch opened after Start or a restarted watch leaves it waiting: no tracking, no
                // alarms. So Start repeats all session; a tracking watch only saves the same alarms again.
                while (isActive && !ending) {
                    val state = watchRequest(this@NapSessionService, start)?.state
                    val shown = state?.name ?: "NO_ANSWER"
                    if (shown != last) sessionEvent(logLine("WATCH_START", "state" to shown)) // changes only
                    last = shown
                    misses = if (state == WatchState.RECORDING) 0 else misses + 1
                    SessionMonitor.update {
                        when {
                            misses >= WATCH_MISSES -> it.copy(error = watchMissing)
                            it.error == watchMissing -> it.copy(error = null)
                            else -> it
                        }
                    }
                    if (state != null && voice != null) {
                        sessionEvent(logLine("WAKE_VOICE_SENT", "stored" to player.storeOnWatch(voice)))
                        voice = null
                    }
                    delay(WATCH_RETRY_MS)
                }
            }
            lifecycleScope.launch(Dispatchers.IO) { log.writeConfig(configRows()) }
            val (filter, categories) = getSystemService(NotificationManager::class.java).dndState()
            val index = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioEvent(
                AudioEvent("DND_STATE", null, dndFilter = filter, dndCategories = categories, streamIndex = index),
                cycle = 0,
            )
            sessionEvent(
                logLine(
                    "START", "id" to id, "mode" to config.mode, "origin" to "PHONE",
                    "firstLatencyMin" to scheduler.firstLatencyMin,
                ),
            )
            handle(scheduler.start())
            tickJob = lifecycleScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    handle(scheduler.onTick(elapsedMs()))
                }
            }
            gateJob = gate?.let { g ->
                lifecycleScope.launch {
                    // Only seconds that arrive after Start: frames already held belong to before the session.
                    var seen = WatchTelemetry.live.value.let { it.sessionId to (it.frames.lastOrNull()?.seq ?: -1L) }
                    WatchTelemetry.live.collect { live ->
                        val lastSeq = if (live.sessionId == seen.first) seen.second else -1L
                        for (frame in live.frames) {
                            if (frame.seq > lastSeq) g.add(elapsedMs(), frame.packet.stillnessScore, frame.packet.hr)
                        }
                        seen = live.sessionId to (live.frames.lastOrNull()?.seq ?: lastSeq)
                    }
                }
            }
            flushJob = lifecycleScope.launch {
                while (isActive) {
                    delay(FLUSH_MS)
                    log.flush()
                }
            }
        }

        /** Stop = abort(USER) (row 21): the playing cue is cut and no SESSION_END plays. */
        fun stop() {
            cueJob?.cancel()
            handle(scheduler.abort(SessionEndReason.USER, elapsedMs()))
        }

        fun release() {
            log.close()
            if (wakeLock.isHeld) wakeLock.release()
        }

        private fun handle(events: List<TimedEvent>) {
            for (e in events) {
                when (e) {
                    is TimedEvent.StateChanged -> {
                        log.transition(e)
                        sessionEvent(
                            logLine("STATE ${e.from}->${e.to}", "driver" to "TIMED", "reason" to e.reason, "cycle" to e.cycle),
                        )
                    }
                    is TimedEvent.PlayCue -> play(e.cue, e.cycle)
                    is TimedEvent.StartReportWindow ->
                        sessionEvent(logLine("REPORT_WINDOW", "cycle" to e.cycle, "durationMs" to e.durationMs))
                    is TimedEvent.CycleCompleted ->
                        sessionEvent(logLine("STAGE CYCLE_COMPLETE", "cycle" to e.cycle, "outcome" to e.outcome))
                    is TimedEvent.SessionCompleted ->
                        sessionEvent(logLine("SESSION_COMPLETE", "reason" to e.reason, "cycles" to scheduler.completedCycles))
                }
            }
            SessionMonitor.update {
                it.copy(
                    state = scheduler.state,
                    cycle = scheduler.cycle,
                    completedCycles = scheduler.completedCycles,
                    nextEventAtRealtimeMs = scheduler.nextEventAtMs?.let { at -> startRealtimeMs + at },
                )
            }
            if (scheduler.state == TdiState.SESSION_COMPLETE && !ending) end()
        }

        private fun play(cue: PromptCue, cycle: Int) {
            val previous = cueJob
            cueJob = lifecycleScope.launch {
                // One cue at a time. Not cancelAndJoin: SESSION_END is asked for from inside the RE_INCUBATION job.
                previous?.join()
                val audio = if (cue == PromptCue.SESSION_END) endCue else cues.audio.getValue(cue)
                val played = player.play(cue, audio, config.audio, calibratedIndex) { audioEvent(it, cycle) }
                val now = elapsedMs()
                handle(if (played) scheduler.onCueFinished(cue, now) else scheduler.onCueFailed(cue, now))
            }
        }

        /** Waits for the SESSION_END cue (if one was asked for), then closes the CSVs and stops the service. */
        private fun end() {
            ending = true
            tickJob?.cancel()
            flushJob?.cancel()
            gateJob?.cancel()
            val last = cueJob
            lifecycleScope.launch {
                last?.join()
                release()
                NightsRepository.syncInBackground(applicationContext) // outlives the service; best effort
                SessionMonitor.update { it.copy(running = false, nextEventAtRealtimeMs = null) }
                session = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        private fun elapsedMs() = SystemClock.elapsedRealtime() - startRealtimeMs

        private fun audioEvent(e: AudioEvent, cycle: Int) {
            log.audio(e, cycle)
            show(TAG_AUDIO, e.toLogLine(config.audio.route, cycle))
        }

        private fun sessionEvent(line: String) = show(TAG_SESSION, line)

        private fun show(tag: String, line: String) {
            Log.i(tag, line)
            val time = LocalTime.now().format(CLOCK)
            SessionMonitor.update { it.copy(lastEvent = "$time $line") }
        }

        /** p_config.csv (§12.3): app, device, TTS and route rows, then every SessionConfig field and const.* row. */
        private fun configRows(): List<Pair<String, String>> {
            val app = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            val speaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            return buildList {
                add("app.version" to app.versionName.orEmpty())
                add("app.versionCode" to app.longVersionCode.toString())
                add("app.applicationId" to packageName)
                add("device.manufacturer" to Build.MANUFACTURER)
                add("device.model" to Build.MODEL)
                add("device.androidVersion" to Build.VERSION.RELEASE)
                add("device.sdkInt" to Build.VERSION.SDK_INT.toString())
                add("session.id" to id)
                add("session.firstLatencyMin" to scheduler.firstLatencyMin.toString())
                add(QUICK_TEST_ROW to if (quickTest) "1" else "0")
                add("tts.engine" to cues.set.engine)
                add("tts.voice" to cues.set.voice)
                for (rendered in cues.set.cues) {
                    val key = "cue.${rendered.cue}"
                    add("$key.ttsSampleRate" to rendered.ttsSampleRate.toString())
                    add("$key.ttsEncoding" to encodingName(rendered.ttsEncoding))
                    add("$key.ttsChannels" to rendered.ttsChannels.toString())
                    add("$key.sha256" to cues.sha256.getValue(rendered.cue))
                }
                if (config.audio.route != AudioRouteMode.WATCH_WRIST_SPEAKER) { // the watch keeps its own calibration
                    add("route.deviceType" to speaker?.type?.toString().orEmpty())
                    add("route.deviceId" to speaker?.id?.toString().orEmpty())
                    add("audio.calibratedStreamIndex" to calibratedIndex.toString())
                }
                add("audio.streamMaxIndex" to audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toString())
                add("audio.targetGain" to config.audio.targetGain().toString())
                add(
                    "audio.supportAudioSourceUnprocessed" to
                        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED).orEmpty(),
                )
                add("audio.flingerStandbyTimeMs" to systemProperty("ro.audio.flinger_standbytime_ms"))
                addAll(config.toConfigRows())
                addAll(config.detection.thresholds().toConfigRows())
                addAll(StructuralConstants.configRows())
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.opentdi.mobile.action.STOP"
        const val EXTRA_QUICK_TEST = "quickTest"
        /** The p_config.csv key that marks a Quick test, which Stats leaves out. */
        const val QUICK_TEST_ROW = "session.quickTest"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "session"
        private const val TICK_MS = 250L
        private const val FLUSH_MS = 5000L // §12.1
        private const val WATCH_RETRY_MS = 20_000L
        private const val WATCH_MISSES = 3 // Start requests without a tracking watch before Tonight says so
        private val COMMAND_URI = "wear://*${DataLayerPaths.STATE_COMMAND}".toUri()
        private val CLOCK = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)

        // ro.* properties are readable through getprop; the value is empty when the build does not set it.
        private fun systemProperty(name: String): String = try {
            ProcessBuilder("getprop", name).start().inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: IOException) {
            ""
        }
    }
}
