package com.nandanes.emu.data.rom

import com.nandanes.emu.data.settings.SettingsStoreNames
import com.nandanes.emu.data.settings.settingsDataStore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class RecentRom(
    val romId: String,
    val label: String,
    val path: String,
    val lastPlayedAt: Long
)

class RecentRomStore(context: Context) {
    private val dataStore = settingsDataStore(context, SettingsStoreNames.RECENT_ROMS)

    fun list(): List<RecentRom> {
        val raw = runBlocking { dataStore.data.first() }[KEY_RECENTS].orEmpty()
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 4) return@mapNotNull null
                val lastPlayedAt = parts[3].toLongOrNull() ?: return@mapNotNull null
                RecentRom(
                    romId = Uri.decode(parts[0]),
                    label = Uri.decode(parts[1]),
                    path = Uri.decode(parts[2]),
                    lastPlayedAt = lastPlayedAt
                )
            }
            .filter { it.path.isNotBlank() }
            .sortedByDescending { it.lastPlayedAt }
            .toList()
    }

    fun record(romId: String, label: String, path: String) {
        val updated = list()
            .filterNot { it.romId == romId || it.path == path }
            .toMutableList()
            .apply {
                add(
                    0,
                    RecentRom(
                        romId = romId,
                        label = label.ifBlank { path.substringAfterLast('/') },
                        path = path,
                        lastPlayedAt = System.currentTimeMillis()
                    )
                )
            }
            .sortedByDescending { it.lastPlayedAt }
            .take(MAX_RECENTS)

        saveEncoded(encode(updated))
    }

    fun pruneMissingFiles() {
        val filtered = list().filter { java.io.File(it.path).exists() }
        saveEncoded(encode(filtered))
    }

    private fun saveEncoded(encoded: String) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_RECENTS] = encoded
            }
        }
    }

    private fun encode(recents: List<RecentRom>): String =
        recents.joinToString(separator = "\n") { recent ->
            listOf(
                Uri.encode(recent.romId),
                Uri.encode(recent.label),
                Uri.encode(recent.path),
                recent.lastPlayedAt.toString()
            ).joinToString(separator = "\t")
        }

    private companion object {
        private val KEY_RECENTS = stringPreferencesKey("recent_roms")
        private const val MAX_RECENTS = 8
    }
}
