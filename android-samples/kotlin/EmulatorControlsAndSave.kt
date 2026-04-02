package com.nandanes.emu

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File

enum class SnesKey(val nativeCode: Int) {
    UP(0), DOWN(1), LEFT(2), RIGHT(3),
    A(4), B(5), X(6), Y(7), L(8), R(9),
    START(10), SELECT(11)
}

class EmulatorActivity : ComponentActivity() {
    private val bridge = NativeBridge()
    private lateinit var vibration: VibrationController
    private lateinit var saveManager: SaveStateManager
    private lateinit var autoSaveManager: AutoSaveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibration = VibrationController(this)
        saveManager = SaveStateManager(this)
        autoSaveManager = AutoSaveManager(saveManager, bridge)

        setContent {
            EmulatorOverlay(
                onPress = { key ->
                    vibration.click()
                    bridge.reportButton(key.nativeCode, true)
                },
                onRelease = { key ->
                    bridge.reportButton(key.nativeCode, false)
                },
                onSaveSlot = { slot -> bridge.saveState(saveManager.manualSlotPath(slot).absolutePath) },
                onLoadSlot = { slot -> bridge.loadState(saveManager.manualSlotPath(slot).absolutePath) }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        autoSaveManager.tryAutoSave("onPause")
    }

    override fun onStop() {
        super.onStop()
        autoSaveManager.tryAutoSave("onStop")
    }

    // Llamar desde BroadcastReceiver o monitor de batería.
    fun onBatteryCritical(levelPercent: Int) {
        if (levelPercent <= 8) {
            autoSaveManager.tryAutoSave("battery_critical")
        }
    }

    fun tryLoadLastAutoSaveOnRomStart() {
        val autoSlot = saveManager.autoSlotPath()
        if (autoSlot.exists()) {
            bridge.loadState(autoSlot.absolutePath)
        }
    }
}

class NativeBridge {
    external fun reportButton(buttonCode: Int, pressed: Boolean)
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean

    companion object {
        init {
            System.loadLibrary("nandanes")
        }
    }
}

class VibrationController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun click() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(12)
        }
    }
}

class SaveStateManager(private val context: Context) {
    private val statesDir: File = File(context.filesDir, "saves").also { it.mkdirs() }
    private val thumbsDir: File = File(context.filesDir, "thumbs").also { it.mkdirs() }

    fun manualSlotPath(slot: Int): File {
        require(slot in 1..5) { "Slot manual fuera de rango" }
        return File(statesDir, "slot_$slot.frz")
    }

    fun manualThumbPath(slot: Int): File {
        require(slot in 1..5) { "Thumbnail manual fuera de rango" }
        return File(thumbsDir, "slot_$slot.png")
    }

    fun autoSlotPath(): File = File(statesDir, "autosave.frz")
    fun autoThumbPath(): File = File(thumbsDir, "autosave.png")
}

class AutoSaveManager(
    private val saveStateManager: SaveStateManager,
    private val nativeBridge: NativeBridge
) {
    fun tryAutoSave(reason: String): Boolean {
        val ok = nativeBridge.saveState(saveStateManager.autoSlotPath().absolutePath)
        if (!ok) return false
        // Aquí puedes marcar metadata: timestamp + reason en SharedPreferences o Room.
        return true
    }
}

@Composable
fun EmulatorOverlay(
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0x22000000))) {
        // D-pad izquierda
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayButton("UP", SnesKey.UP, onPress, onRelease)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayButton("LEFT", SnesKey.LEFT, onPress, onRelease)
                OverlayButton("RIGHT", SnesKey.RIGHT, onPress, onRelease)
            }
            OverlayButton("DOWN", SnesKey.DOWN, onPress, onRelease)
        }

        // ABXY derecha
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayButton("X", SnesKey.X, onPress, onRelease)
                OverlayButton("Y", SnesKey.Y, onPress, onRelease)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayButton("A", SnesKey.A, onPress, onRelease)
                OverlayButton("B", SnesKey.B, onPress, onRelease)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverlayButton("SELECT", SnesKey.SELECT, onPress, onRelease)
            OverlayButton("START", SnesKey.START, onPress, onRelease)
        }

        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayButton("L", SnesKey.L, onPress, onRelease)
            OverlayButton("R", SnesKey.R, onPress, onRelease)
        }

        // Ejemplo mínimo de UI de slots manuales 1..5
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (slot in 1..5) {
                Button(onClick = { onSaveSlot(slot) }) { Text("S$slot") }
                Button(onClick = { onLoadSlot(slot) }) { Text("L$slot") }
            }
        }
    }
}

@Composable
private fun OverlayButton(
    label: String,
    key: SnesKey,
    onPress: (SnesKey) -> Unit,
    onRelease: (SnesKey) -> Unit
) {
    val listener = remember {
        View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    onPress(key)
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_CANCEL -> {
                    onRelease(key)
                    true
                }
                else -> false
            }
        }
    }

    AndroidTouchButton(label = label, touchListener = listener)
}

@Composable
private fun AndroidTouchButton(label: String, touchListener: View.OnTouchListener) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.widget.Button(context).apply {
                text = label
                setOnTouchListener(touchListener)
                alpha = 0.75f
            }
        },
        modifier = Modifier.size(64.dp).background(Color(0xAA303030), CircleShape)
    )
}
