## NandaNes Android (NDK + Compose)

### Que incluye
- `app/src/main/java/com/nandanes/emu/EmulatorControlsAndSave.kt`: UI Compose, importacion de ROM, controles tactiles, render de video, audio, save states y auto-save.
- `app/src/main/java/com/nandanes/emu/RecentRomStore.kt`: persistencia de ROMs recientes.
- `app/src/main/cpp/nandanes_jni_bridge.cpp`: puente JNI hacia input, video, audio y save/load del core.
- `third_party/snes9x`: core clonado (official upstream), enlazado desde CMake.

### Estado actual
- Video visible en pantalla via una vista Android que consume frames ARGB del core.
- Audio PCM reproducido con `AudioTrack`.
- Slots manuales + auto-save con debounce y reemplazo por archivo temporal.
- Importacion de ROMs `.sfc`, `.smc` y `.fig`.
- Historial de ROMs recientes para reabrir sesiones rapido.

### Build en Windows
El wrapper de Gradle ya esta incluido.

Opciones:
- **Android Studio**: abre la carpeta `android/` como proyecto. Android Studio sincronizara el wrapper y el SDK/NDK necesarios.
- **CLI**:
  1) Asegura Android SDK + NDK configurados en tu entorno
  2) En `android/` ejecuta:

```bat
.\gradlew.bat :app:assembleDebug
```

### Estado de integracion del core
- `app/src/main/cpp/CMakeLists.txt` compila el conjunto `libretro` de Snes9x como `snes9xcore` (static).
- `nandanes` enlaza esa libreria y expone las llamadas JNI desde Android.
- Si actualizas `third_party/snes9x`, revisa que no cambie la lista de fuentes en CMake.
