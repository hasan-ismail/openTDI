package com.opentdi.mobile.ui.stats

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentdi.mobile.TAG_LINK
import com.opentdi.mobile.stats.NightRow
import com.opentdi.mobile.stats.NightsRepository
import com.opentdi.mobile.stats.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Whether a copy from the watch is running, and how the last one ended. */
data class Sync(val copying: Boolean = false, val result: SyncResult? = null)

/** The Stats tab's nights, kept while other tabs show; each return to the tab copies new nights from the watch. */
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val mutableRows = MutableStateFlow<List<NightRow>?>(null) // null until first read
    val rows: StateFlow<List<NightRow>?> = mutableRows.asStateFlow()
    private val mutableSync = MutableStateFlow(Sync())
    val sync: StateFlow<Sync> = mutableSync.asStateFlow()

    /** The range filter, kept across tab switches. */
    var range by mutableStateOf(StatsRange.MONTH)

    /** Shows the nights already on the phone, copies new ones from the watch in the background, then shows them all. */
    fun open() {
        if (mutableSync.value.copying) return
        mutableSync.update { it.copy(copying = true) }
        viewModelScope.launch {
            load()
            val result = try {
                withContext(Dispatchers.IO) { NightsRepository.syncFromWatch(app) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // a failed copy must not close the app; the status line says why
                Log.w(TAG_LINK, "Stats copy from the watch failed", e)
                SyncResult(0, 0, "Something went wrong copying from the watch. Try again.")
            }
            mutableSync.value = Sync(result = result)
            load()
        }
    }

    private suspend fun load() {
        try {
            mutableRows.value = withContext(Dispatchers.IO) { NightsRepository.rows(app) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // an unreadable night must not close the app
            Log.w(TAG_LINK, "Stats nights unreadable", e)
            if (mutableRows.value == null) mutableRows.value = emptyList()
        }
    }
}
