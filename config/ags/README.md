# AGS bar

VCR deck front-panel styled top bar + a togglable cassette-inspection HUD popup, built with [AGS v2](https://aylur.github.io/ags-docs/) (Astal + TypeScript, GTK3).

## Layout

- `app.ts` -- entrypoint, maps `Bar` over every monitor and creates the single `Hud` popup.
- `widget/Bar.tsx` -- the bar window; wires the pieces below into a `<centerbox>`.
- `widget/Workspaces.tsx` -- Hyprland workspaces shown as a "TAPE:" program selector: `[01]`/`[02]`/... buttons, `.active` = focused, `.occupied` = has client windows.
- `widget/Media.tsx` -- VFD-style track readout (`[MM:SS] Artist - Title`, or "NO TAPE INSERTED") + REW/PLAY-PAUSE/STOP/FF transport buttons, driven by the first `AstalMpris` player. Clicking the VFD or the 📼 button toggles `Hud.tsx`.
- `widget/Hud.tsx` -- popup window (`name="vhs-hud"`, toggled via `App.toggleWindow`): tape-spool mockup, scrubbable position slider, volume fader, a decorative "TRACKING" RAM-load readout, and a STATUS/FORMAT/CODEC/ALBUM metadata matrix.
- `widget/Osd.tsx` -- camcorder-style telemetry strip: blinking "REC ●", an "SP" speed badge, a glowing green "INDEX" tape counter (HH:MM:SS), and a cyan "TRK:" RAM-usage readout.
- `widget/Vu.tsx` -- passive audio level meter (`AstalWp` default speaker volume) as green/amber/red segment ticks.
- `widget/Battery.tsx` -- reuses `RetroVHSSucks/assets/battery_{full,mid,low}.svg` and the same `/sys/class/power_supply` poll as `config/quickshell/overlay/InfoOverlayComponent.qml`, so both overlays stay visually consistent.
- `widget/Tray.tsx` -- system tray (`AstalTray`).
- `style.scss` -- brushed-metal chassis bevels, inset green-phosphor VFD screens, and the deck's four signal colors (green/amber/red/cyan). Edit colors at the top of the file.

Each widget is its own file specifically so you can add/remove/restyle one piece without touching the others.

## Running it

```sh
ags run ~/dotfiles/config/ags
```

It's also wired into `config/hypr/conf/autostart.lua`, so it starts automatically with Hyprland.

### Requires a NixOS rebuild first

`Workspaces.tsx`, `Tray.tsx`, `Vu.tsx`, `Media.tsx` and `Hud.tsx` import `gi://AstalHyprland`, `gi://AstalTray`, `gi://AstalWp` and `gi://AstalMpris`. Those typelibs come from `astal.hyprland` / `astal.tray` / `astal.wireplumber` / `astal.notifd` / `astal.mpris` in `nixos/modules/hyprland.nix`'s `environment.systemPackages` -- already declared there, but they only land on `GI_TYPELIB_PATH` after `sudo nixos-rebuild switch`, and `GI_TYPELIB_PATH` itself is set via `environment.sessionVariables`, which only takes effect for a *new* login session -- log out and back into Hyprland (not just restarting `ags`/`qs`) after a rebuild that adds a new typelib. Until then `ags run` will fail with `Typelib file for namespace '...' not found`.

## Regenerating `package.json`'s astal path

`package.json` pins an absolute `/nix/store/...-astal-gjs-.../share/astal/gjs` path for editor/`tsc` type resolution only (not needed for `ags run` itself). It can go stale after a NixOS generation bump; regenerate it with:

```sh
cd ~/dotfiles/config/ags
ags types -p
```
