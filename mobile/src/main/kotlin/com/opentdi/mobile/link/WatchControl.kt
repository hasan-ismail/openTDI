package com.opentdi.mobile.link

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.opentdi.mobile.loadConfig
import com.opentdi.shared.model.DataLayerPaths
import com.opentdi.shared.model.OpenTdiJson
import com.opentdi.shared.model.TdiCommand
import com.opentdi.shared.model.WatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends [command] to the watch on `/opentdi/state_command` and returns its [WatchStatus]; null when no watch answers
 * within 5 s (not connected, or an app version without the command service).
 */
suspend fun watchRequest(context: Context, command: TdiCommand): WatchStatus? = withTimeoutOrNull(5_000) {
    try {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return@withTimeoutOrNull null
        val request = OpenTdiJson.encodeToString(TdiCommand.serializer(), command).encodeToByteArray()
        val reply = Wearable.getMessageClient(context).sendRequest(node.id, DataLayerPaths.STATE_COMMAND, request).await()
        OpenTdiJson.decodeFromString(WatchStatus.serializer(), reply.decodeToString())
    } catch (e: ApiException) {
        null
    } catch (e: IllegalArgumentException) { // SerializationException: another app version
        null
    }
}

/**
 * A watch with OpenTDI is known to this phone, in reach or not; false without Wear OS. True when the check itself does
 * not answer, so a stuck check never hides a change the watch is missing.
 */
suspend fun watchKnown(context: Context): Boolean = withTimeoutOrNull(5_000) {
    try {
        Wearable.getCapabilityClient(context)
            .getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_ALL).await().nodes.isNotEmpty()
    } catch (e: ApiException) {
        false
    }
} ?: true

private const val SYNC_PREFS = "watch_sync"
private const val KEY_PENDING = "pending_revision"

private fun syncPrefs(context: Context) = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

/** The config revision of the last alarm or watch-output change the watch hasn't answered; 0 when none. */
fun pendingWatchSync(context: Context): Long = syncPrefs(context).getLong(KEY_PENDING, 0)

/**
 * Kept on disk, and sent by [WatchSyncJob] too: the screen's resend loop ends with the app, and a missed change rings
 * at the old time.
 */
fun markWatchSyncPending(context: Context, revision: Long) {
    syncPrefs(context).edit().putLong(KEY_PENDING, revision).apply()
    val scheduler = context.getSystemService(JobScheduler::class.java)
    val job = JobInfo.Builder(SYNC_JOB_ID, ComponentName(context, WatchSyncJob::class.java))
        .setBackoffCriteria(SYNC_RETRY_MS, JobInfo.BACKOFF_POLICY_LINEAR)
    // Expedited: it runs now, even in Doze. Out of that quota, an ordinary job still runs, later.
    if (scheduler.schedule(job.setExpedited(true).build()) != JobScheduler.RESULT_SUCCESS) {
        scheduler.schedule(job.setExpedited(false).build())
    }
}

/** The watch answered a command carrying the config of [revision], so every change up to it has arrived. */
fun watchSyncAnswered(context: Context, revision: Long) {
    val prefs = syncPrefs(context)
    if (prefs.getLong(KEY_PENDING, 0) <= revision) {
        prefs.edit().remove(KEY_PENDING).apply()
        context.getSystemService(JobScheduler::class.java).cancel(SYNC_JOB_ID)
    }
}

private const val SYNC_JOB_ID = 1 // the app's only JobScheduler job
private const val SYNC_RETRY_MS = 30_000L

/**
 * Sends the change the watch hasn't answered, with the app closed too; the system runs it again with a backoff until
 * the watch has it. Not DataClient: its items are kept and backed up to the cloud. [WatchSyncListener] stays the fast
 * path when the watch comes back in reach.
 */
class WatchSyncJob : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.IO).launch {
            val config = loadConfig(this@WatchSyncJob) // the newest change, not the one that scheduled this
            val answered = watchRequest(this@WatchSyncJob, TdiCommand.ConfigSync(null, config))?.understood == true
            if (answered) watchSyncAnswered(this@WatchSyncJob, config.revision)
            // Again until the newest change is in; with no watch at all (a phone-only night) there is none to send to.
            val again = pendingWatchSync(this@WatchSyncJob) != 0L && (answered || watchKnown(this@WatchSyncJob))
            jobFinished(params, again)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }
}

/**
 * Sends a change the watch hasn't answered as soon as the watch comes back in reach, even with the app closed or
 * killed, so an alarm moved or turned off at night never rings the old way.
 */
class WatchSyncListener : WearableListenerService() {
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.nodes.isEmpty() || pendingWatchSync(this) == 0L) return
        val config = loadConfig(this)
        // Called on a background thread; blocking keeps the service alive until the watch answered (5 s at most).
        if (runBlocking { watchRequest(this@WatchSyncListener, TdiCommand.ConfigSync(null, config)) }?.understood == true) {
            watchSyncAnswered(this, config.revision)
        }
    }
}
