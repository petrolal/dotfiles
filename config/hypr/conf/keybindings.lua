-- keybindings.lua --- application launching, window/workspace management,
-- and media key bindings
-- License: GPL-3.0-or-later
--
-- Expects `mainMod` and `imp_bin` to already be set as globals by the
-- loader (config/hypr/hyprland.lua) before this file is dofile'd.
--
-- Keybinding scheme follows ML4W's dotfiles (mylinuxforwork/dotfiles,
-- config/hypr/conf/keybindings/default.lua) at the user's request --
-- same key combinations and intent, pointed at apps actually installed on
-- this NixOS host instead of ML4W's own script/quickshell ecosystem (which
-- isn't installed here). ML4W features with no local equivalent (cliphist,
-- dock, sidebar/calendar widgets, theme switcher, hyprsunset, overview) are
-- left out rather than bound to nothing. See docs/migration_plan.md.
--
-- XFCE has been fully decommissioned (docs/migration_plan.md section 0A):
-- every binding below that used to shell out to an xfce4-* binary now
-- targets a real standalone package (nixos/modules/packages.nix) instead,
-- and the status bar is AGS, not Waybar.

-- Applications
hl.bind(mainMod .. " + RETURN", hl.dsp.exec_cmd("kitty"), { description = "Open the terminal" })
hl.bind(mainMod .. " + B", hl.dsp.exec_cmd(imp_bin .. " browser"), { description = "Open the browser" })
hl.bind(mainMod .. " + E", hl.dsp.exec_cmd("thunar"), { description = "Open the filemanager" })
hl.bind(mainMod .. " + SPACE", hl.dsp.exec_cmd("tofi-drun --drun-launch=true"), { description = "Open application launcher (menu)" })
hl.bind(mainMod .. " + D", hl.dsp.exec_cmd("tofi-drun --drun-launch=true"), { description = "Open application launcher (menu)" })

-- Workspaces: focus with mainMod + [0-9], move window with mainMod + SHIFT + [0-9]
for i = 1, 10 do
    local key = i % 10 -- 10 maps to key 0
    hl.bind(mainMod .. " + " .. key,         hl.dsp.focus({ workspace = i}), { description = "Focus workspace " .. i })
    hl.bind(mainMod .. " + SHIFT + " .. key, hl.dsp.window.move({ workspace = i }), { description = "Move window to workspace " .. i })
end

-- Windows
hl.bind(mainMod .. " + Q", hl.dsp.window.close(), { description = "Kill active window" })
hl.bind(mainMod .. " + F", hl.dsp.window.fullscreen({ mode = "fullscreen", action = "toggle" }), { description = "Toggle Fullscreen" })
hl.bind(mainMod .. " + M", hl.dsp.window.fullscreen({ mode = "maximized", action = "toggle" }), { description = "Toggle Maximize Window" })
hl.bind(mainMod .. " + T", hl.dsp.window.float({ action = "toggle" }), { description = "Toggle Floating" })
hl.bind(mainMod .. " + J", hl.dsp.layout("togglesplit"), { description = "Toggle split" })
hl.bind(mainMod .. " + G", hl.dsp.group.toggle(), { description = "Toggle window group" })
hl.bind(mainMod .. " + K", hl.dsp.layout("swapsplit"), { description = "Swapsplit" })

hl.bind(mainMod .. " + left",  hl.dsp.focus({ direction = "left" }), { description = "Move focus left" })
hl.bind(mainMod .. " + right", hl.dsp.focus({ direction = "right" }), { description = "Move focus right" })
hl.bind(mainMod .. " + up",    hl.dsp.focus({ direction = "up" }), { description = "Move focus up" })
hl.bind(mainMod .. " + down",  hl.dsp.focus({ direction = "down" }), { description = "Move focus down" })

hl.bind(mainMod .. " + ALT + left",  hl.dsp.window.swap({ direction = "l" }), { description = "Swap tiled window left" })
hl.bind(mainMod .. " + ALT + right", hl.dsp.window.swap({ direction = "r" }), { description = "Swap tiled window right" })
hl.bind(mainMod .. " + ALT + up",    hl.dsp.window.swap({ direction = "u" }), { description = "Swap tiled window up" })
hl.bind(mainMod .. " + ALT + down",  hl.dsp.window.swap({ direction = "d" }), { description = "Swap tiled window down" })

hl.bind(mainMod .. " + SHIFT + right", hl.dsp.window.resize({ x = 100, y = 0, relative = true }),  { repeating = true, description = "Increase window width" })
hl.bind(mainMod .. " + SHIFT + left",  hl.dsp.window.resize({ x = -100, y = 0, relative = true }), { repeating = true, description = "Reduce window width" })
hl.bind(mainMod .. " + SHIFT + down",  hl.dsp.window.resize({ x = 0, y = 100, relative = true }),  { repeating = true, description = "Increase window height" })
hl.bind(mainMod .. " + SHIFT + up",    hl.dsp.window.resize({ x = 0, y = -100, relative = true }), { repeating = true, description = "Reduce window height" })

hl.bind(mainMod .. " + mouse:272", hl.dsp.window.drag(),   { mouse = true, description = "Move window with the mouse" })
hl.bind(mainMod .. " + mouse:273", hl.dsp.window.resize(), { mouse = true, description = "Resize window with the mouse" })

