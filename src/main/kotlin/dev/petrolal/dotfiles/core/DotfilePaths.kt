// DotfilePaths.java --- Path and environment utilities
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core

import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

object DotfilePaths {
    @JvmStatic
    fun userHomeDirectory(): Path {
        val home = System.getenv("HOME")
        return Paths.get(home ?: System.getProperty("user.home"))
    }

    /** Mirrors config/hypr/hyprland.lua's `DOTFILES_DIR` resolution exactly,
     * so both halves of the config agree on the repo root for a clone
     * living anywhere other than `~/dotfiles`.  */
    @JvmStatic
    fun findDotfilesRoot(): Path {
        val envDir = System.getenv("DOTFILES_DIR")
        val dir = if (envDir != null) Paths.get(envDir) else userHomeDirectory().resolve("dotfiles")
        return try {
            dir.toRealPath()
        } catch (ignored: IOException) {
            dir.toAbsolutePath().normalize()
        }
    }
}
