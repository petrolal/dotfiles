// BarLauncher.kt --- AGS status bar lifecycle manager
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.domain.DotfileError.CommandFailed
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner
import java.io.File

object BarLauncher {

    @JvmStatic
    fun run(action: String): Result<String> {
        return when (action.lowercase()) {
            "start" -> start()
            "restart" -> restart()
            "stop" -> stop()
            "status" -> status()
            else -> Result.err(CommandFailed("unknown bar action: $action (valid: start, restart, stop, status)", 1))
        }
    }

    @JvmStatic
    fun isBarRunning(): Boolean {
        val result = ProcessRunner.captureOutput(listOf("pgrep", "-f", "ags.js"))
        return result.isOk() && !result.getOrNull().isNullOrBlank()
    }

    @JvmStatic
    fun getBarPid(): String {
        val result = ProcessRunner.captureOutput(listOf("pgrep", "-f", "ags.js"))
        return result.getOrNull()?.lines()?.firstOrNull()?.trim() ?: "unknown"
    }

    @JvmStatic
    fun start(): Result<String> {
        if (isBarRunning()) {
            return Result.ok("AGS bar is already running (PID: ${getBarPid()}).")
        }

        // Wait up to 5s for Wayland display socket
        val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
        val runtimeDir = System.getenv("XDG_RUNTIME_DIR") ?: "/run/user/${ProcessHandle.current().info().user().orElse("")}"
        if (waylandDisplay != null) {
            val socketFile = File(runtimeDir, waylandDisplay)
            var attempts = 0
            while (!socketFile.exists() && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
        }

        // Wait up to 5s for D-Bus session bus to answer
        var dbusReady = false
        var attempts = 0
        while (!dbusReady && attempts < 50) {
            val dbusCheck = ProcessRunner.run(
                listOf("dbus-send", "--session", "--dest=org.freedesktop.DBus", "--type=method_call", "/org/freedesktop/DBus", "org.freedesktop.DBus.GetId")
            )
            if (dbusCheck.isOk()) {
                dbusReady = true
            } else {
                Thread.sleep(100)
                attempts++
            }
        }

        stopProcesses()

        val agsConfig = DotfilePaths.findDotfilesRoot().resolve("apps/ags").toString()
        ProcessRunner.spawnDetached(listOf("ags", "run", agsConfig))
        return Result.ok("AGS bar started.")
    }

    @JvmStatic
    fun restart(): Result<String> {
        stopProcesses()
        Thread.sleep(250)
        return start()
    }

    @JvmStatic
    fun stop(): Result<String> {
        stopProcesses()
        return Result.ok("AGS bar stopped.")
    }

    @JvmStatic
    fun status(): Result<String> {
        return if (isBarRunning()) {
            Result.ok("AGS bar is active (PID: ${getBarPid()}).")
        } else {
            Result.ok("AGS bar is stopped.")
        }
    }

    private fun stopProcesses() {
        ProcessRunner.run(listOf("ags", "quit", "-i", "bar"))
        ProcessRunner.run(listOf("pkill", "-f", "ags run"))
        ProcessRunner.run(listOf("pkill", "-f", "ags.js"))
        var attempts = 0
        while (isBarRunning() && attempts < 20) {
            Thread.sleep(50)
            attempts++
        }
    }
}
