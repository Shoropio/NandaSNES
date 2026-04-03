package com.nandanes.emu

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nandanes.emu.data.rom.CompatibleRom
import com.nandanes.emu.data.rom.ImportedRom
import com.nandanes.emu.data.rom.RecentRom
import com.nandanes.emu.data.rom.RecentRomStore
import com.nandanes.emu.data.rom.RomLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RomUiState(
    val romLoaded: Boolean = false,
    val romId: String = "",
    val romLabel: String = "",
    val romPath: String = "",
    val importError: String? = null,
    val slotsRefresh: Int = 0,
    val recentRoms: List<RecentRom> = emptyList(),
    val compatibleRoms: List<CompatibleRom> = emptyList(),
    val isProcessingSaveState: Boolean = false
)

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {
    private val recentRomStore = RecentRomStore(application)
    private val romLibraryRepository = RomLibraryRepository(application, recentRomStore)

    private val _uiState = MutableStateFlow(RomUiState())
    val uiState: StateFlow<RomUiState> = _uiState.asStateFlow()

    init {
        refreshRomLists()
    }

    fun importRom(
        contentResolver: ContentResolver,
        uri: Uri,
        onSuccess: (ImportedRom) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { romLibraryRepository.importRom(contentResolver, uri) }
                .onSuccess(onSuccess)
                .onFailure { updateImportError(it.message ?: "Error al importar") }
        }
    }

    fun updateImportError(message: String?) {
        _uiState.update { it.copy(importError = message) }
    }

    fun refreshRomLists() {
        recentRomStore.pruneMissingFiles()
        _uiState.update {
            it.copy(
                recentRoms = recentRomStore.list(),
                compatibleRoms = romLibraryRepository.listCompatibleRoms()
            )
        }
    }

    fun onRomLoaded(romId: String, displayLabel: String, path: String) {
        recentRomStore.record(romId, displayLabel, path)
        _uiState.value = RomUiState(
            romLoaded = true,
            romId = romId,
            romLabel = displayLabel,
            romPath = path,
            recentRoms = recentRomStore.list(),
            compatibleRoms = romLibraryRepository.listCompatibleRoms()
        )
    }

    fun onRomClosed() {
        _uiState.value = RomUiState(
            recentRoms = recentRomStore.list(),
            compatibleRoms = romLibraryRepository.listCompatibleRoms()
        )
    }

    fun bumpSlotsRefresh() {
        _uiState.update { state -> state.copy(slotsRefresh = state.slotsRefresh + 1) }
    }

    fun setSaveStateProcessing(processing: Boolean) {
        _uiState.update { it.copy(isProcessingSaveState = processing) }
    }
}
