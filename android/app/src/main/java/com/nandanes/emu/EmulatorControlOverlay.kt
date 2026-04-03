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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val shellTop: Color,
    val shellBottom: Color,
    val shellEdge: Color,
    val shellGlow: Color,
    val shoulder: Color,
    val dpad: Color,
    val center: Color,
    val actionNest: Color,
    val actionX: Color,
    val actionY: Color,
    val actionA: Color,
    val actionB: Color,
    val text: Color,
    val chrome: Color
)

private fun paletteForSkin(skin: ControlSkin): ControlSkinPalette = when (skin) {
    ControlSkin.CLASSIC -> ControlSkinPalette(
        shellTop = Color(0xFF4A4D58),
        shellBottom = Color(0xFF21242D),
        shellEdge = Color(0xFF868B97),
        shellGlow = Color(0xFFF1F2FF),
        shoulder = Color(0xFF494D56),
        dpad = Color(0xFF606874),
        center = Color(0xFF343946),
        actionNest = Color(0xFF20222B),
        actionX = Color(0xFF758ACD),
        actionY = Color(0xFF6D97AA),
        actionA = Color(0xFF5D9278),
        actionB = Color(0xFF9D6268),
        text = Color(0xFFF7F8FC),
        chrome = Color(0xFFCBD0DB)
    )
    ControlSkin.NEON -> ControlSkinPalette(
        shellTop = Color(0xFF23445B),
        shellBottom = Color(0xFF0D1923),
        shellEdge = Color(0xFF6FD5FF),
        shellGlow = Color(0xFFB9F8FF),
        shoulder = Color(0xFF28445B),
        dpad = Color(0xFF1A89AE),
        center = Color(0xFF173348),
        actionNest = Color(0xFF111C27),
        actionX = Color(0xFF4FC3F7),
        actionY = Color(0xFF00BCD4),
        actionA = Color(0xFF00E676),
        actionB = Color(0xFFFF5252),
        text = Color(0xFFF5FEFF),
        chrome = Color(0xFF9FE8FF)
    )
    ControlSkin.CARBON -> ControlSkinPalette(
        shellTop = Color(0xFF45484D),
        shellBottom = Color(0xFF17191D),
        shellEdge = Color(0xFF767A80),
        shellGlow = Color(0xFFE2E5EA),
        shoulder = Color(0xFF3B3E43),
        dpad = Color(0xFF4C4F55),
        center = Color(0xFF32353A),
        actionNest = Color(0xFF16181C),
        actionX = Color(0xFF70757F),
        actionY = Color(0xFF7A808A),
        actionA = Color(0xFF959B63),
        actionB = Color(0xFF9B6666),
        text = Color(0xFFF1F2F4),
        chrome = Color(0xFFC6CBD2)
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
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xCC3E5068) else Color(0x8C181B24),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text("MENU", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
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
        shape = RoundedCornerShape(18.dp),
        background = color.copy(alpha = alpha),
        chrome = Color.White.copy(alpha = alpha * 0.24f),
        textColor = Color.White
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
        border = Color(0x77FFFFFF),
        chrome = Color.White.copy(alpha = alpha * 0.16f),
        textColor = Color(0xFFF9FBFF)
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
    Box(modifier = Modifier.size(buttonSize * 2.5f)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(buttonSize * 1.72f)
                .shadow(18.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.actionNest.copy(alpha = alpha * 0.95f),
                            palette.actionNest.copy(alpha = alpha * 0.68f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = alpha * 0.1f), CircleShape)
        )
        ActionButton("X", SnesKey.X, onPress, onRelease, palette.actionX.copy(alpha = alpha), palette, Modifier.align(Alignment.TopCenter).size(buttonSize * 0.94f))
        ActionButton("Y", SnesKey.Y, onPress, onRelease, palette.actionY.copy(alpha = alpha), palette, Modifier.align(Alignment.CenterStart).size(buttonSize * 0.94f))
        ActionButton("A", SnesKey.A, onPress, onRelease, palette.actionA.copy(alpha = alpha), palette, Modifier.align(Alignment.CenterEnd).size(buttonSize * 0.94f))
        ActionButton("B", SnesKey.B, onPress, onRelease, palette.actionB.copy(alpha = alpha), palette, Modifier.align(Alignment.BottomCenter).size(buttonSize * 0.94f))
    }
}

