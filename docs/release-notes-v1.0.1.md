# NandaSNES v1.0.1

Cierre de la primera etapa estable y de publicacion de NandaSNES para Android.

## Destacado

- repositorio publico confirmado en `https://github.com/Shoropio/NandaSNES`
- documentacion alineada con el nombre y URL finales del proyecto
- portada actualizada con identidad `NS`
- acceso rapido desde inicio para cargar ROMs recientes o importadas
- overlay tactil refinado con mejor geometria, respuesta visual y feedback haptico
- panel de save states mejorado con acciones visuales de guardar, cargar y borrar
- iconos launcher y recursos de app listos para distribucion
- APK y AAB de release firmados y listos para publicacion

## Mejoras tecnicas

- build de produccion alineada a `versionName 1.0.0` y `versionCode 2`
- slots manuales con borrado real de estado y miniatura
- catalogo de ROMs compatibles limitado a ROMs importadas por la app, evitando escaneo inseguro de `Downloads`
- documentacion de release y firma actualizada para el repo publico
- ramas `development` y `master` sincronizadas en el mismo estado estable

## Artefactos

- APK firmado: `android/app/build/outputs/apk/release/NandaSNES-release.apk`
- AAB firmado: `android/app/build/outputs/bundle/release/NandaSNES-release.aab`

## Notas

- el core emulado actual es SNES basado en `snes9x`
- se mantuvo el `applicationId` y parte del naming interno para no romper compatibilidad de instalacion ni firma
- la publicacion de la release en GitHub debe hacerse desde la web porque `gh` no esta instalado en esta maquina
