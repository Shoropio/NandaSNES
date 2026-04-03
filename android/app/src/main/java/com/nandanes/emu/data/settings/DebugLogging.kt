package com.nandanes.emu.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class DebugSettings(
    val enabled: Boolean = false
)

class DebugSettingsStore(context: Context) {
    private val dataStore = settingsDataStore(context, SettingsStoreNames.DEBUG)

    fun load(): DebugSettings {
        val prefs = runBlocking { dataStore.data.first() }
        return DebugSettings(
            enabled = prefs[KEY_ENABLED] ?: false
        )
    }

    fun save(settings: DebugSettings) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_ENABLED] = settings.enabled
            }
        }
    }

    private companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
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
        return runCatching {
            readTail(file, maxLines).ifBlank { "Sin eventos todavia." }
        }.getOrElse {
            "Sin eventos todavia."
        }
    }

    fun logPath(): String = logFile?.absolutePath ?: ""

    private fun readTail(file: File, maxLines: Int): String {
        RandomAccessFile(file, "r").use { raf ->
            val fileLength = raf.length()
            if (fileLength <= 0L) return ""

            val windowSize = minOf(fileLength, MAX_TAIL_BYTES.toLong()).toInt()
            val start = fileLength - windowSize
            raf.seek(start)
            val bytes = ByteArray(windowSize)
            raf.readFully(bytes)

            val content = bytes.toString(Charsets.UTF_8)
            return content
                .lines()
                .takeLast(maxLines)
                .joinToString("\n")
        }
    }

    private const val MAX_TAIL_BYTES = 64 * 1024
}
