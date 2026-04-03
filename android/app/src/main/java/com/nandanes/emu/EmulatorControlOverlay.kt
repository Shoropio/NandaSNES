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
        Text("NandaNes Inc 2026", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Text("Desarrollado por Shoropio Corporation 2026", color = Color(0xFFD7DCE4), style = MaterialTheme.typography.bodyMedium)
        Text(
            "NandaNes es una app para emulacion de Super Nintendo.",
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
        dpad = Color(0xFF69707C),
        center = Color(0xFF444A57),
        actionX = Color(0xFF6F90D8),
        actionY = Color(0xFF6DB2C6),
        actionA = Color(0xFF63C18F),
        actionB = Color(0xFFD47373)
    )
    ControlSkin.NEON -> ControlSkinPalette(
        shoulder = Color(0xFF28445B),
        dpad = Color(0xFF00AEEF),
        center = Color(0xFF21425A),
        actionX = Color(0xFF4FC3F7),
        actionY = Color(0xFF00BCD4),
        actionA = Color(0xFF00E676),
        actionB = Color(0xFFFF5252)
    )
    ControlSkin.CARBON -> ControlSkinPalette(
        shoulder = Color(0xFF3B3E43),
        dpad = Color(0xFF4C4F55),
        center = Color(0xFF32353A),
        actionX = Color(0xFF70757F),
        actionY = Color(0xFF7A808A),
        actionA = Color(0xFF959B63),
        actionB = Color(0xFF9B6666)
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
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF5D4FA3) else Color(0x883A3A42),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text("MENU", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
private fun RetroPillButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    width: Dp,
    height: Dp,
    color: Color,
    alpha: Float
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = Modifier.size(width = width, height = height),
        shape = CutCornerShape(14.dp),
        background = color.copy(alpha = alpha)
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
        shape = RoundedCornerShape(50),
        background = Color(0xCC30343B).copy(alpha = alpha),
        border = Color(0x77FFFFFF)
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
    Box(modifier = Modifier.size(buttonSize * 2.35f)) {
        ActionButton("X", SnesKey.X, onPress, onRelease, palette.actionX.copy(alpha = alpha), Modifier.align(Alignment.TopCenter).size(buttonSize))
        ActionButton("Y", SnesKey.Y, onPress, onRelease, palette.actionY.copy(alpha = alpha), Modifier.align(Alignment.CenterStart).size(buttonSize))
        ActionButton("A", SnesKey.A, onPress, onRelease, palette.actionA.copy(alpha = alpha), Modifier.align(Alignment.CenterEnd).size(buttonSize))
        ActionButton("B", SnesKey.B, onPress, onRelease, palette.actionB.copy(alpha = alpha), Modifier.align(Alignment.BottomCenter).size(buttonSize))
    }
}

@Composable
private fun ActionButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    color: Color,
    modifier: Modifier
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = modifier,
        shape = CircleShape,
        background = color
    )
}

@Composable
private fun RetroDpad(
    buttonSize: Dp,
    alpha: Float,
    palette: ControlSkinPalette,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    Box(modifier = Modifier.size(buttonSize * 3f)) {
        DpadButton("U", SnesKey.UP, onPress, onRelease, palette.dpad.copy(alpha = alpha), Modifier.align(Alignment.TopCenter).size(buttonSize))
        DpadButton("L", SnesKey.LEFT, onPress, onRelease, palette.dpad.copy(alpha = alpha), Modifier.align(Alignment.CenterStart).size(buttonSize))
        DpadButton("R", SnesKey.RIGHT, onPress, onRelease, palette.dpad.copy(alpha = alpha), Modifier.align(Alignment.CenterEnd).size(buttonSize))
        DpadButton("D", SnesKey.DOWN, onPress, onRelease, palette.dpad.copy(alpha = alpha), Modifier.align(Alignment.BottomCenter).size(buttonSize))
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(buttonSize * 0.92f)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.center.copy(alpha = alpha * 0.85f))
        )
    }
}

@Composable
private fun DpadButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    color: Color,
    modifier: Modifier
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        background = color
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    background: Color,
    border: Color = Color.Transparent
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) background.copy(alpha = 0.9f) else background, shape)
            .border(1.dp, border, shape)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        pressed = true
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
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
