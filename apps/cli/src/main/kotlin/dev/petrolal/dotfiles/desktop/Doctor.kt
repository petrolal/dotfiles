// Doctor.kt --- System diagnostics and health check for the retro desktop environment
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner
import java.io.File
import java.nio.file.Files

object Doctor {

    data class CheckItem(val name: String, val status: Boolean, val detail: String)

    @JvmStatic
    fun run(): Result<String> {
        val checks = mutableListOf<CheckItem>()

        // 1. Wayland & Compositor
        val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
        val hyprlandRunning = ProcessRunner.run(listOf("pgrep", "-f", "Hyprland")).isOk()
        checks.add(
            CheckItem(
                "Compositor (Hyprland)",
                hyprlandRunning && waylandDisplay != null,
                if (hyprlandRunning) "Running on ${waylandDisplay ?: "Wayland"}" else "Hyprland not running"
            )
        )

        // 2. D-Bus Session Bus
        val dbusOk = ProcessRunner.run(
            listOf("dbus-send", "--session", "--dest=org.freedesktop.DBus", "--type=method_call", "/org/freedesktop/DBus", "org.freedesktop.DBus.GetId")
        ).isOk()
        checks.add(CheckItem("D-Bus Session Bus", dbusOk, if (dbusOk) "Active" else "Not responding"))

        // 3. Audio Server
        val wpOk = ProcessRunner.run(listOf("pgrep", "-f", "wireplumber")).isOk()
        checks.add(CheckItem("Audio Server (WirePlumber)", wpOk, if (wpOk) "Active" else "Stopped"))

        // 4. Status Bar (AGS)
        val barOk = BarLauncher.isBarRunning()
        checks.add(CheckItem("Status Bar (AGS)", barOk, if (barOk) "PID ${BarLauncher.getBarPid()}" else "Stopped"))

        // 5. Quickshell HUD & Overlays
        val hudOk = HudLauncher.isHudRunning()
        checks.add(CheckItem("HUD Overlays (Quickshell)", hudOk, if (hudOk) "PID ${HudLauncher.getHudPid()}" else "Stopped"))

        // 6. Wallpaper Daemon (hyprpaper)
        val paperOk = ProcessRunner.run(listOf("pgrep", "-f", "hyprpaper")).isOk()
        checks.add(CheckItem("Wallpaper Daemon (hyprpaper)", paperOk, if (paperOk) "Active" else "Stopped"))

        // 7. Idle Manager (hypridle)
        val idleOk = ProcessRunner.run(listOf("pgrep", "-f", "hypridle")).isOk()
        checks.add(CheckItem("Idle Screensaver (hypridle)", idleOk, if (idleOk) "Active" else "Stopped"))

        // 8. Notification Logger
        val notifOk = ProcessRunner.run(listOf("pgrep", "-f", "imp notif-logger")).isOk()
        checks.add(CheckItem("Notification Logger daemon", notifOk, if (notifOk) "Active" else "Stopped"))

        // 9. Music Player Daemon (MPD)
        val mpdOk = ProcessRunner.run(listOf("pgrep", "-f", "mpd")).isOk()
        checks.add(CheckItem("Music Daemon (MPD)", mpdOk, if (mpdOk) "Active" else "Stopped"))

        // 10. Key Dotfile Symlinks
        val home = DotfilePaths.userHomeDirectory()
        val symlinkPaths = listOf(".config/hypr/hyprland.lua", ".config/quickshell/shell.qml", ".config/ags/app.ts")
        for (rel in symlinkPaths) {
            val file = home.resolve(rel)
            val isLink = Files.isSymbolicLink(file)
            val targetExists = Files.exists(file)
            checks.add(
                CheckItem(
                    "Dotfile: ~/$rel",
                    isLink && targetExists,
                    if (isLink && targetExists) "Valid symlink" else if (isLink) "Broken symlink" else "Missing or not a symlink"
                )
            )
        }

        val total = checks.size
        val passed = checks.count { it.status }

        val output = buildString {
            appendLine("=== RetroVHSSucks Desktop Doctor ===")
            appendLine()
            for (item in checks) {
                val tag = if (item.status) "[  OK  ]" else "[ WARN ]"
                appendLine(String.format("%s %-32s : %s", tag, item.name, item.detail))
            }
            appendLine()
            append("Summary: $passed / $total components healthy.")
        }

        return Result.ok(output)
    }
}
