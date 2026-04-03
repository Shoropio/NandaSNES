#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
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
    if (std::strcmp(key, "snes9x_lightgun_mode") == 0) return "Lightgun";
    if (std::strcmp(key, "snes9x_superscope_reverse_buttons") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_superscope_crosshair") == 0) return "1";
    if (std::strcmp(key, "snes9x_superscope_color") == 0) return "White";
    if (std::strcmp(key, "snes9x_justifier1_crosshair") == 0) return "1";
    if (std::strcmp(key, "snes9x_justifier1_color") == 0) return "White";
    if (std::strcmp(key, "snes9x_justifier2_crosshair") == 0) return "1";
    if (std::strcmp(key, "snes9x_justifier2_color") == 0) return "White";
    if (std::strcmp(key, "snes9x_rifle_crosshair") == 0) return "1";
    if (std::strcmp(key, "snes9x_rifle_color") == 0) return "White";
    if (std::strcmp(key, "snes9x_block_invalid_vram_access") == 0) return "enabled";
    if (std::strcmp(key, "snes9x_echo_buffer") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_blargg") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_show_lightgun_settings") == 0) return "disabled";
    if (std::strcmp(key, "snes9x_show_advanced_av_settings") == 0) return "disabled";
    if (std::strncmp(key, "snes9x_sndchan_", 15) == 0) return "enabled";
    if (std::strncmp(key, "snes9x_layer_", 13) == 0) return "enabled";
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
    __android_log_print(ANDROID_LOG_DEBUG, kLogTag, "%s", buffer);
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
    gLastFrameChecksum = 0;
    gLastFrameFirstPixel = 0;
    gLastFrameCenterPixel = 0;
}

void ResetAudioBuffer() {
    std::lock_guard<std::mutex> lock(gAudioMutex);
    gAudioQueue.clear();
    gAudioBatchCount = 0;
    gAudioSamplePairCount = 0;
    gLastAudioPeak = 0;
    gLastAudioAverageAbs = 0;
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
        default: break;
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
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            if (data == nullptr) return false;
            static retro_log_callback logCb{RetroFrontendLogCb};
            *static_cast<retro_log_callback *>(data) = logCb;
            return true;
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (data == nullptr) return false;
            gPixelFormat = *static_cast<const retro_pixel_format *>(data);
            DebugLog("pixel format set=%d", static_cast<int>(gPixelFormat));
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (data == nullptr) return false;
            auto *var = static_cast<retro_variable *>(data);
            var->value = DefaultCoreOptionValue(var->key);
            if (var->value != nullptr) {
                DebugLog("get variable key=%s value=%s", var->key, var->value);
                return true;
            }
            DebugLog("get variable key=%s value=(null)", var->key ? var->key : "(null)");
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char **>(data) = gSystemDir;
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char **>(data) = gSaveDir;
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool *>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            *static_cast<int *>(data) = 3; // audio + video
            return true;
        default:
            return false;
    }
}

