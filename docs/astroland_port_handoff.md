# Astroland Integration Guide

This document describes the official Astroland integration within this dotfiles repository.

Official upstream repository: [zacoons/astroland_dots](https://codeberg.org/zacoons/astroland_dots) (winner of Hyprland Contest #5).

---

## 1. Architecture Overview

Astroland consists of two core components working together with Hyprland:

```
dotfiles/
├── astroland/
│   ├── assets/              # Sounds (WAV), images (PNG/WEBP), animations (GIF)
│   └── plugin/              # Official C++ Hyprland Plugin (`libastroland.so`)
│       ├── CMakeLists.txt   # Modern CMake build with Hyprland dev headers
│       └── src/             # Physics engine, Astrolayout, ship, moons, bullets
│
├── config/
│   ├── hypr/
│   │   ├── hyprland.lua     # Hyprland lua config loading libastroland.so
│   │   ├── hypridle.conf    # Idle trigger activating Quickshell screensaver
│   │   └── vhs.frag         # CRT / VHS retro post-processing shader
│   └── quickshell/          # Quickshell QML overlays & shell
│       ├── shell.qml        # Root Quickshell entrypoint
│       ├── InfoOverlay.qml  # Clock & battery HUD
│       ├── NotifsOverlay.qml# Desktop notification daemon (D-Bus)
│       ├── ScreensaverComponent.qml # Bouncing DVD screensaver + audio
│       └── LockSurface.qml  # Astroland retro lockscreen
│
└── nixos/modules/hyprland.nix # Astroland package dependencies (quickshell, sox, mpd, etc.)
```

---

## 2. Building the Plugin

The plugin compiles into `astroland/plugin/build/libastroland.so` targeting the system Hyprland version (0.56.2).

### Using the Nix Flake devShell
```bash
nix develop .#astroland
cd astroland/plugin
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

The resulting library `libastroland.so` is dlopened directly by Hyprland:
```lua
hl.plugin.load(os.getenv("HOME") .. "/dotfiles/astroland/plugin/build/libastroland.so")
```

---

## 3. Quickshell Overlays

Overlays run via `qs` (Quickshell):
1. **Clock & Battery HUD** (`InfoOverlay.qml`): Displays battery level and time in retro-styled digital and analog formats.
2. **Notifications** (`NotifsOverlay.qml`): Listens on `org.freedesktop.Notifications` with animated typewriter text reveal and sound effects.
3. **Idle Screensaver** (`ScreensaverComponent.qml`): Triggered by `hypridle` after 300 seconds (`qs ipc call screensaver on/off`).
4. **Lockscreen** (`LockSurface.qml`): Authenticates via PAM (`pam/password.conf`).

---

## 4. Controls

When `astrolayout` is active in Hyprland:
- **WASD**: Pilot the spaceship
- **Mouse cursor**: Aim ship direction
- **Left click**: Fire bullets (or focus window if clicked directly)
- **SUPER + CTRL + SHIFT + S**: Save state to `~/.config/hypr/save.lua`
