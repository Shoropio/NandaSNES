package com.nandanes.emu.runtime

import android.content.Context
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.nandanes.emu.data.save.SaveSlotMetadata
import com.nandanes.emu.data.settings.EmulatorDebug
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.nandanes.emu.domain.usecase.EmulationRuntime
import com.nandanes.emu.domain.usecase.SaveStateRuntime
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

enum class SnesKey(val nativeCode: Int) {
    UP(0), DOWN(1), LEFT(2), RIGHT(3),
    A(4), B(5), X(6), Y(7), L(8), R(9),
    START(10), SELECT(11)
}

class NativeBridge {
    external fun initializeInputMapping(): Boolean
    external fun loadRom(path: String): Boolean
    external fun unloadRom()
    external fun startEmulation()
    external fun stopEmulation()
    external fun isRomLoaded(): Boolean
    external fun reportButton(buttonCode: Int, pressed: Boolean)
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean
    external fun getFrameArgb8888(): IntArray
    external fun getLatestFrameInfo(): IntArray
    external fun getVideoFrameCount(): Long
    external fun renderLatestFrameToBitmap(bitmap: Bitmap): Boolean
    external fun consumeAudioSamples(maxSamples: Int): ShortArray
    external fun getPendingAudioSamples(): Int
    external fun getAudioSampleRate(): Int
    external fun setDebugLoggingEnabled(enabled: Boolean)
    external fun getDebugSnapshot(): String
    external fun getNativeDebugLog(): String
    external fun clearNativeDebugLog()

    companion object {
        init {
            System.loadLibrary("nandanes")
        }
    }
}

class NativeEmulationRuntime(
    private val bridge: NativeBridge
) : EmulationRuntime {
    override fun loadRom(path: String): Boolean = bridge.loadRom(path)

    override fun loadState(path: String): Boolean = bridge.loadState(path)
}

class VibrationController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun click(enabled: Boolean = true, strength: Float = 0.65f) {
        if (!enabled) return
        val current = vibrator ?: return
        if (!current.hasVibrator()) return
        val clamped = strength.coerceIn(0.1f, 1f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                current.vibrate(
                    VibrationEffect.createOneShot(
                        (10 + 18 * clamped).toLong(),
                        (80 + 175 * clamped).toInt()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                current.vibrate((10 + 18 * clamped).toLong())
            }
        } catch (_: SecurityException) {
            // Si el dispositivo o la build niegan vibracion, no derribamos la app.
        }
    }
}

class SaveStateManager(private val context: Context) {
    private var activeRomId: String = "default"

    fun setActiveRomId(romId: String) {
        activeRomId = romId.ifBlank { "default" }
        savesRoot().mkdirs()
        thumbsRoot().mkdirs()
    }

    fun manualSlotPath(slot: Int): File {
        require(slot in 1..5) { "Slot manual fuera de rango" }
        return File(savesRoot(), "slot_$slot.frz")
    }

    fun manualThumbPath(slot: Int): File {
        require(slot in 1..5) { "Thumbnail manual fuera de rango" }
        return File(thumbsRoot(), "slot_$slot.png")
    }

    fun autoSlotPath(): File = File(savesRoot(), "autosave.frz")
    fun autoThumbPath(): File = File(thumbsRoot(), "autosave.png")

    fun saveStateAtomically(bridge: NativeBridge, target: File): Boolean {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.delete()
        val saved = bridge.saveState(temp.absolutePath)
        if (!saved) {
            temp.delete()
            return false
        }
        return replaceAtomically(temp, target)
    }