void RetroVideoCb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data == nullptr || width == 0 || height == 0) return;
    const uint64_t frameIndex = ++gVideoFrameCount;
    uint32_t checksum = 0;
    uint32_t firstPixel = 0;
    uint32_t centerPixel = 0;

    if (gDebugLoggingEnabled.load()) {
        const uint8_t *srcBytes = static_cast<const uint8_t *>(data);
        checksum = 2166136261u;

        for (unsigned y = 0; y < height; y++) {
            const uint8_t *srcLine = srcBytes + y * pitch;
            for (unsigned x = 0; x < width; x++) {
                uint8_t r8 = 0;
                uint8_t g8 = 0;
                uint8_t b8 = 0;
                switch (gPixelFormat) {
                    case RETRO_PIXEL_FORMAT_XRGB8888: {
                        const uint32_t pixel = reinterpret_cast<const uint32_t *>(srcLine)[x];
                        r8 = static_cast<uint8_t>((pixel >> 16) & 0xFF);
                        g8 = static_cast<uint8_t>((pixel >> 8) & 0xFF);
                        b8 = static_cast<uint8_t>(pixel & 0xFF);
                        break;
                    }
                    case RETRO_PIXEL_FORMAT_0RGB1555: {
                        const uint16_t pixel = reinterpret_cast<const uint16_t *>(srcLine)[x];
                        const uint8_t r5 = (pixel >> 10) & 0x1F;
                        const uint8_t g5 = (pixel >> 5) & 0x1F;
                        const uint8_t b5 = pixel & 0x1F;
                        r8 = static_cast<uint8_t>((r5 * 255) / 31);
                        g8 = static_cast<uint8_t>((g5 * 255) / 31);
                        b8 = static_cast<uint8_t>((b5 * 255) / 31);
                        break;
                    }
                    case RETRO_PIXEL_FORMAT_RGB565:
                    default: {
                        const uint16_t pixel = reinterpret_cast<const uint16_t *>(srcLine)[x];
                        const uint8_t r5 = (pixel >> 11) & 0x1F;
                        const uint8_t g6 = (pixel >> 5) & 0x3F;
                        const uint8_t b5 = pixel & 0x1F;
                        r8 = static_cast<uint8_t>((r5 * 255) / 31);
                        g8 = static_cast<uint8_t>((g6 * 255) / 63);
                        b8 = static_cast<uint8_t>((b5 * 255) / 31);
                        break;
                    }
                }
                const uint32_t argb = (0xFFu << 24) | (r8 << 16) | (g8 << 8) | b8;
                checksum ^= argb;
                checksum *= 16777619u;
                if (x == 0 && y == 0) {
                    firstPixel = argb;
                }
                if (x == width / 2u && y == height / 2u) {
                    centerPixel = argb;
                }
            }
        }
    }

    std::lock_guard<std::mutex> lock(gFrameMutex);
    gLastFrameRaw.resize(pitch * height);
    std::memcpy(gLastFrameRaw.data(), data, pitch * height);
    gLastFrameWidth = static_cast<int>(width);
    gLastFrameHeight = static_cast<int>(height);
    gLastFramePitch = pitch;
    gLastFrameChecksum = checksum;
    gLastFrameFirstPixel = firstPixel;
    gLastFrameCenterPixel = centerPixel;
    if (frameIndex == 1 || (frameIndex % 300) == 0) {
        DebugLog(
            "video frame=%llu size=%ux%u pitch=%zu checksum=%08x first=%08x center=%08x",
            static_cast<unsigned long long>(frameIndex),
            width,
            height,
            pitch,
            checksum,
            gLastFrameFirstPixel.load(),
            gLastFrameCenterPixel.load()
        );
    }
}

void RetroAudioSampleCb(int16_t left, int16_t right) {
    const int16_t stereo[2] = {left, right};
    AppendAudioSamples(stereo, 2);
}

size_t RetroAudioSampleBatchCb(const int16_t *data, size_t frames) {
    AppendAudioSamples(data, frames * 2);
    const uint64_t batchIndex = ++gAudioBatchCount;
    gAudioSamplePairCount += frames;
    int peak = 0;
    int64_t sumAbs = 0;
    const size_t sampleCount = frames * 2;
    for (size_t i = 0; i < sampleCount; ++i) {
        const int sample = std::abs(static_cast<int>(data[i]));
        peak = std::max(peak, sample);
        sumAbs += sample;
    }
    gLastAudioPeak = peak;
    gLastAudioAverageAbs = sampleCount > 0 ? static_cast<int>(sumAbs / static_cast<int64_t>(sampleCount)) : 0;
    if (batchIndex == 1 || (batchIndex % 240) == 0) {
        DebugLog(
            "audio batch=%llu frames=%zu sampleRate=%d peak=%d avgAbs=%d",
            static_cast<unsigned long long>(batchIndex),
            frames,
            gAudioSampleRate,
            gLastAudioPeak.load(),
            gLastAudioAverageAbs.load()
        );
    }
    return frames;
}

void RetroInputPollCb() {}

int16_t RetroInputStateCb(unsigned port, unsigned device, unsigned /*index*/, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;
    if (id >= gTouchPressed.size()) return 0;
    return gTouchPressed[id] ? 1 : 0;
}

bool EnsureCoreInitialized() {
    if (gCoreInitialized) return true;

    DebugLog("initializing core");

    retro_set_environment(RetroEnvironmentCb);
    retro_set_video_refresh(RetroVideoCb);
    retro_set_audio_sample(RetroAudioSampleCb);
    retro_set_audio_sample_batch(RetroAudioSampleBatchCb);
    retro_set_input_poll(RetroInputPollCb);
    retro_set_input_state(RetroInputStateCb);
    retro_init();

    gCoreInitialized = true;
    DebugLog("core initialized");
    return true;
}

