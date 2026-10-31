// HudLauncher.kt --- Quickshell HUD overlay lifecycle manager
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.domain.DotfileError.CommandFailed
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner

object HudLauncher {

    @JvmStatic
    fun run(action: String): Result<String> {
        return when (action.lowercase()) {
            "start" -> start()
            "restart" -> restart()
            "stop" -> stop()
            "status" -> status()
            else -> Result.err(CommandFailed("unknown hud action: $action (valid: start, restart, stop, status)", 1))
        }
    }

    @JvmStatic
    fun isHudRunning(): Boolean {
        val result = ProcessRunner.captureOutput(listOf("pgrep", "-f", "quickshell"))
        return result.isOk() && !result.getOrNull().isNullOrBlank()
    }

    @JvmStatic
    fun getHudPid(): String {
        val result = ProcessRunner.captureOutput(listOf("pgrep", "-f", "quickshell"))
        return result.getOrNull()?.lines()?.firstOrNull()?.trim() ?: "unknown"
    }

    @JvmStatic
    fun start(): Result<String> {
        if (isHudRunning()) {
            return Result.ok("Quickshell HUD is already running (PID: ${getHudPid()}).")
        }
        ProcessRunner.spawnDetached(listOf("qs"), getEnv())
        return Result.ok("Quickshell HUD started.")
    }

    @JvmStatic
    fun restart(): Result<String> {
        stop()
        Thread.sleep(100)
        ProcessRunner.spawnDetached(listOf("qs"), getEnv())
        return Result.ok("Quickshell HUD restarted.")
    }

    @JvmStatic
    fun stop(): Result<String> {
        ProcessRunner.run(listOf("pkill", "-f", "quickshell"))
        var attempts = 0
        while (isHudRunning() && attempts < 20) {
            Thread.sleep(50)
            attempts++
        }
        return Result.ok("Quickshell HUD stopped.")
    }

    private fun getEnv(): Map<String, String> {
        val pluginDir = dev.petrolal.dotfiles.core.DotfilePaths.findDotfilesRoot().resolve("build/plugins/retroroom").toString()
        val currentQmlPath = System.getenv("QML2_IMPORT_PATH") ?: ""
        return mapOf("QML2_IMPORT_PATH" to if (currentQmlPath.isNotBlank()) "$pluginDir:$currentQmlPath" else pluginDir)
    }

    @JvmStatic
    fun status(): Result<String> {
        return if (isHudRunning()) {
            Result.ok("Quickshell HUD is active (PID: ${getHudPid()}).")
        } else {
            Result.ok("Quickshell HUD is stopped.")
        }
    }
}
