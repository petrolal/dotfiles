# Project Architecture & Modular Monorepo Guide

This document defines the modular architecture, directory structure, toolchains, and developer workflows for this dotfiles repository.

---

## 1. Core Architectural Principles

1. **Strict Monorepo Separation (`apps/`, `plugins/`, `config/`, `assets/`)**:
   - Software applications with dependencies and lifecycles live under [`apps/`](../apps/).
   - Native extensions and compiled plugins live under [`plugins/`](../plugins/).
   - Pure, static runtime configurations live under [`config/`](../config/).
   - Shared images, textures, audio, and decals live under [`assets/`](../assets/).
   - Operating system specifications and derivations live under [`nixos/`](../nixos/) and [`nix/`](../nix/).

2. **Polyglot Workspace ("Native toolchain per module, unified coordinator at root")**:
   - Rather than forcing all languages into awkward JVM Gradle plugins, each module retains its standard, native toolchain (`apps/cli/` uses Gradle, `plugins/retroroom/` uses CMake/Ninja, `apps/ags/` uses Astal/TS, `nixos/` uses Nix Flakes).
   - The root [`build.gradle`](../build.gradle) orchestrates these modules via high-level lifecycle tasks (`test`, `compileRetroRoom`, `checkAgs`, `deploy`).

3. **Clean Runtime Isolation**:
   - The deployer [`Linker`](../apps/cli/src/main/kotlin/dev/petrolal/dotfiles/linker/Linker.kt) maps application code and configurations to their appropriate targets (`~/.config/quickshell`, `~/.config/ags`, `~/.config/hypr`).
   - Development metadata (`package.json`, `tsconfig.json`, `.gitignore`, `README.md`) is excluded from symlinks, keeping `~/.config/` pure and clean.

---

## 2. Directory Structure

```
dotfiles/
├── apps/                            # [Software Applications]
│   ├── cli/                         # Kotlin Spring Shell + GraalVM Native Image deployer (imp)
│   │   ├── build.gradle             # Subproject build configuration
│   │   └── src/                     # CLI commands, Linker, desktop operations
│   ├── ags/                         # Astal / GTK3 VHS top bar (TypeScript)
│   │   ├── app.ts                   # Bar entrypoint & monitor mapping
│   │   ├── widget/                  # Bar.tsx, Media.tsx, Workspaces.tsx, RoomManager.ts
│   │   └── style.scss               # CRT styling & 3D laptop fold perspective CSS
│   └── quickshell/                  # Overlays, HUD, screensaver & 3D background room (QML)
│       ├── shell.qml                # Root Quickshell layer-shell entrypoint
│       ├── room/                    # RoomScene.qml, VhsEffect.qml, vhs_effect.frag
│       ├── overlay/                 # Battery & clock HUD
│       ├── notif/                   # D-Bus notification daemon panel
│       ├── screensaver/             # Bouncing DVD idle screensaver
│       └── lock/                    # Retro PAM-authenticated lockscreen
│
├── plugins/                         # [Native C++ Plugins]
│   └── retroroom/                   # Qt 6 Quick 3D background room plugin
│       ├── CMakeLists.txt           # Standard Qt 6 QML module CMake definition
│       ├── build.sh                 # Fast build & test script
│       ├── include/                 # Public C++ headers (CameraRig, AnchorRegistry, Controller)
│       ├── src/                     # C++ implementations & interpolation math
│       └── tests/                   # Qt Test automated suite (100% headless transform tests)
│
├── config/                          # [Pure Static Runtime Dotfiles] (Symlinked to ~/.config/)
│   ├── hypr/                        # Hyprland window manager & bindings (Lua)
│   │   ├── hyprland.lua             # Root loader
│   │   ├── conf/                    # autostart.lua, keybindings.lua, env.lua, input.lua
│   │   ├── hypridle.conf            # Idle screensaver triggers
│   │   └── vhs.frag                 # Hyprland CRT screen shader
│   ├── fish/                        # Fish interactive shell configuration
│   ├── tofi/                        # Wayland application launcher & notification center
│   ├── gtk-3.0/, gtk-4.0/           # GTK theme configuration & CSS
│   └── fastfetch/                   # Terminal system info configuration
│
├── assets/                          # Dedicated media assets (sounds, textures, posters)
│   ├── *.png, *.svg, *.webp         # CRT decals, radio textures, logo artwork
│   ├── *.wav, *.gif                 # Notification chimes, screensaver audio, animations
│   └── room/                        # 3D room texture pack (posters, PS1, wood, CRT screens, carpet)
│
├── nixos/                           # [Operating System Definition]
│   ├── configuration.nix            # System hardware, audio, services, users
│   ├── flake.nix                    # Machine flake configuration
│   └── modules/                     # Modular NixOS configurations (hyprland.nix, packages.nix)
│
├── nix/                             # [Nix Package Derivations]
│   ├── dotfiles-imp.nix             # Hermetic GraalVM native-image recipe for `imp`
│   └── retroroom.nix                # CMake Qt 6 derivation recipe for `RetroRoom`
│
├── bin/                             # Compiled standalone executables (bin/imp)
├── build/                           # Unified gitignored build directory
│   ├── cli/                         # Gradle build outputs
│   └── plugins/retroroom/           # C++ Ninja/CMake build outputs
├── settings.gradle                  # Gradle multi-project definition: include 'cli'
├── build.gradle                     # Polyglot root task coordinator
└── flake.nix                        # Root Flake: devShells & package declarations
```

