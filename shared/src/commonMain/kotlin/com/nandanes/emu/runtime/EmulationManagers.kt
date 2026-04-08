package com.nandanes.emu.runtime

import com.nandanes.emu.domain.usecase.EmulationRuntime
import com.nandanes.emu.domain.usecase.SaveStateRuntime
import java.io.File
import java.io.IOException

class NativeEmulationRuntime(
    private val bridge: NativeBridge
) : EmulationRuntime {
    override fun loadRom(path: String): Boolean = bridge.loadRom(path)
    override fun loadState(path: String): Boolean = bridge.loadState(path)
}

class SaveStateManager(private val baseDir: File) {
    private var activeRomId: String = "default"

    fun setActiveRomId(romId: String) {
        activeRomId = romId.ifBlank { "default" }
        savesRoot().mkdirs()
        thumbsRoot().mkdirs()
    }

    fun manualSlotPath(slot: Int): File {
        return File(savesRoot(), "slot_$slot.frz")
    }

    fun manualThumbPath(slot: Int): File {
        return File(thumbsRoot(), "slot_$slot.png")
    }

    fun autoSlotPath(): File = File(savesRoot(), "autosave.frz")
    fun autoThumbPath(): File = File(thumbsRoot(), "autosave.png")

    fun saveStateAtomically(bridge: NativeBridge, target: File): Boolean {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.delete()
        val saved = bridge.saveState(temp.absolutePath)
        if (!saved) {
            temp.delete()
            return false
        }
        return replaceAtomically(temp, target)
    }

    private fun savesRoot(): File = File(baseDir, "saves/$activeRomId")
    private fun thumbsRoot(): File = File(baseDir, "thumbs/$activeRomId")

    private fun replaceAtomically(temp: File, target: File): Boolean {
        return try {
            if (target.exists() && !target.delete()) {
                // Fallback copy
                temp.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                temp.delete()
                true
            } else {
                temp.renameTo(target)
            }
        } catch (_: IOException) {
            temp.delete()
            false
        }
    }
}

class NativeSaveStateRuntime(
    private val bridge: NativeBridge,
    private val saveStateManager: SaveStateManager
) : SaveStateRuntime {
    override fun setActiveRomId(romId: String) {
        saveStateManager.setActiveRomId(romId)
    }

    override fun saveManualState(slot: Int): Boolean =
        saveStateManager.saveStateAtomically(bridge, saveStateManager.manualSlotPath(slot))

    override fun writeManualThumbnail(slot: Int): Boolean {
        // La escritura de miniaturas será específica de plataforma 
        // debido a las diferencias entre Bitmap y ImageInputStream
        return false 
    }

    override fun manualSlotPath(slot: Int): File = saveStateManager.manualSlotPath(slot)
    override fun autoSlotPath(): File = saveStateManager.autoSlotPath()
}
