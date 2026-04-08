package com.nandanes.emu.data.rom

import java.io.File

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
