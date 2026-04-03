package com.nandanes.emu.data.rom

import com.nandanes.emu.data.settings.SettingsStoreNames
import com.nandanes.emu.data.settings.settingsDataStore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class RecentRom(
    val romId: String,
    val label: String,
    val path: String,
    val lastPlayedAt: Long
)

internal object RecentRomCodec {
    fun decode(raw: String): List<RecentRom> {
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 4) return@mapNotNull null
                val lastPlayedAt = parts[3].toLongOrNull() ?: return@mapNotNull null
                RecentRom(
                    romId = decodeValue(parts[0]),
                    label = decodeValue(parts[1]),
                    path = decodeValue(parts[2]),
                    lastPlayedAt = lastPlayedAt
                )
            }
            .filter { it.path.isNotBlank() }
            .sortedByDescending { it.lastPlayedAt }
            .toList()
    }

    fun encode(recents: List<RecentRom>): String =
        recents.joinToString(separator = "\n") { recent ->
            listOf(
                encodeValue(recent.romId),
                encodeValue(recent.label),
                encodeValue(recent.path),
                recent.lastPlayedAt.toString()
            ).joinToString(separator = "\t")
        }

    private fun encodeValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decodeValue(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

class RecentRomStore(context: Context) {
    private val dataStore = settingsDataStore(context, SettingsStoreNames.RECENT_ROMS)

    fun list(): List<RecentRom> {
        val raw = runBlocking { dataStore.data.first() }[KEY_RECENTS].orEmpty()
        return RecentRomCodec.decode(raw)
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

        saveEncoded(RecentRomCodec.encode(updated))
    }

    fun pruneMissingFiles() {
        val filtered = list().filter { java.io.File(it.path).exists() }
        saveEncoded(RecentRomCodec.encode(filtered))
    }

    private fun saveEncoded(encoded: String) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_RECENTS] = encoded
            }
        }
    }

    private companion object {
        private val KEY_RECENTS = stringPreferencesKey("recent_roms")
        private const val MAX_RECENTS = 8
    }
}
