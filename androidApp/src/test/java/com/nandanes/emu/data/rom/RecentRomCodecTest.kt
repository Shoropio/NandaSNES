package com.nandanes.emu.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentRomCodecTest {
    @Test
    fun `decode ignores malformed rows and sorts by last played desc`() {
        val raw = buildString {
            appendLine("rom-1\tSuper%20Metroid\tC%3A%2Froms%2Fsm.sfc\t100")
            appendLine("broken")
            appendLine("rom-2\tChrono%20Trigger\tC%3A%2Froms%2Fct.smc\t200")
            appendLine("rom-3\tBlank\t\t300")
        }

        val decoded = RecentRomCodec.decode(raw)

        assertEquals(2, decoded.size)
        assertEquals("rom-2", decoded[0].romId)
        assertEquals("Chrono Trigger", decoded[0].label)
        assertEquals("rom-1", decoded[1].romId)
    }

    @Test
    fun `encode and decode preserve rom metadata`() {
        val recents = listOf(
            RecentRom("rom-1", "Super Metroid", "C:/roms/sm.sfc", 100L),
            RecentRom("rom-2", "Chrono Trigger", "C:/roms/ct.smc", 200L)
        )

        val encoded = RecentRomCodec.encode(recents)
        val decoded = RecentRomCodec.decode(encoded)

        assertEquals(
            listOf(
                RecentRom("rom-2", "Chrono Trigger", "C:/roms/ct.smc", 200L),
                RecentRom("rom-1", "Super Metroid", "C:/roms/sm.sfc", 100L)
            ),
            decoded
        )
        assertTrue(encoded.contains("%3A%2Froms%2F"))
    }
}
