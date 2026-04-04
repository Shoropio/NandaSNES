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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.nandanes.emu.data.settings.ControlOverlaySettings
import com.nandanes.emu.data.settings.ControlSkin
import com.nandanes.emu.data.settings.EmulatorDebug
import com.nandanes.emu.runtime.SnesKey
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
        val sw = this.maxWidth
        val sh = this.maxHeight
        val compactScale = 0.92f
        val actionBase = 66.dp * settings.sizeScale * compactScale
        val dpadBase = 74.dp * settings.sizeScale * compactScale
        val centerButtonWidth = 90.dp * settings.sizeScale * compactScale
        val centerButtonHeight = 40.dp * settings.sizeScale * compactScale
        val shoulderWidth = 112.dp * settings.sizeScale * compactScale
        val shoulderHeight = 48.dp * settings.sizeScale * compactScale
        val dpadClusterSize = dpadBase * 2.3f
        val actionClusterSize = actionBase * 2.4f
        val menuWidth = 112.dp
        val menuHeight = 52.dp
        val leftShoulderAnchor = proportionalAnchor(sw, sh, 0.14f, 0.15f, shoulderWidth, shoulderHeight)
        val rightShoulderAnchor = proportionalAnchor(sw, sh, 0.86f, 0.15f, shoulderWidth, shoulderHeight)
        val dpadAnchor = proportionalAnchor(sw, sh, 0.18f, 0.62f, dpadClusterSize, dpadClusterSize)
        val actionAnchor = proportionalAnchor(sw, sh, 0.82f, 0.62f, actionClusterSize, actionClusterSize)
        val centerAnchor = proportionalAnchor(
            sw,
            sh,
            0.5f,
            0.88f,
            centerButtonWidth * 2f + 18.dp,
            centerButtonHeight
        )
        val menuAnchor = proportionalAnchor(sw, sh, 0.5f, 0.15f, menuWidth, menuHeight)
        val dpadAlpha = (settings.opacity * settings.dpadOpacity).coerceIn(0.06f, 0.98f)
        val actionAlpha = (settings.opacity * settings.actionOpacity).coerceIn(0.06f, 0.98f)
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
                .offset(x = menuAnchor.x, y = menuAnchor.y),
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
                    .offset(
                        x = leftShoulderAnchor.x,
                        y = leftShoulderAnchor.y + settings.shoulderOffsetY.dp
                    ),
                onDrag = { _, dy ->
                    onSettingsChange(settings.copy(shoulderOffsetY = (settings.shoulderOffsetY + dy).coerceIn(-24f, 70f)))
                },
                editorLabel = "L"
            ) {
                RetroShoulderButton("L", SnesKey.L, onPress, onRelease, shoulderWidth, shoulderHeight, shoulderAlpha, palette, mirrored = false)
            }

            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .offset(
                        x = rightShoulderAnchor.x,
                        y = rightShoulderAnchor.y + settings.shoulderOffsetY.dp
                    ),
                onDrag = { _, dy ->
                    onSettingsChange(settings.copy(shoulderOffsetY = (settings.shoulderOffsetY + dy).coerceIn(-24f, 70f)))
                },
                editorLabel = "R"
            ) {
                RetroShoulderButton("R", SnesKey.R, onPress, onRelease, shoulderWidth, shoulderHeight, shoulderAlpha, palette, mirrored = true)
            }

            DraggableControlLayer(
                enabled = editing,
                modifier = Modifier
                    .offset(
                        x = dpadAnchor.x + settings.dpadOffsetX.dp,
                        y = dpadAnchor.y + settings.dpadOffsetY.dp
                    ),
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
                    .offset(
                        x = actionAnchor.x + settings.actionOffsetX.dp,
                        y = actionAnchor.y + settings.actionOffsetY.dp
                    ),
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
                    .offset(
                        x = centerAnchor.x,
                        y = centerAnchor.y + settings.centerOffsetY.dp
                    ),
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
                    subtitle = "Elige una acción sin tapar la partida",
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
        Text("NandaSNES Inc 2026", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Text("Desarrollado por Shoropio Corporation 2026", color = Color(0xFFD7DCE4), style = MaterialTheme.typography.bodyMedium)
        Text(
            "NandaSNES es una app para emulacion de Super Nintendo.",
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
                Text(
                    "Registra pasos del runtime y del puente nativo.",
                    color = Color(0xFFB3B3B3),
                    style = MaterialTheme.typography.labelSmall
                )
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

private data class ControlSkinPalette(
    val outline: Color,
    val fill: Color,
    val fillPressed: Color,
    val accent: Color,
    val text: Color
)

private fun paletteForSkin(skin: ControlSkin): ControlSkinPalette = when (skin) {
    ControlSkin.CLASSIC -> ControlSkinPalette(
        outline = Color(0xD9FFFFFF),
        fill = Color(0x14000000),
        fillPressed = Color(0x26FFFFFF),
        accent = Color(0x80FFFFFF),
        text = Color(0xF2FFFFFF)
    )
    ControlSkin.NEON -> ControlSkinPalette(
        outline = Color(0xCCB8F6FF),
        fill = Color(0x14003E52),
        fillPressed = Color(0x2455E8FF),
        accent = Color(0x994CEBFF),
        text = Color(0xFFF3FEFF)
    )
    ControlSkin.CARBON -> ControlSkinPalette(
        outline = Color(0xCCECECEC),
        fill = Color(0x12000000),
        fillPressed = Color(0x1FFFFFFF),
        accent = Color(0x8ACFCFCF),
        text = Color(0xFFF6F6F6)
    )
}

@Composable
private fun ControlSettingsPanel(
    settings: ControlOverlaySettings,
    onSettingsChange: (ControlOverlaySettings) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledSlider("Tamano", settings.sizeScale, 0.72f..1.3f) {
            onSettingsChange(settings.copy(sizeScale = it))
        }
        LabeledSlider("Opacidad general", settings.opacity, 0.1f..1f) {
            onSettingsChange(settings.copy(opacity = it))
        }
        LabeledSlider("Opacidad D-Pad", settings.dpadOpacity, 0.1f..1f) {
            onSettingsChange(settings.copy(dpadOpacity = it))
        }
        LabeledSlider("Opacidad ABXY", settings.actionOpacity, 0.1f..1f) {
            onSettingsChange(settings.copy(actionOpacity = it))
        }
        LabeledSlider("Opacidad Start/Select", settings.centerOpacity, 0.1f..1f) {
            onSettingsChange(settings.copy(centerOpacity = it))
        }
        LabeledSlider("Opacidad L/R", settings.shoulderOpacity, 0.1f..1f) {
            onSettingsChange(settings.copy(shoulderOpacity = it))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vibracion", color = Color.White)
            Switch(
                checked = settings.hapticsEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(hapticsEnabled = it)) }
            )
        }
        if (settings.hapticsEnabled) {
            LabeledSlider("Intensidad vibracion", settings.hapticsStrength, 0.1f..1f) {
                onSettingsChange(settings.copy(hapticsStrength = it))
            }
        }
        Text("Skin", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlSkin.entries.forEach { skin ->
                CompactActionButton(
                    onClick = { onSettingsChange(settings.copy(skin = skin)) },
                    accent = if (settings.skin == skin) Color(0xFF5D4FA3) else Color(0x663A3A42)
                ) {
                    Text(skin.label)
                }
            }
        }
        CompactActionButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            accent = Color(0xFF4A2727)
        ) {
            Text("Restablecer")
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label ${String.format(Locale.US, "%.2f", value)}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun FloatingMenuButton(
    modifier: Modifier = Modifier,
    active: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(1.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0x1FFFFFFF) else Color(0x14000000),
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (active) 0.9f else 0.55f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("MENU", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DraggableControlLayer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (dx: Float, dy: Float) -> Unit,
    editorLabel: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.pointerInput(enabled) {
            if (enabled) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x / density, dragAmount.y / density)
                }
            }
        }
    ) {
        content()
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp))
            )
            Text(
                text = editorLabel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-24).dp)
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RetroShoulderButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    width: Dp,
    height: Dp,
    alpha: Float,
    palette: ControlSkinPalette,
    mirrored: Boolean
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = Modifier.size(width = width, height = height),
        shape = shoulderShape(mirrored),
        background = palette.fill.copy(alpha = alpha),
        border = palette.outline.copy(alpha = alpha),
        chrome = palette.fillPressed.copy(alpha = alpha),
        textColor = palette.text,
        textStyle = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun RetroGhostPillButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    width: Dp,
    height: Dp,
    alpha: Float
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = Modifier.size(width = width, height = height),
        shape = RoundedCornerShape(1.dp),
        background = Color.Transparent,
        border = Color.White.copy(alpha = alpha),
        chrome = Color.White.copy(alpha = alpha * 0.14f),
        textColor = Color.White,
        textStyle = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun RetroActionCluster(
    buttonSize: Dp,
    alpha: Float,
    palette: ControlSkinPalette,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    Box(modifier = Modifier.size(buttonSize * 2.8f)) {
        ActionButton("X", SnesKey.X, onPress, onRelease, palette, alpha, Modifier.align(Alignment.TopCenter).size(buttonSize))
        ActionButton("Y", SnesKey.Y, onPress, onRelease, palette, alpha, Modifier.align(Alignment.CenterStart).size(buttonSize))
        ActionButton("A", SnesKey.A, onPress, onRelease, palette, alpha, Modifier.align(Alignment.CenterEnd).size(buttonSize))
        ActionButton("B", SnesKey.B, onPress, onRelease, palette, alpha, Modifier.align(Alignment.BottomCenter).size(buttonSize))
    }
}