---

## 3. Toolchains & Build Pipelines

### 1. Kotlin CLI (`apps/cli/` -> `bin/imp`)
* **Toolchain**: Kotlin 2.4, Java 25, GraalVM Native Image, Spring Shell 3.4.
* **Build command**:
  ```bash
  ./gradlew nativeCompile    # Compiles standalone native binary to bin/imp (~1 min)
  ./gradlew quick-deploy     # Fast JVM execution without recompiling native image
  ```
* **Test command**:
  ```bash
  ./gradlew :cli:test
  ```

### 2. C++ Qt Quick 3D Plugin (`plugins/retroroom/`)
* **Toolchain**: C++17, Qt 6.5+ (`Core`, `Quick`, `Qml`, `Quick3D`, `Quick3DPrivate`, `Test`), CMake, Ninja.
* **Build command**:
  ```bash
  ./gradlew compileRetroRoom
  # Or natively inside devshell:
  nix develop .#room --command plugins/retroroom/build.sh
  ```
* **Output**: `build/plugins/retroroom/RetroRoom/libRetroRoomplugin.so` and `qmldir`.
* **Testing**:
  ```bash
  ./gradlew testRetroRoom
  ```

### 3. TypeScript AGS Bar (`apps/ags/`)
* **Toolchain**: TypeScript, Node/NPM, GJS, Astal GTK3 libraries.
* **Build & Typecheck verification**:
  ```bash
  ./gradlew checkAgs
  ```
* **Execution**: Run directly via `ags run apps/ags` or `imp bar`.

### 4. NixOS & System Integration (`nixos/`, `nix/`, `flake.nix`)
* **Nix Derivations**:
  - `nix/dotfiles-imp.nix`: Hermetically compiles `imp`.
  - `nix/retroroom.nix`: Hermetically compiles the C++ plugin with `qt6.qtquick3d`.
* **System Rebuild**:
  ```bash
  ./gradlew nix-switch        # sudo nixos-rebuild switch --flake ./nixos
  ```

---

## 4. Day-to-Day Developer Workflows

| Action | Command | What happens |
|---|---|---|
| **Run all tests** | `./gradlew test` | Runs Kotlin tests + C++ Qt tests + AGS TypeScript bundle check |
| **Deploy dotfiles** | `./gradlew deploy` | Recompiles native `bin/imp` and links applications and configs |
| **Fast dev deploy** | `./gradlew quick-deploy` | Links dotfiles immediately via JVM without native-image wait |
| **Compile C++ plugin** | `./gradlew compileRetroRoom` | Builds `plugins/retroroom` with CMake/Ninja into `build/` |
| **Bundle AGS Bar** | `./gradlew checkAgs` | Validates TypeScript and bundles AGS application |
| **Build all artifacts** | `./gradlew all` | Builds both native `bin/imp` and C++ QML plugin |
| **Clean workspace** | `./gradlew clean` | Removes `build/` and output binaries |

---

## 5. Runtime Communication Architecture

```
                       [ User Input / Shortcuts ]
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                                         ▼
      [ Hyprland Bindings ]                       [ AGS Top Bar ]
      SUPER + L, SUPER + V                       TAPE: / HUD clicks
              │                                         │
              ▼                                         ▼
       `imp lock / room`                       `roomManager.travel(...)`
              │                                         │
              └────────────────────┬────────────────────┘
                                   ▼
                       `qs ipc call room ...`
                                   │
                                   ▼
                       [ Quickshell Background ]
                          (shell.qml / qs)
                                   │
                      ┌────────────┴────────────┐
                      ▼                         ▼
            [ RoomScene.qml ]          [ C++ RetroRoom Plugin ]
            - View3D                   - RoomSceneController (debouncing)
            - VhsEffect.qml            - CameraRig (vector & angular math)
            - Scene Anchors            - SceneAnchorRegistry
```
