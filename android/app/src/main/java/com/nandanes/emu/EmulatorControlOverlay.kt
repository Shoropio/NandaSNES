package com.nandanes.emu

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

private enum class OverlayPanelMode {
    NONE,
    MENU,
    EDITOR,
    SAVES,
    DEBUG,
    ABOUT
}

@Composable
fun EmulatorOverlay(
    romLabel: String,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    onGoHome: () -> Unit,
    settings: ControlOverlaySettings,
    onSettingsChange: (ControlOverlaySettings) -> Unit,
    debugEnabled: Boolean,
    onToggleDebug: (Boolean) -> Unit,
    debugContent: @Composable () -> Unit = {},
    topContent: @Composable (closePanel: () -> Unit) -> Unit = {}
) {
    var panelMode by remember { mutableStateOf(OverlayPanelMode.NONE) }
    val palette = remember(settings.skin) { paletteForSkin(settings.skin) }
    val editing = panelMode == OverlayPanelMode.EDITOR
    val showingSaves = panelMode == OverlayPanelMode.SAVES
    val showControls = !showingSaves

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactScale = 0.92f
        val actionBase = 66.dp * settings.sizeScale * compactScale
        val dpadBase = 60.dp * settings.sizeScale * compactScale
        val centerButtonWidth = 88.dp * settings.sizeScale * compactScale
        val centerButtonHeight = 38.dp * settings.sizeScale * compactScale
        val shoulderWidth = 78.dp * settings.sizeScale * compactScale
        val shoulderHeight = 32.dp * settings.sizeScale * compactScale
        val dpadAlpha = (settings.opacity * settings.dpadOpacity).coerceIn(0.06f, 0.98f)
        val actionAlpha = (settings.opacity * settings.actionOpacity).coerceIn(0.06f, 1f)
        val centerAlpha = (settings.opacity * settings.centerOpacity).coerceIn(0.06f, 0.98f)
        val shoulderAlpha = (settings.opacity * settings.shoulderOpacity).coerceIn(0.06f, 0.98f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0x90000000), Color.Transparent)))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x22000000), Color(0x7A000000))))
        )

        FloatingMenuButton(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            active = panelMode != OverlayPanelMode.NONE,
            onClick = {
                panelMode = if (panelMode == OverlayPanelMode.NONE) {
                    OverlayPanelMode.MENU
                } else {
                    OverlayPanelMode.NONE
                }
            }
        )

        if (showControls) {
            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = settings.shoulderOffsetY.dp)
                    .padding(start = 18.dp, top = 44.dp),
                onDrag = { _, dy ->
                    onSettingsChange(settings.copy(shoulderOffsetY = (settings.shoulderOffsetY + dy).coerceIn(-24f, 70f)))
                },
                editorLabel = "L"
            ) {
                RetroPillButton("L", SnesKey.L, onPress, onRelease, shoulderWidth, shoulderHeight, palette.shoulder, shoulderAlpha)
            }

            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = settings.shoulderOffsetY.dp)
                    .padding(end = 18.dp, top = 44.dp),
                onDrag = { _, dy ->
                    onSettingsChange(settings.copy(shoulderOffsetY = (settings.shoulderOffsetY + dy).coerceIn(-24f, 70f)))
                },
                editorLabel = "R"
            ) {
                RetroPillButton("R", SnesKey.R, onPress, onRelease, shoulderWidth, shoulderHeight, palette.shoulder, shoulderAlpha)
            }

            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = settings.dpadOffsetX.dp, y = settings.dpadOffsetY.dp)
                    .padding(start = 14.dp, bottom = 20.dp),
                onDrag = { dx, dy ->
                    onSettingsChange(
                        settings.copy(
                            dpadOffsetX = (settings.dpadOffsetX + dx).coerceIn(-60f, 120f),
                            dpadOffsetY = (settings.dpadOffsetY + dy).coerceIn(-130f, 30f)
                        )
                    )
                },
                editorLabel = "D-Pad"
            ) {
                RetroDpad(dpadBase, dpadAlpha, palette, onPress, onRelease)
            }

            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = settings.actionOffsetX.dp, y = settings.actionOffsetY.dp)
                    .padding(end = 14.dp, bottom = 20.dp),
                onDrag = { dx, dy ->
                    onSettingsChange(
                        settings.copy(
                            actionOffsetX = (settings.actionOffsetX + dx).coerceIn(-120f, 60f),
                            actionOffsetY = (settings.actionOffsetY + dy).coerceIn(-130f, 30f)
                        )
                    )
                },
                editorLabel = "ABXY"
            ) {
                RetroActionCluster(actionBase, actionAlpha, palette, onPress, onRelease)
            }

            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = settings.centerOffsetY.dp)
                    .padding(bottom = 16.dp),
                onDrag = { _, dy ->
                    onSettingsChange(settings.copy(centerOffsetY = (settings.centerOffsetY + dy).coerceIn(-120f, 24f)))
                },
                editorLabel = "Start / Select"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RetroGhostPillButton("SELECT", SnesKey.SELECT, onPress, onRelease, centerButtonWidth, centerButtonHeight, centerAlpha)
                    RetroGhostPillButton("START", SnesKey.START, onPress, onRelease, centerButtonWidth, centerButtonHeight, centerAlpha)
                }
            }
        }

        if (panelMode != OverlayPanelMode.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x36000000))
            )
        }

        when (panelMode) {
            OverlayPanelMode.NONE -> Unit
            OverlayPanelMode.MENU -> {
                OverlayBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    title = romLabel,
                    subtitle = "Elige una accion sin tapar la partida",
                    onDismiss = { panelMode = OverlayPanelMode.NONE }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactActionButton(
                            onClick = { panelMode = OverlayPanelMode.EDITOR },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tune Controls")
                        }
                        CompactActionButton(
                            onClick = { panelMode = OverlayPanelMode.SAVES },
                            modifier = Modifier.weight(1f),
                            accent = Color(0xFF2B3D5C)
                        ) {
                            Text("Estados guardados")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Modo debug", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Switch(checked = debugEnabled, onCheckedChange = onToggleDebug)
                    }
                    CompactActionButton(
                        onClick = { panelMode = OverlayPanelMode.DEBUG },
                        modifier = Modifier.fillMaxWidth(),
                        accent = Color(0xFF38404D)
                    ) {
                        Text("Abrir panel debug")
                    }
                    CompactActionButton(
                        onClick = { panelMode = OverlayPanelMode.ABOUT },
                        modifier = Modifier.fillMaxWidth(),
                        accent = Color(0xFF2D353F)
                    ) {
                        Text("Acerca de y licencias")
                    }
                    CompactActionButton(
                        onClick = {
                            panelMode = OverlayPanelMode.NONE
                            onGoHome()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accent = Color(0xFF5B3131)
                    ) {
                        Text("Volver al inicio")
                    }
                }
            }
            OverlayPanelMode.EDITOR -> {
                OverlayBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    title = "Retro Controls",
                    subtitle = "Arrastra cada bloque o afina opacidad, tamano y vibracion.",
                    onDismiss = { panelMode = OverlayPanelMode.NONE }
                ) {
                    ControlSettingsPanel(settings, onSettingsChange) {
                        onSettingsChange(ControlOverlaySettings())
                    }
                }
            }
            OverlayPanelMode.SAVES -> {
                OverlayBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    title = "Estados guardados",
                    subtitle = "Carga, guarda o borra estados sin perder progreso.",
                    onDismiss = { panelMode = OverlayPanelMode.NONE }
                ) {
                    topContent { panelMode = OverlayPanelMode.NONE }
                }
            }
            OverlayPanelMode.DEBUG -> {
                OverlayBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    title = "Debug",
                    subtitle = "Activa logs detallados y revisa el estado del core.",
                    onDismiss = { panelMode = OverlayPanelMode.MENU }
                ) {
                    debugContent()
                }
            }
            OverlayPanelMode.ABOUT -> {
                OverlayBottomSheet(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    title = "Acerca de",
                    subtitle = "Creditos, derechos de autor y componentes open source.",
                    onDismiss = { panelMode = OverlayPanelMode.MENU }
                ) {
                    AboutPanelContent()
                }
            }
        }
    }
}