@Composable
private fun ActionButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    palette: ControlSkinPalette,
    alpha: Float,
    modifier: Modifier
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = modifier,
        shape = CircleShape,
        background = palette.fill.copy(alpha = alpha),
        border = palette.outline.copy(alpha = alpha),
        chrome = palette.fillPressed.copy(alpha = alpha),
        textColor = palette.text,
        textStyle = MaterialTheme.typography.headlineSmall
    )
}

private enum class DirectionGlyph { UP, LEFT, RIGHT, DOWN }

@Composable
private fun RetroDpad(
    buttonSize: Dp,
    alpha: Float,
    palette: ControlSkinPalette,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    val totalSize = buttonSize * 2.18f

    Box(modifier = Modifier.size(totalSize)) {
        DpadButton(
            DirectionGlyph.UP,
            SnesKey.UP,
            onPress,
            onRelease,
            palette,
            alpha,
            Modifier
                .align(Alignment.TopCenter)
                .size(width = buttonSize * 0.92f, height = buttonSize)
        )

        DpadButton(
            DirectionGlyph.LEFT,
            SnesKey.LEFT,
            onPress,
            onRelease,
            palette,
            alpha,
            Modifier
                .align(Alignment.CenterStart)
                .size(width = buttonSize, height = buttonSize * 0.92f)
        )

        DpadButton(
            DirectionGlyph.RIGHT,
            SnesKey.RIGHT,
            onPress,
            onRelease,
            palette,
            alpha,
            Modifier
                .align(Alignment.CenterEnd)
                .size(width = buttonSize, height = buttonSize * 0.92f)
        )

        DpadButton(
            DirectionGlyph.DOWN,
            SnesKey.DOWN,
            onPress,
            onRelease,
            palette,
            alpha,
            Modifier
                .align(Alignment.BottomCenter)
                .size(width = buttonSize * 0.92f, height = buttonSize)
        )
    }
}

