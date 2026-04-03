## NandaSNES Android

Aplicacion Android del proyecto, construida con Kotlin, Jetpack Compose, NDK y un bridge JNI hacia el core `snes9x`.

## Estructura actual

### Presentation

- `app/src/main/java/com/nandanes/emu/EmulatorActivity.kt`
- `app/src/main/java/com/nandanes/emu/EmulatorViewModel.kt`
- `app/src/main/java/com/nandanes/emu/EmulatorControlOverlay.kt`
- `app/src/main/java/com/nandanes/emu/SaveStatePanel.kt`

### Domain

- `app/src/main/java/com/nandanes/emu/domain/usecase/EmulationUseCases.kt`

### Data

- `app/src/main/java/com/nandanes/emu/data/rom/`
- `app/src/main/java/com/nandanes/emu/data/save/`
- `app/src/main/java/com/nandanes/emu/data/settings/`

### Runtime

- `app/src/main/java/com/nandanes/emu/runtime/EmulatorRuntime.kt`
- `app/src/main/cpp/nandanes_jni_bridge.cpp`

## Funcionalidad actual

- Selector de ROMs con historial reciente
- Overlay tactil configurable
- Render de video desde el core nativo
- Audio PCM con `AudioTrack`
- Save states manuales y autosave
- Persistencia de settings con `DataStore`
- Utilidades de debug con activacion controlada

## Requisitos

- JDK 17
- Android SDK
- Android NDK
- CMake 3.22.1

## Build

### Android Studio

Abre la carpeta `android/` como proyecto. Android Studio resolvera Gradle, SDK y NDK.

### CLI en Windows

```bat
.\gradlew.bat :app:assembleDebug
```

## Core nativo

- `app/src/main/cpp/CMakeLists.txt` compila `snes9x` como libreria estatica.
- El core vive en `../third_party/snes9x` respecto a esta carpeta.
- Si clonas el repo desde cero, inicializa submodulos antes de compilar:

```bash
git submodule update --init --recursive
```

## Mantenimiento

- No se deben versionar `.gradle`, `.idea`, `build`, `.cxx` ni `local.properties`.
- La firma de release debe configurarse fuera de Git usando `keystore.properties` o variables de entorno.
- Si actualizas `snes9x`, valida compatibilidad con `CMakeLists.txt` y con el bridge JNI.
