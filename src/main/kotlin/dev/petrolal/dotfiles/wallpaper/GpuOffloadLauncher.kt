// GpuOffloadLauncher.java --- Decides whether the wallpaper should render via NVIDIA offload, and if so, does it
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.wallpaper

import dev.petrolal.dotfiles.core.DotfilePaths
import sun.misc.Signal
import java.io.IOException

/**
 * On hybrid Intel+NVIDIA laptops where an output is physically wired to the
 * discrete GPU (common for HDMI/DP on mux-less gaming laptops), rendering
 * through the default (Intel) GLX driver forces every frame bound for that
 * output through a cross-GPU copy before it can scan out -- confirmed on
 * real hardware to cause visible cursor/animation stutter on that monitor
 * specifically. Relaunching through the NixOS-provided `nvidia-offload`
 * wrapper (hardware.nvidia.prime.offload in nixos/modules/nvidia.nix) renders
 * the whole scene on the GPU that already owns the display pipeline instead
 * of whichever one GLX's default vendor selection happens to pick.
 * 
 * One reason to change: the self-relaunch decision and mechanics -- kept
 * separate from [WallpaperDaemonManager], which manages the daemon as
 * an external process from a different invocation (deploy, toggle-hud); this
 * class only concerns the current process deciding how to render itself.
 */
object GpuOffloadLauncher {
    /** True when this process is already the nvidia-offload-relaunched child
     * (set by the wrapper before it execs into us), the guard against
     * relaunching forever.  */
    private fun alreadyOffloaded(): Boolean =
        System.getenv("__NV_PRIME_RENDER_OFFLOAD") != null

    fun offloadAvailableAndNotActive(): Boolean =
        !alreadyOffloaded() && DotfilePaths.commandExists("nvidia-offload")

    /** Re-execs this same command through nvidia-offload, inheriting this
     * process's stdio/terminal so the relaunched process stays in the same
     * foreground process group. */
    fun relaunch(verbose: Boolean, maxFps: Int): LaunchResult? {
        val exePathOpt = ProcessHandle.current().info().command()
        if (exePathOpt.isEmpty) {
            return null
        }
        val exePath = exePathOpt.get()

        if (verbose) {
            println("Relaunching via nvidia-offload (an output on this machine is wired to the discrete GPU)...")
        }
        val cmd = mutableListOf("nvidia-offload", exePath, "wallpaper")
        if (!verbose) cmd.add("--quiet")
        if (maxFps > 0) cmd.add("--max-fps=$maxFps")

        return try {
            val child = ProcessBuilder(cmd).inheritIO().start()

            Signal.handle(Signal("TERM")) { child.destroy() }
            Signal.handle(Signal("INT")) {
                try {
                    ProcessBuilder("kill", "-INT", child.pid().toString()).start().waitFor()
                } catch (_: IOException) {
                    // best-effort signal relay -- nothing to do if `kill` itself fails to run
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            val exitCode = child.waitFor()
            LaunchResult(exitCode == 0)
        } catch (e: IOException) {
            System.err.println("Error relaunching wallpaper via nvidia-offload: ${e.message}")
            LaunchResult(false)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            LaunchResult(false)
        }
    }

    @JvmRecord
    data class LaunchResult(val ok: Boolean)
}
