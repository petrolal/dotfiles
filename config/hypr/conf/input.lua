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
    decoration = {
        screen_shader = os.getenv("HOME") .. "/.config/hypr/vhs.frag",
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
        -- Direct scanout skips the compositor pass (screen_shader above,
        -- Astroland's custom render pass) for newly mapped/fullscreen
        -- windows, so they show as a blank frame until something forces a
        -- full composite (e.g. grim screenshotting the output). Disabling
        -- it routes every frame through the normal render path.
        direct_scanout = false,
    },
})
