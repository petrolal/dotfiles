// DesktopServices.java --- Reloads live XFCE desktop component processes
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop;

import java.util.List;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.system.ProcessRunner;

/** Restarts the XFCE desktop components that pick up new settings only on
 * restart (panel, window manager, xsettings daemon, notifyd, file manager).
 * One reason to change: which components get reloaded and how. */
public final class DesktopServices {
    private DesktopServices() {}

    /** Spawned with Theme.ENV by ProcessRunner.spawnDetached, so restarted
     * components pick up the theme regardless of the session environment. */
    private static final List<List<String>> RELOAD_COMMANDS = List.of(
            List.of("xfsettingsd", "--replace"),
            List.of("sh", "-c", "if pgrep -x xfce4-panel >/dev/null 2>&1; then xfce4-panel -r; else xfce4-panel & fi"),
            List.of("xfwm4", "--replace"),
            List.of("pkill", "-f", "xfce4-notifyd"),
            List.of("thunar", "-q"));

    /** Reloads active XFCE desktop components if running in an X11 session. */
    public static void reload(boolean dryRun, boolean verbose) {
        if (System.getenv("DISPLAY") == null) {
            if (verbose)
                System.out.println("[SKIP] No DISPLAY available, skipping desktop reload.");
            return;
        }
        if (verbose)
            System.out.println("Reloading XFCE services...");

        for (List<String> args : RELOAD_COMMANDS) {
            String binary = args.getFirst();
            if (!DotfilePaths.commandExists(binary)) {
                if (verbose)
                    System.out.println("[SKIP] " + binary + " not found, skipping.");
            } else if (dryRun) {
                System.out.println("[DRY-RUN] Would execute: " + String.join(" ", args));
            } else {
                ProcessRunner.spawnDetached(args);
            }
        }
    }
}
