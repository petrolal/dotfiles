package dev.petrolal.dotfiles.cli.commands

import dev.petrolal.dotfiles.desktop.BarLauncher
import dev.petrolal.dotfiles.desktop.BrowserLauncher
import dev.petrolal.dotfiles.desktop.Doctor
import dev.petrolal.dotfiles.desktop.HudLauncher
import dev.petrolal.dotfiles.desktop.KeybindHelper
import dev.petrolal.dotfiles.desktop.NotificationCenter
import dev.petrolal.dotfiles.desktop.NotificationLogger
import dev.petrolal.dotfiles.desktop.Notifier
import dev.petrolal.dotfiles.desktop.RetroRoomControl
import dev.petrolal.dotfiles.desktop.Screenshot
import dev.petrolal.dotfiles.desktop.VhsShader
import dev.petrolal.dotfiles.desktop.VolumeControl
import dev.petrolal.dotfiles.system.ProcessRunner
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option

@Command(group = "Desktop Operations")
class DesktopCommands : ImpCommandSupport() {

    @Command(command = ["browser"], alias = ["launch-browser"], description = "Launch Wayland & GPU-optimized browser")
    fun browser(
        @Option(longNames = ["browser"], description = "Browser executable name", defaultValue = "google-chrome-stable")
        browser: String = "google-chrome-stable",

        @Option(longNames = ["args"], description = "Additional arguments to pass to the browser")
        args: List<String>? = null
    ): String {
        BrowserLauncher.launch(browser, args ?: emptyList()).orThrow()
        return "Browser launched."
    }

    @Command(command = ["vhs"], alias = ["toggle-vhs"], description = "Toggle VHS / CRT screen shader in Hyprland")
    fun toggleVhs(): String = VhsShader.toggle().orThrow()

    @Command(command = ["notif-center"], description = "Open interactive Tofi notification center")
    fun notifCenter(): String {
        val exitCode = NotificationCenter.show()
        return if (exitCode == 0) "Notification center closed." else "Notification center exited with code $exitCode."
    }

    @Command(command = ["notif-logger"], description = "Run background D-Bus notification logger daemon")
    fun notifLogger(): String {
        NotificationLogger.run()
        return "Notification logger stopped."
    }

    @Command(command = ["volume"], description = "Control volume and OSD notifications (up, down, mute, mic-mute)")
    fun volume(
        @Option(longNames = ["action"], description = "Action: up, down, mute, mic-mute", required = true)
        action: String
    ): String {
        return VolumeControl.run(action).orThrow()
    }

    @Command(command = ["lock"], description = "Lock session with RetroVHSSucks retro lockscreen")
    fun lock(): String {
        if (!HudLauncher.isHudRunning()) {
            throw CommandFailedException("Quickshell HUD is not running. Start it with 'imp hud' or wait for desktop autostart.")
        }
        ProcessRunner.run(listOf("qs", "ipc", "call", "lockscreen", "lock")).orThrow()
        return "Session locked."
    }

    @Command(command = ["keybinds"], alias = ["keys", "help-keys"], description = "Show a Tofi keybinding cheat sheet")
    fun keybinds(): String {
        val exitCode = KeybindHelper.show()
        return if (exitCode == 0) "Keybind helper closed." else "Keybind helper exited with code $exitCode."
    }

    @Command(command = ["screenshot"], description = "Capture a screenshot with grim/slurp (mode: area, full)")
    fun screenshot(
        @Option(longNames = ["mode"], description = "Capture mode: area or full", defaultValue = "area")
        mode: String = "area"
    ): String {
        val file = Screenshot.capture(mode).orThrow()
        return "Screenshot saved: $file"
    }

    @Command(command = ["bar"], description = "Manage AGS status bar lifecycle (start, restart, stop, status)")
    fun bar(
        @Option(longNames = ["action"], shortNames = ['a'], description = "Action: start, restart, stop, status", defaultValue = "start")
        action: String = "start"
    ): String = BarLauncher.run(action).orThrow()

    @Command(command = ["hud"], description = "Manage Quickshell HUD overlays lifecycle (start, restart, stop, status)")
    fun hud(
        @Option(longNames = ["action"], shortNames = ['a'], description = "Action: start, restart, stop, status", defaultValue = "start")
        action: String = "start"
    ): String = HudLauncher.run(action).orThrow()

    @Command(command = ["room"], description = "Control 3D Retro Room camera navigation via Quickshell IPC")
    fun room(
        @Option(longNames = ["action"], shortNames = ['a'], description = "Action: travel, reset, status, vhs", defaultValue = "status")
        action: String = "status",

        @Option(longNames = ["anchor"], description = "Anchor target for travel (home, terminal, music)")
        anchor: String? = null
    ): String {
        return when (action.lowercase()) {
            "travel" -> {
                val target = anchor ?: throw CommandFailedException("missing mandatory --anchor option for room travel")
                RetroRoomControl.travel(target).orThrow()
            }
            "reset" -> RetroRoomControl.reset().orThrow()
            "status" -> RetroRoomControl.status().orThrow()
            "vhs" -> RetroRoomControl.toggleVhs().orThrow()
            else -> throw CommandFailedException("unknown room action: $action (valid: travel, reset, status, vhs)")
        }
    }

    @Command(command = ["doctor"], alias = ["check"], description = "Run full system diagnostic health check on desktop services and dotfiles")
    fun doctor(): String = Doctor.run().orThrow()
}
