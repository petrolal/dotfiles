-- autostart.lua --- processes launched once Hyprland starts
-- License: GPL-3.0-or-later
--
-- Expects `imp_bin` and `DOTFILES_DIR` to already be set as globals by the
-- loader (config/hypr/hyprland.lua) before this file is dofile'd.

hl.on("hyprland.start", function ()
  hl.exec_cmd("hyprpaper")
  -- Idle-triggered screensaver -- see config/hypr/hypridle.conf.
  hl.exec_cmd("hypridle")
  -- Quickshell handles RetroVHSSucks overlays: battery/clock info overlay,
  -- robot notification popups, lockscreen, and screensaver.
  hl.exec_cmd("qs")
  -- AGS bar (config/ags/) -- VHS/camcorder-styled top bar: workspaces,
  -- clock, tray, battery. Needs astal.hyprland/.tray/.wireplumber/.notifd
  -- typelibs on GI_TYPELIB_PATH, which nixos/modules/hyprland.nix already
  -- lists in environment.systemPackages.
  hl.exec_cmd("ags run " .. DOTFILES_DIR .. "/config/ags")
  -- Background notification logger for Tofi notification center
  hl.exec_cmd(imp_bin .. " notif-logger")
end)
