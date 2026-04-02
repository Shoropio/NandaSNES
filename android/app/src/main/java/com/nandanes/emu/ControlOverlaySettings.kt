package com.nandanes.emu

import android.content.Context

data class ControlOverlaySettings(
    val sizeScale: Float = 0.92f,
    val opacity: Float = 0.66f,
    val dpadOpacity: Float = 0.74f,
    val actionOpacity: Float = 0.8f,
    val centerOpacity: Float = 0.7f,
    val shoulderOpacity: Float = 0.68f,
    val dpadOffsetX: Float = 10f,
    val dpadOffsetY: Float = -12f,
    val actionOffsetX: Float = -10f,
    val actionOffsetY: Float = -14f,
    val centerOffsetY: Float = -18f,
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
    private val prefs = context.getSharedPreferences("nandanes_controls", Context.MODE_PRIVATE)

    fun load(): ControlOverlaySettings {
        val defaults = ControlOverlaySettings()
        val storedVersion = prefs.getInt(KEY_LAYOUT_VERSION, 0)
        if (storedVersion != LAYOUT_VERSION) {
            save(defaults)
            return defaults
        }

        return ControlOverlaySettings(
            sizeScale = prefs.getFloat(KEY_SIZE_SCALE, defaults.sizeScale),
            opacity = prefs.getFloat(KEY_OPACITY, defaults.opacity),
            dpadOpacity = prefs.getFloat(KEY_DPAD_OPACITY, defaults.dpadOpacity),
            actionOpacity = prefs.getFloat(KEY_ACTION_OPACITY, defaults.actionOpacity),
            centerOpacity = prefs.getFloat(KEY_CENTER_OPACITY, defaults.centerOpacity),
            shoulderOpacity = prefs.getFloat(KEY_SHOULDER_OPACITY, defaults.shoulderOpacity),
            dpadOffsetX = prefs.getFloat(KEY_DPAD_X, defaults.dpadOffsetX),
            dpadOffsetY = prefs.getFloat(KEY_DPAD_Y, defaults.dpadOffsetY),
            actionOffsetX = prefs.getFloat(KEY_ACTION_X, defaults.actionOffsetX),
            actionOffsetY = prefs.getFloat(KEY_ACTION_Y, defaults.actionOffsetY),
            centerOffsetY = prefs.getFloat(KEY_CENTER_Y, defaults.centerOffsetY),
            shoulderOffsetY = prefs.getFloat(KEY_SHOULDER_Y, defaults.shoulderOffsetY),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, defaults.hapticsEnabled),
            hapticsStrength = prefs.getFloat(KEY_HAPTICS_STRENGTH, defaults.hapticsStrength),
            skin = prefs.getString(KEY_SKIN, ControlSkin.CLASSIC.name)
                ?.let { name -> ControlSkin.entries.firstOrNull { it.name == name } }
                ?: defaults.skin
        )
    }

    fun save(settings: ControlOverlaySettings) {
        prefs.edit()
            .putInt(KEY_LAYOUT_VERSION, LAYOUT_VERSION)
            .putFloat(KEY_SIZE_SCALE, settings.sizeScale)
            .putFloat(KEY_OPACITY, settings.opacity)
            .putFloat(KEY_DPAD_OPACITY, settings.dpadOpacity)
            .putFloat(KEY_ACTION_OPACITY, settings.actionOpacity)
            .putFloat(KEY_CENTER_OPACITY, settings.centerOpacity)
            .putFloat(KEY_SHOULDER_OPACITY, settings.shoulderOpacity)
            .putFloat(KEY_DPAD_X, settings.dpadOffsetX)
            .putFloat(KEY_DPAD_Y, settings.dpadOffsetY)
            .putFloat(KEY_ACTION_X, settings.actionOffsetX)
            .putFloat(KEY_ACTION_Y, settings.actionOffsetY)
            .putFloat(KEY_CENTER_Y, settings.centerOffsetY)
            .putFloat(KEY_SHOULDER_Y, settings.shoulderOffsetY)
            .putBoolean(KEY_HAPTICS_ENABLED, settings.hapticsEnabled)
            .putFloat(KEY_HAPTICS_STRENGTH, settings.hapticsStrength)
            .putString(KEY_SKIN, settings.skin.name)
            .apply()
    }

    companion object {
        private const val LAYOUT_VERSION = 3
        private const val KEY_LAYOUT_VERSION = "layout_version"
        private const val KEY_SIZE_SCALE = "size_scale"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_DPAD_OPACITY = "dpad_opacity"
        private const val KEY_ACTION_OPACITY = "action_opacity"
        private const val KEY_CENTER_OPACITY = "center_opacity"
        private const val KEY_SHOULDER_OPACITY = "shoulder_opacity"
        private const val KEY_DPAD_X = "dpad_x"
        private const val KEY_DPAD_Y = "dpad_y"
        private const val KEY_ACTION_X = "action_x"
        private const val KEY_ACTION_Y = "action_y"
        private const val KEY_CENTER_Y = "center_y"
        private const val KEY_SHOULDER_Y = "shoulder_y"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_HAPTICS_STRENGTH = "haptics_strength"
        private const val KEY_SKIN = "skin"
    }
}
