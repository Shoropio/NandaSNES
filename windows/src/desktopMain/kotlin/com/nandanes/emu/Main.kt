package com.nandanes.emu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.input.key.*
import com.nandanes.emu.runtime.NativeBridge
import com.nandanes.emu.runtime.SnesKey
import com.nandanes.emu.ui.DesktopVideoSurface
import com.studiohartman.jamepad.ControllerButton
import com.studiohartman.jamepad.ControllerManager
import kotlinx.coroutines.delay
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.sound.sampled.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() = application {
    val bridge = remember { NativeBridge() }
    val controllerManager = remember { 
        ControllerManager().apply { 
            try {
                initSDLGamepad() 
            } catch (e: Exception) {
                println("Jamepad warning: ${e.message}")
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            controllerManager.quitSDLGamepad()
        }
    }

    var isRomLoaded by remember { mutableStateOf(false) }
    var romTitle by remember { mutableStateOf("NandaSNES") }
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    var isFullscreen by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = romTitle,
        state = windowState,
        onPreviewKeyEvent = { keyEvent ->
            var handled = false
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyEvent.key) {
                    Key.F11 -> {
                        isFullscreen = !isFullscreen
                        windowState.placement = if (isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
                        handled = true
                    }
                    Key.Escape -> {
                        if (isRomLoaded) {
                            bridge.stopEmulation()
                            isRomLoaded = false
                            if (isFullscreen) {
                                isFullscreen = false
                                windowState.placement = WindowPlacement.Floating
                            }
                            handled = true
                        }
                    }
                }
            }
            
            if (!handled && isRomLoaded) {
                handled = handleKeyEvent(keyEvent, bridge)
            }
            handled
        }
    ) {
        LaunchedEffect(isRomLoaded) {
            if (isRomLoaded) {
                val sampleRate = bridge.getAudioSampleRate().toFloat()
                if (sampleRate > 0) {
                    val format = AudioFormat(sampleRate, 16, 2, true, false)
                    val info = DataLine.Info(SourceDataLine::class.java, format)
                    if (AudioSystem.isLineSupported(info)) {
                        val line = AudioSystem.getLine(info) as SourceDataLine
                        line.open(format, 4096 * 4)
                        line.start()
                        
                        try {
                            println("Audio: Line opened at $sampleRate Hz")
                            val maxSamplesPerPull = 2048
                            val byteBuffer = ByteBuffer.allocate(maxSamplesPerPull * 2).order(ByteOrder.LITTLE_ENDIAN)
                            while (isRomLoaded) {
                                val samples = bridge.consumeAudioSamples(maxSamplesPerPull)
                                if (samples != null && samples.isNotEmpty()) {
                                    byteBuffer.clear()
                                    for (s in samples) {
                                        byteBuffer.putShort(s)
                                    }
                                    line.write(byteBuffer.array(), 0, samples.size * 2)
                                } else {
                                    delay(5)
                                }
                            }
                        } catch (e: Exception) {
                            println("Audio error: ${e.message}")
                            e.printStackTrace()
                        } finally {
                            println("Audio: Closing line")
                            line.stop()
                            line.close()
                        }
                    }
                }
            }
        }

        LaunchedEffect(isRomLoaded) {
            if (isRomLoaded) {
                while (true) {
                    controllerManager.update()
                    val controller = controllerManager.getControllerIndex(0)
                    if (controller.isConnected) {
                        bridge.reportButton(SnesKey.UP.nativeCode, controller.isButtonPressed(ControllerButton.DPAD_UP))
                        bridge.reportButton(SnesKey.DOWN.nativeCode, controller.isButtonPressed(ControllerButton.DPAD_DOWN))
                        bridge.reportButton(SnesKey.LEFT.nativeCode, controller.isButtonPressed(ControllerButton.DPAD_LEFT))
                        bridge.reportButton(SnesKey.RIGHT.nativeCode, controller.isButtonPressed(ControllerButton.DPAD_RIGHT))
                        
                        // Mapping standard: A->B, B->A, X->Y, Y->X
                        bridge.reportButton(SnesKey.A.nativeCode, controller.isButtonPressed(ControllerButton.B))
                        bridge.reportButton(SnesKey.B.nativeCode, controller.isButtonPressed(ControllerButton.A))
                        bridge.reportButton(SnesKey.X.nativeCode, controller.isButtonPressed(ControllerButton.Y))
                        bridge.reportButton(SnesKey.Y.nativeCode, controller.isButtonPressed(ControllerButton.X))
                        
                        bridge.reportButton(SnesKey.L.nativeCode, controller.isButtonPressed(ControllerButton.LEFTBUMPER))
                        bridge.reportButton(SnesKey.R.nativeCode, controller.isButtonPressed(ControllerButton.RIGHTBUMPER))
                        bridge.reportButton(SnesKey.START.nativeCode, controller.isButtonPressed(ControllerButton.START))
                        bridge.reportButton(SnesKey.SELECT.nativeCode, controller.isButtonPressed(ControllerButton.BACK))
                    }
                    delay(16)
                }
            }
        }

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
                        
                        // Barra de herramientas flotante (se muestra brevemente o al pasar el mouse)
                        // Por simplicidad, botones sutiles en las esquinas
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { 
                                    isFullscreen = !isFullscreen 
                                    windowState.placement = if (isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit 
                                    else Icons.Default.Fullscreen, 
                                    contentDescription = "Pantalla Completa",
                                    tint = Color.White
                                )
                            }
                            
                            IconButton(
                                onClick = { 
                                    bridge.stopEmulation()
                                    isRomLoaded = false 
                                    if (isFullscreen) {
                                        isFullscreen = false
                                        windowState.placement = WindowPlacement.Floating
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }

                        // Pequeño indicador de controles en la esquina
                        Text(
                            "ESC para menú | F11 Pantalla completa",
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

private fun handleKeyEvent(event: KeyEvent, bridge: NativeBridge): Boolean {
    val pressed = event.type == KeyEventType.KeyDown
    val key = when (event.key) {
        Key.DirectionUp -> SnesKey.UP
        Key.DirectionDown -> SnesKey.DOWN
        Key.DirectionLeft -> SnesKey.LEFT
        Key.DirectionRight -> SnesKey.RIGHT
        Key.X -> SnesKey.A
        Key.Z -> SnesKey.B
        Key.S -> SnesKey.X
        Key.A -> SnesKey.Y
        Key.Q -> SnesKey.L
        Key.W -> SnesKey.R
        Key.Enter -> SnesKey.START
        Key.Spacebar -> SnesKey.SELECT
        else -> null
    }

    return if (key != null) {
        bridge.reportButton(key.nativeCode, pressed)
        true
    } else {
        false
    }
}
