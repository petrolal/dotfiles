// Orchestrator.scala --- Deployment orchestration
// License: GPL-3.0-or-later
// Ported from Orchestrator.java (docs/scala-migration-checklist.md step 8).
package dev.petrolal.dotfiles.core

import java.nio.file.Path
import dev.petrolal.dotfiles.desktop.{DesktopServices, Xfconf}
import dev.petrolal.dotfiles.linker.Linker
import dev.petrolal.dotfiles.locker.ScreenLocker
import dev.petrolal.dotfiles.wallpaper.{GpuOffloadLauncher, Wallpaper, WallpaperDaemonManager}

/**
 * Composes the focused, single-purpose collaborators below into the CLI's
 * four commands (deploy, uninstall, wallpaper, toggle-hud). This class's one
 * reason to change is how those commands are sequenced -- not how linking,
 * settings, desktop reloads, or the wallpaper daemon's lifecycle actually
 * work, each of which lives in its own class:
 * <ul>
 *   <li>{@link Linker} -- symlink discovery/creation/removal</li>
 *   <li>{@link Xfconf} -- XFCE/GNOME settings</li>
 *   <li>{@link dev.petrolal.dotfiles.desktop.KeyboardShortcuts} -- keybinding catalog</li>
 *   <li>{@link DesktopServices} -- reloading live desktop components</li>
 *   <li>{@link WallpaperDaemonManager} -- daemon lifecycle from another invocation</li>
 *   <li>{@link GpuOffloadLauncher} -- the daemon's own self-relaunch decision</li>
 * </ul>
 */
object Orchestrator:

  case class DeployResult(ok: Boolean, failures: Int, successes: Int)
  case class UninstallResult(ok: Boolean, failures: Int, removed: Int)
  case class WallpaperResult(ok: Boolean, failures: Int)

  def deploy(dryRun: Boolean, verbose: Boolean, reload: Boolean, applySettings: Boolean,
             applyWallpaper: Boolean): DeployResult =
    if verbose then println(s"=== Deploying ${Theme.NAME} Dotfiles ===")
    val root = DotfilePaths.findDotfilesRoot()
    val home = DotfilePaths.userHomeDirectory()
    var failures = 0
    var successes = 0
    if verbose then
      println(s"Root:   $root")
      println(s"Target: $home")

    for mapping <- Linker.collectAllMappings(root) do
      if Linker.linkFile(mapping.from, mapping.to, root, home, dryRun, verbose).isOk then
        successes += 1
      else
        failures += 1

    if applySettings then
      failures += Xfconf.applyXfceSettings(dryRun, verbose)
      if reload then
        DesktopServices.reload(dryRun, verbose)

    // The config/autostart/invoker-wallpaper.desktop entry (symlinked by
    // the loop above like any other config/ file) covers future logins;
    // this covers the current session, so `deploy` leaves the freshest
    // build actually running immediately instead of only promising it for
    // next time.
    if applyWallpaper then
      if System.getenv("DISPLAY") == null then
        if verbose then println("[SKIP] No DISPLAY available, skipping wallpaper daemon (re)start.")
      else if dryRun then
        if verbose then println("[DRY-RUN] Would (re)start the 3D wallpaper daemon.")
      else
        WallpaperDaemonManager.restart(verbose)

    if verbose then
      if failures == 0 then
        println("Deployment finished successfully.")
      else
        System.err.println(s"Deployment finished with $failures failure(s).")

    DeployResult(failures == 0, failures, successes)

  def uninstall(dryRun: Boolean, verbose: Boolean): UninstallResult =
    if verbose then println(s"=== Removing ${Theme.NAME} Dotfiles Symlinks ===")
    val root = DotfilePaths.findDotfilesRoot()
    val home = DotfilePaths.userHomeDirectory()
    var failures = 0
    var removedCount = 0
    if verbose then println(s"Target: $home")

    for mapping <- Linker.collectAllMappings(root) do
      Linker.unlinkFile(mapping.to, home, dryRun, verbose) match
        case Linker.UnlinkOutcome.REMOVED => removedCount += 1
        case Linker.UnlinkOutcome.FAILED => failures += 1
        case Linker.UnlinkOutcome.SKIPPED => ()

    if verbose then
      if dryRun then
        println(s"Dry-run complete. $removedCount symlink(s) would be removed.")
      else
        val failureSuffix = if failures > 0 then s", $failures failure(s)" else ""
        println(s"Uninstallation complete. $removedCount symlink(s) removed$failureSuffix.")

    UninstallResult(failures == 0, failures, removedCount)

  def set3DWallpaper(dryRun: Boolean, verbose: Boolean, maxFps: Int): WallpaperResult =
    if verbose then println("=== Applying 3D Wallpaper ===")

    if dryRun then
      if verbose then println("[DRY-RUN] Would start OpenGL 3D wallpaper daemon.")
      WallpaperResult(true, 0)
    else if GpuOffloadLauncher.offloadAvailableAndNotActive() then
      // Empty means "couldn't resolve this executable's own path, never
      // attempted the relaunch" -- fall through to render in-process
      // instead of reporting a failure for something that never ran.
      GpuOffloadLauncher.relaunch(verbose, maxFps) match
        case Some(result) =>
          val ok = result.ok
          WallpaperResult(ok, if ok then 0 else 1)
        case None =>
          runWallpaperInProcess(maxFps)
    else
      runWallpaperInProcess(maxFps)

  private def runWallpaperInProcess(maxFps: Int): WallpaperResult =
    try
      new Wallpaper().start(maxFps)
      WallpaperResult(true, 0)
    catch
      case ex: Throwable =>
        System.err.println(s"Error on wallpaper application: ${ex.getMessage}")
        WallpaperResult(false, 1)

  /** Starts the interactive PAM-authenticated screen locker in-process
   * (blocks the calling thread until the user unlocks or the process is
   * signaled -- see [[ScreenLocker]]). `testTimeoutSeconds > 0`,
   * `dumpFramePath != null`, and `injectPassword != null` are
   * dev/test-only escape hatches; never pass any of them for a real lock. */
  def lockScreen(dryRun: Boolean, testTimeoutSeconds: Float, dumpFramePath: String, injectPassword: String): Boolean =
    if dryRun then
      println("[DRY-RUN] Would start the interactive screen locker.")
      true
    else
      try
        new ScreenLocker().start(testTimeoutSeconds, dumpFramePath, injectPassword)
        true
      catch
        case ex: Throwable =>
          System.err.println(s"Error on screen locker: ${ex.getMessage}")
          false

  /** Toggles the running wallpaper daemon's MangoHud performance overlay
   * on or off. See [[dev.petrolal.dotfiles.desktop.KeyboardShortcuts]]
   * for the keybinding that invokes this (via `invoker toggle-hud`). */
  def toggleWallpaperHud(verbose: Boolean): Boolean =
    WallpaperDaemonManager.toggleHud(verbose)
