// DesktopServices.scala --- Reloads live XFCE desktop component processes
// License: GPL-3.0-or-later
// Ported from DesktopServices.java (docs/scala-migration-checklist.md step 3).
//
// Restarts the XFCE desktop components that pick up new settings only on
// restart (panel, window manager, xsettings daemon, notifyd, file manager).
// One reason to change: which components get reloaded and how.
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.system.ProcessRunner

object DesktopServices:

  // Spawned with Theme.ENV by ProcessRunner.spawnDetached, so restarted
  // components pick up the theme regardless of the session environment.
  private val ReloadCommands: List[List[String]] = List(
    List("xfsettingsd", "--replace"),
    List("sh", "-c", "if pgrep -x xfce4-panel >/dev/null 2>&1; then xfce4-panel -r; else xfce4-panel & fi"),
    List("xfwm4", "--replace"),
    List("pkill", "-f", "xfce4-notifyd"),
    List("thunar", "-q")
  )

  /** Reloads active XFCE desktop components if running in an X11 session. */
  def reload(dryRun: Boolean, verbose: Boolean): Unit =
    if System.getenv("DISPLAY") == null then
      if verbose then println("[SKIP] No DISPLAY available, skipping desktop reload.")
    else
      if verbose then println("Reloading XFCE services...")

      for args <- ReloadCommands do
        val binary = args.head
        if !DotfilePaths.commandExists(binary) then
          if verbose then println(s"[SKIP] $binary not found, skipping.")
        else if dryRun then
          println(s"[DRY-RUN] Would execute: ${args.mkString(" ")}")
        else
          ProcessRunner.spawnDetached(args)
