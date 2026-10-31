// Env.qml --- Shared path/environment resolution for the Quickshell config
// License: GPL-3.0-or-later
//
// Mirrors DotfilePaths.findDotfilesRoot() (src/main/kotlin/.../core/DotfilePaths.kt)
// and config/hypr/hyprland.lua's DOTFILES_DIR fallback, so a repo cloned
// somewhere other than ~/dotfiles doesn't silently break this part of the
// config too -- and so asset/cache paths aren't hardcoded to one user's home
// directory across every notification/lock/overlay file.
pragma Singleton

import Quickshell

Singleton {
    function envOr(name, fallback) {
        const v = Quickshell.env(name);
        return (v !== undefined && v !== null && String(v).length > 0) ? v : fallback;
    }

    readonly property string home: envOr("HOME", "/home/" + envOr("USER", "user"))
    readonly property string dotfilesDir: envOr("DOTFILES_DIR", home + "/dotfiles")
    readonly property string assetsDir: dotfilesDir + "/assets"
    readonly property string cacheDir: envOr("XDG_CACHE_HOME", home + "/.cache") + "/notifications"
}
