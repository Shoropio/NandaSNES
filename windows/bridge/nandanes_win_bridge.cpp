#include <jni.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <deque>
#include <future>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <iostream>

#include "snes9x.h"
#include "controls.h"
#include "gfx.h"
#include "libretro.h"
#include "ppu.h"
#include "snapshot.h"

namespace {
constexpr uint32 kTouchIdUp = 0;
constexpr uint32 kTouchIdDown = 1;
constexpr uint32 kTouchIdLeft = 2;
constexpr uint32 kTouchIdRight = 3;
constexpr uint32 kTouchIdA = 4;
constexpr uint32 kTouchIdB = 5;
constexpr uint32 kTouchIdX = 6;
constexpr uint32 kTouchIdY = 7;
constexpr uint32 kTouchIdL = 8;
constexpr uint32 kTouchIdR = 9;
constexpr uint32 kTouchIdStart = 10;
constexpr uint32 kTouchIdSelect = 11;

bool gInputMapped = false;
std::atomic<bool> gCoreInitialized{false};
std::atomic<bool> gRomLoaded{false};
std::atomic<bool> gRunning{false};
std::atomic<bool> gShutdownRequested{false};
std::atomic<bool> gDebugLoggingEnabled{false};
std::atomic<uint64_t> gRetroRunCount{0};
std::atomic<uint64_t> gVideoFrameCount{0};
std::atomic<uint64_t> gAudioBatchCount{0};
std::atomic<uint64_t> gAudioSamplePairCount{0};
std::atomic<uint32_t> gLastFrameChecksum{0};
std::atomic<uint32_t> gLastFrameFirstPixel{0};
std::atomic<uint32_t> gLastFrameCenterPixel{0};
std::atomic<int> gLastAudioPeak{0};
std::atomic<int> gLastAudioAverageAbs{0};
std::atomic<int64_t> gFrameDurationNs{16666667LL};
std::thread gCoreThread;
std::mutex gTaskMutex;
std::condition_variable gTaskCv;
std::deque<std::function<void()>> gTasks;
std::mutex gNativeLogMutex;
std::deque<std::string> gNativeLogLines;
constexpr size_t kMaxNativeLogLines = 400;

std::array<std::atomic<bool>, 12> gTouchPressed{};

std::mutex gFrameMutex;
std::vector<uint8_t> gLastFrameRaw;
int gLastFrameWidth = 0;
int gLastFrameHeight = 0;
size_t gLastFramePitch = 0;
retro_pixel_format gPixelFormat = RETRO_PIXEL_FORMAT_RGB565;

std::mutex gAudioMutex;
std::deque<int16_t> gAudioQueue;
int gAudioSampleRate = 32040;
constexpr size_t kMaxAudioSamples = 32040 * 4;
constexpr const char *kLogTag = "NandaSnesNative";
constexpr int64_t kDefaultFrameDurationNs = 16666667LL;

char gSystemDir[1024] = ".";
char gSaveDir[1024] = ".";

void DebugLog(const char *fmt, ...);

void RetroFrontendLogCb(enum retro_log_level level, const char *fmt, ...) {
    if (!gDebugLoggingEnabled) return;
    char message[512];
    va_list args;
    va_start(args, fmt);
    std::vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);
    const char *levelName = "INFO";
    switch (level) {
        case RETRO_LOG_DEBUG: levelName = "DEBUG"; break;
        case RETRO_LOG_INFO: levelName = "INFO"; break;
        case RETRO_LOG_WARN: levelName = "WARN"; break;
        case RETRO_LOG_ERROR: levelName = "ERROR"; break;
        default: break;
    }
    DebugLog("core[%s] %s", levelName, message);
}

