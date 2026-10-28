package dev.petrolal.dotfiles.cli.commands

import dev.petrolal.dotfiles.desktop.BrowserLauncher
import dev.petrolal.dotfiles.desktop.KeybindHelper
import dev.petrolal.dotfiles.desktop.NotificationCenter
import dev.petrolal.dotfiles.desktop.NotificationLogger
import dev.petrolal.dotfiles.desktop.VhsShader
import dev.petrolal.dotfiles.desktop.VolumeControl
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option

@Command(group = "Desktop Operations")
class DesktopCommands {

    @Command(command = ["browser"], alias = ["launch-browser"], description = "Launch Wayland & GPU-optimized browser")
    fun browser(
        @Option(longNames = ["browser"], description = "Browser executable name", defaultValue = "google-chrome-stable")
        browser: String = "google-chrome-stable",

        @Option(description = "Additional arguments to pass to the browser")
        args: List<String>? = null
    ): String {
        val result = BrowserLauncher.launch(browser, args ?: emptyList())
        return if (result.isOk()) "Browser launched." else "Failed to launch browser."
    }

    @Command(command = ["vhs"], alias = ["toggle-vhs"], description = "Toggle VHS / CRT screen shader in Hyprland")
    fun toggleVhs(): String {
        val result = VhsShader.toggle()
        return if (result.isOk()) result.getOrNull() ?: "Toggled VHS mode." else "Failed to toggle VHS shader."
    }

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
        @Option(description = "Action: up, down, mute, mic-mute", required = true)
        action: String
    ): String {
        val result = VolumeControl.run(action)
        return if (result.isOk()) result.getOrNull() ?: "Volume adjusted." else "Failed to adjust volume."
    }

    @Command(command = ["lock"], description = "Lock session with Astroland retro lockscreen")
    fun lock(): String {
        val result = dev.petrolal.dotfiles.system.ProcessRunner.run(listOf("qs", "ipc", "call", "lockscreen", "lock"))
        return if (result.isOk()) "Session locked." else "Failed to lock session."
    }

    @Command(command = ["keybinds"], alias = ["keys", "help-keys"], description = "Show a Tofi keybinding cheat sheet")
    fun keybinds(): String {
        val exitCode = KeybindHelper.show()
        return if (exitCode == 0) "Keybind helper closed." else "Keybind helper exited with code $exitCode."
    }
}