@Composable
private fun ActionButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    color: Color,
    palette: ControlSkinPalette,
    modifier: Modifier
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = modifier,
        shape = CircleShape,
        background = color,
        border = palette.chrome.copy(alpha = 0.22f),
        chrome = palette.shellGlow.copy(alpha = 0.18f),
        textColor = palette.text
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
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(buttonSize * 2.55f)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = buttonSize * 1.08f, height = buttonSize * 2.7f)
                    .shadow(20.dp, RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.shellTop.copy(alpha = alpha),
                                palette.dpad.copy(alpha = alpha),
                                palette.shellBottom.copy(alpha = alpha)
                            )
                        )
                    )
                    .border(1.dp, palette.shellEdge.copy(alpha = alpha * 0.34f), RoundedCornerShape(20.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = buttonSize * 2.7f, height = buttonSize * 1.08f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                palette.shellTop.copy(alpha = alpha),
                                palette.dpad.copy(alpha = alpha),
                                palette.shellBottom.copy(alpha = alpha)
                            )
                        )
                    )
                    .border(1.dp, palette.shellEdge.copy(alpha = alpha * 0.34f), RoundedCornerShape(20.dp))
            )
        }
        DpadButton("^", SnesKey.UP, onPress, onRelease, palette.dpad.copy(alpha = alpha), palette, Modifier.align(Alignment.TopCenter).size(buttonSize * 0.98f))
        DpadButton("<", SnesKey.LEFT, onPress, onRelease, palette.dpad.copy(alpha = alpha), palette, Modifier.align(Alignment.CenterStart).size(buttonSize * 0.98f))
        DpadButton(">", SnesKey.RIGHT, onPress, onRelease, palette.dpad.copy(alpha = alpha), palette, Modifier.align(Alignment.CenterEnd).size(buttonSize * 0.98f))
        DpadButton("v", SnesKey.DOWN, onPress, onRelease, palette.dpad.copy(alpha = alpha), palette, Modifier.align(Alignment.BottomCenter).size(buttonSize * 0.98f))
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(buttonSize * 0.84f)
                .shadow(12.dp, RoundedCornerShape(14.dp), clip = false)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.center.copy(alpha = alpha),
                            palette.shellBottom.copy(alpha = alpha * 0.95f)
                        )
                    )
                )
                .border(1.dp, palette.shellGlow.copy(alpha = alpha * 0.16f), RoundedCornerShape(14.dp))
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
    palette: ControlSkinPalette,
    modifier: Modifier
) {
    TouchButton(
        label = label,
        key = key,
        onPress = onPress,
        onRelease = onRelease,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        background = color,
        border = palette.chrome.copy(alpha = 0.18f),
        chrome = palette.shellGlow.copy(alpha = 0.14f),
        textColor = palette.text
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
    shape: Shape,
    background: Color,
    border: Color = Color.Transparent,
    chrome: Color = Color.White.copy(alpha = 0.14f),
    textColor: Color = Color.White
) {
    var pressed by remember { mutableStateOf(false) }
    val outerBorder = if (border == Color.Transparent) background.highlight(0.14f) else border
    val surfaceBrush = Brush.verticalGradient(
        listOf(
            background.highlight(0.26f),
            background,
            background.shade(0.28f)
        )
    )
    Box(
        modifier = modifier
            .shadow(if (pressed) 8.dp else 16.dp, shape, clip = false)
            .clip(shape)
            .background(surfaceBrush, shape)
            .border(1.dp, outerBorder, shape)
            .background(Color.Transparent)
            .border(1.dp, Color.Black.copy(alpha = 0.16f), shape)
            .clip(shape)
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(2.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            chrome,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f)
                        )
                    )
                )
        )
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun Color.highlight(amount: Float): Color = lerp(this, Color.White, amount.coerceIn(0f, 1f))

private fun Color.shade(amount: Float): Color = lerp(this, Color.Black, amount.coerceIn(0f, 1f))
