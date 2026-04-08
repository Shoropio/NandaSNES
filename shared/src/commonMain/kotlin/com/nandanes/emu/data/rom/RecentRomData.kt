package com.nandanes.emu.data.rom

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
                
                // Nota: Usaré un decodificador simple ya que URLEncoder es de Java,
                // para commonMain seremos más genéricos o usaremos expect/actual si es necesario.
                RecentRom(
                    romId = parts[0],
                    label = parts[1],
                    path = parts[2],
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
                recent.romId,
                recent.label,
                recent.path,
                recent.lastPlayedAt.toString()
            ).joinToString(separator = "\t")
        }
}
