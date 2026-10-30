// Screenshot.kt --- Capture a screenshot with grim/slurp and notify
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.domain.DotfileError.IoError
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner
import java.nio.file.Files

object Screenshot {
    /** `mode` is "area" (interactive region, via slurp) or "full" (whole screen). */
    @JvmStatic
    fun capture(mode: String): Result<String> {
        val picturesDir = DotfilePaths.userHomeDirectory().resolve("Pictures")
        Files.createDirectories(picturesDir)
        val file = picturesDir.resolve("screenshot-${System.currentTimeMillis() / 1000}.png").toString()

        val grimCmd = when (mode) {
            "area" -> {
                val region = ProcessRunner.captureOutput(listOf("slurp")).getOrNull()?.trim()
                if (region.isNullOrBlank()) return Result.err(IoError("No region selected"))
                listOf("grim", "-g", region, file)
            }
            "full" -> listOf("grim", file)
            else -> return Result.err(IoError("Unknown screenshot mode: $mode"))
        }

        when (val result = ProcessRunner.run(grimCmd)) {
            is Result.Err -> return Result.err(result.error)
            is Result.Ok -> {}
        }
        Notifier.toast("Screenshot saved", file)
        return Result.ok(file)
    }
}
