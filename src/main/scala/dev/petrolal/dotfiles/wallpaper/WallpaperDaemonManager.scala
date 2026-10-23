// WallpaperDaemonManager.scala --- Manages the wallpaper daemon as an external process
// License: GPL-3.0-or-later
// Ported from WallpaperDaemonManager.java (docs/scala-migration-checklist.md step 5).
//
// Finds, stops, starts, restarts, and toggles the wallpaper daemon from a
// *different* invocation of the CLI (deploy, toggle-hud) -- as opposed to
// GpuOffloadLauncher, which handles the daemon deciding how to render
// itself on its own startup. One reason to change: how the daemon is
// discovered and (re)started as an OS process.
package dev.petrolal.dotfiles.wallpaper

import java.io.IOException
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.jdk.StreamConverters.*

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.desktop.DesktopNotifier
import dev.petrolal.dotfiles.system.ProcessRunner

object WallpaperDaemonManager:

  /** Env vars MangoHud needs to find NVIDIA's management library, which
    * NixOS keeps under /run/opengl-driver/lib rather than a standard system
    * lib path, plus the flag that actually activates its LD_PRELOAD hook. */
  private val MangohudEnv: Map[String, String] = Map(
    "LD_LIBRARY_PATH" -> "/run/opengl-driver/lib",
    "MANGOHUD" -> "1"
  )

  /** Stops any already-running daemon (so a redeploy actually picks up a
    * freshly rebuilt binary or shader instead of leaving the old process
    * running alongside a new one) and starts a fresh one, detached. */
  def restart(verbose: Boolean): Unit =
    stopAll(verbose)
    start(verbose, false)

  /** Stops the running daemon and restarts it with MangoHud's on-screen
    * FPS/frametime/GPU overlay toggled on or off, whichever it wasn't
    * already doing. Meant for a keyboard shortcut (see KeyboardShortcuts)
    * so performance can be checked on demand without a terminal. */
  def toggleHud(verbose: Boolean): Boolean =
    val launcher = ProcessHandle.allProcesses()
      .toScala(LazyList)
      .filter(isWallpaperDaemon)
      .minByOption(_.pid())
    val wasHudActive = launcher.exists(isMangoHudActive)

    stopAll(verbose)

    val enableHud = !wasHudActive
    start(verbose, enableHud)
    DesktopNotifier.notify("Performance HUD",
      if enableHud then "Enabled — FPS / frametime / GPU overlay" else "Disabled")
    true

  /** Starts the wallpaper daemon detached, optionally wrapped with MangoHud
    * for an on-screen FPS/frametime/GPU overlay. Falls back to a plain start
    * if MangoHud was requested but isn't installed. */
  def start(verbose: Boolean, withHud: Boolean): Unit =
    val exePathOpt = ProcessHandle.current().info().command().toScala
    exePathOpt match
      case None =>
        if verbose then
          println("[SKIP] Could not determine this executable's path; not starting the wallpaper daemon.")
      case Some(exePath) =>
        var useHud = withHud
        if useHud && !DotfilePaths.commandExists("mangohud") then
          if verbose then println("[SKIP] mangohud not found; starting without the performance HUD.")
          useHud = false

        if verbose then
          println(if useHud then "Starting 3D wallpaper daemon (with performance HUD)..."
                  else "Starting 3D wallpaper daemon...")

        if useHud then
          ProcessRunner.spawnDetached(List("mangohud", exePath, "wallpaper", "--quiet"), MangohudEnv)
        else
          ProcessRunner.spawnDetached(List(exePath, "wallpaper", "--quiet"))

  def stopAll(verbose: Boolean): Unit =
    val existing = ProcessHandle.allProcesses().toScala(LazyList).filter(isWallpaperDaemon).toList
    if existing.nonEmpty then
      if verbose then println(s"Stopping ${existing.size} running wallpaper daemon process(es)...")
      val exits = existing.map { ph =>
        ph.destroy()
        ph.onExit()
      }
      try
        // Our own daemon's shutdown-hook watchdog gives up after 2s (see
        // Wallpaper.start()); wait slightly longer than that for a clean
        // handoff, but this is best-effort -- if it's still not gone, the
        // new instance starts anyway rather than blocking the caller on it.
        CompletableFuture.allOf(exits*).get(3, TimeUnit.SECONDS)
        ()
      catch case _: Exception => ()

  private def isWallpaperDaemon(ph: ProcessHandle): Boolean =
    val commandLine = ph.info().commandLine().toScala.getOrElse("")
    (commandLine.contains("imp") || commandLine.contains("invoker")) && commandLine.contains("wallpaper")

  /** Reads MANGOHUD=1 out of a running process's own environment (Linux
    * only, via /proc -- ProcessHandle itself has no API for another
    * process's environment, by design). */
  private def isMangoHudActive(ph: ProcessHandle): Boolean =
    try
      val environ = Files.readString(Path.of(s"/proc/${ph.pid()}/environ"))
      environ.split("\u0000").contains("MANGOHUD=1")
    catch case _: IOException => false
