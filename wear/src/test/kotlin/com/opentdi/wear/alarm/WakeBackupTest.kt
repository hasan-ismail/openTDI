package com.opentdi.wear.alarm

import com.opentdi.shared.model.AlarmConfig
import com.opentdi.shared.model.AlarmTone
import com.opentdi.wear.alarm.WakeBackup.Fire
import com.opentdi.wear.alarm.WakeBackup.RETRY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class WakeBackupTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val night = 23 * hour // a night begun at 23:00 (UTC day 0)
    private val seven = 31 * hour // 7:00 the next morning
    private val rec = WakeBackup.Record(dueAt = seven, wakeAtMin = 7 * 60, nightStartMs = night, dir = "/s/n1")
    private val on = AlarmConfig(wakeEnabled = true, wakeAtMin = 7 * 60)

    private fun due(wakeAt: Long?, kind: String? = null, ringStart: Long? = null, done: Boolean = false, now: Long) =
        WakeBackup.dueAt(wakeAt, kind, ringStart, maxRingMin = 10, morningDone = done, nowMs = now)

    @Test
    fun dueAt_followsWhatTheClockMustDoNext() {
        // Before the ring: the wake time; after a snooze: the snooze's time (WakeAlarms puts it in wakeAt).
        assertEquals(seven, due(seven, now = night))
        assertEquals(seven + 9 * min, due(seven + 9 * min, now = seven + min))
        // While the wake-up rings (wakeAt already tomorrow's): the end of its ring, when the clock snoozes or stops it.
        assertEquals(seven + 10 * min, due(seven + 24 * hour, "WAKE", seven, now = seven + min))
        // The early wake-up rings over the wake time: the wake-up comes after its ring; well before it, unchanged.
        assertEquals(seven + 5 * min, due(seven, "WBTB", seven - 5 * min, now = seven - min))
        assertEquals(seven, due(seven, "WBTB", seven - 3 * hour, now = seven - 3 * hour))
    }

    @Test
    fun dueAt_isNoneOnceTheMorningIsOverOrWithTheWakeUpOff() {
        assertNull(due(seven + 24 * hour, done = true, now = seven + min)) // answered: never tomorrow's
        assertNull(due(null, now = night))
    }

    @Test
    fun dueAt_aTimeAlreadyPastIsATryAMinuteOn() {
        // The clock is about to ring (or a restored one rings now): never an alarm in the past, which fires at once.
        assertEquals(seven + 2000 + RETRY_MS, due(seven, now = seven + 2000))
    }

    @Test
    fun onFire_ringsOnlyTheRecordsOwnAlarmWithTheWakeUpOn() {
        assertEquals(Fire.RING, WakeBackup.onFire(rec, seven, on, seven))
        // Ended the night, answered, or turned off: no record, never a ring.
        assertEquals(Fire.NONE, WakeBackup.onFire(null, seven, on, seven))
        // Replaced while the alarm was already on its way.
        assertEquals(Fire.STALE, WakeBackup.onFire(rec, seven - min, on, seven))
        assertEquals(Fire.WAKE_OFF, WakeBackup.onFire(rec, seven, on.copy(wakeEnabled = false), seven))
        // Before the first unlock the settings cannot be read: the record alone decides.
        assertEquals(Fire.RING, WakeBackup.onFire(rec, seven, null, seven))
    }

    @Test
    fun onFire_doesNotRingAnHourLateOrAnotherNight() {
        assertEquals(Fire.RING, WakeBackup.onFire(rec, seven, on, seven + 59 * min))
        assertEquals(Fire.TOO_LATE, WakeBackup.onFire(rec, seven, on, seven + 61 * min))
        val tomorrow = rec.copy(dueAt = seven + 24 * hour)
        assertEquals(Fire.TOO_LATE, WakeBackup.onFire(tomorrow, tomorrow.dueAt, on, tomorrow.dueAt))
    }

    @Test
    fun onFire_givesUpAfterTwoTriesNoClockAnswered() {
        assertEquals(Fire.RING, WakeBackup.onFire(rec.copy(unanswered = 1), seven, on, seven))
        assertEquals(Fire.GIVE_UP, WakeBackup.onFire(rec.copy(unanswered = 2), seven, on, seven))
    }

    @Test
    fun retry_armsTheNextTryBeforeRinging() {
        val t = WakeBackup.retry(rec, seven)
        assertEquals(seven + RETRY_MS, t.dueAt)
        assertEquals(1, t.unanswered)
        // That try fires with no clock answering: one more try, then the last resort.
        assertEquals(Fire.RING, WakeBackup.onFire(t, t.dueAt, on, t.dueAt))
        val t2 = WakeBackup.retry(t, t.dueAt)
        assertEquals(Fire.GIVE_UP, WakeBackup.onFire(t2, t2.dueAt, on, t2.dueAt))
    }

    @Test
    fun keep_afterARestart() {
        assertTrue(WakeBackup.keep(rec, night + hour)) // still ahead
        assertTrue(WakeBackup.keep(rec, seven + 3 * min)) // missed during a short restart: rung at once
        assertFalse(WakeBackup.keep(rec, seven + 3 * hour)) // the battery died: never rung at 10 AM
    }

    @Test
    fun reconcile_followsThePhonesAlarmChanges() {
        val utc = ZoneOffset.UTC
        assertNull(WakeBackup.reconcile(rec, on.copy(wakeEnabled = false), night + hour, utc))
        // Never arms a backup by itself: only a started night does.
        assertNull(WakeBackup.reconcile(null, on, night + hour, utc))
        // A tone or ramp change keeps it as it is.
        assertSame(rec, WakeBackup.reconcile(rec, on.copy(wakeTone = AlarmTone.VIBRATE_ONLY, rampS = 0), night, utc))
        // A new wake time moves it and drops a pending snooze, as the live clock does.
        val snoozed = rec.copy(dueAt = seven + 9 * min, snoozes = 1, tappedSnoozes = 1, firstRingAt = seven)
        val moved = WakeBackup.reconcile(snoozed, on.copy(wakeAtMin = 8 * 60), seven + 2 * min, utc)
        assertEquals(rec.copy(dueAt = seven + hour, wakeAtMin = 8 * 60), moved)
        // Moved to a time already gone today: tomorrow's is no longer that night's.
        assertNull(WakeBackup.reconcile(snoozed, on.copy(wakeAtMin = 6 * 60 + 30), seven + 2 * min, utc))
    }

    @Test
    fun record_survivesARoundTripAndOlderTextAndCorruptTextReadsAsNone() {
        val full = rec.copy(snoozes = 2, tappedSnoozes = 1, firstRingAt = seven, unanswered = 1)
        assertEquals(full, WakeBackup.decode(WakeBackup.encode(full)))
        // The first stored form: every later field needs a default, so a record saved before an update still reads.
        val v1 = """{"dueAt":$seven,"wakeAtMin":420,"nightStartMs":$night,"dir":"/s/n1"}"""
        assertEquals(rec, WakeBackup.decode(v1))
        assertNull(WakeBackup.decode("{not json"))
        assertNull(WakeBackup.decode(null))
    }
}
