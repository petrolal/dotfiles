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
├── RetroVHSSucks/
│   └── assets/              # Sounds (WAV), images (PNG/WEBP), animations (GIF) --
│                             # shared by the Quickshell overlays below and the AGS bar
│
├── config/
│   ├── ags/                 # AGS/Astal top bar (VHS/camcorder styling)
│   ├── hypr/
│   │   ├── hyprland.lua     # loader: dofile()s each conf/*.lua module below
│   │   ├── conf/            # input, monitors, autostart, env,
│   │   │                    # cursor, keybindings, window-rules (one file each)
│   │   ├── hypridle.conf    # Idle trigger activating Quickshell screensaver
│   │   └── vhs.frag         # CRT / VHS retro post-processing shader (mainMod+V toggle)
│   └── quickshell/          # Quickshell QML overlays & shell, grouped by feature
│       ├── shell.qml        # Root Quickshell entrypoint
│       ├── overlay/         # InfoOverlay.qml, InfoOverlayComponent.qml (clock & battery HUD)
│       ├── notif/           # NotifsOverlay.qml (desktop notification daemon, D-Bus)
│       ├── screensaver/     # ScreensaverComponent.qml (bouncing DVD screensaver + audio)
│       └── lock/            # LockContext.qml, LockController.qml, LockSurface.qml (retro lockscreen)
│
└── nixos/modules/hyprland.nix # Package dependencies (quickshell, sox, mpd, ags/astal, etc.)
```

---

## 2. Quickshell Overlays

Overlays run via `qs` (Quickshell), autostarted from `config/hypr/conf/autostart.lua`:
1. **Clock & Battery HUD** (`overlay/InfoOverlay.qml`): Displays battery level and time in retro-styled digital and analog formats.
2. **Notifications** (`notif/NotifsOverlay.qml`): Listens on `org.freedesktop.Notifications` with animated typewriter text reveal and sound effects.
3. **Idle Screensaver** (`screensaver/ScreensaverComponent.qml`): Triggered by `hypridle` after 300 seconds (`qs ipc call screensaver on/off`).
4. **Lockscreen** (`lock/LockSurface.qml`): Authenticates via PAM (`pam/password.conf`), bound to `SUPER + L` / `SUPER + CTRL + L` (`imp lock`).

See `config/ags/README.md` for the separate AGS top bar.
