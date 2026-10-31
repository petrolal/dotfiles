// RetroRoomControl.kt --- 3D Retro Room camera and navigation controller via Quickshell IPC
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.domain.DotfileError.CommandFailed
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner

object RetroRoomControl {

    val VALID_ANCHORS = listOf("home", "terminal", "music")

    @JvmStatic
    fun travel(anchor: String): Result<String> {
        val target = anchor.lowercase().trim()
        if (target !in VALID_ANCHORS) {
            return Result.err(CommandFailed("unknown anchor: '$anchor' (valid: ${VALID_ANCHORS.joinToString(", ")})", 1))
        }
        if (!HudLauncher.isHudRunning()) {
            return Result.err(CommandFailed("Quickshell HUD is not running. Start it with 'imp hud' or ensure qs is running.", 1))
        }

        val result = ProcessRunner.captureOutput(listOf("qs", "ipc", "call", "room", "travel", target))
        return when (result) {
            is Result.Ok -> {
                val response = result.value.trim()
                when (response) {
                    "OK" -> Result.ok("Camera traveling to anchor: $target")
                    "BUSY" -> Result.ok("Room camera is currently transitioning (BUSY).")
                    else -> Result.ok("Room response: $response")
                }
            }
            is Result.Err -> Result.err(result.error)
        }
    }

    @JvmStatic
    fun reset(): Result<String> {
        if (!HudLauncher.isHudRunning()) {
            return Result.err(CommandFailed("Quickshell HUD is not running. Start it with 'imp hud' or ensure qs is running.", 1))
        }
        val result = ProcessRunner.captureOutput(listOf("qs", "ipc", "call", "room", "reset"))
        return when (result) {
            is Result.Ok -> Result.ok("Camera reset to home anchor.")
            is Result.Err -> Result.err(result.error)
        }
    }

    @JvmStatic
    fun toggleVhs(): Result<String> {
        if (!HudLauncher.isHudRunning()) {
            return Result.err(CommandFailed("Quickshell HUD is not running. Start it with 'imp hud' or ensure qs is running.", 1))
        }
        val result = ProcessRunner.captureOutput(listOf("qs", "ipc", "call", "room", "toggleVhs"))
        return when (result) {
            is Result.Ok -> Result.ok("3D Room VHS shader toggled: ${result.value.trim()}")
            is Result.Err -> Result.err(result.error)
        }
    }

    @JvmStatic
    fun status(): Result<String> {
        val hudActive = HudLauncher.isHudRunning()
        return buildString {
            appendLine("3D Retro Room Status:")
            appendLine("  Quickshell backend: ${if (hudActive) "ACTIVE" else "STOPPED"}")
            appendLine("  Registered anchors: ${VALID_ANCHORS.joinToString(", ")}")
            append("  Usage: imp room travel --anchor [${VALID_ANCHORS.joinToString("|")}]")
        }.let { Result.ok(it) }
    }
}
