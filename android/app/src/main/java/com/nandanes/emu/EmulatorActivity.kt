package com.nandanes.emu

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private data class RomUiState(
    val romLoaded: Boolean = false,
    val romId: String = "",
    val romLabel: String = "",
    val romPath: String = "",
    val importError: String? = null,
    val slotsRefresh: Int = 0,
    val recentRoms: List<RecentRom> = emptyList()
)

class EmulatorActivity : ComponentActivity() {
    private val bridge = NativeBridge()
    private lateinit var vibration: VibrationController
    private lateinit var saveManager: SaveStateManager
    private lateinit var autoSaveManager: AutoSaveManager
    private lateinit var recentRomStore: RecentRomStore
    private lateinit var overlaySettingsStore: ControlOverlaySettingsStore
    private lateinit var debugSettingsStore: DebugSettingsStore
    private lateinit var audioPlayer: AudioPlayer
    private val uiState = mutableStateOf(RomUiState())
    private val overlaySettings = mutableStateOf(ControlOverlaySettings())
    private val debugSettings = mutableStateOf(DebugSettings())
    private val debugLogText = mutableStateOf("Sin eventos todavia.")
    private val nativeDebugLogText = mutableStateOf("Sin eventos nativos todavia.")
    private val nativeSnapshotText = mutableStateOf("Abre una ROM para capturar snapshot nativo.")
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val pickRomLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importRomFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = ""
        actionBar?.hide()
        enterImmersiveMode()
        vibration = VibrationController(this)
        saveManager = SaveStateManager(this)
        autoSaveManager = AutoSaveManager(this, saveManager, bridge)
        recentRomStore = RecentRomStore(this)
        overlaySettingsStore = ControlOverlaySettingsStore(this)
        debugSettingsStore = DebugSettingsStore(this)
        overlaySettings.value = overlaySettingsStore.load()
        debugSettings.value = debugSettingsStore.load()
        EmulatorDebug.initialize(this, debugSettings.value)
        audioPlayer = AudioPlayer(this, bridge)
        bridge.setDebugLoggingEnabled(debugSettings.value.enabled)
        EmulatorDebug.log("APP", "onCreate")
        bridge.initializeInputMapping()
        refreshDebugLog()
        refreshRecentRoms()

        val romPathExtra = intent?.getStringExtra("romPath")
        if (!romPathExtra.isNullOrBlank()) {
            loadRomFile(File(romPathExtra), File(romPathExtra).name)
        }

