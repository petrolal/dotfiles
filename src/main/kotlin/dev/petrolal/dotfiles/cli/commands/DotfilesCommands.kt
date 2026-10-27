package dev.petrolal.dotfiles.cli.commands

import dev.petrolal.dotfiles.cli.Cli
import dev.petrolal.dotfiles.core.Orchestrator
import dev.petrolal.dotfiles.core.Theme
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option

@Command(group = "Dotfiles Management")
class DotfilesCommands {

    @Command(command = ["deploy"], description = "Perform full deployment (link, configure, reload)")
    fun deploy(
        @Option(longNames = ["dry-run"], shortNames = ['n'], description = "Simulate actions without modifying filesystem or xfconf")
        dryRun: Boolean = false,

        @Option(longNames = ["quiet"], shortNames = ['q'], description = "Suppress non-error output")
        quiet: Boolean = false,

        @Option(longNames = ["links-only"], description = "Only symlink dotfiles (implies --no-xfconf --no-reload --no-wallpaper)")
        linksOnly: Boolean = false,

        @Option(longNames = ["no-xfconf"], description = "Do not apply XFCE desktop settings via xfconf")
        noXfconf: Boolean = false,

        @Option(longNames = ["no-reload"], description = "Do not reload XFCE desktop services")
        noReload: Boolean = false,

        @Option(longNames = ["no-wallpaper"], description = "Do not (re)start the 3D wallpaper daemon")
        noWallpaper: Boolean = false
    ): String {
        val verbose = !quiet
        val applySettings = !linksOnly && !noXfconf
        val reload = !linksOnly && !noReload
        val applyWallpaper = !linksOnly && !noWallpaper

        val ok = Orchestrator.deploy(dryRun, verbose, reload, applySettings, applyWallpaper).ok
        return if (ok) "Deployment completed successfully." else "Deployment failed."
    }

    @Command(command = ["uninstall"], description = "Remove all deployed dotfile symlinks safely")
    fun uninstall(
        @Option(longNames = ["dry-run"], shortNames = ['n'], description = "Simulate actions without modifying filesystem")
        dryRun: Boolean = false,

        @Option(longNames = ["quiet"], shortNames = ['q'], description = "Suppress non-error output")
        quiet: Boolean = false
    ): String {
        val verbose = !quiet
        val ok = Orchestrator.uninstall(dryRun, verbose).ok
        return if (ok) "Uninstall completed successfully." else "Uninstall failed."
    }

    @Command(command = ["wallpaper"], description = "Start OpenGL 3D wallpaper")
    fun wallpaper(
        @Option(longNames = ["dry-run"], shortNames = ['n'], description = "Simulate actions")
        dryRun: Boolean = false,

        @Option(longNames = ["quiet"], shortNames = ['q'], description = "Suppress non-error output")
        quiet: Boolean = false,

        @Option(longNames = ["max-fps"], defaultValue = "0", description = "Cap the render loop to N frames/sec")
        maxFps: Int = 0
    ): String {
        val verbose = !quiet
        val ok = Orchestrator.set3DWallpaper(dryRun, verbose, maxFps).ok
        return if (ok) "Wallpaper started." else "Failed to start wallpaper."
    }

    @Command(command = ["overlay"], description = "Start the 3D app-launcher overlay")
    fun overlay(
        @Option(longNames = ["dry-run"], shortNames = ['n'], description = "Simulate actions")
        dryRun: Boolean = false
    ): String {
        val ok = Orchestrator.runOverlay(dryRun)
        return if (ok) "Overlay exited cleanly." else "Overlay encountered an error."
    }

    @Command(command = ["lock"], description = "Start the interactive PAM-authenticated screen locker")
    fun lock(
        @Option(longNames = ["dry-run"], shortNames = ['n'], description = "Simulate actions")
        dryRun: Boolean = false,

        @Option(longNames = ["test-timeout"], defaultValue = "0", description = "(DEV/TEST ONLY) auto-unlock after N seconds")
        testTimeout: Float = 0f,

        @Option(longNames = ["dump-frame"], description = "(DEV/TEST ONLY) render one frame and save to path")
        dumpFrame: String? = null,

        @Option(longNames = ["inject-password"], description = "(DEV/TEST ONLY) synthesize keypresses for password")
        injectPassword: String? = null
    ): String {
        val ok = Orchestrator.lockScreen(dryRun, testTimeout, dumpFrame, injectPassword)
        return if (ok) "Locker exited cleanly." else "Locker exited with failure."
    }

    @Command(command = ["toggle-hud"], description = "Toggle the running wallpaper's MangoHud performance overlay")
    fun toggleHud(
        @Option(longNames = ["quiet"], shortNames = ['q'], description = "Suppress non-error output")
        quiet: Boolean = false
    ): String {
        val verbose = !quiet
        val ok = Orchestrator.toggleWallpaperHud(verbose)
        return if (ok) "HUD toggled." else "Failed to toggle HUD."
    }

    @Command(command = ["version"], alias = ["-v"], description = "Display version information")
    fun version(): String {
        return buildString {
            appendLine("${Cli.PROGRAM_NAME} ${Cli.VERSION} (${Theme.NAME} / Windows 98)")
            appendLine("License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.")
            appendLine("This is free software: you are free to change and redistribute it.")
            append("There is NO WARRANTY, to the extent permitted by law.")
        }
    }
}
