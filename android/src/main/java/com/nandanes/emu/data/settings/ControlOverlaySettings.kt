package com.nandanes.emu.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class ControlOverlaySettings(
    val sizeScale: Float = 0.92f,
    val opacity: Float = 0.66f,
    val dpadOpacity: Float = 0.74f,
    val actionOpacity: Float = 0.8f,
    val centerOpacity: Float = 0.7f,
    val shoulderOpacity: Float = 0.68f,
    val dpadOffsetX: Float = 0f,
    val dpadOffsetY: Float = 0f,
    val actionOffsetX: Float = 0f,
    val actionOffsetY: Float = 0f,
    val centerOffsetY: Float = 0f,
    val shoulderOffsetY: Float = 0f,
    val hapticsEnabled: Boolean = true,
    val hapticsStrength: Float = 0.65f,
    val skin: ControlSkin = ControlSkin.CLASSIC
)

enum class ControlSkin(val label: String) {
    CLASSIC("Classic"),
    NEON("Neon"),
    CARBON("Carbon")
}

class ControlOverlaySettingsStore(context: Context) {
    private val dataStore = settingsDataStore(context, SettingsStoreNames.CONTROL_OVERLAY)

    fun load(): ControlOverlaySettings {
        val defaults = ControlOverlaySettings()
        val prefs = runBlocking { dataStore.data.first() }
        val storedVersion = prefs[KEY_LAYOUT_VERSION] ?: 0
        if (storedVersion != LAYOUT_VERSION) {
            save(defaults)
            return defaults
        }

        return ControlOverlaySettings(
            sizeScale = prefs[KEY_SIZE_SCALE] ?: defaults.sizeScale,
            opacity = prefs[KEY_OPACITY] ?: defaults.opacity,
            dpadOpacity = prefs[KEY_DPAD_OPACITY] ?: defaults.dpadOpacity,
            actionOpacity = prefs[KEY_ACTION_OPACITY] ?: defaults.actionOpacity,
            centerOpacity = prefs[KEY_CENTER_OPACITY] ?: defaults.centerOpacity,
            shoulderOpacity = prefs[KEY_SHOULDER_OPACITY] ?: defaults.shoulderOpacity,
            dpadOffsetX = prefs[KEY_DPAD_X] ?: defaults.dpadOffsetX,
            dpadOffsetY = prefs[KEY_DPAD_Y] ?: defaults.dpadOffsetY,
            actionOffsetX = prefs[KEY_ACTION_X] ?: defaults.actionOffsetX,
            actionOffsetY = prefs[KEY_ACTION_Y] ?: defaults.actionOffsetY,
            centerOffsetY = prefs[KEY_CENTER_Y] ?: defaults.centerOffsetY,
            shoulderOffsetY = prefs[KEY_SHOULDER_Y] ?: defaults.shoulderOffsetY,
            hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
            hapticsStrength = prefs[KEY_HAPTICS_STRENGTH] ?: defaults.hapticsStrength,
            skin = prefs[KEY_SKIN]
                ?.let { stored -> ControlSkin.entries.firstOrNull { it.name == stored } }
                ?: defaults.skin
        )
    }

    fun save(settings: ControlOverlaySettings) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_LAYOUT_VERSION] = LAYOUT_VERSION
                prefs[KEY_SIZE_SCALE] = settings.sizeScale
                prefs[KEY_OPACITY] = settings.opacity
                prefs[KEY_DPAD_OPACITY] = settings.dpadOpacity
                prefs[KEY_ACTION_OPACITY] = settings.actionOpacity
                prefs[KEY_CENTER_OPACITY] = settings.centerOpacity
                prefs[KEY_SHOULDER_OPACITY] = settings.shoulderOpacity
                prefs[KEY_DPAD_X] = settings.dpadOffsetX
                prefs[KEY_DPAD_Y] = settings.dpadOffsetY
                prefs[KEY_ACTION_X] = settings.actionOffsetX
                prefs[KEY_ACTION_Y] = settings.actionOffsetY
                prefs[KEY_CENTER_Y] = settings.centerOffsetY
                prefs[KEY_SHOULDER_Y] = settings.shoulderOffsetY
                prefs[KEY_HAPTICS_ENABLED] = settings.hapticsEnabled
                prefs[KEY_HAPTICS_STRENGTH] = settings.hapticsStrength
                prefs[KEY_SKIN] = settings.skin.name
            }
        }
    }

    companion object {
        private const val LAYOUT_VERSION = 4
        private val KEY_LAYOUT_VERSION = intPreferencesKey("layout_version")
        private val KEY_SIZE_SCALE = floatPreferencesKey("size_scale")
        private val KEY_OPACITY = floatPreferencesKey("opacity")
        private val KEY_DPAD_OPACITY = floatPreferencesKey("dpad_opacity")
        private val KEY_ACTION_OPACITY = floatPreferencesKey("action_opacity")
        private val KEY_CENTER_OPACITY = floatPreferencesKey("center_opacity")
        private val KEY_SHOULDER_OPACITY = floatPreferencesKey("shoulder_opacity")
        private val KEY_DPAD_X = floatPreferencesKey("dpad_x")
        private val KEY_DPAD_Y = floatPreferencesKey("dpad_y")
        private val KEY_ACTION_X = floatPreferencesKey("action_x")
        private val KEY_ACTION_Y = floatPreferencesKey("action_y")
        private val KEY_CENTER_Y = floatPreferencesKey("center_y")
        private val KEY_SHOULDER_Y = floatPreferencesKey("shoulder_y")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_HAPTICS_STRENGTH = floatPreferencesKey("haptics_strength")
        private val KEY_SKIN = stringPreferencesKey("skin")
    }
}
