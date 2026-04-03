#include <jni.h>
#include <string>

// Headers reales dependen de como este embebido Snes9x en NandaSNES.
extern "C" {
    bool S9xFreezeGame(const char* filename);
    bool S9xUnfreezeGame(const char* filename);
    void S9xReportButton(int buttonCode, bool pressed);
}

namespace {
const char* JStringToUtfChars(JNIEnv* env, jstring s) {
    if (s == nullptr) return nullptr;
    return env->GetStringUTFChars(s, nullptr);
}

void ReleaseUtfChars(JNIEnv* env, jstring s, const char* chars) {
    if (s != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(s, chars);
    }
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_nandanes_emu_NativeBridge_reportButton(
    JNIEnv* env,
    jobject /*thiz*/,
    jint buttonCode,
    jboolean pressed
) {
    (void)env;
    S9xReportButton(static_cast<int>(buttonCode), pressed == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_saveState(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring path
) {
    const char* cPath = JStringToUtfChars(env, path);
    if (cPath == nullptr) return JNI_FALSE;
    const bool ok = S9xFreezeGame(cPath);
    ReleaseUtfChars(env, path, cPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nandanes_emu_NativeBridge_loadState(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring path
) {
    const char* cPath = JStringToUtfChars(env, path);
    if (cPath == nullptr) return JNI_FALSE;
    const bool ok = S9xUnfreezeGame(cPath);
    ReleaseUtfChars(env, path, cPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}