const char *DefaultCoreOptionValue(const char *key) {
    if (key == nullptr) return nullptr;
    if (std::strcmp(key, "snes9x_hires_blend") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_overclock_superfx") == 0) return "100";
    if (std::strcmp(key, "snes9x_up_down_allowed") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_gfx_clip") == 0) return "enabled";
    if (std::strcmp(key, "snes9x_gfx_transp") == 0) return "enabled";
    if (std::strcmp(key, "snes9x_audio_interpolation") == 0) return "gaussian";
    if (std::strcmp(key, "snes9x_overclock_cycles") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_reduce_sprite_flicker") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_randomize_memory") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_overscan") == 0) return "auto";
    if (std::strcmp(key, "snes9x_aspect") == 0) return "auto";
    if (std::strcmp(key, "snes9x_region") == 0) return "ntsc";
    return nullptr;
}

const char *JStringToUtfChars(JNIEnv *env, jstring s) {
    if (s == nullptr) return nullptr;
    return env->GetStringUTFChars(s, nullptr);
}

void ReleaseUtfChars(JNIEnv *env, jstring s, const char *chars) {
    if (s != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(s, chars);
    }
}

bool MapTouchButton(uint32 id, const char *commandName) {
    const s9xcommand_t cmd = S9xGetCommandT(commandName);
    return S9xMapButton(id, cmd, false);
}

void DebugLog(const char *fmt, ...) {
    if (!gDebugLoggingEnabled) return;
    char buffer[512];
    va_list args;
    va_start(args, fmt);
    std::vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    
    // On Windows, we log to stdout
    std::cout << "[" << kLogTag << "] " << buffer << std::endl;

    {
        std::lock_guard<std::mutex> lock(gNativeLogMutex);
        gNativeLogLines.emplace_back(buffer);
        while (gNativeLogLines.size() > kMaxNativeLogLines) {
            gNativeLogLines.pop_front();
        }
    }
}

void ResetVideoBuffer() {
    std::lock_guard<std::mutex> lock(gFrameMutex);
    gLastFrameRaw.clear();
    gLastFrameWidth = 0;
    gLastFrameHeight = 0;
    gLastFramePitch = 0;
    gPixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    gVideoFrameCount = 0;
}

void ResetAudioBuffer() {
    std::lock_guard<std::mutex> lock(gAudioMutex);
    gAudioQueue.clear();
    gAudioBatchCount = 0;
}

void ResetInputState() {
    for (auto &pressed : gTouchPressed) {
        pressed = false;
    }
}

void AppendAudioSamples(const int16_t *samples, size_t count) {
    if (samples == nullptr || count == 0) return;
    std::lock_guard<std::mutex> lock(gAudioMutex);
    for (size_t i = 0; i < count; i++) {
        gAudioQueue.push_back(samples[i]);
    }
    while (gAudioQueue.size() > kMaxAudioSamples) {
        gAudioQueue.pop_front();
    }
}

size_t PendingAudioSamples() {
    std::lock_guard<std::mutex> lock(gAudioMutex);
    return gAudioQueue.size();
}

void SetPressedByTouchCode(int buttonCode, bool pressed) {
    if (buttonCode >= 0 && buttonCode < 12) {
       // Map to retro IDs
       // This matches the Android logic
       switch (buttonCode) {
           case kTouchIdUp: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_UP] = pressed; break;
           case kTouchIdDown: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_DOWN] = pressed; break;
           case kTouchIdLeft: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_LEFT] = pressed; break;
           case kTouchIdRight: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_RIGHT] = pressed; break;
           case kTouchIdA: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_A] = pressed; break;
           case kTouchIdB: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_B] = pressed; break;
           case kTouchIdX: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_X] = pressed; break;
           case kTouchIdY: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_Y] = pressed; break;
           case kTouchIdL: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_L] = pressed; break;
           case kTouchIdR: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_R] = pressed; break;
           case kTouchIdStart: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_START] = pressed; break;
           case kTouchIdSelect: gTouchPressed[RETRO_DEVICE_ID_JOYPAD_SELECT] = pressed; break;
       }
    }
}

void ExtractDirectory(const char *path, char *outBuf, size_t outSize) {
    if (path == nullptr || outSize == 0) return;
    std::string p(path);
    const size_t slash = p.find_last_of("/\\");
    std::string d = (slash == std::string::npos) ? "." : p.substr(0, slash);
    if (d.empty()) d = ".";
    std::snprintf(outBuf, outSize, "%s", d.c_str());
}

