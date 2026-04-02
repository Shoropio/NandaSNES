package com.nandanes.emu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaveSlotsPanel(
    romId: String,
    saveManager: SaveStateManager,
    refresh: Int,
    onClose: () -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
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
                Text("Save States", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (autoAt > 0) {
                        val base = "Auto-save ${dateFmt.format(Date(autoAt))}"
                        autoReason?.let { "$base · $it" } ?: base
                    } else {
                        "Usa los slots sin tapar la partida"
                    },
                    color = Color(0xFFB7B7B7),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CompactActionButton(onClick = onClose, accent = Color(0x663A3A42)) {
                Text("Cerrar", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = autoResumeEnabled,
                onCheckedChange = { enabled ->
                    autoResumeEnabled = enabled
                    SaveSlotMetadata.setAutoResumeEnabled(ctx, romId, enabled)
                }
            )
        }

        val autoThumbFile = saveManager.autoThumbPath()
        val autoBmp = remember(refresh, romId) {
            if (autoThumbFile.exists()) BitmapFactory.decodeFile(autoThumbFile.absolutePath) else null
        }

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
                            base + (autoReason?.let { " ($it)" } ?: "")
                        }
                        autoFileExists -> "Guardado"
                        else -> "Vacio"
                    }

                    SaveSlotCard(
                        title = "Auto",
                        subtitle = autoText,
                        bitmap = autoBmp,
                        primary = { CompactActionButton(onClick = onLoadAutoSave, enabled = autoFileExists, modifier = Modifier.weight(1f)) { Text("Load", style = MaterialTheme.typography.labelSmall) } },
                        secondary = { CompactActionButton(onClick = onDeleteAutoSave, enabled = autoFileExists, modifier = Modifier.weight(1f), accent = Color(0xFF4A2727)) { Text("Del", style = MaterialTheme.typography.labelSmall) } }
                    )
                } else {
                    val slot = slotOrAuto
                    val thumbFile = saveManager.manualThumbPath(slot)
                    val stateFile = saveManager.manualSlotPath(slot)
                    val savedAt = SaveSlotMetadata.getSlotSavedAtMillis(ctx, romId, slot)
                    val bmp = remember(refresh, slot, romId) {
                        if (thumbFile.exists()) BitmapFactory.decodeFile(thumbFile.absolutePath) else null
                    }
                    val text = if (stateFile.exists() && savedAt > 0) dateFmt.format(Date(savedAt)) else "Vacio"

                    SaveSlotCard(
                        title = "Slot $slot",
                        subtitle = text,
                        bitmap = bmp,
                        primary = { CompactActionButton(onClick = { onSave(slot) }, modifier = Modifier.weight(1f)) { Text("Save", style = MaterialTheme.typography.labelSmall) } },
                        secondary = { CompactActionButton(onClick = { onLoad(slot) }, enabled = stateFile.exists(), modifier = Modifier.weight(1f), accent = Color(0xFF2B3D5C)) { Text("Load", style = MaterialTheme.typography.labelSmall) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveSlotCard(
    title: String,
    subtitle: String,
    bitmap: Bitmap?,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit
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
            Text(subtitle, color = Color.LightGray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                primary()
                secondary()
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
    Button(
        onClick = onClick,
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
