package com.nandanes.emu

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class DebugSettings(
    val enabled: Boolean = false
)

class DebugSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("nandanes_debug", Context.MODE_PRIVATE)

    fun load(): DebugSettings = DebugSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false)
    )

    fun save(settings: DebugSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .apply()
    }

    private companion object {
        private const val KEY_ENABLED = "enabled"
    }
}

object EmulatorDebug {
    private const val TAG = "NandaNesDebug"
    private var logFile: File? = null
    @Volatile private var enabled: Boolean = false
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun initialize(context: Context, settings: DebugSettings) {
        val debugDir = File(context.filesDir, "debug").also { it.mkdirs() }
        logFile = File(debugDir, "emu-debug.log")
        enabled = settings.enabled
        logAlways("SYSTEM", "Logger ready. debugEnabled=${settings.enabled}")
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        logAlways("SYSTEM", "Debug mode ${if (value) "enabled" else "disabled"}")
    }

    fun isEnabled(): Boolean = enabled

    fun log(step: String, message: String) {
        if (!enabled) return
        logAlways(step, message)
    }

    fun logAlways(step: String, message: String) {
        val line = "${timestampFormat.format(Date())} [$step] $message"
        Log.d(TAG, line)
        val target = logFile ?: return
        ioExecutor.execute {
            runCatching {
                target.appendText(line + "\n")
            }
        }
    }

    fun clear() {
        val target = logFile ?: return
        ioExecutor.execute {
            runCatching {
                target.writeText("")
                val line = "${timestampFormat.format(Date())} [SYSTEM] Logs cleared"
                Log.d(TAG, line)
                target.appendText(line + "\n")
            }
        }
    }

    fun readRecent(maxLines: Int = 120): String {
        val file = logFile ?: return "Log no inicializado."
        if (!file.exists()) return "Sin eventos todavia."
        val lines = file.readLines()
        return lines.takeLast(maxLines).joinToString("\n").ifBlank { "Sin eventos todavia." }
    }

    fun logPath(): String = logFile?.absolutePath ?: ""
}