    fun writeCurrentFrameThumbnail(bridge: NativeBridge, output: File): Boolean {
        val frameInfo = bridge.getLatestFrameInfo()
        if (frameInfo.size < 2) return false
        val width = frameInfo[0]
        val height = frameInfo[1]
        if (width <= 0 || height <= 0) return false

        val temp = File(output.parentFile ?: thumbsRoot(), "${output.name}.tmp")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val rendered = bridge.renderLatestFrameToBitmap(bitmap)
        if (!rendered) {
            bitmap.recycle()
            return false
        }
        temp.parentFile?.mkdirs()
        FileOutputStream(temp).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, fos)
        }
        bitmap.recycle()
        return replaceAtomically(temp, output)
    }

    private fun savesRoot(): File = File(context.filesDir, "saves/$activeRomId")
    private fun thumbsRoot(): File = File(context.filesDir, "thumbs/$activeRomId")

    private fun replaceAtomically(temp: File, target: File): Boolean {
        return try {
            if (target.exists() && !target.delete()) {
                target.outputStream().use { targetOut ->
                    temp.inputStream().use { tempIn -> tempIn.copyTo(targetOut) }
                }
                temp.delete()
                true
            } else if (temp.renameTo(target)) {
                true
            } else {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (_: IOException) {
            temp.delete()
            false
        }
    }
}

class NativeSaveStateRuntime(
    private val bridge: NativeBridge,
    private val saveStateManager: SaveStateManager
) : SaveStateRuntime {
    override fun setActiveRomId(romId: String) {
        saveStateManager.setActiveRomId(romId)
    }

    override fun saveManualState(slot: Int): Boolean =
        saveStateManager.saveStateAtomically(bridge, saveStateManager.manualSlotPath(slot))

    override fun writeManualThumbnail(slot: Int): Boolean =
        saveStateManager.writeCurrentFrameThumbnail(bridge, saveStateManager.manualThumbPath(slot))

    override fun manualSlotPath(slot: Int): File = saveStateManager.manualSlotPath(slot)

    override fun autoSlotPath(): File = saveStateManager.autoSlotPath()
}

class AutoSaveManager(
    private val context: Context,
    private val saveStateManager: SaveStateManager,
    private val nativeBridge: NativeBridge
) {
    private var lastAutoSaveElapsedMs: Long = 0L

    fun tryAutoSave(reason: String, romId: String): Boolean {
        if (!nativeBridge.isRomLoaded()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoSaveElapsedMs < AUTO_SAVE_DEBOUNCE_MS) return false

        saveStateManager.setActiveRomId(romId)
        val ok = saveStateManager.saveStateAtomically(nativeBridge, saveStateManager.autoSlotPath())
        if (!ok) return false

        saveStateManager.writeCurrentFrameThumbnail(nativeBridge, saveStateManager.autoThumbPath())
        SaveSlotMetadata.setAutoSaveAtMillis(context, romId, System.currentTimeMillis())
        SaveSlotMetadata.setAutoSaveReason(context, romId, reason)
        lastAutoSaveElapsedMs = now
        return true
    }

    fun deleteAutoSave(romId: String) {
        saveStateManager.setActiveRomId(romId)
        saveStateManager.autoSlotPath().delete()
        saveStateManager.autoThumbPath().delete()
        SaveSlotMetadata.setAutoSaveAtMillis(context, romId, 0L)
        SaveSlotMetadata.setAutoSaveReason(context, romId, null)
    }

    private companion object {
        private const val AUTO_SAVE_DEBOUNCE_MS = 2500L
    }
}

class AudioPlayer(
    private val context: Context,
    private val bridge: NativeBridge
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var resampleBuffer = ShortArray(0)
    @Volatile private var running = false
    @Volatile private var playbackToken = 0
    private var audioTrack: AudioTrack? = null
    private var sourceSampleRate: Int = 0
    private var outputSampleRate: Int = 0

    fun start() {
        if (running || !bridge.isRomLoaded()) return
        ensureTrack()
        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            EmulatorDebug.log("AUDIO", "AudioTrack not initialized at outputSampleRate=$outputSampleRate")
            return
        }
        running = true
        val token = ++playbackToken
        val primeTargetSamples = max(sourceSampleRate / 8, 4096)
        EmulatorDebug.log(
            "AUDIO",
            "Playback start sourceRate=$sourceSampleRate outputRate=$outputSampleRate token=$token"
        )
        repeat(25) {
            if (bridge.getPendingAudioSamples() >= primeTargetSamples) return@repeat
            Thread.sleep(8)
        }
        track.play()
        executor.execute {
            val silenceChunk = ShortArray(4096)
            while (running && playbackToken == token) {
                val sourceSamples = bridge.consumeAudioSamples(AUDIO_READ_CHUNK_SAMPLES)
                val sampleCount = sourceSamples.size
                if (sampleCount > 0) {
                    if (sourceSampleRate != outputSampleRate) {
                        val writeLength = resampleStereo(
                            input = sourceSamples,
                            sampleCount = sampleCount,
                            inRate = sourceSampleRate,
                            outRate = outputSampleRate
                        )
                        track.write(resampleBuffer, 0, writeLength, AudioTrack.WRITE_BLOCKING)
                    } else {
                        track.write(sourceSamples, 0, sampleCount, AudioTrack.WRITE_BLOCKING)
                    }
                } else {
                    Thread.sleep(3)
                    if (!running || playbackToken != token) break
                    track.write(silenceChunk, 0, silenceChunk.size, AudioTrack.WRITE_BLOCKING)
                }
            }
            EmulatorDebug.log("AUDIO", "Playback loop stop token=$token running=$running")
        }
    }

    fun stop() {
        running = false
        playbackToken++
        audioTrack?.pause()
        audioTrack?.flush()
        EmulatorDebug.log("AUDIO", "Playback stop")
    }

    fun resetForNextRom() {
        stop()
        audioTrack?.release()
        audioTrack = null
        sourceSampleRate = 0
        outputSampleRate = 0
        EmulatorDebug.logAlways("AUDIO", "Audio reset for next ROM")
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        executor.shutdownNow()
    }

    private fun ensureTrack() {
        val sourceRate = bridge.getAudioSampleRate().takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE
        val deviceRate = deviceOutputSampleRate()
        if (audioTrack != null && sourceSampleRate == sourceRate && outputSampleRate == deviceRate) return

        audioTrack?.release()
        sourceSampleRate = sourceRate
        outputSampleRate = deviceRate
        val minSize = AudioTrack.getMinBufferSize(
            outputSampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minSize * 2, (outputSampleRate / 4) * 4)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(outputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        audioTrack = builder.build()
        EmulatorDebug.log(
            "AUDIO",
            "AudioTrack prepared sourceRate=$sourceSampleRate outputRate=$outputSampleRate bufferSize=$bufferSize"
        )
    }

    private fun deviceOutputSampleRate(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val value = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return value?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_OUTPUT_SAMPLE_RATE
    }

    private fun ensureResampleCapacity(requiredSamples: Int) {
        if (resampleBuffer.size < requiredSamples) {
            resampleBuffer = ShortArray(requiredSamples)
        }
    }

    private fun resampleStereo(input: ShortArray, sampleCount: Int, inRate: Int, outRate: Int): Int {
        if (sampleCount <= 0 || inRate <= 0 || outRate <= 0 || inRate == outRate) {
            ensureResampleCapacity(sampleCount)
            input.copyInto(resampleBuffer, endIndex = sampleCount)
            return sampleCount
        }
        val inputFrames = sampleCount / 2
        if (inputFrames <= 1) {
            ensureResampleCapacity(sampleCount)
            input.copyInto(resampleBuffer, endIndex = sampleCount)
            return sampleCount
        }
        val outputFrames = max(1, ((inputFrames.toDouble() * outRate) / inRate).roundToInt())
        val outputSamples = outputFrames * 2
        ensureResampleCapacity(outputSamples)
        val step = inRate.toDouble() / outRate.toDouble()
        var position = 0.0
        for (frame in 0 until outputFrames) {
            val base = position.toInt().coerceIn(0, inputFrames - 1)
            val next = (base + 1).coerceAtMost(inputFrames - 1)
            val frac = position - base
            val left0 = input[base * 2].toInt()
            val right0 = input[base * 2 + 1].toInt()
            val left1 = input[next * 2].toInt()
            val right1 = input[next * 2 + 1].toInt()
            resampleBuffer[frame * 2] = (left0 + ((left1 - left0) * frac)).roundToInt().toShort()
            resampleBuffer[frame * 2 + 1] = (right0 + ((right1 - right0) * frac)).roundToInt().toShort()
            position += step
        }
        return outputSamples
    }

    private companion object {
        private const val AUDIO_READ_CHUNK_SAMPLES = 8192
        private const val DEFAULT_SAMPLE_RATE = 32040
        private const val DEFAULT_OUTPUT_SAMPLE_RATE = 48000
    }
}

@Composable
fun EmulatorVideoSurface(bridge: NativeBridge, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context -> NativeVideoView(context, bridge) },
        update = { it.bind(bridge) }
    )
}