bool RetroEnvironmentCb(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            if (data == nullptr) return false;
            static retro_log_callback logCb{RetroFrontendLogCb};
            *static_cast<retro_log_callback *>(data) = logCb;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (data == nullptr) return false;
            gPixelFormat = *static_cast<const retro_pixel_format *>(data);
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (data == nullptr) return false;
            auto *var = static_cast<retro_variable *>(data);
            var->value = DefaultCoreOptionValue(var->key);
            return var->value != nullptr;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char **>(data) = gSystemDir;
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char **>(data) = gSaveDir;
            return true;
        default:
            return false;
    }
}

void RetroVideoCb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data == nullptr || width == 0 || height == 0) return;
    gVideoFrameCount++;
    std::lock_guard<std::mutex> lock(gFrameMutex);
    gLastFrameRaw.resize(pitch * height);
    std::memcpy(gLastFrameRaw.data(), data, pitch * height);
    gLastFrameWidth = static_cast<int>(width);
    gLastFrameHeight = static_cast<int>(height);
    gLastFramePitch = pitch;
}

void RetroAudioSampleCb(int16_t left, int16_t right) {
    const int16_t stereo[2] = {left, right};
    AppendAudioSamples(stereo, 2);
}

size_t RetroAudioSampleBatchCb(const int16_t *data, size_t frames) {
    AppendAudioSamples(data, frames * 2);
    gAudioBatchCount++;
    return frames;
}

void RetroInputPollCb() {}

int16_t RetroInputStateCb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;
    if (id >= gTouchPressed.size()) return 0;
    return gTouchPressed[id] ? 1 : 0;
}

bool EnsureCoreInitialized() {
    if (gCoreInitialized) return true;
    retro_set_environment(RetroEnvironmentCb);
    retro_set_video_refresh(RetroVideoCb);
    retro_set_audio_sample(RetroAudioSampleCb);
    retro_set_audio_sample_batch(RetroAudioSampleBatchCb);
    retro_set_input_poll(RetroInputPollCb);
    retro_set_input_state(RetroInputStateCb);
    retro_init();
    gCoreInitialized = true;
    return true;
}

void StopLoopLocked() { gRunning = false; }
void StartLoopLocked() { if (gRomLoaded) gRunning = true; gTaskCv.notify_all(); }

void CoreThreadLoop() {
    auto nextFrameTime = std::chrono::steady_clock::now();
    while (!gShutdownRequested) {
        std::function<void()> task;
        {
            std::unique_lock<std::mutex> lock(gTaskMutex);
            gTaskCv.wait_for(lock, std::chrono::milliseconds(16), 
                [] { return gShutdownRequested || !gTasks.empty() || (gRunning && gRomLoaded); });
            if (gShutdownRequested) break;
            if (!gTasks.empty()) {
                task = std::move(gTasks.front());
                gTasks.pop_front();
            }
        }
        if (task) { task(); continue; }
        if (gRunning && gRomLoaded) {
            retro_run();
            // Simple frame timing
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
        }
    }
}

void EnsureCoreThread() {
    if (gCoreThread.joinable()) return;
    gShutdownRequested = false;
    gCoreThread = std::thread(CoreThreadLoop);
}

