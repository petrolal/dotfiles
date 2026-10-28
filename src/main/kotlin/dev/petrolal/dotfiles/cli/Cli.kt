// Cli.kt --- Command-line entry point
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli

import dev.petrolal.dotfiles.core.Orchestrator
import dev.petrolal.dotfiles.desktop.BrowserLauncher
import dev.petrolal.dotfiles.desktop.NotificationCenter
import dev.petrolal.dotfiles.desktop.NotificationLogger
import dev.petrolal.dotfiles.desktop.VhsShader
import dev.petrolal.dotfiles.desktop.VolumeControl
import kotlin.system.exitProcess

object Cli {
    const val VERSION: String = "1.3.0"
    const val PROGRAM_NAME: String = "imp"

    private fun printHelp() {
        System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME)
        println("Deploy dotfiles symlinks and manage desktop features.\n")
        println("Commands:")
        println("  deploy            perform full deployment (symlink dotfiles) [default]")
        println("  uninstall         remove all deployed dotfile symlinks safely")
        println("  browser           launch Wayland & GPU-optimized Chrome")
        println("  vhs, toggle-vhs   toggle VHS / CRT screen shader in Hyprland")
        println("  notif-center      open interactive Tofi notification center")
        println("  notif-logger      run background D-Bus notification logger daemon")
        println("  volume <action>   volume control: up, down, mute, mic-mute")
        println("  shell             start interactive Spring Shell REPL\n")
        println("Options:")
        println("  -u, --uninstall   remove all deployed dotfile symlinks safely")
        println("  -n, --dry-run     simulate actions without modifying the filesystem")
        println("  -q, --quiet       suppress non-error output")
        println("  -h, --help        display this help text and exit")
        println("  -v, --version     display version information and exit")
    }

    private fun printVersion() {
        System.out.printf("%s %s%n", PROGRAM_NAME, VERSION)
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
        var action: Action? = null
        val extraArgs = mutableListOf<String>()

        var i = 0
        while (i < argv.size) {
            val arg = argv[i]
            when (arg) {
                "-h", "--help", "help" -> {
                    printHelp()
                    return 0
                }

                "-v", "--version", "version" -> {
                    printVersion()
                    return 0
                }

                "-n", "--dry-run" -> dryRun = true
                "-q", "--quiet" -> verbose = false
                "-u", "--uninstall", "uninstall" -> action = Action.UNINSTALL
                "deploy" -> action = Action.DEPLOY
                "browser", "launch-browser" -> action = Action.BROWSER
                "vhs", "toggle-vhs" -> action = Action.VHS
                "notif-center" -> action = Action.NOTIF_CENTER
                "notif-logger" -> action = Action.NOTIF_LOGGER
                "volume" -> action = Action.VOLUME
                "shell" -> action = Action.SHELL
                else -> {
                    if (action != null) {
                        extraArgs.add(arg)
                    } else if (arg.startsWith("-")) {
                        System.err.println(PROGRAM_NAME + ": unrecognized option '" + arg + "'")
                        System.err.println("Try '" + PROGRAM_NAME + " --help' for more information.")
                        return 1
                    } else {
                        System.err.println(PROGRAM_NAME + ": unrecognized command '" + arg + "'")
                        System.err.println("Try '" + PROGRAM_NAME + " --help' for more information.")
                        return 1
                    }
                }
            }
            i++
        }

        val resolvedAction = action ?: Action.DEPLOY

        val ok = when (resolvedAction) {
            Action.UNINSTALL -> Orchestrator.uninstall(dryRun, verbose).ok
            Action.DEPLOY -> Orchestrator.deploy(dryRun, verbose).ok
            Action.BROWSER -> BrowserLauncher.launch(extraArgs = extraArgs).isOk()
            Action.VHS -> VhsShader.toggle().isOk()
            Action.NOTIF_CENTER -> NotificationCenter.show() == 0
            Action.NOTIF_LOGGER -> NotificationLogger.run() == 0
            Action.VOLUME -> VolumeControl.run(extraArgs.firstOrNull() ?: "up").isOk()
            Action.SHELL -> ImpApplication.run(emptyArray()) == 0
        }
        return if (ok) 0 else 1
    }

    private enum class Action {
        DEPLOY, UNINSTALL, BROWSER, VHS, NOTIF_CENTER, NOTIF_LOGGER, VOLUME, SHELL
    }
}
