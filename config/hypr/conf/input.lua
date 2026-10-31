-- input.lua --- keyboard/touchpad input, misc compositor behavior, and
-- decoration/render settings
-- License: GPL-3.0-or-later

-- Never set before, so Hyprland fell back to plain "English (US)" with no
-- accent support at all. Mirrors nixos/modules/desktop.nix's
-- `console.keyMap = "us-acentos"` (the TTY-only keymap, which this doesn't
-- cover) on the Wayland side: physical US keyboard, dead-key-composed
-- accents (XKB's "us(intl)" variant) for typing Portuguese.
hl.config({
    input = {
        kb_layout = "us",
        kb_variant = "intl",
        touchpad = {
            disable_while_typing = false,
        },
    },
    misc = {
        disable_hyprland_logo = true,
        disable_splash_rendering = true,
        background_color = 0x000000,
    },
    -- Imp98's window-border colors, ported from the old xfwm4 themerc
    -- (active_color_1/inactive_border_color) now that xfwm4 is gone and
    -- Hyprland draws its own borders natively -- no window manager theme
    -- engine needed.
    general = {
        col = {
            active_border = 0xffB81424,
            -- Imp98's mid_light_shadow gray (not black -- that's the
            -- background color and would make unfocused windows borderless).
            inactive_border = 0xff4a4a4a,
        },
        border_size = 2,
        gaps_out = 8,
    },
    decoration = {
        -- Square corners to match the Windows-98 look the old theme had.
        rounding = 0,
        -- VHS/CRT shader starts off; toggle with mainMod+V (see
        -- VhsShader.kt / keybindings.lua "toggle-vhs"), which flips
        -- decoration:screen_shader live via `hyprctl eval`.
        screen_shader = "",
        -- Disabled: on this host's NVIDIA/Intel PRIME setup, Hyprland
        -- aborts every time a window's drop shadow gets rendered
        -- (CHyprDropShadowDecoration::render -> renderRoundedShadow),
        -- crashing the whole session back to the greetd/tuigreet login
        -- screen the instant any window opens (autostart apps, kitty, ...).
        -- Confirmed from journalctl coredump backtraces on 2026-10-28.
        shadow = {
            enabled = false,
        },
    },
    render = {
        -- Direct scanout skips the compositor pass (screen_shader above)
        -- for newly mapped/fullscreen windows, so they show as a blank
        -- frame until something forces a full composite (e.g. grim
        -- screenshotting the output). Disabling it routes every frame
        -- through the normal render path.
        direct_scanout = false,
    },
    animations = {
        enabled = true,
        bezier = {
            "snappy, 0.05, 0.9, 0.1, 1.05",
        },
        animation = {
            "windows, 1, 2, snappy, popin 90%",
            "windowsOut, 1, 2, snappy, popin 90%",
            "windowsMove, 1, 2, snappy",
            "border, 1, 2, snappy",
            "fade, 1, 2, snappy",
            "workspaces, 1, 2, snappy, slide",
        },
    },
})
