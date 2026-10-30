// VhsShader.kt --- Toggle VHS / CRT screen shader on and off in Hyprland
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner

object VhsShader {
    @JvmStatic
    fun toggle(): Result<String> {
        val home = DotfilePaths.userHomeDirectory().toString()
        val shaderPath = "$home/.config/hypr/vhs.frag"

        val currentResult = ProcessRunner.captureOutput(listOf("hyprctl", "getoption", "decoration:screen_shader", "-j"))
        val current = currentResult.getOrNull() ?: ""

        val isVhsActive = current.contains("vhs.frag")
        val (evalCmd, notifStatus) = if (isVhsActive) {
            """hl.config({ decoration = { screen_shader = "" } })""" to "Desativado"
        } else {
            """hl.config({ decoration = { screen_shader = "$shaderPath" } })""" to "Ativado"
        }

        when (val evalResult = ProcessRunner.run(listOf("hyprctl", "eval", evalCmd))) {
            is Result.Err -> return Result.err(evalResult.error)
            is Result.Ok -> {}
        }
        // Best-effort: a failed OSD toast shouldn't fail a shader toggle that
        // already applied successfully.
        Notifier.toast("Modo VHS", notifStatus, "video-display")

        return Result.ok("Modo VHS $notifStatus")
    }
}
