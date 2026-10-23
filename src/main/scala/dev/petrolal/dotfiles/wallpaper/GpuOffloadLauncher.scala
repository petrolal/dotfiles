// GpuOffloadLauncher.scala --- Decides whether the wallpaper should render via NVIDIA offload, and if so, does it
// License: GPL-3.0-or-later
// Ported from GpuOffloadLauncher.java (docs/scala-migration-checklist.md step 5).
//
// On hybrid Intel+NVIDIA laptops where an output is physically wired to the
// discrete GPU (common for HDMI/DP on mux-less gaming laptops), rendering
// through the default (Intel) GLX driver forces every frame bound for that
// output through a cross-GPU copy before it can scan out -- confirmed on
// real hardware to cause visible cursor/animation stutter on that monitor
// specifically. Relaunching through the NixOS-provided nvidia-offload
// wrapper (hardware.nvidia.prime.offload in nixos/modules/nvidia.nix) renders
// the whole scene on the GPU that already owns the display pipeline instead
// of whichever one GLX's default vendor selection happens to pick.
//
// One reason to change: the self-relaunch decision and mechanics -- kept
// separate from WallpaperDaemonManager, which manages the daemon as an
// external process from a different invocation (deploy, toggle-hud); this
// object only concerns the current process deciding how to render itself.
package dev.petrolal.dotfiles.wallpaper

import java.io.IOException
import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*

import dev.petrolal.dotfiles.core.DotfilePaths

import sun.misc.Signal

object GpuOffloadLauncher:

  case class LaunchResult(ok: Boolean)

  /** True when this process is already the nvidia-offload-relaunched child
    * (set by the wrapper before it execs into us), the guard against
    * relaunching forever. */
  private def alreadyOffloaded(): Boolean =
    System.getenv("__NV_PRIME_RENDER_OFFLOAD") != null

  def offloadAvailableAndNotActive(): Boolean =
    !alreadyOffloaded() && DotfilePaths.commandExists("nvidia-offload")

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
    *
    * Returns None (not a failed result) when this process's own executable
    * path can't be determined -- that's "didn't attempt", distinct from
    * "attempted and failed", and tells the caller to fall back to rendering
    * in-process on whatever GPU GLX picks by default instead of reporting
    * an error. */
  def relaunch(verbose: Boolean, maxFps: Int): Option[LaunchResult] =
    val exePath = ProcessHandle.current().info().command().toScala
    exePath match
      case None => None
      case Some(exe) =>
        if verbose then
          println("Relaunching via nvidia-offload (an output on this machine is wired to the discrete GPU)...")

        val cmd = scala.collection.mutable.ListBuffer[String]("nvidia-offload", exe, "wallpaper")
        if !verbose then cmd += "--quiet"
        if maxFps > 0 then cmd += s"--max-fps=$maxFps"

        try
          val child = new ProcessBuilder(cmd.asJava).inheritIO().start()

          Signal.handle(new Signal("TERM"), _ => child.destroy())
          Signal.handle(new Signal("INT"), _ => {
            try new ProcessBuilder("kill", "-INT", String.valueOf(child.pid())).start().waitFor()
            catch
              case _: IOException => () // best-effort signal relay -- nothing to do if `kill` itself fails to run
              case _: InterruptedException => Thread.currentThread().interrupt()
          })

          val exitCode = child.waitFor()
          Some(LaunchResult(exitCode == 0))
        catch
          case e: IOException =>
            System.err.println(s"Error relaunching wallpaper via nvidia-offload: ${e.getMessage}")
            Some(LaunchResult(false))
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
            Some(LaunchResult(false))
