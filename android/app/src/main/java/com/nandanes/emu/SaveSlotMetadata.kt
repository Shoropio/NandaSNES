package com.nandanes.emu

import android.content.Context

object SaveSlotMetadata {
    private const val PREFS = "nandanes_slots"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun slotKey(romId: String, slot: Int) = "${romId}_slot_$slot"
    private fun autoResumeKey(romId: String) = "${romId}_auto_resume_enabled"

    fun getSlotSavedAtMillis(context: Context, romId: String, slot: Int): Long =
        prefs(context).getLong(slotKey(romId, slot), 0L)

    fun setSlotSavedAtMillis(context: Context, romId: String, slot: Int, timeMillis: Long) {
        prefs(context).edit().putLong(slotKey(romId, slot), timeMillis).apply()
    }

    fun getAutoSaveAtMillis(context: Context, romId: String): Long =
        prefs(context).getLong("${romId}_auto_time", 0L)

    fun setAutoSaveAtMillis(context: Context, romId: String, timeMillis: Long) {
        prefs(context).edit().putLong("${romId}_auto_time", timeMillis).apply()
    }

    fun getAutoSaveReason(context: Context, romId: String): String? =
        prefs(context).getString("${romId}_auto_reason", null)

    fun setAutoSaveReason(context: Context, romId: String, reason: String?) {
        // `putString(..., null)` elimina la key en SharedPreferences.
        prefs(context).edit().putString("${romId}_auto_reason", reason).apply()
    }

    fun getAutoResumeEnabled(context: Context, romId: String): Boolean =
        prefs(context).getBoolean(autoResumeKey(romId), true)

    fun setAutoResumeEnabled(context: Context, romId: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(autoResumeKey(romId), enabled).apply()
    }
}
