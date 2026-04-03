# NandaSNES v1.0.0

Primera version estable publicada de NandaSNES para Android.

## Destacado

- branding actualizado de NandaNes a NandaSNES
- arquitectura Android reorganizada por capas (`presentation`, `data`, `domain`, `runtime`)
- configuracion migrada a `DataStore`
- correcciones de estabilidad en arranque, saves y retorno al inicio
- correccion de audio restaurando la ruta JNI estable de extraccion de muestras
- APK y AAB de release firmados y listos para distribucion

## Mejoras tecnicas

- separacion de responsabilidades y reduccion de acoplamiento en la app
- limpieza de repositorio para publicacion en GitHub
- pipeline base de CI con build y pruebas
- pruebas unitarias para partes de `data` y `domain`
- smoke tests instrumentados de arranque
- optimizaciones en thumbnails, debug logging y manejo de estado

## Artefactos

- APK firmado: `android/app/build/outputs/apk/release/NandaSNES-release.apk`
- AAB firmado: `android/app/build/outputs/bundle/release/NandaSNES-release.aab`

## Notas

- el core emulado actual es SNES basado en `snes9x`
- se mantuvo el `applicationId` y parte del naming interno para no romper compatibilidad de instalacion ni firma