template <typename Fn>
auto RunOnCoreThreadSync(Fn &&fn) -> decltype(fn()) {
    using Result = decltype(fn());
    EnsureCoreThread();
    auto task = std::make_shared<std::packaged_task<Result()>>(std::forward<Fn>(fn));
    auto future = task->get_future();
    {
        std::lock_guard<std::mutex> lock(gTaskMutex);
        gTasks.push_back([task]() { (*task)(); });
    }
    gTaskCv.notify_one();
    return future.get();
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_nandanes_emu_runtime_NativeBridge_loadRom(JNIEnv *env, jobject thiz, jstring romPath) {
    const char *cPath = JStringToUtfChars(env, romPath);
    if (cPath == nullptr) return JNI_FALSE;
    std::string pathCopy(cPath);
    ReleaseUtfChars(env, romPath, cPath);

    return RunOnCoreThreadSync([pathCopy]() {
        if (!EnsureCoreInitialized()) return false;
        
        std::FILE *file = std::fopen(pathCopy.c_str(), "rb");
        if (!file) return false;
        std::fseek(file, 0, SEEK_END);
        long size = std::ftell(file);
        std::rewind(file);
        std::vector<uint8_t> buffer(size);
        std::fread(buffer.data(), 1, size, file);
        std::fclose(file);

        retro_game_info info = { pathCopy.c_str(), buffer.data(), (size_t)size, nullptr };
        gRomLoaded = retro_load_game(&info);
        return (bool)gRomLoaded;
    }) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_nandanes_emu_runtime_NativeBridge_startEmulation(JNIEnv *env, jobject thiz) {
    RunOnCoreThreadSync([]() { StartLoopLocked(); return 0; });
}

JNIEXPORT void JNICALL Java_com_nandanes_emu_runtime_NativeBridge_stopEmulation(JNIEnv *env, jobject thiz) {
    RunOnCoreThreadSync([]() { StopLoopLocked(); return 0; });
}

JNIEXPORT jintArray JNICALL Java_com_nandanes_emu_runtime_NativeBridge_getFrameArgb8888(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(gFrameMutex);
    if (gLastFrameRaw.empty()) return nullptr;

    const int pixelCount = gLastFrameWidth * gLastFrameHeight;
    std::vector<jint> out(2 + pixelCount);
    out[0] = gLastFrameWidth;
    out[1] = gLastFrameHeight;

    const uint8_t *src = gLastFrameRaw.data();
    for (int i = 0; i < pixelCount; i++) {
        uint16_t pixel = reinterpret_cast<const uint16_t *>(src)[i];
        uint8_t r = ((pixel >> 11) & 0x1F) << 3;
        uint8_t g = ((pixel >> 5) & 0x3F) << 2;
        uint8_t b = (pixel & 0x1F) << 3;
        out[2 + i] = (0xFFu << 24) | (r << 16) | (g << 8) | b;
    }

    jintArray result = env->NewIntArray(out.size());
    env->SetIntArrayRegion(result, 0, out.size(), out.data());
    return result;
}

JNIEXPORT void JNICALL Java_com_nandanes_emu_runtime_NativeBridge_reportButton(JNIEnv *env, jobject thiz, jint buttonCode, jboolean pressed) {
    SetPressedByTouchCode(buttonCode, pressed == JNI_TRUE);
}

// Stubs for other Android specific calls that might be called
JNIEXPORT jboolean JNICALL Java_com_nandanes_emu_runtime_NativeBridge_initializeInputMapping(JNIEnv *env, jobject thiz) { return JNI_TRUE; }
JNIEXPORT void JNICALL Java_com_nandanes_emu_runtime_NativeBridge_unloadRom(JNIEnv *env, jobject thiz) { gRomLoaded = false; }
JNIEXPORT jboolean JNICALL Java_com_nandanes_emu_runtime_NativeBridge_isRomLoaded(JNIEnv *env, jobject thiz) { return gRomLoaded; }
JNIEXPORT void JNICALL Java_com_nandanes_emu_runtime_NativeBridge_setDebugLoggingEnabled(JNIEnv *env, jobject thiz, jboolean enabled) { gDebugLoggingEnabled = enabled; }
JNIEXPORT jstring JNICALL Java_com_nandanes_emu_runtime_NativeBridge_getNativeDebugLog(JNIEnv *env, jobject thiz) { return env->NewStringUTF("Windows Log active"); }
JNIEXPORT void JNICALL Java_com_nandanes_emu_runtime_NativeBridge_clearNativeDebugLog(JNIEnv *env, jobject thiz) {}
JNIEXPORT jshortArray JNICALL Java_com_nandanes_emu_runtime_NativeBridge_consumeAudioSamples(JNIEnv *env, jobject thiz, jint maxSamples) { return nullptr; }
JNIEXPORT jint JNICALL Java_com_nandanes_emu_runtime_NativeBridge_getPendingAudioSamples(JNIEnv *env, jobject thiz) { return 0; }
JNIEXPORT jint JNICALL Java_com_nandanes_emu_runtime_NativeBridge_getAudioSampleRate(JNIEnv *env, jobject thiz) { return gAudioSampleRate; }

} // extern "C"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) { return JNI_VERSION_1_6; }
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    gShutdownRequested = true;
    if (gCoreThread.joinable()) gCoreThread.join();
}
