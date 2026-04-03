package com.nandanes.emu.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmulationUseCasesTest {
    @Test
    fun `load rom sets active rom and returns generated id on success`() {
        val runtime = FakeEmulationRuntime(loadRomResult = true)
        val saveStateRuntime = FakeSaveStateRuntime()
        val romFile = createTempFile(suffix = ".sfc").apply { writeText("rom") }

        val result = LoadRomUseCase(runtime, saveStateRuntime)(romFile)

        assertTrue(result.success)
        assertEquals(romFile.absolutePath, runtime.lastLoadedRomPath)
        assertEquals(result.romId, saveStateRuntime.recordedRomId)
        romFile.delete()
    }

    @Test
    fun `load rom rejects unsupported extension`() {
        val runtime = FakeEmulationRuntime(loadRomResult = true)
        val saveStateRuntime = FakeSaveStateRuntime()
        val romFile = createTempFile(suffix = ".zip").apply { writeText("zip") }

        val result = LoadRomUseCase(runtime, saveStateRuntime)(romFile)

        assertFalse(result.success)
        assertNull(result.romId)
        assertNull(runtime.lastLoadedRomPath)
        romFile.delete()
    }

    @Test
    fun `save state use case delegates state save and thumbnail write`() {
        val saveStateRuntime = FakeSaveStateRuntime(
            saveManualStateResult = true,
            writeThumbnailResult = true
        )
        val useCase = SaveStateUseCase(saveStateRuntime)

        assertTrue(useCase("rom-123", 2))
        assertTrue(useCase.writeThumbnail("rom-123", 2))
        assertEquals("rom-123", saveStateRuntime.recordedRomId)
        assertEquals(2, saveStateRuntime.lastSavedSlot)
        assertEquals(2, saveStateRuntime.lastThumbnailSlot)
    }

    @Test
    fun `load state use case resolves manual and autosave paths`() {
        val runtime = FakeEmulationRuntime(loadStateResult = true)
        val saveStateRuntime = FakeSaveStateRuntime()
        val useCase = LoadStateUseCase(runtime, saveStateRuntime)

        assertTrue(useCase.manualSlot("rom-123", 3))
        assertEquals(saveStateRuntime.manualSlotPath(3).absolutePath, runtime.lastLoadedStatePath)

        assertTrue(useCase.autoSave("rom-123"))
        assertEquals(saveStateRuntime.autoSlotPath().absolutePath, runtime.lastLoadedStatePath)
    }

    private class FakeEmulationRuntime(
        private val loadRomResult: Boolean = false,
        private val loadStateResult: Boolean = false
    ) : EmulationRuntime {
        var lastLoadedRomPath: String? = null
        var lastLoadedStatePath: String? = null

        override fun loadRom(path: String): Boolean {
            lastLoadedRomPath = path
            return loadRomResult
        }

        override fun loadState(path: String): Boolean {
            lastLoadedStatePath = path
            return loadStateResult
        }
    }

    private class FakeSaveStateRuntime(
        private val saveManualStateResult: Boolean = false,
        private val writeThumbnailResult: Boolean = false
    ) : SaveStateRuntime {
        var recordedRomId: String? = null
        var lastSavedSlot: Int? = null
        var lastThumbnailSlot: Int? = null

        override fun setActiveRomId(romId: String) {
            recordedRomId = romId
        }

        override fun saveManualState(slot: Int): Boolean {
            lastSavedSlot = slot
            return saveManualStateResult
        }

        override fun writeManualThumbnail(slot: Int): Boolean {
            lastThumbnailSlot = slot
            return writeThumbnailResult
        }

        override fun manualSlotPath(slot: Int): File = File("manual-$slot.frz")

        override fun autoSlotPath(): File = File("autosave.frz")
    }
}
