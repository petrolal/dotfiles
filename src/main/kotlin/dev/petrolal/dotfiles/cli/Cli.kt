// Cli.java --- Command-line entry point
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli

import dev.petrolal.dotfiles.core.Orchestrator
import kotlin.system.exitProcess

object Cli {
    const val VERSION: String = "1.3.0"
    const val PROGRAM_NAME: String = "imp"

    private fun printHelp() {
        System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME)
        println("Deploy dotfiles symlinks and manage the Hyprland launcher.\n")
        println("Commands:")
        println("  deploy            perform full deployment (symlink dotfiles) [default]")
        println("  uninstall         remove all deployed dotfile symlinks safely")
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
        var action = Action.DEPLOY

        for (arg in argv) {
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
                "-u", "--uninstall", "uninstall" -> action = Action.UNINSTALL
                "deploy" -> action = Action.DEPLOY
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
            Action.DEPLOY -> Orchestrator.deploy(dryRun, verbose).ok
            Action.SHELL -> ImpApplication.run(emptyArray()) == 0
        }
        return if (ok) 0 else 1
    }

    private enum class Action {
        DEPLOY, UNINSTALL, SHELL
    }
}
