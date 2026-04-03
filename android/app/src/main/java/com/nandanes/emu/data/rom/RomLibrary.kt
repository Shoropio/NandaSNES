package com.nandanes.emu.data.rom

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

data class CompatibleRom(
    val label: String,
    val path: String,
    val lastModified: Long
)

data class ImportedRom(
    val file: File,
    val displayLabel: String,
    val romId: String
)

class RomLibraryRepository(
    context: Context,
    private val recentRomStore: RecentRomStore
) {
    private val importsDir = File(context.filesDir, "rom_imports").also { it.mkdirs() }

    fun importRom(contentResolver: ContentResolver, uri: Uri): ImportedRom {
        val displayLabel = queryDisplayName(contentResolver, uri)?.trim().orEmpty().ifBlank { DEFAULT_ROM_NAME }
        require(isSupportedRomName(displayLabel)) {
            "Formato no soportado. Usa .sfc, .smc o .fig"
        }

        val romId = romIdFromUri(uri)
        val extension = normalizedExtension(displayLabel)
        val destination = File(importsDir, "$romId.$extension")

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir el archivo")

        if (!destination.exists() || destination.length() <= 0L) {
            destination.delete()
            error("El archivo importado esta vacio o corrupto")
        }

        return ImportedRom(
            file = destination,
            displayLabel = displayLabel,
            romId = romId
        )
    }

    fun listCompatibleRoms(): List<CompatibleRom> {
        val knownLabels = recentRomStore.list().associate { it.path to it.label }
        return importsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && isSupportedRomName(it.name) }
            .map { file ->
                CompatibleRom(
                    label = knownLabels[file.absolutePath] ?: file.nameWithoutExtension,
                    path = file.absolutePath,
                    lastModified = file.lastModified()
                )
            }
            .sortedByDescending { it.lastModified }
            .toList()
    }

    companion object {
        private const val DEFAULT_ROM_NAME = "rom.sfc"

        fun isSupportedRomName(name: String): Boolean {
            val lower = name.lowercase(Locale.US)
            return lower.endsWith(".sfc") || lower.endsWith(".smc") || lower.endsWith(".fig")
        }

        fun romIdFromPath(file: File): String {
            val raw = "${file.absolutePath}_${file.length()}_${file.lastModified()}"
            val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun romIdFromUri(uri: Uri): String {
            val digest = MessageDigest.getInstance("MD5").digest(uri.toString().toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun normalizedExtension(name: String): String =
            name.substringAfterLast('.', "sfc").lowercase(Locale.US)

        private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
            val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
            cursor.use {
                if (!it.moveToFirst()) return null
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) return null
                return it.getString(index)
            }
        }
    }
}
