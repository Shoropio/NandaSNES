package com.nandanes.emu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.nandanes.emu.runtime.NativeBridge
import kotlinx.coroutines.delay
import java.awt.image.BufferedImage

@Composable
fun DesktopVideoSurface(bridge: NativeBridge, modifier: Modifier = Modifier) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    // Bucle de renderizado (Aprox 60 FPS)
    LaunchedEffect(Unit) {
        while (true) {
            val frameData = bridge.getFrameArgb8888()
            if (frameData != null && frameData.size >= 2) {
                val width = frameData[0]
                val height = frameData[1]
                if (width > 0 && height > 0) {
                    val pixels = frameData.sliceArray(2 until frameData.size)
                    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    bufferedImage.setRGB(0, 0, width, height, pixels, 0, width)
                    imageBitmap = bufferedImage.toComposeImageBitmap()
                }
            }
            delay(16) // ~60 FPS
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        imageBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Snes Frame",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
