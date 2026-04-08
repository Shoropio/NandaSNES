cmake_minimum_required(VERSION 3.22)
project(NandaSNES_Windows)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(JAVA_HOME "C:/Users/Shoropio/.antigravity/extensions/redhat.java-1.53.0-win32-x64/jre/21.0.10-win32-x86_64")
set(JNI_INCLUDE_DIRS "${JAVA_HOME}/include" "${JAVA_HOME}/include/win32")
include_directories(${JNI_INCLUDE_DIRS})

set(SNES9X_CORE_DIR ${CMAKE_SOURCE_DIR}/../third_party/snes9x)
set(LIBRETRO_COMMON_DIR ${SNES9X_CORE_DIR}/libretro/libretro-common)

set(SNES9X_SOURCES_C
    ${SNES9X_CORE_DIR}/filter/snes_ntsc.c
)

set(SNES9X_SOURCES_CXX
    ${SNES9X_CORE_DIR}/apu/apu.cpp
    ${SNES9X_CORE_DIR}/apu/bapu/dsp/sdsp.cpp
    ${SNES9X_CORE_DIR}/apu/bapu/smp/smp.cpp
    ${SNES9X_CORE_DIR}/apu/bapu/smp/smp_state.cpp
    ${SNES9X_CORE_DIR}/bsx.cpp
    ${SNES9X_CORE_DIR}/c4.cpp
    ${SNES9X_CORE_DIR}/c4emu.cpp
    ${SNES9X_CORE_DIR}/cheats.cpp
    ${SNES9X_CORE_DIR}/cheats2.cpp
    ${SNES9X_CORE_DIR}/clip.cpp
    ${SNES9X_CORE_DIR}/conffile.cpp
    ${SNES9X_CORE_DIR}/controls.cpp
    ${SNES9X_CORE_DIR}/cpu.cpp
    ${SNES9X_CORE_DIR}/cpuexec.cpp
    ${SNES9X_CORE_DIR}/cpuops.cpp
    ${SNES9X_CORE_DIR}/crosshairs.cpp
    ${SNES9X_CORE_DIR}/dma.cpp
    ${SNES9X_CORE_DIR}/dsp.cpp
    ${SNES9X_CORE_DIR}/dsp1.cpp
    ${SNES9X_CORE_DIR}/dsp2.cpp
    ${SNES9X_CORE_DIR}/dsp3.cpp
    ${SNES9X_CORE_DIR}/dsp4.cpp
    ${SNES9X_CORE_DIR}/fxinst.cpp
    ${SNES9X_CORE_DIR}/fxemu.cpp
    ${SNES9X_CORE_DIR}/gfx.cpp
    ${SNES9X_CORE_DIR}/globals.cpp
    ${SNES9X_CORE_DIR}/memmap.cpp
    ${SNES9X_CORE_DIR}/obc1.cpp
    ${SNES9X_CORE_DIR}/msu1.cpp
    ${SNES9X_CORE_DIR}/ppu.cpp
    ${SNES9X_CORE_DIR}/stream.cpp
    ${SNES9X_CORE_DIR}/sa1.cpp
    ${SNES9X_CORE_DIR}/sa1cpu.cpp
    ${SNES9X_CORE_DIR}/screenshot.cpp
    ${SNES9X_CORE_DIR}/sdd1.cpp
    ${SNES9X_CORE_DIR}/sdd1emu.cpp
    ${SNES9X_CORE_DIR}/seta.cpp
    ${SNES9X_CORE_DIR}/seta010.cpp
    ${SNES9X_CORE_DIR}/seta011.cpp
    ${SNES9X_CORE_DIR}/seta018.cpp
    ${SNES9X_CORE_DIR}/snapshot.cpp
    ${SNES9X_CORE_DIR}/snes9x.cpp
    ${SNES9X_CORE_DIR}/spc7110.cpp
    ${SNES9X_CORE_DIR}/srtc.cpp
    ${SNES9X_CORE_DIR}/tile.cpp
    ${SNES9X_CORE_DIR}/tileimpl-n1x1.cpp
    ${SNES9X_CORE_DIR}/tileimpl-n2x1.cpp
    ${SNES9X_CORE_DIR}/tileimpl-h2x1.cpp
    ${SNES9X_CORE_DIR}/sha256.cpp
    ${SNES9X_CORE_DIR}/bml.cpp
    ${SNES9X_CORE_DIR}/movie.cpp
    ${SNES9X_CORE_DIR}/fscompat.cpp
    ${SNES9X_CORE_DIR}/libretro/libretro.cpp
)

add_library(snes9x-core-win STATIC
    ${SNES9X_SOURCES_C}
    ${SNES9X_SOURCES_CXX}
)

target_include_directories(snes9x-core-win PRIVATE
    ${SNES9X_CORE_DIR}
    ${SNES9X_CORE_DIR}/apu
    ${SNES9X_CORE_DIR}/apu/bapu
    ${SNES9X_CORE_DIR}/libretro
    ${LIBRETRO_COMMON_DIR}/include
    ${CMAKE_SOURCE_DIR}/bridge
)

target_compile_definitions(snes9x-core-win PRIVATE
    __LIBRETRO__
    HAVE_STDINT_H
    RIGHTSHIFT_IS_SAR
    ALLOW_CPU_OVERCLOCK
    _CRT_SECURE_NO_WARNINGS
)

if(WIN32)
    add_definitions(-Dstrcasecmp=_stricmp)
    add_definitions(-Dstrncasecmp=_strnicmp)
    add_definitions(-Dftruncate=_chsize)
endif()

add_library(nandanes-win SHARED
    bridge/nandanes_win_bridge.cpp
)

target_include_directories(nandanes-win PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${SNES9X_CORE_DIR}
    ${SNES9X_CORE_DIR}/apu
    ${SNES9X_CORE_DIR}/apu/bapu
    ${SNES9X_CORE_DIR}/libretro
    ${LIBRETRO_COMMON_DIR}/include
)

target_link_libraries(nandanes-win
    snes9x-core-win
)