@Composable
private fun OverlayBottomSheet(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xB814161B))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(28.dp))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(onClick = onDismiss, accent = Color(0x663A3A42)) {
                Text("Cerrar")
            }
        }
        Text(subtitle, color = Color(0xFFB3B7C0), style = MaterialTheme.typography.labelMedium)
        content()
    }
}

@Composable
fun AboutPanelContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("NandaNes\u00A9 Inc 2026", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Text("Desarrollado por Shoropio\u00A9 Corporation 2026", color = Color(0xFFD7DCE4), style = MaterialTheme.typography.bodyMedium)
        Text(
            "NandaNes\u00A9 es una app para emulación de Super Nintendo.",
            color = Color(0xFFB3B7C0),
            style = MaterialTheme.typography.bodySmall
        )
        Text("Librerias y componentes open source", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(
            "Snes9x libretro core\nAndroidX Activity Compose\nJetpack Compose UI\nMaterial 3\nKotlin for Android",
            color = Color(0xFFB9C1CC),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Las ROMs, consolas y marcas pertenecen a sus respectivos propietarios. Usa solo contenido que tengas derecho a ejecutar.",
            color = Color(0xFF8E98A7),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private data class ControlSkinPalette(
    val shoulder: Color,
    val dpad: Color,
    val center: Color,
    val actionX: Color,
    val actionY: Color,
    val actionA: Color,
    val actionB: Color
)

private fun paletteForSkin(skin: ControlSkin): ControlSkinPalette = when (skin) {
    ControlSkin.CLASSIC -> ControlSkinPalette(
        shoulder = Color(0xFF494D56),
        dpad = Color(0xFF35363B),
        center = Color(0xFF444650),
        actionX = Color(0xFF61666F),
        actionY = Color(0xFF61666F),
        actionA = Color(0xFF61666F),
        actionB = Color(0xFF61666F)
    )
    ControlSkin.NEON -> ControlSkinPalette(
        shoulder = Color(0xFF59606D),
        dpad = Color(0xFF343943),
        center = Color(0xFF4E5560),
        actionX = Color(0xFF7A828F),
        actionY = Color(0xFF7A828F),
        actionA = Color(0xFF7A828F),
        actionB = Color(0xFF7A828F)
    )
    ControlSkin.CARBON -> ControlSkinPalette(
        shoulder = Color(0xFF4C4C4C),
        dpad = Color(0xFF2D2D2D),
        center = Color(0xFF595959),
        actionX = Color(0xFF6A6F78),
        actionY = Color(0xFF6A6F78),
        actionA = Color(0xFF6A6F78),
        actionB = Color(0xFF6A6F78)
    )
}

@Composable
private fun FloatingMenuButton(
    modifier: Modifier = Modifier,
    active: Boolean,
    onClick: () -> Unit
) {
    FloatingIconButton(
        modifier = modifier,
        label = if (active) "X" else "\u2261",
        active = active,
        onClick = onClick
    )
}

@Composable
private fun FloatingIconButton(
    modifier: Modifier = Modifier,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xAA5D4FA3) else Color(0x7A202228),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudChipButton(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF5D4FA3) else Color(0xE6242529),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun ControlSettingsPanel(
    settings: ControlOverlaySettings,
    onSettingsChange: (ControlOverlaySettings) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlSkin.entries.forEach { skin ->
                HudChipButton(
                    label = skin.label,
                    active = settings.skin == skin,
                    onClick = { onSettingsChange(settings.copy(skin = skin)) }
                )
            }
        }

        ControlSliderRow("Tamano", settings.sizeScale, 0.55f..1.6f) {
            onSettingsChange(settings.copy(sizeScale = it))
        }
        ControlSliderRow("Opacidad base", settings.opacity, 0.08f..1f) {
            onSettingsChange(settings.copy(opacity = it))
        }
        ControlSliderRow("D-pad", settings.dpadOpacity, 0.05f..1f) {
            onSettingsChange(settings.copy(dpadOpacity = it))
        }
        ControlSliderRow("ABXY", settings.actionOpacity, 0.05f..1f) {
            onSettingsChange(settings.copy(actionOpacity = it))
        }
        ControlSliderRow("Centro", settings.centerOpacity, 0.05f..1f) {
            onSettingsChange(settings.copy(centerOpacity = it))
        }
        ControlSliderRow("Hombros", settings.shoulderOpacity, 0.05f..1f) {
            onSettingsChange(settings.copy(shoulderOpacity = it))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Haptics", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("Vibracion al pulsar botones", color = Color(0xFFB3B3B3), style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = settings.hapticsEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(hapticsEnabled = it)) }
            )
        }

        if (settings.hapticsEnabled) {
            ControlSliderRow("Intensidad", settings.hapticsStrength, 0.1f..1f) {
                onSettingsChange(settings.copy(hapticsStrength = it))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            CompactActionButton(onClick = onReset, accent = Color(0xFF3A3A3E)) {
                Text("Reset")
            }
        }
    }
}

@Composable
fun DebugPanelContent(
    enabled: Boolean,
    logText: String,
    nativeLogText: String,
    nativeSnapshot: String,
    onToggleEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onCaptureSnapshot: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Modo debug", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text("Registra pasos del runtime y del puente nativo.", color = Color(0xFFB3B3B3), style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = enabled, onCheckedChange = onToggleEnabled)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactActionButton(onClick = onRefresh, modifier = Modifier.weight(1f), accent = Color(0xFF38404D)) {
                Text("Refrescar")
            }
            CompactActionButton(onClick = onCaptureSnapshot, modifier = Modifier.weight(1f), accent = Color(0xFF2E5A4D)) {
                Text("Snapshot")
            }
            CompactActionButton(onClick = onClear, modifier = Modifier.weight(1f), accent = Color(0xFF5B3131)) {
                Text("Borrar")
            }
        }

        Text("Estado nativo", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(nativeSnapshot, color = Color(0xFFB9C1CC), style = MaterialTheme.typography.bodySmall)
        Text("Log nativo", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(nativeLogText, color = Color(0xFFE0C79B), style = MaterialTheme.typography.bodySmall)
        Text("Log reciente", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(logText, color = Color(0xFFB9C1CC), style = MaterialTheme.typography.bodySmall)
        Text(EmulatorDebug.logPath(), color = Color(0xFF7E8794), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ControlSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text(String.format(Locale.US, "%.2f", value), color = Color(0xFFB3B3B3), style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun DraggableControlLayer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
    editorLabel: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures { _, dragAmount ->
                onDrag(dragAmount.x / density, dragAmount.y / density)
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (enabled) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xCC1A1B1E))
                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(editorLabel, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        content()
    }
}

@Composable
private fun RetroDpad(
    size: Dp,
    opacity: Float,
    palette: ControlSkinPalette,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    Box(modifier = Modifier.size(size * 2.44f)) {
        Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-2).dp)) {
            DpadArrowButton("\u2191", SnesKey.UP, onPress, onRelease, size * 0.9f, size * 0.8f, opacity)
        }
        Box(modifier = Modifier.align(Alignment.CenterStart).offset(x = (-2).dp)) {
            DpadArrowButton("\u2190", SnesKey.LEFT, onPress, onRelease, size * 0.9f, size * 0.8f, opacity)
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).offset(x = 2.dp)) {
            DpadArrowButton("\u2192", SnesKey.RIGHT, onPress, onRelease, size * 0.9f, size * 0.8f, opacity)
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 2.dp)) {
            DpadArrowButton("\u2193", SnesKey.DOWN, onPress, onRelease, size * 0.9f, size * 0.8f, opacity)
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun DpadArrowButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    buttonWidth: Dp,
    buttonHeight: Dp,
    opacity: Float
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = when (label) {
        "\u2191" -> CutCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        "\u2193" -> CutCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        "\u2190" -> CutCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp)
        "\u2192" -> CutCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 4.dp, bottomEnd = 4.dp)
        else -> CutCornerShape(12.dp)
    }
    Box(
        modifier = Modifier
            .size(buttonWidth, buttonHeight)
            .clip(shape)
            .background(if (pressed) Color(0x10181B21) else Color.Transparent)
            .border(1.6.dp, Color(0xFF9CA3AE).copy(alpha = opacity * if (pressed) 0.95f else 0.72f), shape)
            .pointerInteropFilter {
                when (it.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
                        onPress(key)
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        onRelease(key)
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF777D88).copy(alpha = opacity * if (pressed) 1f else 0.82f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
private fun RetroActionCluster(
    size: Dp,
    opacity: Float,
    palette: ControlSkinPalette,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    Box(modifier = Modifier.size(size * 2.6f)) {
        Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-6).dp)) {
            RetroRoundButton("X", SnesKey.X, size, palette.actionX, opacity, onPress, onRelease)
        }
        Box(modifier = Modifier.align(Alignment.CenterStart).offset(x = (-6).dp)) {
            RetroRoundButton("Y", SnesKey.Y, size, palette.actionY, opacity, onPress, onRelease)
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).offset(x = 6.dp)) {
            RetroRoundButton("A", SnesKey.A, size, palette.actionA, opacity, onPress, onRelease)
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 6.dp)) {
            RetroRoundButton("B", SnesKey.B, size, palette.actionB, opacity, onPress, onRelease)
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RetroRoundButton(
    label: String,
    key: SnesKey,
    size: Dp,
    accent: Color,
    opacity: Float,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF23262D).copy(alpha = opacity * if (pressed) 0.88f else 0.72f),
                        Color(0xFF131417).copy(alpha = opacity)
                    )
                )
            )
            .border(2.dp, Color(0xFF6F7683).copy(alpha = opacity * 0.9f), CircleShape)
            .pointerInteropFilter {
                when (it.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
                        onPress(key)
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        onRelease(key)
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RetroPillButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    buttonWidth: Dp,
    buttonHeight: Dp,
    fillColor: Color,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(buttonHeight / 2f)
    Box(
        modifier = modifier
            .size(buttonWidth, buttonHeight)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C2F35).copy(alpha = opacity * if (pressed) 0.9f else 0.72f),
                        Color(0xFF111216).copy(alpha = opacity)
                    )
                )
            )
            .border(2.dp, Color(0xFF6F7683).copy(alpha = opacity * 0.85f), shape)
            .pointerInteropFilter {
                when (it.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
                        onPress(key)
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        onRelease(key)
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RetroGhostPillButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    buttonWidth: Dp,
    buttonHeight: Dp,
    opacity: Float
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(buttonWidth, buttonHeight)
            .clip(shape)
            .background(if (pressed) Color(0x10181B21) else Color(0x08000000))
            .border(1.6.dp, Color(0xFF7E8591).copy(alpha = opacity * if (pressed) 0.95f else 0.72f), shape)
            .pointerInteropFilter {
                when (it.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
                        onPress(key)
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        onRelease(key)
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF6F7682).copy(alpha = opacity * if (pressed) 1f else 0.82f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}
