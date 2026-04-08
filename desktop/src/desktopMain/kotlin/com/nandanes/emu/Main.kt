package com.nandanes.emu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nandanes.emu.runtime.NativeBridge
import com.nandanes.emu.ui.DesktopVideoSurface
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val bridge = remember { NativeBridge() }
    var isRomLoaded by remember { mutableStateOf(false) }
    var romTitle by remember { mutableStateOf("NandaSNES") }

    Window(
        onCloseRequest = ::exitApplication,
        title = romTitle,
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF5D4FA3),
                background = Color(0xFF0A0A0A),
                surface = Color(0xFF13161C)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (!isRomLoaded) {
                    // Pantalla de Bienvenida / Selector
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("NS", style = MaterialTheme.typography.displayLarge, color = Color(0xFF5D4FA3))
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = {
                            val file = pickFile("Seleccionar ROM de SNES", listOf(".sfc", ".smc", ".fig"))
                            if (file != null) {
                                val success = bridge.loadRom(file.absolutePath)
                                if (success) {
                                    bridge.startEmulation()
                                    romTitle = "NandaSNES - ${file.nameWithoutExtension}"
                                    isRomLoaded = true
                                }
                            }
                        }) {
                            Text("Abrir ROM")
                        }
                    }
                } else {
                    // Pantalla de Emulación
                    Box(modifier = Modifier.fillMaxSize()) {
                        DesktopVideoSurface(bridge, modifier = Modifier.fillMaxSize())
                        
                        // Pequeño indicador de controles en la esquina
                        Text(
                            "Pulsa ESC para salir",
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun pickFile(title: String, extensions: List<String>): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> 
        extensions.any { name.lowercase().endsWith(it) } 
    }
    dialog.isVisible = true
    return if (dialog.file != null) {
        File(dialog.directory, dialog.file)
    } else null
}
