# RetroVHSSucks Integration Guide

This document describes the RetroVHSSucks integration within this dotfiles repository.

Original upstream inspiration: [zacoons/astroland_dots](https://codeberg.org/zacoons/astroland_dots) (winner of Hyprland Contest #5).

The C++ Hyprland game plugin (ship/camera/world-position simulation,
`RetroVHSSucks/plugin/`) has been removed: it wasn't working reliably across a
real multi-monitor setup. What remains is the retro-VHS visual identity --
assets, the Quickshell overlays, and the lockscreen/screensaver -- with no
Hyprland plugin or `astrolayout` window layout involved.

---

## 1. Architecture Overview

```
dotfiles/
├── assets/                  # Dedicated sounds (WAV), textures (PNG/WEBP), animations (GIF)
│                             # shared across Quickshell overlays, 3D room, and AGS bar
│
├── apps/                    # Polyglot software applications
│   ├── cli/                 # Kotlin Spring Shell + GraalVM Native Image deployer (`imp`)
│   ├── ags/                 # AGS/Astal top bar (VHS/camcorder styling, TypeScript)
│   └── quickshell/          # Quickshell QML overlays, lockscreen & 3D room
│       ├── shell.qml        # Root Quickshell entrypoint
│       ├── room/            # RoomScene.qml, VhsEffect.qml, vhs_effect.frag
│       ├── overlay/         # InfoOverlay.qml, InfoOverlayComponent.qml (clock & battery HUD)
│       ├── notif/           # NotifsOverlay.qml (desktop notification daemon, D-Bus)
│       ├── screensaver/     # ScreensaverComponent.qml (bouncing DVD screensaver + audio)
│       └── lock/            # LockContext.qml, LockController.qml, LockSurface.qml (retro lockscreen)
│
├── plugins/
│   └── retroroom/           # Native C++ Qt 6 Quick 3D plugin (CameraRig, SceneAnchorRegistry)
│
├── config/                  # Static runtime configurations symlinked to ~/.config/
│   ├── hypr/
│   │   ├── hyprland.lua     # loader: dofile()s each conf/*.lua module below
│   │   ├── conf/            # input, monitors, autostart, env, cursor, keybindings
│   │   ├── hypridle.conf    # Idle trigger activating Quickshell screensaver
│   │   └── vhs.frag         # CRT / VHS retro post-processing shader (SUPER + V toggle)
│   └── fish/, tofi/, gtk-*/
│
└── nixos/modules/hyprland.nix # Package dependencies (quickshell, sox, mpd, ags/astal, etc.)
```

---

## 2. Quickshell Overlays

Overlays run via `qs` (Quickshell), autostarted from `config/hypr/conf/autostart.lua`:
1. **Clock & Battery HUD** (`apps/quickshell/overlay/InfoOverlay.qml`): Displays battery level and time in retro-styled digital and analog formats.
2. **Notifications** (`apps/quickshell/notif/NotifsOverlay.qml`): Listens on `org.freedesktop.Notifications` with animated typewriter text reveal and sound effects.
3. **Idle Screensaver** (`apps/quickshell/screensaver/ScreensaverComponent.qml`): Triggered by `hypridle` after 300 seconds (`qs ipc call screensaver on/off`).
4. **Lockscreen** (`apps/quickshell/lock/LockSurface.qml`): Authenticates via PAM (`pam/password.conf`), bound to `SUPER + L` / `SUPER + CTRL + L` (`imp lock`).

See `apps/ags/README.md` for the separate AGS top bar.

---

## 3. Interactive 3D Retro Room (Qt Quick 3D Migration)

The interactive 3D background room replaces the old C++ Hyprland compositor plugin with a clean userspace Qt Quick 3D scene inside Quickshell (`qs`).

* Full architecture, design decisions, and implementation status: see [docs/retro_room_architecture.md](retro_room_architecture.md).
* C++ QML Plugin: `plugins/retroroom/` (`RetroRoom 1.0` module with `CameraRig`, `SceneAnchorRegistry`, and `RoomSceneController`).
* Bar signaling & transitions: AGS bar triggers notebook-fold animation and signals `qs ipc call room travel <anchorId>`.