        setContent {
            val state by uiState
            val controlSettings by overlaySettings
            val debugMode by debugSettings
            val debugLog by debugLogText
            val nativeDebugLog by nativeDebugLogText
            val nativeSnapshot by nativeSnapshotText
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
                    if (!state.romLoaded) {
                        RomPickerScreen(
                            error = state.importError,
                            recentRoms = state.recentRoms,
                            debugSettings = debugMode,
                            debugLog = debugLog,
                            nativeDebugLog = nativeDebugLog,
                            onPickRom = { pickRomLauncher.launch(arrayOf("*/*")) },
                            onOpenRecent = { recent -> loadRomFile(File(recent.path), recent.label) },
                            onToggleDebug = { enabled -> updateDebugMode(enabled) },
                            onRefreshDebug = { refreshDebugLog() },
                            onClearDebug = {
                                EmulatorDebug.clear()
                                bridge.clearNativeDebugLog()
                                refreshDebugLog()
                            },
                            onCaptureSnapshot = {
                                captureSnapshot()
                                refreshDebugLog()
                            }
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            EmulatorVideoSurface(bridge = bridge, modifier = Modifier.fillMaxSize())
                            EmulatorOverlay(
                                romLabel = state.romLabel,
                                onPress = { key ->
                                    vibration.click(
                                        enabled = controlSettings.hapticsEnabled,
                                        strength = controlSettings.hapticsStrength
                                    )
                                    bridge.reportButton(key.nativeCode, true)
                                },
                                onRelease = { key -> bridge.reportButton(key.nativeCode, false) },
                                onGoHome = { returnToHomeScreen() },
                                settings = controlSettings,
                                onSettingsChange = {
                                    overlaySettings.value = it
                                    overlaySettingsStore.save(it)
                                },
                                debugEnabled = debugMode.enabled,
                                onToggleDebug = { enabled -> updateDebugMode(enabled) },
                                debugContent = {
                                    DebugPanelContent(
                                        enabled = debugMode.enabled,
                                        logText = debugLog,
                                        nativeLogText = nativeDebugLog,
                                        nativeSnapshot = nativeSnapshot,
                                        onToggleEnabled = { enabled -> updateDebugMode(enabled) },
                                        onRefresh = { refreshDebugLog() },
                                        onClear = {
                                            EmulatorDebug.clear()
                                            bridge.clearNativeDebugLog()
                                            refreshDebugLog()
                                        },
                                        onCaptureSnapshot = {
                                            captureSnapshot()
                                            refreshDebugLog()
                                        }
                                    )
                                },
                                topContent = { closeSavePanel ->
                                    SaveSlotsPanel(
                                        romId = state.romId,
                                        saveManager = saveManager,
                                        refresh = state.slotsRefresh,
                                        onClose = closeSavePanel,
                                        onSave = { slot ->
                                            saveManager.setActiveRomId(state.romId)
                                            val saved = saveManager.saveStateAtomically(
                                                bridge,
                                                saveManager.manualSlotPath(slot)
                                            )
                                            if (saved) {
                                                saveManager.writeCurrentFrameThumbnail(
                                                    bridge,
                                                    saveManager.manualThumbPath(slot)
                                                )
                                                SaveSlotMetadata.setSlotSavedAtMillis(
                                                    this@EmulatorActivity,
                                                    state.romId,
                                                    slot,
                                                    System.currentTimeMillis()
                                                )
                                                bumpSlotsRefresh()
                                            }
                                        },
                                        onLoad = { slot ->
                                            saveManager.setActiveRomId(state.romId)
                                            bridge.loadState(saveManager.manualSlotPath(slot).absolutePath)
                                        },
                                        onLoadAutoSave = {
                                            saveManager.setActiveRomId(state.romId)
                                            bridge.loadState(saveManager.autoSlotPath().absolutePath)
                                        },
                                        onDeleteAutoSave = {
                                            autoSaveManager.deleteAutoSave(state.romId)
                                            bumpSlotsRefresh()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        EmulatorDebug.log("APP", "onResume romLoaded=${bridge.isRomLoaded()}")
        if (bridge.isRomLoaded()) {
            bridge.startEmulation()
            audioPlayer.start()
        }
    }

    override fun onPause() {
        super.onPause()
        EmulatorDebug.log("APP", "onPause romLoaded=${uiState.value.romLoaded}")
        audioPlayer.stop()
        if (uiState.value.romLoaded && autoSaveManager.tryAutoSave("onPause", uiState.value.romId)) {
            bumpSlotsRefresh()
        }
        refreshDebugLog()
    }

    override fun onStop() {
        super.onStop()
        EmulatorDebug.log("APP", "onStop romLoaded=${uiState.value.romLoaded}")
        audioPlayer.stop()
        if (uiState.value.romLoaded && autoSaveManager.tryAutoSave("onStop", uiState.value.romId)) {
            bumpSlotsRefresh()
        }
        bridge.stopEmulation()
        refreshDebugLog()
    }

    override fun onDestroy() {
        super.onDestroy()
        EmulatorDebug.log("APP", "onDestroy")
        audioPlayer.release()
        bridge.stopEmulation()
        bridge.unloadRom()
        ioExecutor.shutdownNow()
    }

    private fun refreshRecentRoms() {
        recentRomStore.pruneMissingFiles()
        uiState.value = uiState.value.copy(recentRoms = recentRomStore.list())
    }

    private fun bumpSlotsRefresh() {
        val state = uiState.value
        uiState.value = state.copy(slotsRefresh = state.slotsRefresh + 1)
    }

    private fun importRomFromUri(uri: Uri) {
        EmulatorDebug.log("ROM", "Import requested uri=$uri")
        ioExecutor.execute {
            try {
                val label = queryDisplayName(uri) ?: "rom.sfc"
                if (!isSupportedRomName(label)) {
                    EmulatorDebug.log("ROM", "Unsupported import label=$label")
                    runOnUiThread {
                        uiState.value = uiState.value.copy(importError = "Formato no soportado. Usa .sfc, .smc o .fig")
                    }
                    return@execute
                }

                val romId = romIdFromUri(uri)
                val extension = label.substringAfterLast('.', "sfc")
                val destDir = File(filesDir, "rom_imports").also { it.mkdirs() }
                val dest = File(destDir, "$romId.$extension")
                EmulatorDebug.log("ROM", "Copy import label=$label dest=${dest.absolutePath}")

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: run {
                    runOnUiThread {
                        uiState.value = uiState.value.copy(importError = "No se pudo abrir el archivo")
                    }
                    return@execute
                }

                runOnUiThread { loadRomFile(dest, label) }
            } catch (e: Exception) {
                EmulatorDebug.logAlways("ROM", "Import error ${e.message ?: e.javaClass.simpleName}")
                runOnUiThread {
                    uiState.value = uiState.value.copy(importError = e.message ?: "Error al importar")
                }
            }
        }
    }

    private fun loadRomFile(file: File, displayLabel: String) {
        EmulatorDebug.log("ROM", "Load request file=${file.absolutePath} label=$displayLabel")
        if (!file.exists()) {
            uiState.value = uiState.value.copy(importError = "La ROM no existe")
            refreshRecentRoms()
            return
        }
        if (!isSupportedRomName(file.name)) {
            uiState.value = uiState.value.copy(importError = "Formato no soportado para la ROM")
            return
        }

        val romId = romIdFromPath(file)
        saveManager.setActiveRomId(romId)
        audioPlayer.resetForNextRom()

        val loaded = bridge.loadRom(file.absolutePath)
        EmulatorDebug.log("ROM", "loadRom result=$loaded snapshot=${bridge.getDebugSnapshot()}")
        if (!loaded) {
            uiState.value = uiState.value.copy(importError = "No se pudo cargar la ROM")
            refreshDebugLog()
            return
        }

        bridge.startEmulation()
        audioPlayer.start()
        tryLoadLastAutoSaveOnRomStart(romId)
        recentRomStore.record(romId, displayLabel, file.absolutePath)
        refreshRecentRoms()
        uiState.value = RomUiState(
            romLoaded = true,
            romId = romId,
            romLabel = displayLabel,
            romPath = file.absolutePath,
            importError = null,
            recentRoms = recentRomStore.list()
        )
        refreshDebugLog()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            return it.getString(idx)
        }
    }

    private fun tryLoadLastAutoSaveOnRomStart(romId: String) {
        if (!SaveSlotMetadata.getAutoResumeEnabled(this, romId)) return
        saveManager.setActiveRomId(romId)
        val autoSlot = saveManager.autoSlotPath()
        if (autoSlot.exists()) {
            EmulatorDebug.log("SAVE", "Auto resume from ${autoSlot.absolutePath}")
            bridge.loadState(autoSlot.absolutePath)
        }
    }

    private fun updateDebugMode(enabled: Boolean) {
        val next = DebugSettings(enabled = enabled)
        debugSettings.value = next
        debugSettingsStore.save(next)
        EmulatorDebug.setEnabled(enabled)
        bridge.setDebugLoggingEnabled(enabled)
        if (!enabled) {
            bridge.clearNativeDebugLog()
        }
        EmulatorDebug.logAlways("APP", "Debug setting changed enabled=$enabled")
        refreshDebugLog()
    }

    private fun captureSnapshot() {
        val snapshot = bridge.getDebugSnapshot()
        nativeSnapshotText.value = snapshot
        EmulatorDebug.logAlways("SNAPSHOT", snapshot)
    }

    private fun refreshDebugLog() {
        debugLogText.value = EmulatorDebug.readRecent()
        nativeDebugLogText.value = bridge.getNativeDebugLog()
        nativeSnapshotText.value = if (debugSettings.value.enabled && bridge.isRomLoaded()) {
            bridge.getDebugSnapshot()
        } else {
            "Abre una ROM para capturar snapshot nativo."
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun returnToHomeScreen() {
        val state = uiState.value
        EmulatorDebug.log("APP", "Return to home requested romLoaded=${state.romLoaded}")
        audioPlayer.stop()
        if (state.romLoaded) {
            autoSaveManager.tryAutoSave("returnHome", state.romId)
        }
        bridge.stopEmulation()
        bridge.unloadRom()
        refreshRecentRoms()
        uiState.value = RomUiState(
            romLoaded = false,
            recentRoms = recentRomStore.list()
        )
        refreshDebugLog()
    }
}

private fun isSupportedRomName(name: String): Boolean {
    val lower = name.lowercase(Locale.US)
    return lower.endsWith(".sfc") || lower.endsWith(".smc") || lower.endsWith(".fig")
}

private fun romIdFromUri(uri: Uri): String {
    val digest = MessageDigest.getInstance("MD5").digest(uri.toString().toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun romIdFromPath(file: File): String {
    val raw = "${file.absolutePath}_${file.length()}_${file.lastModified()}"
    val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

@Composable
private fun RomPickerScreen(
    error: String?,
    recentRoms: List<RecentRom>,
    debugSettings: DebugSettings,
    debugLog: String,
    nativeDebugLog: String,
    onPickRom: () -> Unit,
    onOpenRecent: (RecentRom) -> Unit,
    onToggleDebug: (Boolean) -> Unit,
    onRefreshDebug: () -> Unit,
    onClearDebug: () -> Unit,
    onCaptureSnapshot: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text("NandaNes", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Emulador SNES para Android con controles tactiles, auto-save y personalizacion del overlay.",
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onPickRom) { Text("Elegir ROM") }
            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF7A7A))
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            DebugPanelCard(
                enabled = debugSettings.enabled,
                logText = debugLog,
                nativeLogText = nativeDebugLog,
                nativeSnapshot = "Abre una ROM para capturar snapshot nativo.",
                onToggleEnabled = onToggleDebug,
                onRefresh = onRefreshDebug,
                onClear = onClearDebug,
                onCaptureSnapshot = onCaptureSnapshot
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12151B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Acerca de", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    AboutPanelContent()
                }
            }
        }

        if (recentRoms.isNotEmpty()) {
            item {
                Text(
                    "ROMs recientes",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(recentRoms, key = { it.path }) { recent ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(recent.label, color = Color.White, maxLines = 1)
                            Text(
                                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(recent.lastPlayedAt)),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { onOpenRecent(recent) }) { Text("Abrir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugPanelCard(
    enabled: Boolean,
    logText: String,
    nativeLogText: String,
    nativeSnapshot: String,
    onToggleEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onCaptureSnapshot: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15171C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modo Debug", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Guarda eventos Kotlin/JNI y ayuda a ubicar en que paso falla la emulacion.", color = Color.LightGray)
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refrescar") }
                Button(onClick = onCaptureSnapshot, modifier = Modifier.weight(1f)) { Text("Snapshot") }
                Button(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Borrar") }
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
}
