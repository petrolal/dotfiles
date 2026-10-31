-- env.lua --- environment variables exported into the Hyprland session
-- License: GPL-3.0-or-later

hl.env("XCURSOR_SIZE", "24")
hl.env("HYPRCURSOR_SIZE", "24")

-- Traditional black arrow pointer (DMZ-Black, nixos/modules/hyprland.nix's
-- vanilla-dmz package) instead of Hyprland's own bundled hyprcursor theme.
-- DMZ-Black only ships XCursor-format data, no hyprcursor-native theme, so
-- setting HYPRCURSOR_THEME to its name is what makes Hyprland fall back to
-- loading it as an XCursor theme instead of its own default.
hl.env("XCURSOR_THEME", "DMZ-Black")
hl.env("HYPRCURSOR_THEME", "DMZ-Black")

-- Include RetroRoom C++ QML plugin build in QML2_IMPORT_PATH
local current_qml_path = os.getenv("QML2_IMPORT_PATH") or ""
hl.env("QML2_IMPORT_PATH", DOTFILES_DIR .. "/build/plugins/retroroom" .. (current_qml_path ~= "" and (":" .. current_qml_path) or ""))

