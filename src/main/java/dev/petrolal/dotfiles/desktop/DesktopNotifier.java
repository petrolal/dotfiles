// DesktopNotifier.java --- Desktop notification sender (org.freedesktop.Notifications over D-Bus)
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop;

import java.util.List;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.system.ProcessRunner;

/** Sends a desktop notification via gdbus. One reason to change: how a
 * notification gets shown -- callers never need to know gdbus is involved. */
public final class DesktopNotifier {
    private DesktopNotifier() {}

    public static void notify(String summary, String body) {
        if (!DotfilePaths.commandExists("gdbus")) return;
        ProcessRunner.run(List.of("gdbus", "call", "--session",
                "--dest", "org.freedesktop.Notifications",
                "--object-path", "/org/freedesktop/Notifications",
                "--method", "org.freedesktop.Notifications.Notify",
                "Wallpaper", "0", "utilities-system-monitor", summary, body, "[]", "{}", "3000"));
    }
}
