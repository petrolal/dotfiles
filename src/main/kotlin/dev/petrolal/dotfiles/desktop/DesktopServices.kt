// DesktopServices.java --- Reloads live XFCE desktop component processes
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.system.ProcessRunner

/** Restarts the XFCE desktop components that pick up new settings only on
 * restart (panel, window manager, xsettings daemon, notifyd, file manager).
 * One reason to change: which components get reloaded and how.  */
object DesktopServices {
    /** Spawned with Theme.ENV by ProcessRunner.spawnDetached, so restarted
     * components pick up the theme regardless of the session environment.  */
    private val RELOAD_COMMANDS: List<List<String>> = listOf(
        listOf("xfsettingsd", "--replace"),
        listOf(
            "sh",
            "-c",
            "if pgrep -x xfce4-panel >/dev/null 2>&1; then xfce4-panel -r; else xfce4-panel & fi"
        ),
        listOf("xfwm4", "--replace"),
        listOf("pkill", "-f", "xfce4-notifyd"),
        listOf("thunar", "-q")
    )

    /** Reloads active XFCE desktop components if running in an X11 session.  */
    @JvmStatic
    fun reload(dryRun: Boolean, verbose: Boolean) {
        if (System.getenv("DISPLAY") == null) {
            if (verbose) println("[SKIP] No DISPLAY available, skipping desktop reload.")
            return
        }
        if (verbose) println("Reloading XFCE services...")

        for (args in RELOAD_COMMANDS) {
            val binary = args.first()
            if (!DotfilePaths.commandExists(binary)) {
                if (verbose) println("[SKIP] $binary not found, skipping.")
            } else if (dryRun) {
                println("[DRY-RUN] Would execute: ${args.joinToString(" ")}")
            } else {
                ProcessRunner.spawnDetached(args)
            }
        }
    }
}
