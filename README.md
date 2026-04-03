# NandaSNES

Android emulator app backed by the `snes9x` core through JNI. The project uses Jetpack Compose for the UI, Kotlin for the Android app layer, and C++ for the native runtime bridge.

## Current status

- Android app with Compose-based ROM picker and in-game overlay
- Native runtime for video, audio, input, save states, and autosave
- Refactored project structure with `presentation`, `data`, `domain`, and `runtime` responsibilities
- `snes9x` integrated as a Git submodule under `third_party/snes9x`

Important: the current GitHub repository URL may still use the legacy `NandaNes` name until the remote is renamed, but the product branding is now `NandaSNES`.

## Repository layout

- `android/`: Android Studio project and app module
- `third_party/snes9x/`: upstream emulator core used by the NDK build
- `docs/`: project notes and supporting documentation
- `android-samples/`: auxiliary Android sample material

## Architecture overview

### Presentation

- `EmulatorActivity`
- `EmulatorViewModel`
- Compose UI such as `EmulatorControlOverlay` and `SaveStatePanel`

### Domain

- use cases in `android/app/src/main/java/com/nandanes/emu/domain/usecase/`

### Data

- ROM catalog and recent history in `android/app/src/main/java/com/nandanes/emu/data/rom/`
- save metadata in `android/app/src/main/java/com/nandanes/emu/data/save/`
- app settings in `android/app/src/main/java/com/nandanes/emu/data/settings/`

### Runtime

- JNI bridge, audio, video surface, vibration, save-state and autosave management in `android/app/src/main/java/com/nandanes/emu/runtime/`
- native code in `android/app/src/main/cpp/`

## Requirements

- Android Studio with Android SDK installed
- Android NDK compatible with AGP 8.7.x / CMake 3.22.1
- JDK 17

## Clone and build

Clone the repository with submodules so the native core is available:

```bash
git clone --recurse-submodules https://github.com/Shoropio/NandaNes.git
cd NandaNes
```

If you already cloned it without submodules:

```bash
git submodule update --init --recursive
```

Build the Android app from the `android/` directory:

```bat
cd android
.\gradlew.bat :app:assembleDebug
```

## Notes for contributors

- Generated folders such as `.gradle`, `.idea`, `build`, and `.cxx` are intentionally ignored.
- `local.properties` is local-only and must not be committed.
- Release signing is configured via local `android/keystore.properties` or `NANDANES_*` environment variables.
- If you update the emulator core, keep the submodule pointer in sync and verify `android/app/src/main/cpp/CMakeLists.txt`.
- Saves, states, and debug settings are stored locally by the app and are not intended to be versioned.

## Verification

The refactored Android project has been verified with:

```bat
.\gradlew.bat :app:assembleDebug
```
