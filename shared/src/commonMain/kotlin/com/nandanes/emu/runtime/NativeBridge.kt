package com.nandanes.emu.runtime

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
    
    // Este método solo se implementa en Android por eficiencia (Bitmap)
    // En Windows usaremos getFrameArgb8888
    // external fun renderLatestFrameToBitmap(bitmap: Any): Boolean
    
    external fun consumeAudioSamples(maxSamples: Int): ShortArray
    external fun getPendingAudioSamples(): Int
    external fun getAudioSampleRate(): Int
    external fun setDebugLoggingEnabled(enabled: Boolean)
    external fun getDebugSnapshot(): String
    external fun getNativeDebugLog(): String
    external fun clearNativeDebugLog()

    companion object {
        init {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                System.loadLibrary("nandanes-win")
            } else {
                System.loadLibrary("nandanes")
            }
        }
    }
}
