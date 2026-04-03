package com.nandanes.emu

import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nandanes.emu.data.rom.CompatibleRom
import com.nandanes.emu.data.rom.RecentRom
import com.nandanes.emu.data.rom.RomLibraryRepository
import com.nandanes.emu.data.save.SaveSlotMetadata
import com.nandanes.emu.data.settings.ControlOverlaySettings
import com.nandanes.emu.data.settings.ControlOverlaySettingsStore
import com.nandanes.emu.data.settings.DebugSettings
import com.nandanes.emu.data.settings.DebugSettingsStore
import com.nandanes.emu.data.settings.EmulatorDebug
import com.nandanes.emu.domain.usecase.LoadRomUseCase
import com.nandanes.emu.domain.usecase.LoadStateUseCase
import com.nandanes.emu.domain.usecase.SaveStateUseCase
import com.nandanes.emu.runtime.AudioPlayer
import com.nandanes.emu.runtime.AutoSaveManager
import com.nandanes.emu.runtime.EmulatorVideoSurface
import com.nandanes.emu.runtime.NativeEmulationRuntime
import com.nandanes.emu.runtime.NativeBridge
import com.nandanes.emu.runtime.NativeSaveStateRuntime
import com.nandanes.emu.runtime.SaveStateManager
import com.nandanes.emu.runtime.VibrationController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmulatorActivity : ComponentActivity() {
    private val viewModel: EmulatorViewModel by viewModels()
    private val bridge = NativeBridge()
    private lateinit var vibration: VibrationController
    private lateinit var saveManager: SaveStateManager
    private lateinit var autoSaveManager: AutoSaveManager
    private lateinit var overlaySettingsStore: ControlOverlaySettingsStore
    private lateinit var debugSettingsStore: DebugSettingsStore
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var loadRomUseCase: LoadRomUseCase
    private lateinit var saveStateUseCase: SaveStateUseCase
    private lateinit var loadStateUseCase: LoadStateUseCase
    private val overlaySettings = mutableStateOf(ControlOverlaySettings())
    private val debugSettings = mutableStateOf(DebugSettings())
    private val debugLogText = mutableStateOf("Sin eventos todavia.")
    private val nativeDebugLogText = mutableStateOf("Sin eventos nativos todavia.")
    private val nativeSnapshotText = mutableStateOf("Abre una ROM para capturar snapshot nativo.")

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
        val emulationRuntime = NativeEmulationRuntime(bridge)
        val saveStateRuntime = NativeSaveStateRuntime(bridge, saveManager)
        loadRomUseCase = LoadRomUseCase(emulationRuntime, saveStateRuntime)
        saveStateUseCase = SaveStateUseCase(saveStateRuntime)
        loadStateUseCase = LoadStateUseCase(emulationRuntime, saveStateRuntime)
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

        val romPathExtra = intent?.getStringExtra("romPath")
        if (!romPathExtra.isNullOrBlank()) {
            loadRomFile(File(romPathExtra), File(romPathExtra).name)
        }

        setContent {
            val state by viewModel.uiState.collectAsState()
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
                            compatibleRoms = state.compatibleRoms,
                            onPickRom = { pickRomLauncher.launch(arrayOf("*/*")) },
                            onOpenRecent = { recent -> loadRomFile(File(recent.path), recent.label) },
                            onOpenCompatible = { compatible -> loadRomFile(File(compatible.path), compatible.label) }
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
                                        isBusy = state.isProcessingSaveState,
                                        onClose = closeSavePanel,
                                        onSave = { slot -> saveManualSlot(state.romId, slot) },
                                        onLoad = { slot -> loadManualSlot(state.romId, slot) },
                                        onLoadAutoSave = { loadAutoSave(state.romId) },
                                        onDeleteAutoSave = {
                                            autoSaveManager.deleteAutoSave(state.romId)
                                            viewModel.bumpSlotsRefresh()
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
        val currentState = viewModel.uiState.value
        EmulatorDebug.log("APP", "onPause romLoaded=${currentState.romLoaded}")
        audioPlayer.stop()
        if (currentState.romLoaded && autoSaveManager.tryAutoSave("onPause", currentState.romId)) {
            viewModel.bumpSlotsRefresh()
        }
        refreshDebugLog()
    }

    override fun onStop() {
        super.onStop()
        val currentState = viewModel.uiState.value
        EmulatorDebug.log("APP", "onStop romLoaded=${currentState.romLoaded}")
        audioPlayer.stop()
        if (currentState.romLoaded && autoSaveManager.tryAutoSave("onStop", currentState.romId)) {
            viewModel.bumpSlotsRefresh()
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
    }

    private fun importRomFromUri(uri: Uri) {
        EmulatorDebug.log("ROM", "Import requested uri=$uri")
        viewModel.importRom(contentResolver, uri) { importedRom ->
            lifecycleScope.launch {
                EmulatorDebug.log(
                    "ROM",
                    "Copy import label=${importedRom.displayLabel} dest=${importedRom.file.absolutePath}"
                )
                loadRomFile(importedRom.file, importedRom.displayLabel)
            }
        }
    }

    private fun loadRomFile(file: File, displayLabel: String) {
        EmulatorDebug.log("ROM", "Load request file=${file.absolutePath} label=$displayLabel")
        if (!file.exists()) {
            viewModel.updateImportError("La ROM no existe")
            viewModel.refreshRomLists()
            return
        }
        if (!RomLibraryRepository.isSupportedRomName(file.name)) {
            viewModel.updateImportError("Formato no soportado para la ROM")
            return
        }

        val romId = RomLibraryRepository.romIdFromPath(file)
        audioPlayer.resetForNextRom()

        val loadResult = loadRomUseCase(file)
        val loaded = loadResult.success
        EmulatorDebug.log("ROM", "loadRom result=$loaded snapshot=${bridge.getDebugSnapshot()}")
        if (!loaded) {
            viewModel.updateImportError("No se pudo cargar la ROM")
            refreshDebugLog()
            return
        }

        val resolvedRomId = loadResult.romId ?: romId
        bridge.startEmulation()
        audioPlayer.start()
        tryLoadLastAutoSaveOnRomStart(resolvedRomId)
        viewModel.onRomLoaded(resolvedRomId, displayLabel, file.absolutePath)
        refreshDebugLog()
    }

    private fun tryLoadLastAutoSaveOnRomStart(romId: String) {
        if (!SaveSlotMetadata.getAutoResumeEnabled(this, romId)) return
        saveManager.setActiveRomId(romId)
        val autoSlot = saveManager.autoSlotPath()
        if (autoSlot.exists()) {
            EmulatorDebug.log("SAVE", "Auto resume from ${autoSlot.absolutePath}")
            val restored = bridge.loadState(autoSlot.absolutePath)
            EmulatorDebug.log("SAVE", "Auto resume result=$restored snapshot=${bridge.getDebugSnapshot()}")
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
        val state = viewModel.uiState.value
        EmulatorDebug.log("APP", "Return to home requested romLoaded=${state.romLoaded}")
        audioPlayer.stop()
        if (state.romLoaded) {
            autoSaveManager.tryAutoSave("returnHome", state.romId)
        }
        bridge.stopEmulation()
        bridge.unloadRom()
        viewModel.refreshRomLists()
        viewModel.onRomClosed()
        refreshDebugLog()
    }

    private fun saveManualSlot(romId: String, slot: Int) {
        lifecycleScope.launch {
            viewModel.setSaveStateProcessing(true)
            val saved = withContext(Dispatchers.IO) {
                val saveSucceeded = saveStateUseCase(romId, slot)
                if (saveSucceeded) {
                    saveStateUseCase.writeThumbnail(romId, slot)
                    SaveSlotMetadata.setSlotSavedAtMillis(
                        this@EmulatorActivity,
                        romId,
                        slot,
                        System.currentTimeMillis()
                    )
                }
                saveSucceeded
            }
            viewModel.setSaveStateProcessing(false)
            if (saved) {
                viewModel.bumpSlotsRefresh()
            } else {
                viewModel.updateImportError("No se pudo guardar el estado")
            }
        }
    }

    private fun loadManualSlot(romId: String, slot: Int) {
        lifecycleScope.launch {
            viewModel.setSaveStateProcessing(true)
            val loaded = withContext(Dispatchers.IO) {
                loadStateUseCase.manualSlot(romId, slot)
            }
            viewModel.setSaveStateProcessing(false)
            if (!loaded) {
                viewModel.updateImportError("No se pudo cargar el estado")
            }
        }
    }

    private fun loadAutoSave(romId: String) {
        lifecycleScope.launch {
            viewModel.setSaveStateProcessing(true)
            val loaded = withContext(Dispatchers.IO) {
                loadStateUseCase.autoSave(romId)
            }
            viewModel.setSaveStateProcessing(false)
            if (!loaded) {
                viewModel.updateImportError("No se pudo cargar el auto-guardado")
            }
        }
    }
}

@Composable
private fun RomPickerScreen(
    error: String?,
    recentRoms: List<RecentRom>,
    compatibleRoms: List<CompatibleRom>,
    onPickRom: () -> Unit,
    onOpenRecent: (RecentRom) -> Unit,
    onOpenCompatible: (CompatibleRom) -> Unit
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
            Box(
                modifier = Modifier
                    .border(2.dp, Color(0xFF5D4FA3), RoundedCornerShape(22.dp))
                    .background(Color(0xFF13161C), RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("NN", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("NandaSNES", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Emulador de SNES para Android con overlay tactil y guardado rapido.",
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

            items(recentRoms, key = { "recent:${recentKey(it)}" }) { recent ->
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

        if (compatibleRoms.isNotEmpty()) {
            item {
                Text(
                    "Juegos compatibles en el dispositivo",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(compatibleRoms, key = { "compatible:${compatibleKey(it)}" }) { compatible ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14161A))
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(compatible.label, color = Color.White, maxLines = 1)
                            Text(
                                "Compatible: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(compatible.lastModified))}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { onOpenCompatible(compatible) }) { Text("Abrir") }
                    }
                }
            }
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
    }
}

private fun recentKey(recent: RecentRom): String = "${recent.romId}:${recent.path}"

private fun compatibleKey(compatible: CompatibleRom): String = compatible.path
