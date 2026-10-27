/**
 * Builds the "brain" of the real Hyprland astroland plugin as a Kotlin/Native
 * shared library (`libastro_native.so`) with a plain C ABI (see
 * [dev.petrolal.dotfiles.astronative.NativeApi] -- every exported function is
 * `@CName`-annotated so it's callable from C/C++ without going through
 * Kotlin/Native's generated API-struct header).
 *
 * This module intentionally has zero JVM/LWJGL/AWT dependencies: it is linked
 * into `native-plugin/` (plain C++, see its CMakeLists.txt), which is the
 * actual `.so` Hyprland `dlopen`s -- a real Hyprland plugin cannot be JVM
 * bytecode or depend on a JVM at all (Hyprland's plugin ABI is raw C++ with
 * no C-callable boundary, so the native-plugin/ shim owns every
 * Hyprland-facing call and this module only ever sees plain numbers in and out).
 */
plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    linuxX64("native") {
        binaries {
            sharedLib("astro_native") {
                baseName = "astro_native"
            }
        }
    }
}
