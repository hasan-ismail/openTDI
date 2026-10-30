package com.opentdi.wear.sensing

import android.Manifest
import android.hardware.SensorManager
import android.os.Build
import com.opentdi.shared.csv.isSessionId
import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.Tracker
import com.samsung.android.service.health.tracking.HealthTrackerException

// Pure rules of the watch sensing layer, unit-tested on the JVM. Only compile-time constants come from Android and
// the Samsung SDK, so nothing here loads their classes.

/** The installed Health Platform / Health Sensor Service app; `….tracking` is only the SDK package (§11.1). */
const val HSS_PACKAGE = "com.samsung.android.service.health"

/** SDK 1.4.x needs Health Sensor Service 1.6.5 or newer (§11.1). */
const val HSS_MIN_VERSION = "1.6.5"

const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
const val READ_ADDITIONAL_HEALTH_DATA =
    "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA"
const val READ_SKIN_TEMPERATURE = "android.permission.health.READ_SKIN_TEMPERATURE"
const val READ_OXYGEN_SATURATION = "android.permission.health.READ_OXYGEN_SATURATION"
const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

/** Samsung trackers that are only logged: each one can be off while the HR/EDA/motion session runs on. */
enum class ExtraTracker(val label: String) { SKIN_TEMP("Skin temperature"), PPG("PPG"), SPO2("Blood oxygen") }

/** Samsung's permission for [extra] (permission-request guide): health permissions from API 36, BODY_SENSORS before. */
fun extraPermission(extra: ExtraTracker, sdkInt: Int): String = when {
    sdkInt < Build.VERSION_CODES.BAKLAVA -> Manifest.permission.BODY_SENSORS
    extra == ExtraTracker.SKIN_TEMP -> READ_SKIN_TEMPERATURE
    extra == ExtraTracker.SPO2 -> READ_OXYGEN_SATURATION
    else -> READ_ADDITIONAL_HEALTH_DATA
}

/** SpO2Set.STATUS of a finished measurement (Samsung's SpO2Set reference). */
const val SPO2_STATUS_COMPLETED = 2
const val SPO2_STATUS_TIMED_OUT = -6

/** The first SpO2 spot check runs this far into a session ("about 2 min after the start"). */
const val SPO2_FIRST_CHECK_S = 120L

/** A spot check is due at session tick [seconds]: at SPO2_FIRST_CHECK_S, then every [intervalMin]. */
fun spo2CheckDue(seconds: Long, intervalMin: Int): Boolean =
    seconds >= SPO2_FIRST_CHECK_S && (seconds - SPO2_FIRST_CHECK_S) % (intervalMin * 60L) == 0L

/** Whether [status] ends a measurement. -4 (moved) and -5 (weak signal) do not: the session's timeout does. */
fun spo2Finished(status: Int?): Boolean = status == SPO2_STATUS_COMPLETED || status == SPO2_STATUS_TIMED_OUT

