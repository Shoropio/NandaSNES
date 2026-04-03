package com.nandanes.emu.data.save

import com.nandanes.emu.data.settings.SettingsStoreNames
import com.nandanes.emu.data.settings.settingsDataStore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object SaveSlotMetadata {
    private fun dataStore(context: Context) =
        settingsDataStore(context, SettingsStoreNames.SAVE_SLOTS)

    private fun slotKey(romId: String, slot: Int) = "${romId}_slot_$slot"
    private fun autoResumeKey(romId: String) = "${romId}_auto_resume_enabled"

    fun getSlotSavedAtMillis(context: Context, romId: String, slot: Int): Long =
        read(context, longPreferencesKey(slotKey(romId, slot))) ?: 0L

    fun setSlotSavedAtMillis(context: Context, romId: String, slot: Int, timeMillis: Long) {
        write(context, longPreferencesKey(slotKey(romId, slot)), timeMillis)
    }

    fun getAutoSaveAtMillis(context: Context, romId: String): Long =
        read(context, longPreferencesKey("${romId}_auto_time")) ?: 0L

    fun setAutoSaveAtMillis(context: Context, romId: String, timeMillis: Long) {
        write(context, longPreferencesKey("${romId}_auto_time"), timeMillis)
    }

    fun getAutoSaveReason(context: Context, romId: String): String? =
        read(context, stringPreferencesKey("${romId}_auto_reason"))

    fun setAutoSaveReason(context: Context, romId: String, reason: String?) {
        val key = stringPreferencesKey("${romId}_auto_reason")
        runBlocking {
            dataStore(context).edit { prefs ->
                if (reason == null) {
                    prefs.remove(key)
                } else {
                    prefs[key] = reason
                }
            }
        }
    }

    fun getAutoResumeEnabled(context: Context, romId: String): Boolean =
        read(context, booleanPreferencesKey(autoResumeKey(romId))) ?: true

    fun setAutoResumeEnabled(context: Context, romId: String, enabled: Boolean) {
        write(context, booleanPreferencesKey(autoResumeKey(romId)), enabled)
    }

    private fun <T> read(context: Context, key: Preferences.Key<T>): T? {
        return runBlocking {
            dataStore(context).data.first()[key]
        }
    }

    private fun <T> write(context: Context, key: Preferences.Key<T>, value: T) {
        runBlocking {
            dataStore(context).edit { prefs ->
                prefs[key] = value
            }
        }
    }
}
