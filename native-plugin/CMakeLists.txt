cmake_minimum_required(VERSION 3.12)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(PkgConfig REQUIRED)

project(astroland VERSION 1.0)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_FLAGS "-fpermissive")

# Where the game assets (ship.png, bg.png, explode.gif, ...) live at
# runtime -- defaults to the Gradle project's resources (same files the
# standalone JVM overlay uses, see src/main/resources/astroland), override
# with -DASTROLAND_ASSETS_DIR=/some/other/path.
if(NOT DEFINED ASTROLAND_ASSETS_DIR)
    set(ASTROLAND_ASSETS_DIR "${CMAKE_SOURCE_DIR}/../src/main/resources/astroland")
endif()

# Where :plugin-native's `./gradlew linkAstro_nativeDebugSharedNative` (or
# the release variant) put libastro_native.so -- this is the "brain"
# (see astro_native.h); this shim is only the Hyprland-facing shell around it.
if(NOT DEFINED ASTRO_NATIVE_LIB_DIR)
    set(ASTRO_NATIVE_LIB_DIR "${CMAKE_SOURCE_DIR}/../plugin-native/build/bin/native/astro_nativeDebugShared")
endif()

add_compile_definitions(ASTROLAND_ASSETS_DIR="${ASTROLAND_ASSETS_DIR}")

include_directories(src)
add_library(astroland SHARED "src/main.cpp" "vendor/gifdec.c")

pkg_check_modules(deps REQUIRED IMPORTED_TARGET
    hyprland
    pixman-1
    cairo
)

find_library(ASTRO_NATIVE_LIB astro_native PATHS "${ASTRO_NATIVE_LIB_DIR}" REQUIRED)

target_link_libraries(astroland PRIVATE rt dl PkgConfig::deps "${ASTRO_NATIVE_LIB}")
target_link_options(astroland PRIVATE "-Wl,-rpath,${ASTRO_NATIVE_LIB_DIR}")

install(TARGETS astroland)
