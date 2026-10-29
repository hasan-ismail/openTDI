package com.opentdi.wear.sensing

import com.opentdi.shared.model.SensorSource
import com.opentdi.shared.model.SensorStatus
import com.opentdi.shared.sensing.Tracker
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensingRulesTest {
    @Test
    fun hssVersion_comparedPerNumericComponent() {
        assertTrue(compareVersions("1.7.00.05", "1.6.5") > 0) // the owner's SM-L500 (device_audit.md)
        assertEquals(0, compareVersions("1.6.05", "1.6.5"))
        assertEquals(0, compareVersions("1.6.5.0", "1.6.5"))
        assertTrue(compareVersions("1.6.4.99", "1.6.5") < 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0) // numeric, not lexicographic
        assertTrue(hssVersionSupported("1.7.00.05"))
        assertTrue(hssVersionSupported("1.6.5"))
        assertTrue(hssVersionSupported("1.8.00.03-beta"))
        assertFalse(hssVersionSupported("1.6.4"))
        assertFalse(hssVersionSupported("1.5.20.7"))
    }

    @Test
    fun connectionFailure_mapsSamsungErrorCodes() {
        assertEquals(SensorStatus.HSS_NOT_INSTALLED, connectionFailureStatus(0)) // PACKAGE_NOT_INSTALLED
        assertEquals(SensorStatus.HSS_TOO_OLD, connectionFailureStatus(1)) // OLD_PLATFORM_VERSION
        assertEquals(SensorStatus.CONNECTION_FAILED, connectionFailureStatus(2))
        assertEquals(SensorStatus.CONNECTION_FAILED, connectionFailureStatus(null))
    }

    @Test
    fun reconnect_backsOffTo30s() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L), (0..6).map(::reconnectDelayMs))
        assertEquals(30000L, reconnectDelayMs(1000))
    }

    @Test
    fun deliveringStatus_needsEveryRequestedTrackerAndHeartRatePlusMotion() {
        val all = setOf(Tracker.HR, Tracker.EDA, Tracker.ACCEL)
        val hrAccel = setOf(Tracker.HR, Tracker.ACCEL)
        assertEquals(SensorStatus.CONNECTED_SAMSUNG, deliveringStatus(SensorSource.SAMSUNG_SDK, all, all, null))
        assertNull(deliveringStatus(SensorSource.SAMSUNG_SDK, all, hrAccel, null)) // EDA not delivered yet
        assertEquals(
            SensorStatus.CONNECTED_SAMSUNG, // EDA unsupported
            deliveringStatus(SensorSource.SAMSUNG_SDK, hrAccel, hrAccel, SensorStatus.TRACKER_UNSUPPORTED),
        )
        assertEquals(
            SensorStatus.FALLBACK_ANDROID_SENSORS,
            deliveringStatus(SensorSource.ANDROID_SENSORS, hrAccel, hrAccel, SensorStatus.AAR_NOT_BUNDLED),
        )
        val accel = setOf(Tracker.ACCEL)
        assertNull(deliveringStatus(SensorSource.ANDROID_SENSORS, accel, accel, null)) // no HR: NO_SENSORS stays
        // A tracker's PERMISSION_ERROR after it had delivered stays visible; a reconnect earns CONNECTED again.
        assertNull(deliveringStatus(SensorSource.SAMSUNG_SDK, all, all, SensorStatus.PERMISSION_MISSING))
        assertNull(deliveringStatus(SensorSource.SAMSUNG_SDK, all, all, SensorStatus.SDK_POLICY_ERROR))
        assertEquals(
            SensorStatus.CONNECTED_SAMSUNG,
            deliveringStatus(SensorSource.SAMSUNG_SDK, all, all, SensorStatus.CONNECTION_FAILED),
        )
    }

    @Test
    fun statusTexts_areInPlainWords() {
        assertEquals(
            "Basic watch sensors: heart rate and movement. Samsung's extra sensors can be added on the phone's Watch tab.",
            statusText(SensorStatus.AAR_NOT_BUNDLED),
        )
        assertEquals("Samsung's Health Platform needs an update. Tap to update it.", statusText(SensorStatus.HSS_TOO_OLD))
        assertEquals(
            "Samsung sensors: heart rate, heartbeat timing, skin response and movement.",
            statusText(SensorStatus.CONNECTED_SAMSUNG),
        )
        assertEquals(
            "Samsung sensors: heart rate, heartbeat timing and movement.",
            statusText(SensorStatus.CONNECTED_SAMSUNG, edaAvailable = false),
        )
        assertEquals(
            "This watch can't measure skin response. Everything else works.",
            statusText(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.EDA),
        )
        assertEquals(
            "Samsung's heart rate isn't available here: using the watch's basic sensors.",
            statusText(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.HR),
        )
        assertEquals(
            "Samsung's motion sensor isn't available here: using the watch's basic sensors.",
            statusText(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.ACCEL),
        )
        // The raw message stays in Logcat.
        assertEquals("Sensors reconnecting…", statusText(SensorStatus.CONNECTION_FAILED, message = "connection ended"))
        SensorStatus.entries.forEach { assertTrue(it.name, statusText(it).isNotBlank()) }
    }

    @Test
    fun runtimePermissions_followTheApiLevel() {
        val activity = "android.permission.ACTIVITY_RECOGNITION"
        val mic = "android.permission.RECORD_AUDIO" // asked at setup, not first at the morning's dream report
        val notifications = "android.permission.POST_NOTIFICATIONS"
        assertEquals(
            listOf(
                "android.permission.health.READ_HEART_RATE", READ_ADDITIONAL_HEALTH_DATA, activity, mic,
                "android.permission.health.READ_SKIN_TEMPERATURE", "android.permission.health.READ_OXYGEN_SATURATION",
                notifications,
            ),
            runtimePermissions(36),
        )
        assertEquals(
            listOf("android.permission.BODY_SENSORS", READ_ADDITIONAL_HEALTH_DATA, activity, mic, notifications),
            runtimePermissions(35),
        )
        assertEquals(
            listOf("android.permission.BODY_SENSORS", READ_ADDITIONAL_HEALTH_DATA, activity, mic),
            runtimePermissions(30),
        )
    }

    @Test
    fun extraPermissions_followSamsungsTable() {
        assertEquals("android.permission.health.READ_SKIN_TEMPERATURE", extraPermission(ExtraTracker.SKIN_TEMP, 36))
        assertEquals("android.permission.health.READ_OXYGEN_SATURATION", extraPermission(ExtraTracker.SPO2, 36))
        assertEquals(READ_ADDITIONAL_HEALTH_DATA, extraPermission(ExtraTracker.PPG, 36))
        ExtraTracker.entries.forEach { assertEquals("android.permission.BODY_SENSORS", extraPermission(it, 35)) }
        // Every extra's API 36 permission is requested with the others, so starting a session asks for it.
        ExtraTracker.entries.forEach { assertTrue(extraPermission(it, 36) in runtimePermissions(36)) }
    }

    @Test
    fun spo2Checks_firstAt2Min_thenEveryInterval() {
        val due = (0L..3_000L).filter { spo2CheckDue(it, intervalMin = 15) }
        assertEquals(listOf(120L, 1020L, 1920L, 2820L), due)
        assertTrue(spo2CheckDue(420, intervalMin = 5))
        assertFalse(spo2CheckDue(419, intervalMin = 5))
        assertTrue(spo2Finished(2)) // completed
        assertTrue(spo2Finished(-6)) // Samsung's own timeout
        listOf(0, -4, -5, null).forEach { assertFalse("$it", spo2Finished(it)) } // calculating, moved, weak signal
    }

    @Test
    fun androidHeartRate_validOnlyWithContactAndAPositiveValue() {
        assertEquals(1, androidHrStatus(62f, 3)) // SENSOR_STATUS_ACCURACY_HIGH
        assertEquals(1, androidHrStatus(62f, 1)) // ACCURACY_LOW still counts
        assertEquals(-1, androidHrStatus(62f, -1)) // SENSOR_STATUS_NO_CONTACT
        assertEquals(0, androidHrStatus(62f, 0)) // SENSOR_STATUS_UNRELIABLE
        assertEquals(0, androidHrStatus(0f, 3))
        assertEquals(0, androidHrStatus(Float.NaN, 3))
    }

    @Test
    fun retention_deletesOnlyOlderSessionDirectories() {
        val sessions = (1..32).map { String.format(Locale.ROOT, "202610%02dT120000Z-%04x", it, it) }
        val names = sessions.shuffled() + listOf("notes", "20261001T120000Z", "zzz")
        assertEquals(sessions.take(2).reversed(), staleSessionDirs(names, keep = 30))
        assertEquals(emptyList<String>(), staleSessionDirs(sessions.take(30), keep = 30))
    }
}
