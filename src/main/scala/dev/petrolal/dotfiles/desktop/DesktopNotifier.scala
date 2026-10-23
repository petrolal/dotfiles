// DesktopNotifier.scala --- Desktop notification sender (org.freedesktop.Notifications over D-Bus)
// License: GPL-3.0-or-later
// Ported from DesktopNotifier.java (docs/scala-migration-checklist.md step 3).
//
// Sends a desktop notification via gdbus. One reason to change: how a
// notification gets shown -- callers never need to know gdbus is involved.
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.system.ProcessRunner

object DesktopNotifier:

  def notify(summary: String, body: String): Unit =
    if DotfilePaths.commandExists("gdbus") then
      ProcessRunner.run(List(
        "gdbus", "call", "--session",
        "--dest", "org.freedesktop.Notifications",
        "--object-path", "/org/freedesktop/Notifications",
        "--method", "org.freedesktop.Notifications.Notify",
        "Wallpaper", "0", "utilities-system-monitor", summary, body, "[]", "{}", "3000"
      ))
