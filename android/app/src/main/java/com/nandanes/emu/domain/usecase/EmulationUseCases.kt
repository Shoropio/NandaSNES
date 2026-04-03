package com.nandanes.emu.domain.usecase

import com.nandanes.emu.data.rom.RomLibraryRepository
import com.nandanes.emu.runtime.NativeBridge
import com.nandanes.emu.runtime.SaveStateManager
import java.io.File

data class LoadRomResult(
    val success: Boolean,
    val romId: String? = null
)

class LoadRomUseCase(
    private val bridge: NativeBridge,
    private val saveStateManager: SaveStateManager
) {
    operator fun invoke(file: File): LoadRomResult {
        if (!file.exists() || !RomLibraryRepository.isSupportedRomName(file.name)) {
            return LoadRomResult(success = false)
        }

        val romId = RomLibraryRepository.romIdFromPath(file)
        saveStateManager.setActiveRomId(romId)
        val loaded = bridge.loadRom(file.absolutePath)
        return if (loaded) {
            LoadRomResult(success = true, romId = romId)
        } else {
            LoadRomResult(success = false)
        }
    }
}

class SaveStateUseCase(
    private val bridge: NativeBridge,
    private val saveStateManager: SaveStateManager
) {
    operator fun invoke(romId: String, slot: Int): Boolean {
        saveStateManager.setActiveRomId(romId)
        return saveStateManager.saveStateAtomically(
            bridge,
            saveStateManager.manualSlotPath(slot)
        )
    }

    fun writeThumbnail(romId: String, slot: Int): Boolean {
        saveStateManager.setActiveRomId(romId)
        return saveStateManager.writeCurrentFrameThumbnail(
            bridge,
            saveStateManager.manualThumbPath(slot)
        )
    }
}

class LoadStateUseCase(
    private val bridge: NativeBridge,
    private val saveStateManager: SaveStateManager
) {
    fun manualSlot(romId: String, slot: Int): Boolean {
        saveStateManager.setActiveRomId(romId)
        return bridge.loadState(saveStateManager.manualSlotPath(slot).absolutePath)
    }

    fun autoSave(romId: String): Boolean {
        saveStateManager.setActiveRomId(romId)
        return bridge.loadState(saveStateManager.autoSlotPath().absolutePath)
    }
}
