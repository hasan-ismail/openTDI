package com.opentdi.shared.stats

import java.io.Reader
import java.time.ZoneId

/**
 * Summarises one watch session folder, read through [open] by CSV file name (null when the file is missing), for the
 * night that started in [zone]. Null when there is too little to summarise.
 *
 * shortcut: a stub until the sleep estimate of docs/references/sleep_stats.md is implemented; callers already handle
 * null.
 */
@Suppress("UNUSED_PARAMETER")
fun summarizeNight(sessionId: String, zone: ZoneId, open: (fileName: String) -> Reader?): NightSummary? = null