-- Native alt-tab (ML4W uses a quickshell app-switcher we don't have installed;
-- Hyprland's own cyclenext dispatcher gives the same key combo and effect).
hl.bind("ALT + Tab",         hl.dsp.exec_cmd("hyprctl dispatch cyclenext"), { description = "Switch applications" })
hl.bind("ALT + SHIFT + Tab", hl.dsp.exec_cmd("hyprctl dispatch cyclenext prev"), { description = "Switch applications (backwards)" })

-- Actions
-- grim/slurp are Wayland-native (xfce4-screenshooter was X11-only and is
-- gone along with the rest of XFCE); routed through `imp screenshot`
-- (Screenshot.kt) so the capture/save/notify pipeline lives in one place
-- instead of being copy-pasted across these three bindings.
hl.bind(mainMod .. " + PRINT", hl.dsp.exec_cmd(imp_bin .. " screenshot --mode area"), { description = "Take a screenshot" })
hl.bind(mainMod .. " + ALT + F", hl.dsp.exec_cmd(imp_bin .. " screenshot --mode full"), { description = "Take an instant full-screen screenshot" })
hl.bind(mainMod .. " + ALT + S", hl.dsp.exec_cmd(imp_bin .. " screenshot --mode area"), { description = "Take an instant area screenshot" })
hl.bind(mainMod .. " + SHIFT + R", hl.dsp.exec_cmd("hyprctl reload"), { description = "Reload Hyprland config" })
hl.bind(mainMod .. " + SHIFT + B", hl.dsp.exec_cmd(imp_bin .. " bar --action restart"), { description = "Restart AGS bar" })
hl.bind(mainMod .. " + SHIFT + H", hl.dsp.exec_cmd(imp_bin .. " hud --action restart"), { description = "Restart Quickshell HUD" })
hl.bind(mainMod .. " + N",        hl.dsp.exec_cmd(imp_bin .. " notif-center"), { description = "Open Tofi notification center" })
hl.bind(mainMod .. " + SLASH",    hl.dsp.exec_cmd(imp_bin .. " keybinds"), { description = "Show Tofi keybinding cheat sheet" })
hl.bind(mainMod .. " + L",        hl.dsp.exec_cmd(imp_bin .. " lock"), { description = "Lock Screen" })
hl.bind(mainMod .. " + CTRL + L", hl.dsp.exec_cmd(imp_bin .. " lock"), { description = "Lock Screen" })
hl.bind(mainMod .. " + CTRL + R", hl.dsp.exec_cmd("gpu-screen-recorder-gtk"), { description = "Open screen recorder" })
hl.bind(mainMod .. " + V", hl.dsp.exec_cmd(imp_bin .. " toggle-vhs"), { description = "Toggle VHS CRT screen shader" })
hl.bind(mainMod .. " + SHIFT + V", hl.dsp.exec_cmd(imp_bin .. " toggle-vhs"), { description = "Toggle VHS CRT screen shader" })

-- Special workspace (scratchpad)
hl.bind(mainMod .. " + S",         hl.dsp.workspace.toggle_special("scratchpad"), { description = "Toggle special workspace scratchpad" })
hl.bind(mainMod .. " + SHIFT + S", function()
    hl.dispatch(hl.dsp.window.float({ action = "toggle" }))
    hl.dispatch(hl.dsp.window.move({ workspace = "special:scratchpad" }))
end, { description = "Move window to special workspace scratchpad" })

-- Scroll through existing workspaces with mainMod + scroll
hl.bind(mainMod .. " + mouse_down", hl.dsp.focus({ workspace = "e+1" }), { description = "Switch to next workspace" })
hl.bind(mainMod .. " + mouse_up",   hl.dsp.focus({ workspace = "e-1" }), { description = "Switch to previous workspace" })

-- Laptop multimedia keys for volume and LCD brightness. Routed through
-- `imp volume` (VolumeControl.kt) rather than raw wpctl so these get the
-- same in-place-updating OSD notification as every other imp_bin-fronted
-- action here -- bare wpctl gives no OSD at all. imp is a GraalVM
-- native-image binary (~70ms per invocation measured), so this is fine
-- even held down with `repeating = true`.
hl.bind("XF86AudioRaiseVolume", hl.dsp.exec_cmd(imp_bin .. " volume --action up"),       { locked = true, repeating = true, description = "Raise volume" })
hl.bind("XF86AudioLowerVolume", hl.dsp.exec_cmd(imp_bin .. " volume --action down"),     { locked = true, repeating = true, description = "Lower volume" })
hl.bind("XF86AudioMute",        hl.dsp.exec_cmd(imp_bin .. " volume --action mute"),     { locked = true, repeating = true, description = "Mute audio" })
hl.bind("XF86AudioMicMute",     hl.dsp.exec_cmd(imp_bin .. " volume --action mic-mute"), { locked = true, repeating = true, description = "Mute microphone" })

-- Requires playerctl
hl.bind("XF86AudioNext",  hl.dsp.exec_cmd("playerctl next"),       { locked = true, description = "Next track" })
hl.bind("XF86AudioPause", hl.dsp.exec_cmd("playerctl play-pause"), { locked = true, description = "Pause audio" })
hl.bind("XF86AudioPlay",  hl.dsp.exec_cmd("playerctl play-pause"), { locked = true, description = "Play audio" })
hl.bind("XF86AudioPrev",  hl.dsp.exec_cmd("playerctl previous"),   { locked = true, description = "Previous track" })