void StopLoopLocked() {
    gRunning = false;
    DebugLog("emulation loop stopped");
}

void StartLoopLocked() {
    if (!gRomLoaded) return;
    gRunning = true;
    DebugLog("emulation loop started");
    gTaskCv.notify_all();
}

void UnloadGameLocked() {
    StopLoopLocked();
    if (gRomLoaded) {
        DebugLog("unloading ROM");
        retro_unload_game();
    }
    gRomLoaded = false;
    ResetInputState();
    ResetAudioBuffer();
    ResetVideoBuffer();
}

bool EnsureInputMapped() {
    if (!EnsureCoreInitialized()) return false;
    if (gInputMapped) return true;

    // Port 1: Joypad #1. Port 2: empty.
    S9xSetController(0, CTL_JOYPAD, 0, 0, 0, 0);
    S9xSetController(1, CTL_NONE, 0, 0, 0, 0);
    S9xVerifyControllers();

    bool ok = true;
    ok = ok && MapTouchButton(kTouchIdUp, "Joypad1 Up");
    ok = ok && MapTouchButton(kTouchIdDown, "Joypad1 Down");
    ok = ok && MapTouchButton(kTouchIdLeft, "Joypad1 Left");
    ok = ok && MapTouchButton(kTouchIdRight, "Joypad1 Right");
    ok = ok && MapTouchButton(kTouchIdA, "Joypad1 A");
    ok = ok && MapTouchButton(kTouchIdB, "Joypad1 B");
    ok = ok && MapTouchButton(kTouchIdX, "Joypad1 X");
    ok = ok && MapTouchButton(kTouchIdY, "Joypad1 Y");
    ok = ok && MapTouchButton(kTouchIdL, "Joypad1 L");
    ok = ok && MapTouchButton(kTouchIdR, "Joypad1 R");
    ok = ok && MapTouchButton(kTouchIdStart, "Joypad1 Start");
    ok = ok && MapTouchButton(kTouchIdSelect, "Joypad1 Select");

    gInputMapped = ok;
    DebugLog("input mapping initialized ok=%d", ok ? 1 : 0);
    return ok;
}

void ShutdownCoreThread() {
    {
        std::lock_guard<std::mutex> lock(gTaskMutex);
        gShutdownRequested = true;
        gTasks.clear();
    }
    gTaskCv.notify_all();
    if (gCoreThread.joinable()) {
        gCoreThread.join();
    }
}

void CoreThreadLoop() {
    DebugLog("core thread loop entered");
    auto nextFrameTime = std::chrono::steady_clock::now();
    while (!gShutdownRequested) {
        std::function<void()> task;
        {
            std::unique_lock<std::mutex> lock(gTaskMutex);
            gTaskCv.wait_for(
                lock,
                std::chrono::milliseconds(16),
                [] { return gShutdownRequested || !gTasks.empty() || (gRunning && gRomLoaded); }
            );
            if (gShutdownRequested) break;
            if (!gTasks.empty()) {
                task = std::move(gTasks.front());
                gTasks.pop_front();
            }
        }

        if (task) {
            task();
            nextFrameTime = std::chrono::steady_clock::now();
            continue;
        }

        if (gRunning && gRomLoaded) {
            const size_t pendingSamples = PendingAudioSamples();
            const size_t targetSamples = static_cast<size_t>(std::max(gAudioSampleRate / 8, 4096));
            const size_t maxBufferedSamples = targetSamples * 2;
            if (pendingSamples > maxBufferedSamples) {
                std::this_thread::sleep_for(std::chrono::milliseconds(2));
                nextFrameTime = std::chrono::steady_clock::now();
                continue;
            }
            const auto now = std::chrono::steady_clock::now();
            if (now < nextFrameTime) {
                std::this_thread::sleep_until(nextFrameTime);
            } else {
                const auto drift = now - nextFrameTime;
                if (drift > std::chrono::milliseconds(50)) {
                    nextFrameTime = now;
                }
            }
            ++gRetroRunCount;
            retro_run();
            nextFrameTime += std::chrono::nanoseconds(gFrameDurationNs.load());
        }
    }
    DebugLog("core thread loop exited");
}

