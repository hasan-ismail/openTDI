package com.opentdi.shared.time

import java.util.Locale

/**
 * A time of day the 12-hour way, on both apps whatever the device setting (owner, 2026-10-28: "all time in app is
 * 12hour"): "11 PM" on the hour, else "4:30 AM"; midnight is "12 AM". [minuteOfDay] wraps, so 1500 is 1 AM. The
 * space is a no-break one ([NBSP]), so a line of text never ends between the time and its AM or PM.
 */
fun clock12(minuteOfDay: Int): String {
    val m = Math.floorMod(minuteOfDay, 24 * 60)
    val hour = (m / 60 + 11) % 12 + 1
    val half = if (m < 12 * 60) "AM" else "PM"
    return if (m % 60 == 0) "$hour$NBSP$half" else String.format(Locale.ROOT, "%d:%02d$NBSP%s", hour, m % 60, half)
}

/** A no-break space: between a number and its unit, so large text never wraps a value in two. */
const val NBSP = '\u00A0'