/** Compares version names per numeric component, so "1.7.00.05" > "1.6.5" and "1.6.05" == "1.6.5". */
fun compareVersions(a: String, b: String): Int {
    val x = versionParts(a)
    val y = versionParts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

private fun versionParts(version: String) = version.split('.').map { part ->
    part.takeWhile(Char::isDigit).toLongOrNull() ?: 0L
}

fun hssVersionSupported(versionName: String): Boolean = compareVersions(versionName, HSS_MIN_VERSION) >= 0

/** `ConnectionListener.onConnectionFailed` error code → status (§11.6). */
fun connectionFailureStatus(errorCode: Int?): SensorStatus = when (errorCode) {
    HealthTrackerException.PACKAGE_NOT_INSTALLED -> SensorStatus.HSS_NOT_INSTALLED
    HealthTrackerException.OLD_PLATFORM_VERSION -> SensorStatus.HSS_TOO_OLD
    else -> SensorStatus.CONNECTION_FAILED
}

/** Reconnect delay after the [attempt]-th failure in a row (0-based): 1, 2, 4 … 30 s (§11.6). */
fun reconnectDelayMs(attempt: Int): Long = minOf(30_000L, 1000L shl attempt.coerceIn(0, 5))

/**
 * CONNECTED_SAMSUNG / FALLBACK_ANDROID_SENSORS once every requested tracker has delivered data, or null. Without both
 * heart rate and motion there is no such status (that path reports NO_SENSORS or PERMISSION_MISSING), and it never
 * replaces a [current] status that needs the user.
 */
fun deliveringStatus(
    source: SensorSource,
    requested: Set<Tracker>,
    delivered: Set<Tracker>,
    current: SensorStatus?,
): SensorStatus? = when {
    current == SensorStatus.PERMISSION_MISSING || current == SensorStatus.SDK_POLICY_ERROR -> null
    Tracker.HR !in requested || Tracker.ACCEL !in requested || !delivered.containsAll(requested) -> null
    source == SensorSource.SAMSUNG_SDK -> SensorStatus.CONNECTED_SAMSUNG
    else -> SensorStatus.FALLBACK_ANDROID_SENSORS
}

/**
 * The on-watch text of a status, in plain words; the codes and the raw [message] of CONNECTION_FAILED stay in Logcat
 * (the parameter is kept for the caller). [unsupported] names the tracker of TRACKER_UNSUPPORTED.
 */
fun statusText(
    status: SensorStatus,
    unsupported: Tracker? = null,
    edaAvailable: Boolean = true,
    @Suppress("UNUSED_PARAMETER") message: String? = null,
): String = when (status) {
    SensorStatus.AAR_NOT_BUNDLED -> "Basic watch sensors: heart rate and movement. Samsung's extra sensors can be " +
        "added on the phone: Settings › More › Samsung's sensor kit."
    SensorStatus.HSS_NOT_INSTALLED -> "Samsung's Health Platform is missing. Tap to install it."
    SensorStatus.HSS_TOO_OLD -> "Samsung's Health Platform needs an update. Tap to update it."
    SensorStatus.SDK_POLICY_ERROR ->
        "Samsung's extra sensors are locked for this app. Tap Use basic sensors: your night is still tracked."
    SensorStatus.PERMISSION_MISSING -> "OpenTDI needs to read your heart rate and movement. Tap to allow."
    SensorStatus.TRACKER_UNSUPPORTED -> when (unsupported) {
        Tracker.EDA -> "This watch can't measure skin response. Everything else works."
        Tracker.HR -> "Samsung's heart rate isn't available here: using the watch's basic sensors."
        else -> "Samsung's motion sensor isn't available here: using the watch's basic sensors."
    }
    SensorStatus.CONNECTION_FAILED -> "Sensors reconnecting…"
    SensorStatus.CONNECTED_SAMSUNG ->
        if (edaAvailable) {
            "Samsung sensors: heart rate, heartbeat timing, skin response and movement."
        } else {
            "Samsung sensors: heart rate, heartbeat timing and movement."
        }
    SensorStatus.FALLBACK_ANDROID_SENSORS -> "Basic watch sensors: heart rate and movement."
    SensorStatus.SIMULATION -> "SIMULATION — not real sensor data."
    SensorStatus.NO_SENSORS -> "The watch can't read its sensors. The phone's voice still plays on its timer."
}

/** READ_HEART_RATE from API 36 (BAKLAVA), BODY_SENSORS before (§11.4). */
fun heartRatePermission(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS

/**
 * Runtime permissions requested before the health service starts (§14.2); POST_NOTIFICATIONS exists from API 33. The
 * microphone is asked for here, at setup, not first at the morning's dream report; refused, the night still runs.
 */
fun runtimePermissions(sdkInt: Int): List<String> = buildList {
    add(heartRatePermission(sdkInt))
    add(READ_ADDITIONAL_HEALTH_DATA)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    add(Manifest.permission.RECORD_AUDIO)
    if (sdkInt >= Build.VERSION_CODES.BAKLAVA) {
        add(READ_SKIN_TEMPERATURE)
        add(READ_OXYGEN_SATURATION)
    }
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(POST_NOTIFICATIONS)
}

/**
 * HEART_RATE_STATUS for an ANDROID_SENSORS reading (§11.8): 1 (Samsung's "ok") when bpm > 0 and the accuracy is
 * neither SENSOR_STATUS_NO_CONTACT (-1) nor SENSOR_STATUS_UNRELIABLE (0); otherwise that accuracy, or 0.
 */
fun androidHrStatus(bpm: Float, accuracy: Int): Int = when {
    accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT || accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE -> accuracy
    bpm > 0f -> 1
    else -> 0
}

/** Session directories to delete so the newest [keep] remain (§12.1); ids start with the UTC start time. */
fun staleSessionDirs(names: List<String>, keep: Int): List<String> =
    names.filter(::isSessionId).sortedDescending().drop(keep)
