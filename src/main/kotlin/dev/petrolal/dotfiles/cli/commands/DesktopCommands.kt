package dev.petrolal.dotfiles.cli.commands

import dev.petrolal.dotfiles.desktop.BrowserLauncher
import dev.petrolal.dotfiles.desktop.KeybindHelper
import dev.petrolal.dotfiles.desktop.NotificationCenter
import dev.petrolal.dotfiles.desktop.NotificationLogger
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
}
