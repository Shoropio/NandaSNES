package com.nandanes.emu

import android.content.Context
import android.net.Uri

data class RecentRom(
    val romId: String,
    val label: String,
    val path: String,
    val lastPlayedAt: Long
)

class RecentRomStore(context: Context) {
    private val prefs = context.getSharedPreferences("nandanes_recent_roms", Context.MODE_PRIVATE)

    fun list(): List<RecentRom> {
        val raw = prefs.getString(KEY_RECENTS, null).orEmpty()
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

        prefs.edit().putString(KEY_RECENTS, encode(updated)).apply()
    }

    fun pruneMissingFiles() {
        val filtered = list().filter { java.io.File(it.path).exists() }
        prefs.edit().putString(KEY_RECENTS, encode(filtered)).apply()
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
        private const val KEY_RECENTS = "recent_roms"
        private const val MAX_RECENTS = 8
    }
}
