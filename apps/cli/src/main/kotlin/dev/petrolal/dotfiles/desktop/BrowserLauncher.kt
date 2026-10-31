// BrowserLauncher.kt --- Wayland & GPU-optimized browser launcher
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.domain.DotfileError.IoError
import dev.petrolal.dotfiles.domain.Result
import java.io.File
import java.io.IOException

object BrowserLauncher {
    val OPTIMIZED_FLAGS: List<String> = listOf(
        "--ozone-platform=wayland",
        "--use-gl=angle",
        "--use-angle=gl",
        "--enable-features=VaapiVideoDecodeLinuxGL,VaapiVideoDecoder,TouchpadOverscrollHistoryNavigation",
        "--enable-zero-copy"
    )

    @JvmStatic
    @JvmOverloads
    fun launch(browser: String = "google-chrome-stable", extraArgs: List<String> = emptyList()): Result<Unit> {
        val command = ArrayList<String>()
        command.add(browser)
        command.addAll(OPTIMIZED_FLAGS)
        command.addAll(extraArgs)

        try {
            val pb = ProcessBuilder(command)
            val env = pb.environment()
            env.remove("GBM_BACKEND")
            env.remove("LIBVA_DRIVER_NAME")
            env.remove("__GLX_VENDOR_LIBRARY_NAME")
            env["NIXOS_OZONE_WL"] = "1"

            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            pb.start()
            return Result.ok(Unit)
        } catch (e: IOException) {
            return Result.err(IoError(e.message ?: "Failed to launch browser: $browser"))
        }
    }
}
