# NandaSNES - Guía para Windows 🚀

Esta carpeta contiene la configuración para compilar y ejecutar NandaSNES en Windows.

## Requisitos
- **CMake** (v3.22 o superior)
- **Visual Studio 2022** (con el componente "Desarrollo para el escritorio con C++")
- **JDK 17** (configurado en el PATH)

## 1. Compilar el Motor Nativo (C++)
Para que el emulador funcione en Windows, primero debemos generar la librería `nandanes-win.dll`:

1. Abre una terminal en la raíz del proyecto.
2. Ejecuta los siguientes comandos:
```bash
mkdir build_windows
cd build_windows
cmake ../windows
cmake --build . --config Release
```
3. Una vez terminado, se generará el archivo `.dll`. **Asegúrate de que esté en el PATH o en la carpeta de ejecución de la app.**

## 2. Ejecutar la Aplicación de Escritorio
Desde la raíz del proyecto, usa Gradle para lanzar la versión de Windows:

```bash
./gradlew :desktop:run
```

## Notas
- El selector de archivos aparecerá automáticamente al pulsar "Abrir ROM".
- Los archivos de guardado se almacenarán en una carpeta local compatible con el formato de Android.
