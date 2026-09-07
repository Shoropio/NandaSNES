package com.nandanes.emu.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap

private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val settingsStores = ConcurrentHashMap<String, DataStore<Preferences>>()

internal fun settingsDataStore(context: Context, name: String) =
    settingsStores.computeIfAbsent(name) {
        val appContext = context.applicationContext
        PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { appContext.preferencesDataStoreFile(name) }
        )
    }

internal object SettingsStoreNames {
    const val CONTROL_OVERLAY = "nandanes_controls"
    const val DEBUG = "nandanes_debug"
    const val RECENT_ROMS = "nandanes_recent_roms"
    const val SAVE_SLOTS = "nandanes_slots"
}
