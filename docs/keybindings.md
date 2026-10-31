# Keybindings

Hand-curated from `config/hypr/conf/keybindings.lua`'s `hl.bind(...)` calls (`hyprland.lua` is just
the loader that `dofile()`s each `conf/*.lua` module). `mainMod` = `SUPER`. If you rebind
something, edit that file directly and re-run `hyprctl reload` (ordinary keybind changes are
hot-reloadable — only plugin changes need a full Hyprland restart).

For a version that can never drift out of sync with that file (because it's parsed from it at
run time instead of hand-copied), run `imp keybinds` -- it scrapes every `hl.bind(...)` call's
keys and description directly (see `KeybindHelper.kt`). This page exists alongside it for the
extra narrative context (grouping, idle/overlay behavior) that a flat generated list doesn't
carry.

## Applications

| Keys | Action |
|---|---|
| `SUPER + Return` | Open the terminal (kitty) |
| `SUPER + B` | Open the browser (`imp browser`) |
| `SUPER + E` | Open the file manager (thunar) |
| `SUPER + Space` / `SUPER + D` | Open application launcher (menu / tofi) |

## Workspaces

| Keys | Action |
|---|---|
| `SUPER + 0-9` | Focus workspace 1-10 (0 = workspace 10) |
| `SUPER + SHIFT + 0-9` | Move focused window to workspace 1-10 |
| `SUPER + mouse wheel down` | Switch to next workspace |
| `SUPER + mouse wheel up` | Switch to previous workspace |
| `SUPER + S` | Toggle special workspace "scratchpad" |
| `SUPER + SHIFT + S` | Float window + move it to the scratchpad |

## Windows

| Keys | Action |
|---|---|
| `SUPER + Q` | Close active window |
| `SUPER + F` | Toggle fullscreen |
| `SUPER + M` | Toggle maximized |
| `SUPER + T` | Toggle floating |
| `SUPER + J` | Toggle split |
| `SUPER + K` | Swap split |
| `SUPER + G` | Toggle window group |
| `SUPER + ←/→/↑/↓` | Move focus left/right/up/down |
| `SUPER + ALT + ←/→/↑/↓` | Swap tiled window left/right/up/down |
| `SUPER + SHIFT + ←/→/↑/↓` | Resize window (repeats while held) |
| `SUPER + left-click drag` | Move window with the mouse |
| `SUPER + right-click drag` | Resize window with the mouse |
| `ALT + Tab` | Switch applications (cyclenext) |
| `ALT + SHIFT + Tab` | Switch applications backwards |

## Screenshots

| Keys | Action |
|---|---|
| `SUPER + Print` | Area screenshot → `~/Pictures/` |
| `SUPER + ALT + F` | Instant full-screen screenshot |
| `SUPER + ALT + S` | Instant area screenshot |

## System / actions

| Keys | Action |
|---|---|
| `SUPER + SHIFT + R` | Reload Hyprland config (`hyprctl reload`) — does **not** reload plugins |
| `SUPER + SHIFT + B` | Restart AGS bar (`imp bar --action restart`) |
| `SUPER + SHIFT + H` | Restart Quickshell HUD (`imp hud --action restart`) |
| `SUPER + N` | Open Tofi notification center (`imp notif-center`) |
| `SUPER + /` | Show Tofi keybinding cheat sheet (`imp keybinds`) |
| `SUPER + V` / `SUPER + SHIFT + V` | Toggle VHS / CRT screen shader (`imp toggle-vhs`) |
| `SUPER + L` / `SUPER + CTRL + L` | Lock screen (RetroVHSSucks retro lockscreen: `imp lock`) |
| `SUPER + CTRL + R` | Open screen recorder (gpu-screen-recorder-gtk) |

## Media & volume keys

| Keys | Action |
|---|---|
| `XF86AudioRaiseVolume` | Raise volume (`imp volume --action up`) |
| `XF86AudioLowerVolume` | Lower volume (`imp volume --action down`) |
| `XF86AudioMute` | Mute audio (`imp volume --action mute`) |
| `XF86AudioMicMute` | Mute microphone (`imp volume --action mic-mute`) |
| `XF86AudioNext` | Next track (`playerctl next`) |
| `XF86AudioPause` | Pause audio (`playerctl play-pause`) |
| `XF86AudioPlay` | Play audio (`playerctl play-pause`) |
| `XF86AudioPrev` | Previous track (`playerctl previous`) |

The four volume/mic keys route through `imp volume` (`VolumeControl.kt`) rather than raw `wpctl`,
so they get the same in-place-updating OSD notification (via the shared `Notifier` helper) as
every other `imp_bin`-fronted binding above. The media transport keys need `playerctl`.

## Idle / screensaver (automatic via hypridle & quickshell)

After 300s idle, `hypridle` runs `qs ipc call screensaver on` (bouncing DVD logo + hold music). Moving
the mouse or keyboard runs `qs ipc call screensaver off`. See `config/hypr/hypridle.conf`.

## Interactive 3D Retro Room (Quickshell background)

A persistent 3D retro room runs behind all desktop windows on the primary display (`apps/quickshell/room/RoomScene.qml`).

| Method | Action |
|---|---|
| Click `"TAPE:"` on AGS bar | Folds bar down and travels camera to CRT Computer (`"terminal"`) |
| Click VFD readout on AGS bar | Folds bar down and travels camera to Cassette Boombox (`"music"`) |
| Click `"📼 SHUT THE FUCK UP!"` logo | Restores camera to room overview (`"home"`) and unfolds bar |
| `imp room --action travel <anchor>` | Command-line camera travel (`home`, `terminal`, `music`) |
| `imp room --action reset` | Reset camera to `"home"` overview |
| `imp room --action vhs` | Toggle 3D scene VHS post-processing effect |

Inactivity return: After 30s of idle time away from home, the camera automatically returns to `"home"` and unfolds the top bar.

## Info overlay (autostarted via quickshell)

Quickshell (`qs`) starts automatically at Hyprland launch, rendering the HUD battery percentage and
analog/digital clock (`apps/quickshell/overlay/InfoOverlay.qml`).

## Notifications overlay (autostarted via quickshell)

Quickshell implements an `org.freedesktop.Notifications` D-Bus daemon (`apps/quickshell/notif/NotifsOverlay.qml`)
at Hyprland launch — any app's notifications land here as a sliding panel with audio feedback.
