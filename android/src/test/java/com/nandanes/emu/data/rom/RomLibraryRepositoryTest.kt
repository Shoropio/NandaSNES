package com.nandanes.emu.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomLibraryRepositoryTest {
    @Test
    fun `supported rom names accept expected extensions`() {
        assertTrue(RomLibraryRepository.isSupportedRomName("chrono.sfc"))
        assertTrue(RomLibraryRepository.isSupportedRomName("chrono.SMC"))
        assertTrue(RomLibraryRepository.isSupportedRomName("chrono.fig"))
    }

    @Test
    fun `supported rom names reject unexpected extensions`() {
        assertFalse(RomLibraryRepository.isSupportedRomName("chrono.zip"))
        assertFalse(RomLibraryRepository.isSupportedRomName("chrono"))
        assertFalse(RomLibraryRepository.isSupportedRomName("chrono.sfc.bak"))
    }

    @Test
    fun `rom id from path is stable for same file metadata`() {
        val file = createTempFile(suffix = ".sfc").apply {
            writeText("rom")
            setLastModified(1_700_000_000_000L)
        }

        val first = RomLibraryRepository.romIdFromPath(file)
        val second = RomLibraryRepository.romIdFromPath(file)

        assertEquals(first, second)
        file.delete()
    }

    @Test
    fun `rom id from path changes when file metadata changes`() {
        val first = createTempFile(prefix = "romA", suffix = ".sfc").apply {
            writeText("rom-a")
            setLastModified(1_700_000_000_000L)
        }
        val second = createTempFile(prefix = "romB", suffix = ".sfc").apply {
            writeText("rom-b")
            setLastModified(1_700_000_100_000L)
        }

        val firstId = RomLibraryRepository.romIdFromPath(first)
        val secondId = RomLibraryRepository.romIdFromPath(second)

        assertNotEquals(firstId, secondId)
        first.delete()
        second.delete()
    }
}