void EnsureCoreThread() {
    if (gCoreThread.joinable()) return;
    gShutdownRequested = false;
    DebugLog("starting core thread");
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_initializeInputMapping(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    return RunOnCoreThreadSync([] { return EnsureInputMapped(); }) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_loadRom(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring romPath) {
    const char *cPath = JStringToUtfChars(env, romPath);
    if (cPath == nullptr) return JNI_FALSE;
    std::string pathCopy(cPath);
    ReleaseUtfChars(env, romPath, cPath);

    const bool ok = RunOnCoreThreadSync([pathCopy]() {
        DebugLog("loadRom path=%s", pathCopy.c_str());
        UnloadGameLocked();
        ExtractDirectory(pathCopy.c_str(), gSystemDir, sizeof(gSystemDir));
        ExtractDirectory(pathCopy.c_str(), gSaveDir, sizeof(gSaveDir));

        if (!EnsureCoreInitialized()) return false;
        if (!EnsureInputMapped()) return false;

        std::FILE *file = std::fopen(pathCopy.c_str(), "rb");
        if (file == nullptr) {
            DebugLog("loadRom fopen failed path=%s", pathCopy.c_str());
            return false;
        }
        std::fseek(file, 0, SEEK_END);
        const long romSize = std::ftell(file);
        std::rewind(file);
        if (romSize <= 0) {
            std::fclose(file);
            DebugLog("loadRom invalid size=%ld", romSize);
            return false;
        }
        std::vector<uint8_t> romBytes(static_cast<size_t>(romSize));
        const size_t readSize = std::fread(romBytes.data(), 1, romBytes.size(), file);
        std::fclose(file);
        if (readSize != romBytes.size()) {
            DebugLog("loadRom fread incomplete expected=%zu got=%zu", romBytes.size(), readSize);
            return false;
        }

        retro_game_info info{};
        info.path = pathCopy.c_str();
        info.data = romBytes.data();
        info.size = romBytes.size();
        info.meta = nullptr;
        const bool loaded = retro_load_game(&info);
        gRomLoaded = loaded;
        ResetAudioBuffer();
        ResetVideoBuffer();
        if (loaded) {
            retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
            retro_system_av_info avInfo{};
            retro_get_system_av_info(&avInfo);
            if (avInfo.timing.sample_rate > 0.0) {
                gAudioSampleRate = static_cast<int>(std::round(avInfo.timing.sample_rate));
            }
            if (avInfo.timing.fps > 0.0) {
                gFrameDurationNs = static_cast<int64_t>(std::llround(1'000'000'000.0 / avInfo.timing.fps));
            } else {
                gFrameDurationNs = kDefaultFrameDurationNs;
            }
            DebugLog(
                "rom loaded sampleRate=%d fps=%.3f frameDurationNs=%lld",
                gAudioSampleRate,
                avInfo.timing.fps,
                static_cast<long long>(gFrameDurationNs.load())
            );
            retro_reset();
            retro_run();
            retro_run();
            DebugLog("primed first frames video=%llu audio=%llu", static_cast<unsigned long long>(gVideoFrameCount.load()), static_cast<unsigned long long>(gAudioBatchCount.load()));
        } else {
            DebugLog("rom load failed");
        }
        return loaded;
    });
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_unloadRom(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    RunOnCoreThreadSync([]() {
        DebugLog("unloadRom request");
        UnloadGameLocked();
        return 0;
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_startEmulation(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    RunOnCoreThreadSync([]() {
        DebugLog("startEmulation request");
        StartLoopLocked();
        return 0;
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_stopEmulation(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    RunOnCoreThreadSync([]() {
        DebugLog("stopEmulation request");
        StopLoopLocked();
        return 0;
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_isRomLoaded(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    return gRomLoaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_reportButton(
    JNIEnv *env,
    jobject /*thiz*/,
    jint buttonCode,
    jboolean pressed) {
    (void) env;
    const bool isPressed = (pressed == JNI_TRUE);
    SetPressedByTouchCode(static_cast<int>(buttonCode), isPressed);

    // Fallback path before ROM/core loop is active.
    if (!(gCoreInitialized && gRomLoaded)) {
        RunOnCoreThreadSync([buttonCode, isPressed]() {
            if (!EnsureInputMapped()) return 0;
            S9xReportButton(static_cast<uint32>(buttonCode), isPressed);
            return 0;
        });
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_saveState(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring path) {
    const char *cPath = JStringToUtfChars(env, path);
    if (cPath == nullptr) return JNI_FALSE;
    std::string pathCopy(cPath);
    ReleaseUtfChars(env, path, cPath);
    const bool ok = RunOnCoreThreadSync([pathCopy]() {
        DebugLog("saveState path=%s", pathCopy.c_str());
        const bool wasRunning = gRunning;
        StopLoopLocked();
        const bool saved = S9xFreezeGame(pathCopy.c_str());
        if (wasRunning) StartLoopLocked();
        DebugLog("saveState result=%d", saved ? 1 : 0);
        return saved;
    });
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_loadState(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring path) {
    const char *cPath = JStringToUtfChars(env, path);
    if (cPath == nullptr) return JNI_FALSE;
    std::string pathCopy(cPath);
    ReleaseUtfChars(env, path, cPath);
    const bool ok = RunOnCoreThreadSync([pathCopy]() {
        DebugLog("loadState path=%s", pathCopy.c_str());
        const bool wasRunning = gRunning;
        StopLoopLocked();
        const bool loaded = S9xUnfreezeGame(pathCopy.c_str());
        if (wasRunning) StartLoopLocked();
        DebugLog("loadState result=%d", loaded ? 1 : 0);
        return loaded;
    });
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_nandanes_emu_NativeBridge_getFrameArgb8888(
    JNIEnv *env,
    jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(gFrameMutex);
    if (gLastFrameRaw.empty() || gLastFrameWidth <= 0 || gLastFrameHeight <= 0) {
        jintArray empty = env->NewIntArray(2);
        if (empty == nullptr) return nullptr;
        jint header[2] = {0, 0};
        env->SetIntArrayRegion(empty, 0, 2, header);
        return empty;
    }

    const int pixelCount = gLastFrameWidth * gLastFrameHeight;
    std::vector<jint> out(2 + pixelCount);
    out[0] = gLastFrameWidth;
    out[1] = gLastFrameHeight;

    for (int y = 0; y < gLastFrameHeight; ++y) {
        const uint8_t *srcLine = gLastFrameRaw.data() + y * gLastFramePitch;
        for (int x = 0; x < gLastFrameWidth; ++x) {
            uint8_t r8 = 0;
            uint8_t g8 = 0;
            uint8_t b8 = 0;
            switch (gPixelFormat) {
                case RETRO_PIXEL_FORMAT_XRGB8888: {
                    const uint32_t pixel = reinterpret_cast<const uint32_t *>(srcLine)[x];
                    r8 = static_cast<uint8_t>((pixel >> 16) & 0xFF);
                    g8 = static_cast<uint8_t>((pixel >> 8) & 0xFF);
                    b8 = static_cast<uint8_t>(pixel & 0xFF);
                    break;
                }
                case RETRO_PIXEL_FORMAT_0RGB1555: {
                    const uint16_t pixel = reinterpret_cast<const uint16_t *>(srcLine)[x];
                    const uint8_t r5 = (pixel >> 10) & 0x1F;
                    const uint8_t g5 = (pixel >> 5) & 0x1F;
                    const uint8_t b5 = pixel & 0x1F;
                    r8 = static_cast<uint8_t>((r5 * 255) / 31);
                    g8 = static_cast<uint8_t>((g5 * 255) / 31);
                    b8 = static_cast<uint8_t>((b5 * 255) / 31);
                    break;
                }
                case RETRO_PIXEL_FORMAT_RGB565:
                default: {
                    const uint16_t pixel = reinterpret_cast<const uint16_t *>(srcLine)[x];
                    const uint8_t r5 = (pixel >> 11) & 0x1F;
                    const uint8_t g6 = (pixel >> 5) & 0x3F;
                    const uint8_t b5 = pixel & 0x1F;
                    r8 = static_cast<uint8_t>((r5 * 255) / 31);
                    g8 = static_cast<uint8_t>((g6 * 255) / 63);
                    b8 = static_cast<uint8_t>((b5 * 255) / 31);
                    break;
                }
            }
            out[2 + y * gLastFrameWidth + x] = static_cast<jint>((0xFFu << 24) | (r8 << 16) | (g8 << 8) | b8);
        }
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(out.size()));
    if (result == nullptr) return nullptr;
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_nandanes_emu_NativeBridge_getLatestFrameInfo(
    JNIEnv *env,
    jobject /*thiz*/) {
    jint out[2];
    {
        std::lock_guard<std::mutex> lock(gFrameMutex);
        out[0] = gLastFrameWidth;
        out[1] = gLastFrameHeight;
    }
    jintArray result = env->NewIntArray(2);
    if (result == nullptr) return nullptr;
    env->SetIntArrayRegion(result, 0, 2, out);
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nandanes_emu_NativeBridge_getVideoFrameCount(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    return static_cast<jlong>(gVideoFrameCount.load());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_renderLatestFrameToBitmap(
    JNIEnv *env,
    jobject /*thiz*/,
    jobject bitmapObj) {
    if (bitmapObj == nullptr) return JNI_FALSE;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmapObj, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(gFrameMutex);
    if (gLastFrameRaw.empty() || gLastFrameWidth <= 0 || gLastFrameHeight <= 0) {
        return JNI_FALSE;
    }
    if (info.width != static_cast<uint32_t>(gLastFrameWidth) || info.height != static_cast<uint32_t>(gLastFrameHeight)) {
        return JNI_FALSE;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmapObj, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
        return JNI_FALSE;
    }

    uint8_t *dst = static_cast<uint8_t *>(pixels);
    const uint8_t *src = gLastFrameRaw.data();

    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565 && gPixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
        const size_t rowBytes = static_cast<size_t>(gLastFrameWidth) * sizeof(uint16_t);
        for (int y = 0; y < gLastFrameHeight; ++y) {
            std::memcpy(dst + y * info.stride, src + y * gLastFramePitch, rowBytes);
        }
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        for (int y = 0; y < gLastFrameHeight; ++y) {
            uint32_t *dstLine = reinterpret_cast<uint32_t *>(dst + y * info.stride);
            const uint8_t *srcLine = src + y * gLastFramePitch;
            for (int x = 0; x < gLastFrameWidth; ++x) {
                uint8_t r8 = 0;
                uint8_t g8 = 0;
                uint8_t b8 = 0;
                switch (gPixelFormat) {
                    case RETRO_PIXEL_FORMAT_XRGB8888: {
                        const uint32_t pixel = reinterpret_cast<const uint32_t *>(srcLine)[x];
                        r8 = static_cast<uint8_t>((pixel >> 16) & 0xFF);
                        g8 = static_cast<uint8_t>((pixel >> 8) & 0xFF);
                        b8 = static_cast<uint8_t>(pixel & 0xFF);
                        break;
                    }
                    case RETRO_PIXEL_FORMAT_0RGB1555: {
                        const uint16_t pixel = reinterpret_cast<const uint16_t *>(srcLine)[x];
                        r8 = static_cast<uint8_t>((((pixel >> 10) & 0x1F) * 255) / 31);
                        g8 = static_cast<uint8_t>((((pixel >> 5) & 0x1F) * 255) / 31);
                        b8 = static_cast<uint8_t>(((pixel & 0x1F) * 255) / 31);
                        break;
                    }
                    case RETRO_PIXEL_FORMAT_RGB565:
                    default: {
                        const uint16_t pixel = reinterpret_cast<const uint16_t *>(srcLine)[x];
                        r8 = static_cast<uint8_t>((((pixel >> 11) & 0x1F) * 255) / 31);
                        g8 = static_cast<uint8_t>((((pixel >> 5) & 0x3F) * 255) / 63);
                        b8 = static_cast<uint8_t>(((pixel & 0x1F) * 255) / 31);
                        break;
                    }
                }
                dstLine[x] = (0xFFu << 24) | (r8 << 16) | (g8 << 8) | b8;
            }
        }
    } else {
        AndroidBitmap_unlockPixels(env, bitmapObj);
        return JNI_FALSE;
    }

    AndroidBitmap_unlockPixels(env, bitmapObj);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_nandanes_emu_NativeBridge_consumeAudioSamples(
    JNIEnv *env,
    jobject /*thiz*/,
    jint maxSamples) {
    const jint cappedMax = (maxSamples <= 0) ? 0 : maxSamples;
    std::vector<int16_t> out;
    {
        std::lock_guard<std::mutex> lock(gAudioMutex);
        const size_t count = std::min(static_cast<size_t>(cappedMax), gAudioQueue.size());
        out.reserve(count);
        for (size_t i = 0; i < count; i++) {
            out.push_back(gAudioQueue.front());
            gAudioQueue.pop_front();
        }
    }

    jshortArray result = env->NewShortArray(static_cast<jsize>(out.size()));
    if (result == nullptr) return nullptr;
    if (!out.empty()) {
        env->SetShortArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nandanes_emu_NativeBridge_consumeAudioSamples__3SI(
    JNIEnv *env,
    jobject /*thiz*/,
    jshortArray buffer,
    jint maxSamples) {
    if (buffer == nullptr || maxSamples <= 0) return 0;

    const jsize bufferLength = env->GetArrayLength(buffer);
    if (bufferLength <= 0) return 0;

    const size_t count = [&]() -> size_t {
        std::lock_guard<std::mutex> lock(gAudioMutex);
        return std::min({
            static_cast<size_t>(maxSamples),
            static_cast<size_t>(bufferLength),
            gAudioQueue.size()
        });
    }();

    if (count == 0) return 0;

    jboolean isCopy = JNI_FALSE;
    jshort *dst = env->GetShortArrayElements(buffer, &isCopy);
    if (dst == nullptr) return 0;

    {
        std::lock_guard<std::mutex> lock(gAudioMutex);
        for (size_t i = 0; i < count; ++i) {
            dst[i] = gAudioQueue.front();
            gAudioQueue.pop_front();
        }
    }

    env->ReleaseShortArrayElements(buffer, dst, 0);
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nandanes_emu_NativeBridge_getPendingAudioSamples(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    std::lock_guard<std::mutex> lock(gAudioMutex);
    return static_cast<jint>(gAudioQueue.size());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nandanes_emu_NativeBridge_getAudioSampleRate(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    return gAudioSampleRate;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_setDebugLoggingEnabled(
    JNIEnv *env,
    jobject /*thiz*/,
    jboolean enabled) {
    (void) env;
    gDebugLoggingEnabled = (enabled == JNI_TRUE);
    DebugLog("native debug logging enabled");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nandanes_emu_NativeBridge_getDebugSnapshot(
    JNIEnv *env,
    jobject /*thiz*/) {
    std::string snapshot = "coreInitialized=" + std::to_string(gCoreInitialized.load() ? 1 : 0) +
        " romLoaded=" + std::to_string(gRomLoaded.load() ? 1 : 0) +
        " running=" + std::to_string(gRunning.load() ? 1 : 0) +
        " sampleRate=" + std::to_string(gAudioSampleRate) +
        " pendingAudio=" + std::to_string(PendingAudioSamples()) +
        " frame=" + std::to_string(gLastFrameWidth) + "x" + std::to_string(gLastFrameHeight) +
        " runCount=" + std::to_string(gRetroRunCount.load()) +
        " videoFrames=" + std::to_string(gVideoFrameCount.load()) +
        " audioBatches=" + std::to_string(gAudioBatchCount.load()) +
        " audioFramePairs=" + std::to_string(gAudioSamplePairCount.load()) +
        " frameChecksum=0x" + [] (uint32_t value) {
            char buffer[16];
            std::snprintf(buffer, sizeof(buffer), "%08x", value);
            return std::string(buffer);
        }(gLastFrameChecksum.load()) +
        " firstPixel=0x" + [] (uint32_t value) {
            char buffer[16];
            std::snprintf(buffer, sizeof(buffer), "%08x", value);
            return std::string(buffer);
        }(gLastFrameFirstPixel.load()) +
        " centerPixel=0x" + [] (uint32_t value) {
            char buffer[16];
            std::snprintf(buffer, sizeof(buffer), "%08x", value);
            return std::string(buffer);
        }(gLastFrameCenterPixel.load()) +
        " audioPeak=" + std::to_string(gLastAudioPeak.load()) +
        " audioAvgAbs=" + std::to_string(gLastAudioAverageAbs.load());
    return env->NewStringUTF(snapshot.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nandanes_emu_NativeBridge_getNativeDebugLog(
    JNIEnv *env,
    jobject /*thiz*/) {
    std::string joined;
    {
        std::lock_guard<std::mutex> lock(gNativeLogMutex);
        for (const auto &line : gNativeLogLines) {
            joined += line;
            joined += '\n';
        }
    }
    if (joined.empty()) {
        joined = "Sin eventos nativos todavia.";
    }
    return env->NewStringUTF(joined.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_clearNativeDebugLog(
    JNIEnv *env,
    jobject /*thiz*/) {
    (void) env;
    std::lock_guard<std::mutex> lock(gNativeLogMutex);
    gNativeLogLines.clear();
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) vm;
    (void) reserved;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    (void) vm;
    (void) reserved;
    ShutdownCoreThread();
}