private class NativeVideoView(context: Context, private var bridge: NativeBridge) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var lastFrameAtMs: Long = 0L
    private var lastRenderedFrameCount: Long = -1L
    private var hasLoggedFirstFrame = false

    fun bind(nextBridge: NativeBridge) {
        bridge = nextBridge
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateDelayed(FRAME_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.BLACK)
        val meta = bridge.getLatestFrameInfo()
        if (meta.size >= 2) {
            val width = meta[0]
            val height = meta[1]
            if (width > 0 && height > 0) {
                ensureBitmap(width, height)
                val frameCount = bridge.getVideoFrameCount()
                val rendered = if (frameCount != lastRenderedFrameCount) {
                    val didRender = bitmap?.let { bridge.renderLatestFrameToBitmap(it) } == true
                    if (didRender) {
                        lastRenderedFrameCount = frameCount
                    }
                    didRender
                } else {
                    bitmap != null
                }
                if (rendered) {
                    val target = aspectFitRect(width.toFloat(), height.toFloat(), this.width.toFloat(), this.height.toFloat())
                    bitmap?.let { canvas.drawBitmap(it, null, target, paint) }
                    lastFrameAtMs = SystemClock.elapsedRealtime()
                    if (!hasLoggedFirstFrame) {
                        EmulatorDebug.log("VIDEO", "First frame drawn width=$width height=$height view=${this.width}x${this.height}")
                        hasLoggedFirstFrame = true
                    }
                }
            }
        }
        if (meta.size <= 2 && lastFrameAtMs > 0L) {
            val idleMs = SystemClock.elapsedRealtime() - lastFrameAtMs
            if (idleMs > 350L) {
                canvas.drawColor(android.graphics.Color.BLACK)
            }
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun ensureBitmap(width: Int, height: Int) {
        val current = bitmap
        if (current != null && current.width == width && current.height == height) return
        current?.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        lastRenderedFrameCount = -1L
    }

    private fun aspectFitRect(srcWidth: Float, srcHeight: Float, dstWidth: Float, dstHeight: Float): RectF {
        if (srcWidth <= 0f || srcHeight <= 0f || dstWidth <= 0f || dstHeight <= 0f) {
            return RectF(0f, 0f, dstWidth, dstHeight)
        }
        val scale = minOf(dstWidth / srcWidth, dstHeight / srcHeight)
        val width = srcWidth * scale
        val height = srcHeight * scale
        val left = (dstWidth - width) / 2f
        val top = (dstHeight - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    private companion object {
        private const val FRAME_DELAY_MS = 16L
    }
}