private fun dpadShape(glyph: DirectionGlyph): Shape = GenericShape { size, _ ->
    when (glyph) {
        DirectionGlyph.UP -> {
            // plano arriba, punta abajo
            moveTo(size.width * 0.12f, 0f)
            lineTo(size.width * 0.88f, 0f)
            lineTo(size.width * 0.88f, size.height * 0.62f)
            lineTo(size.width * 0.50f, size.height)
            lineTo(size.width * 0.12f, size.height * 0.62f)
        }

        DirectionGlyph.DOWN -> {
            // punta arriba, plano abajo
            moveTo(size.width * 0.12f, size.height)
            lineTo(size.width * 0.88f, size.height)
            lineTo(size.width * 0.88f, size.height * 0.38f)
            lineTo(size.width * 0.50f, 0f)
            lineTo(size.width * 0.12f, size.height * 0.38f)
        }

        DirectionGlyph.LEFT -> {
            // plano izquierda, punta derecha
            moveTo(0f, size.height * 0.12f)
            lineTo(size.width * 0.62f, size.height * 0.12f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.62f, size.height * 0.88f)
            lineTo(0f, size.height * 0.88f)
        }

        DirectionGlyph.RIGHT -> {
            // punta izquierda, plano derecha
            moveTo(size.width, size.height * 0.12f)
            lineTo(size.width * 0.38f, size.height * 0.12f)
            lineTo(0f, size.height * 0.50f)
            lineTo(size.width * 0.38f, size.height * 0.88f)
            lineTo(size.width, size.height * 0.88f)
        }
    }
    close()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    modifier: Modifier,
    shape: Shape,
    background: Color,
    border: Color = Color.Transparent,
    chrome: Color = Color.White.copy(alpha = 0.14f),
    textColor: Color = Color.White,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    overlayContent: @Composable (() -> Unit)? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 720f),
        label = "overlayTouchScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(if (pressed) chrome else background, shape)
            .border(1.dp, border, shape)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPress(key)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (pressed) {
                            pressed = false
                            onRelease(key)
                        }
                        true
                    }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (overlayContent != null) {
            overlayContent()
        } else {
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.Medium,
                style = textStyle,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun shoulderShape(mirrored: Boolean): Shape = GenericShape { size, _ ->
    if (!mirrored) {
        moveTo(0f, size.height)
        cubicTo(0f, size.height * 0.36f, size.width * 0.08f, 0f, size.width * 0.2f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
    } else {
        moveTo(0f, 0f)
        lineTo(size.width * 0.8f, 0f)
        cubicTo(size.width * 0.92f, 0f, size.width, size.height * 0.36f, size.width, size.height)
        lineTo(0f, size.height)
    }
    close()
}

private data class ProportionalAnchor(val x: Dp, val y: Dp)

private fun proportionalAnchor(
    screenWidth: Dp,
    screenHeight: Dp,
    xRatio: Float,
    yRatio: Float,
    elementWidth: Dp,
    elementHeight: Dp
): ProportionalAnchor {
    val x = (screenWidth * xRatio) - (elementWidth / 2f)
    val y = (screenHeight * yRatio) - (elementHeight / 2f)
    return ProportionalAnchor(
        x = x.coerceAtLeast(0.dp),
        y = y.coerceAtLeast(0.dp)
    )
}

@Composable
private fun DpadButton(
    glyph: DirectionGlyph,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    palette: ControlSkinPalette,
    alpha: Float,
    modifier: Modifier
) {
    TouchButton(
        label = "",
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = modifier,
        shape = dpadShape(glyph),
        background = palette.fill.copy(alpha = alpha),
        border = palette.outline.copy(alpha = alpha),
        chrome = palette.fillPressed.copy(alpha = alpha),
        textColor = palette.text,
        overlayContent = { DirectionIcon(glyph, palette.text.copy(alpha = alpha)) }
    )
}

@Composable
private fun DirectionIcon(
    glyph: DirectionGlyph,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f

                val triWidth = size.width * 0.18f
                val triHeight = size.height * 0.14f

                val triangle = Path().apply {
                    moveTo(cx, cy - triHeight / 2f)
                    lineTo(cx - triWidth / 2f, cy + triHeight / 2f)
                    lineTo(cx + triWidth / 2f, cy + triHeight / 2f)
                    close()
                }

                withTransform({
                    rotate(
                        degrees = when (glyph) {
                            DirectionGlyph.UP -> 0f
                            DirectionGlyph.RIGHT -> 90f
                            DirectionGlyph.DOWN -> 180f
                            DirectionGlyph.LEFT -> 270f
                        },
                        pivot = Offset(cx, cy)
                    )
                }) {
                    drawPath(
                        path = triangle,
                        color = color,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
    )
}
