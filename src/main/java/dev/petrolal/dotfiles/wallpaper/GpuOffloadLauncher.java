// GpuOffloadLauncher.java --- Decides whether the wallpaper should render via NVIDIA offload, and if so, does it
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.wallpaper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.petrolal.dotfiles.core.DotfilePaths;

import sun.misc.Signal;

/**
 * On hybrid Intel+NVIDIA laptops where an output is physically wired to the
 * discrete GPU (common for HDMI/DP on mux-less gaming laptops), rendering
 * through the default (Intel) GLX driver forces every frame bound for that
 * output through a cross-GPU copy before it can scan out -- confirmed on
 * real hardware to cause visible cursor/animation stutter on that monitor
 * specifically. Relaunching through the NixOS-provided {@code nvidia-offload}
 * wrapper (hardware.nvidia.prime.offload in nixos/modules/nvidia.nix) renders
 * the whole scene on the GPU that already owns the display pipeline instead
 * of whichever one GLX's default vendor selection happens to pick.
 * <p>
 * One reason to change: the self-relaunch decision and mechanics -- kept
 * separate from {@link WallpaperDaemonManager}, which manages the daemon as
 * an external process from a different invocation (deploy, toggle-hud); this
 * class only concerns the current process deciding how to render itself.
 */
public final class GpuOffloadLauncher {
    private GpuOffloadLauncher() {}

    public record LaunchResult(boolean ok) {}

    /** True when this process is already the nvidia-offload-relaunched child
     * (set by the wrapper before it execs into us), the guard against
     * relaunching forever. */
    private static boolean alreadyOffloaded() {
        return System.getenv("__NV_PRIME_RENDER_OFFLOAD") != null;
    }

    public static boolean offloadAvailableAndNotActive() {
        return !alreadyOffloaded() && DotfilePaths.commandExists("nvidia-offload");
    }

    /** Re-execs this same command through nvidia-offload, inheriting this
     * process's stdio/terminal so the relaunched process stays in the same
     * foreground process group -- Ctrl+C (terminal SIGINT) reaches both
     * processes directly via normal job-control semantics, no forwarding
     * needed for that case. But an explicit `kill -TERM <this-pid>` (not a
     * terminal signal, not process-group-wide) targets only this thin
     * launcher by default, leaving the real daemon orphaned but still
     * running -- confirmed on real hardware before this forwarding was
     * added. So TERM and INT are both explicitly forwarded to the child
     * here too, making this launcher transparent to either shutdown path.
     * Blocks until the relaunched daemon exits and relays its status.
     * <p>
     * Returns empty (not a failed result) when this process's own executable
     * path can't be determined -- that's "didn't attempt", distinct from
     * "attempted and failed", and tells the caller to fall back to rendering
     * in-process on whatever GPU GLX picks by default instead of reporting
     * an error. */
    public static Optional<LaunchResult> relaunch(boolean verbose, int maxFps) {
        Optional<String> exePath = ProcessHandle.current().info().command();
        if (exePath.isEmpty()) {
            return Optional.empty();
        }

        if (verbose) {
            System.out.println("Relaunching via nvidia-offload (an output on this machine is wired to the discrete GPU)...");
        }
        List<String> cmd = new ArrayList<>(List.of("nvidia-offload", exePath.get(), "wallpaper"));
        if (!verbose)
            cmd.add("--quiet");
        if (maxFps > 0)
            cmd.add("--max-fps=" + maxFps);

        try {
            Process child = new ProcessBuilder(cmd).inheritIO().start();

            Signal.handle(new Signal("TERM"), _ -> child.destroy());
            Signal.handle(new Signal("INT"), _ -> {
                try {
                    new ProcessBuilder("kill", "-INT", String.valueOf(child.pid())).start().waitFor();
                } catch (IOException ignored) {
                    // best-effort signal relay -- nothing to do if `kill` itself fails to run
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            int exitCode = child.waitFor();
            return Optional.of(new LaunchResult(exitCode == 0));
        } catch (IOException e) {
            System.err.println("Error relaunching wallpaper via nvidia-offload: " + e.getMessage());
            return Optional.of(new LaunchResult(false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of(new LaunchResult(false));
        }
    }
}
