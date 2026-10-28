-- astroland.lua --- loads and wires up the Astroland Hyprland plugin
-- License: GPL-3.0-or-later
--
-- Expects `mainMod` to already be set as a global by the loader
-- (config/hypr/hyprland.lua) before this file is dofile'd.
--
-- Official Astroland Hyprland plugin (libastroland.so).
-- To build: in nix-shell / with cmake, run `cmake -B build && cmake --build build`
-- inside astroland/plugin/.
hl.plugin.load(os.getenv("HOME") .. "/dotfiles/astroland/plugin/build/libastroland.so")

local function file_exists(path)
    local file = io.open(path, "r")
    if file then
        file:close()
        return true
    end
    return false
end

if hl.plugin.astroland ~= nil then
    hl.config({
        general = { layout = "astrolayout" },
        plugin = {
            astroland = {
                spawn_moons = true,
                -- Cosmetic swap: moons render as a looping animated GIF
                -- instead of their static sprite, and bullets bounce off
                -- them instead of damaging them. Off by default, same as
                -- the original.
                loon_moons = false,
            },
        },
    })

    -- mod+ctrl+shift+S to save the current ship/bullet/moon state to
    -- ~/.config/hypr/save.lua
    hl.bind(mainMod .. " + CTRL + SHIFT + S", function()
        hl.plugin.astroland.save()
    end, { description = "Save astroland game state" })

    -- mod+Escape to unfocus all windows and immediately pilot the ship
    hl.bind(mainMod .. " + Escape", function()
        hl.plugin.astroland.unfocus()
    end, { description = "Unfocus windows to pilot astroland ship" })

    -- Load the save file (if any) once Hyprland's ready, same as the original.
    hl.on("hyprland.start", function()
        local savePath = os.getenv("HOME") .. "/.config/hypr/save.lua"
        if file_exists(savePath) then
            dofile(savePath)
        end
    end)
end
