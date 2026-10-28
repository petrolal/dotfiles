# Keybindings

Generated from `config/hypr/conf/keybindings.lua`'s `hl.bind(...)` calls (`hyprland.lua` is just
the loader that `dofile()`s each `conf/*.lua` module). `mainMod` = `SUPER`. If you rebind
something, edit that file directly and re-run `hyprctl reload` (ordinary keybind changes are
hot-reloadable — only plugin changes need a full Hyprland restart).

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
| `SUPER + SHIFT + B` | Restart Quickshell HUD (`pkill -f quickshell; qs &`) |
| `SUPER + N` | Open Tofi notification center (`imp notif-center`) |
| `SUPER + /` | Show Tofi keybinding cheat sheet (`imp keybinds`) |
| `SUPER + V` / `SUPER + SHIFT + V` | Toggle VHS / CRT screen shader (`imp toggle-vhs`) |
| `SUPER + L` / `SUPER + CTRL + L` | Lock screen (Astroland retro lockscreen: `imp lock`) |
| `SUPER + CTRL + R` | Open screen recorder (gpu-screen-recorder-gtk) |

## Astroland plugin (loaded via `hl.plugin.load` in `conf/astroland.lua`)

| Keys | Action |
|---|---|
| `SUPER + CTRL + SHIFT + S` | Save current ship/bullet/moon state to `~/.config/hypr/save.lua` (auto-loaded on next Hyprland start) |
| `SUPER + Escape` | Unfocus all windows to immediately pilot the ship |

While the plugin is loaded (`general.layout = "astrolayout"`), normal window movement is supplemented
by the game: **WASD flies the ship, mouse aims, left-click shoots** (or focuses a window if one's
under the cursor). See `docs/astroland_port_handoff.md` for how it is compiled and loaded into Hyprland.

## Idle / screensaver (automatic via hypridle & quickshell)

After 300s idle, `hypridle` runs `qs ipc call screensaver on` (bouncing DVD logo + hold music). Moving
the mouse or keyboard runs `qs ipc call screensaver off`. See `config/hypr/hypridle.conf`.

## Info overlay (autostarted via quickshell)

Quickshell (`qs`) starts automatically at Hyprland launch, rendering the HUD battery percentage and
analog/digital clock (`config/quickshell/overlay/InfoOverlay.qml`).

## Notifications overlay (autostarted via quickshell)

Quickshell implements an `org.freedesktop.Notifications` D-Bus daemon (`config/quickshell/notif/NotifsOverlay.qml`)
at Hyprland launch — any app's notifications land here as a sliding panel with audio feedback.
