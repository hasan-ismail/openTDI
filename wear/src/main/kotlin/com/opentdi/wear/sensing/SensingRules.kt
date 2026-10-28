package com.opentdi.wear.sensing

import android.Manifest
import android.hardware.SensorManager
import android.os.Build
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

/** The exact on-watch text of §11.6. [unsupported] names the tracker of TRACKER_UNSUPPORTED. */
fun statusText(
    status: SensorStatus,
    unsupported: Tracker? = null,
    edaAvailable: Boolean = true,
    message: String? = null,
): String = when (status) {
    SensorStatus.AAR_NOT_BUNDLED ->
        "This build has no Samsung sensor SDK. Using basic watch sensors (no EDA or beat-to-beat data)."
    SensorStatus.HSS_NOT_INSTALLED -> "Health Sensor Service (Health Platform) is not installed. Tap to install it."
    SensorStatus.HSS_TOO_OLD ->
        "Health Sensor Service (Health Platform) is too old for OpenTDI (needs 1.6.5 or newer). Tap to update it."
    SensorStatus.SDK_POLICY_ERROR ->
        "Samsung blocked sensor access (SDK_POLICY_ERROR). Developer builds need Health Sensor Service developer " +
            "mode: see the OpenTDI developer setup guide. Also check that the watch is online."
    SensorStatus.PERMISSION_MISSING ->
        "OpenTDI needs permission for: heart rate / additional health data / physical activity. Tap to grant."
    SensorStatus.TRACKER_UNSUPPORTED -> when (unsupported) {
        Tracker.EDA -> "This watch does not support EDA. Detection continues without it."
        Tracker.HR -> "This watch does not support heart rate through Samsung's SDK. Using basic watch sensors."
        else -> "This watch does not support motion through Samsung's SDK. Using basic watch sensors."
    }
    SensorStatus.CONNECTION_FAILED -> "Cannot reach Health Sensor Service: $message. Retrying."
    SensorStatus.CONNECTED_SAMSUNG ->
        if (edaAvailable) {
            "Samsung sensors connected: heart rate, IBI, EDA, motion."
        } else {
            "Samsung sensors connected: heart rate, IBI, motion."
        }
    SensorStatus.FALLBACK_ANDROID_SENSORS ->
        "Using basic watch sensors (heart rate, motion). EDA and IBI are unavailable."
    SensorStatus.SIMULATION -> "SIMULATION — not real sensor data."
    SensorStatus.NO_SENSORS -> "No usable sensors. You can still run a timed-cues session from the phone."
}

/** READ_HEART_RATE from API 36 (BAKLAVA), BODY_SENSORS before (§11.4). */
fun heartRatePermission(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS

/** Runtime permissions requested before the health service starts (§14.2); POST_NOTIFICATIONS exists from API 33. */
fun runtimePermissions(sdkInt: Int): List<String> = buildList {
    add(heartRatePermission(sdkInt))
    add(READ_ADDITIONAL_HEALTH_DATA)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
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

private val SESSION_DIR_NAME = Regex("""\d{8}T\d{6}Z-[0-9a-f]{4}""")

/** Session directories to delete so the newest [keep] remain (§12.1); ids start with the UTC start time. */
fun staleSessionDirs(names: List<String>, keep: Int): List<String> =
    names.filter { SESSION_DIR_NAME.matches(it) }.sortedDescending().drop(keep)
