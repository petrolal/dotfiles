// DotfilePaths.java --- Path and environment utilities
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DotfilePaths {
    @JvmStatic
    fun userHomeDirectory(): Path {
        val home = System.getenv("HOME")
        return Paths.get(home ?: System.getProperty("user.home"))
    }

    private fun normalizeDirParent(dir: Path): Path {
        val last = dir.fileName
        if (last != null && (last.toString() == "src" || last.toString() == "bin")) {
            val parent = dir.parent
            return parent ?: dir
        }
        return dir
    }

    @JvmStatic
    fun findDotfilesRoot(): Path {
        val envDir = System.getenv("DOTFILES_DIR")
        if (envDir != null) {
            val p = Paths.get(envDir)
            if (Files.exists(p)) {
                try {
                    return p.toRealPath()
                } catch (ignored: IOException) {
                    return p.toAbsolutePath().normalize()
                }
            }
        }
        val exePath = ProcessHandle.current().info().command()
        if (exePath.isPresent()) {
            val exe = Paths.get(exePath.get())
            if (Files.exists(exe)) {
                try {
                    val real = exe.toRealPath()
                    val dir = real.getParent()
                    if (dir != null) {
                        return normalizeDirParent(dir)
                    }
                } catch (ignored: IOException) {
                    // fall through to cwd
                }
            }
        }
        return Paths.get("").toAbsolutePath().normalize()
    }

    @JvmStatic
    fun commandExists(cmd: String?): Boolean {
        if (cmd.isNullOrEmpty()) return false
        if (cmd.indexOf('/') >= 0) {
            val p = Paths.get(cmd)
            return Files.isRegularFile(p) && Files.isExecutable(p)
        }
        val pathEnv = System.getenv("PATH") ?: return false
        for (dir in pathEnv.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (dir.isEmpty()) continue
            val candidate = Paths.get(dir).resolve(cmd)
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true
            }
        }
        return false
    }
}
