// Cli.java --- Command-line entry point
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli

import dev.petrolal.dotfiles.core.Orchestrator
import dev.petrolal.dotfiles.core.Theme
import kotlin.system.exitProcess

object Cli {
    const val VERSION: String = "1.3.0"
    const val PROGRAM_NAME: String = "imp"

    private fun printHelp() {
        System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME)
        println("Deploy " + Theme.NAME + " dotfiles symlinks and configure desktop settings.\n")
        println("Commands:")
        println("  deploy            perform full deployment (link, configure, reload) [default]")
        println("  uninstall         remove all deployed dotfile symlinks safely")
        println("  wallpaper         start OpenGL 3D wallpaper")
        println("  overlay           start the 3D app-launcher overlay")
        println("  lock              start the interactive PAM-authenticated screen locker")
        println("  toggle-hud        toggle the running wallpaper's MangoHud performance overlay")
        println("  shell             start interactive Spring Shell REPL\n")
        println("Options:")
        println("  -u, --uninstall   remove all deployed dotfile symlinks safely")
        println("  -n, --dry-run     simulate actions without modifying filesystem or xfconf")
        println("  -q, --quiet       suppress non-error output")
        println("      --links-only  only symlink dotfiles (implies --no-xfconf --no-reload --no-wallpaper)")
        println("      --no-xfconf   do not apply XFCE desktop settings via xfconf")
        println("      --no-reload   do not reload XFCE desktop services")
        println("      --no-wallpaper  do not (re)start the 3D wallpaper daemon")
        println("      --max-fps=N   (wallpaper) cap the render loop to N frames/sec,")
        println("                    independent of vsync -- trade smoothness for")
        println("                    battery/thermal headroom on weak hardware")
        println("      --test-timeout=N  (lock, DEV/TEST ONLY) auto-unlock after N")
        println("                    seconds with no password check -- never use this")
        println("                    for a real lock, only for safely testing the grab")
        println("      --dump-frame=PATH  (lock, DEV/TEST ONLY) render one frame, save it")
        println("                    as a PNG at PATH, then exit -- lets the 3D lock UI")
        println("                    be inspected without a human watching the real screen")
        println("      --inject-password=STR  (lock, DEV/TEST ONLY) synthesize keypresses")
        println("                    for STR + Return right after the grab succeeds, driving")
        println("                    the real typing/PAM-check path with no keyboard needed --")
        println("                    STR is visible via argv/ps, only use a throwaway string")
        println("  -h, --help        display this help text and exit")
        println("  -v, --version     display version information and exit")
    }

    private fun printVersion() {
        System.out.printf("%s %s (%s / Windows 98)%n", PROGRAM_NAME, VERSION, Theme.NAME)
        println("License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.")
        println("This is free software: you are free to change and redistribute it.")
        println("There is NO WARRANTY, to the extent permitted by law.")
    }

    @JvmStatic
    fun main(argv: Array<String>) {
        exitProcess(run(argv))
    }

    @JvmStatic
    fun run(argv: Array<String>): Int {
        var dryRun = false
        var verbose = true
        var reload = true
        var applySettings = true
        var applyWallpaper = true
        var maxFps = 0
        var testTimeoutSeconds = 0f
        var dumpFramePath: String? = null
        var injectPassword: String? = null
        var action = Action.DEPLOY

        for (arg in argv) {
            if (arg.startsWith("--max-fps=")) {
                val value = arg.substring("--max-fps=".length)
                try {
                    maxFps = value.toInt()
                } catch (e: NumberFormatException) {
                    maxFps = -1
                }
                if (maxFps <= 0) {
                    System.err.println(PROGRAM_NAME + ": --max-fps requires a positive integer, got '" + value + "'")
                    return 1
                }
                continue
            }
            if (arg.startsWith("--test-timeout=")) {
                val value = arg.substring("--test-timeout=".length)
                try {
                    testTimeoutSeconds = value.toFloat()
                } catch (e: NumberFormatException) {
                    testTimeoutSeconds = -1f
                }
                if (testTimeoutSeconds <= 0f) {
                    System.err.println(PROGRAM_NAME + ": --test-timeout requires a positive number, got '" + value + "'")
                    return 1
                }
                continue
            }
            if (arg.startsWith("--dump-frame=")) {
                dumpFramePath = arg.substring("--dump-frame=".length)
                if (dumpFramePath.isBlank()) {
                    System.err.println(PROGRAM_NAME + ": --dump-frame requires a file path")
                    return 1
                }
                continue
            }
            if (arg.startsWith("--inject-password=")) {
                injectPassword = arg.substring("--inject-password=".length)
                if (injectPassword.isEmpty()) {
                    System.err.println(PROGRAM_NAME + ": --inject-password requires a non-empty string")
                    return 1
                }
                continue
            }
            when (arg) {
                "-h", "--help" -> {
                    printHelp()
                    return 0
                }

                "-v", "--version" -> {
                    printVersion()
                    return 0
                }

                "-n", "--dry-run" -> dryRun = true
                "-q", "--quiet" -> verbose = false
                "--links-only" -> {
                    applySettings = false
                    reload = false
                    applyWallpaper = false
                }

                "--no-xfconf" -> applySettings = false
                "--no-reload" -> reload = false
                "--no-wallpaper" -> applyWallpaper = false
                "-u", "--uninstall", "uninstall" -> action = Action.UNINSTALL
                "deploy" -> action = Action.DEPLOY
                "wallpaper" -> action = Action.WALLPAPER
                "overlay" -> action = Action.OVERLAY
                "lock" -> action = Action.LOCK
                "toggle-hud" -> action = Action.TOGGLE_HUD
                "shell" -> action = Action.SHELL
                else -> {
                    System.err.println(PROGRAM_NAME + ": unrecognized option '" + arg + "'")
                    System.err.println("Try '" + PROGRAM_NAME + " --help' for more information.")
                    return 1
                }
            }
        }

        val ok = when (action) {
            Action.UNINSTALL -> Orchestrator.uninstall(dryRun, verbose).ok
            Action.DEPLOY -> Orchestrator.deploy(dryRun, verbose, reload, applySettings, applyWallpaper).ok
            Action.WALLPAPER -> Orchestrator.set3DWallpaper(dryRun, verbose, maxFps).ok
            Action.OVERLAY -> Orchestrator.runOverlay(dryRun)
            Action.LOCK -> Orchestrator.lockScreen(dryRun, testTimeoutSeconds, dumpFramePath, injectPassword)
            Action.TOGGLE_HUD -> Orchestrator.toggleWallpaperHud(verbose)
            Action.SHELL -> ImpApplication.run(emptyArray()) == 0
        }
        return if (ok) 0 else 1
    }

    private enum class Action {
        DEPLOY, UNINSTALL, WALLPAPER, OVERLAY, TOGGLE_HUD, LOCK, SHELL
    }
}
