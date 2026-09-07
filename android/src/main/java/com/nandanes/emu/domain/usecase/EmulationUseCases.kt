package com.nandanes.emu.domain.usecase

import com.nandanes.emu.data.rom.RomLibraryRepository
import java.io.File

interface EmulationRuntime {
    fun loadRom(path: String): Boolean
    fun loadState(path: String): Boolean
}

interface SaveStateRuntime {
    fun setActiveRomId(romId: String)
    fun saveManualState(slot: Int): Boolean
    fun writeManualThumbnail(slot: Int): Boolean
    fun manualSlotPath(slot: Int): File
    fun autoSlotPath(): File
}

data class LoadRomResult(
    val success: Boolean,
    val romId: String? = null
)

class LoadRomUseCase(
    private val runtime: EmulationRuntime,
    private val saveStateRuntime: SaveStateRuntime
) {
    operator fun invoke(file: File): LoadRomResult {
        if (!file.exists() || !RomLibraryRepository.isSupportedRomName(file.name)) {
            return LoadRomResult(success = false)
        }

        val romId = RomLibraryRepository.romIdFromPath(file)
        saveStateRuntime.setActiveRomId(romId)
        val loaded = runtime.loadRom(file.absolutePath)
        return if (loaded) {
            LoadRomResult(success = true, romId = romId)
        } else {
            LoadRomResult(success = false)
        }
    }
}

class SaveStateUseCase(
    private val saveStateRuntime: SaveStateRuntime
) {
    operator fun invoke(romId: String, slot: Int): Boolean {
        saveStateRuntime.setActiveRomId(romId)
        return saveStateRuntime.saveManualState(slot)
    }

    fun writeThumbnail(romId: String, slot: Int): Boolean {
        saveStateRuntime.setActiveRomId(romId)
        return saveStateRuntime.writeManualThumbnail(slot)
    }
}

class LoadStateUseCase(
    private val runtime: EmulationRuntime,
    private val saveStateRuntime: SaveStateRuntime
) {
    fun manualSlot(romId: String, slot: Int): Boolean {
        saveStateRuntime.setActiveRomId(romId)
        return runtime.loadState(saveStateRuntime.manualSlotPath(slot).absolutePath)
    }

    fun autoSave(romId: String): Boolean {
        saveStateRuntime.setActiveRomId(romId)
        return runtime.loadState(saveStateRuntime.autoSlotPath().absolutePath)
    }
}
