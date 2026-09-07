package com.nandanes.emu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nandanes.emu.data.save.SaveSlotMetadata
import com.nandanes.emu.runtime.SaveStateManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaveSlotsPanel(
    romId: String,
    saveManager: SaveStateManager,
    refresh: Int,
    isBusy: Boolean,
    onClose: () -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onLoadAutoSave: () -> Unit,
    onDeleteAutoSave: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val autoAt = SaveSlotMetadata.getAutoSaveAtMillis(ctx, romId)
    val autoReason = SaveSlotMetadata.getAutoSaveReason(ctx, romId)
    val autoFileExists = remember(refresh, romId) { saveManager.autoSlotPath().exists() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xD9171717))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(12.dp)
    ) {
        var autoResumeEnabled by remember(romId) {
            mutableStateOf(SaveSlotMetadata.getAutoResumeEnabled(ctx, romId))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Estados guardados",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (autoAt > 0) {
                        val base = "Auto-guardado ${dateFmt.format(Date(autoAt))}"
                        autoReason?.let { "$base - ${humanizeAutoSaveReason(it)}" } ?: base
                    } else {
                        "Usa los slots sin tapar la partida"
                    },
                    color = Color(0xFFB7B7B7),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SquareActionButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp),
                accent = Color(0x663A3A42),
                icon = Icons.Outlined.Close,
                contentDescription = "Cerrar panel"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = autoResumeEnabled,
                enabled = !isBusy,
                onCheckedChange = { enabled ->
                    autoResumeEnabled = enabled
                    SaveSlotMetadata.setAutoResumeEnabled(ctx, romId, enabled)
                }
            )
        }

        val autoThumbFile = saveManager.autoThumbPath()
        val autoBmp = rememberThumbnailBitmap(autoThumbFile.absolutePath, refresh, romId)

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listOf(0) + (1..5).toList(), key = { it }) { slotOrAuto ->
                if (slotOrAuto == 0) {
                    val autoText = when {
                        autoAt > 0 -> {
                            val base = dateFmt.format(Date(autoAt))
                            base + (autoReason?.let { " (${humanizeAutoSaveReason(it)})" } ?: "")
                        }
                        autoFileExists -> "Guardado"
                        else -> "Vacio"
                    }

                    SaveSlotCard(
                        title = "Auto",
                        subtitle = autoText,
                        bitmap = autoBmp,
                        actions = {
                            SquareActionButton(
                                onClick = onLoadAutoSave,
                                accent = Color(0xFF2B3D5C),
                                enabled = autoFileExists && !isBusy,
                                icon = Icons.Outlined.Download,
                                contentDescription = "Cargar auto-guardado",
                                modifier = Modifier.weight(1f)
                            )
                            SquareActionButton(
                                onClick = onDeleteAutoSave,
                                accent = Color(0xFF4A2727),
                                enabled = autoFileExists && !isBusy,
                                icon = Icons.Outlined.Delete,
                                contentDescription = "Borrar auto-guardado",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    )
                } else {
                    val slot = slotOrAuto
                    val thumbFile = saveManager.manualThumbPath(slot)
                    val stateFile = saveManager.manualSlotPath(slot)
                    val savedAt = SaveSlotMetadata.getSlotSavedAtMillis(ctx, romId, slot)
                    val bmp = rememberThumbnailBitmap(thumbFile.absolutePath, refresh, romId, slot)
                    val text = if (stateFile.exists() && savedAt > 0) {
                        dateFmt.format(Date(savedAt))
                    } else {
                        "Vacio"
                    }

                    SaveSlotCard(
                        title = "Slot $slot",
                        subtitle = text,
                        bitmap = bmp,
                        actions = {
                            SquareActionButton(
                                onClick = { onSave(slot) },
                                enabled = !isBusy,
                                icon = Icons.Outlined.Save,
                                contentDescription = "Guardar slot $slot",
                                modifier = Modifier.weight(1f)
                            )
                            SquareActionButton(
                                onClick = { onLoad(slot) },
                                enabled = stateFile.exists() && !isBusy,
                                accent = Color(0xFF2B3D5C),
                                icon = Icons.Outlined.Download,
                                contentDescription = "Cargar slot $slot",
                                modifier = Modifier.weight(1f)
                            )
                            SquareActionButton(
                                onClick = { onDelete(slot) },
                                enabled = stateFile.exists() && !isBusy,
                                accent = Color(0xFF4A2727),
                                icon = Icons.Outlined.Delete,
                                contentDescription = "Borrar slot $slot",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberThumbnailBitmap(
    path: String,
    vararg keys: Any?
): Bitmap? {
    val bitmap = remember(*keys) {
        val file = File(path)
        if (!file.exists()) {
            null
        } else {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        }
    }

    DisposableEffect(bitmap) {
        onDispose {
            bitmap?.recycle()
        }
    }

    return bitmap
}

@Composable
private fun SaveSlotCard(
    title: String,
    subtitle: String,
    bitmap: Bitmap?,
    actions: @Composable RowScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202225)),
        modifier = Modifier.width(132.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            ThumbFrame(bitmap)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun ThumbFrame(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .size(116.dp, 70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("-", color = Color.Gray)
        }
    }
}

@Composable
fun CompactActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Color(0xFF5D4FA3),
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = accent.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) { content() }
}

@Composable
private fun SquareActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Color(0xFF5D4FA3)
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = accent.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}

private fun humanizeAutoSaveReason(reason: String): String = when (reason) {
    "returnHome" -> "volver al inicio"
    "onPause" -> "pausa"
    "onStop" -> "segundo plano"
    else -> reason
}
