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
    fun statusTexts_areTheSpecTexts() {
        assertEquals(
            "This build has no Samsung sensor SDK. Using basic watch sensors (no EDA or beat-to-beat data).",
            statusText(SensorStatus.AAR_NOT_BUNDLED),
        )
        assertEquals(
            "Health Sensor Service (Health Platform) is too old for OpenTDI (needs 1.6.5 or newer). Tap to update it.",
            statusText(SensorStatus.HSS_TOO_OLD),
        )
        assertEquals(
            "Samsung sensors connected: heart rate, IBI, EDA, motion.",
            statusText(SensorStatus.CONNECTED_SAMSUNG),
        )
        assertEquals(
            "Samsung sensors connected: heart rate, IBI, motion.",
            statusText(SensorStatus.CONNECTED_SAMSUNG, edaAvailable = false),
        )
        assertEquals(
            "This watch does not support EDA. Detection continues without it.",
            statusText(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.EDA),
        )
        assertEquals(
            "This watch does not support heart rate through Samsung's SDK. Using basic watch sensors.",
            statusText(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.HR),
        )
        assertEquals(
            "This watch does not support motion through Samsung's SDK. Using basic watch sensors.",
            statusText(SensorStatus.TRACKER_UNSUPPORTED, unsupported = Tracker.ACCEL),
        )
        assertEquals(
            "Cannot reach Health Sensor Service: connection ended. Retrying.",
            statusText(SensorStatus.CONNECTION_FAILED, message = "connection ended"),
        )
        SensorStatus.entries.forEach { assertTrue(it.name, statusText(it).isNotBlank()) }
    }

    @Test
    fun runtimePermissions_followTheApiLevel() {
        val activity = "android.permission.ACTIVITY_RECOGNITION"
        val notifications = "android.permission.POST_NOTIFICATIONS"
        assertEquals(
            listOf("android.permission.health.READ_HEART_RATE", READ_ADDITIONAL_HEALTH_DATA, activity, notifications),
            runtimePermissions(36),
        )
        assertEquals(
            listOf("android.permission.BODY_SENSORS", READ_ADDITIONAL_HEALTH_DATA, activity, notifications),
            runtimePermissions(35),
        )
        assertEquals(
            listOf("android.permission.BODY_SENSORS", READ_ADDITIONAL_HEALTH_DATA, activity),
            runtimePermissions(30),
        )
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
