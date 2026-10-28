// Cli.kt --- Command-line entry point
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli

import kotlin.system.exitProcess

// The native-image entrypoint (see build.gradle's `mainClass`). It used to
// hand-roll its own argument parser and call BrowserLauncher/VhsShader/
// Orchestrator/etc. directly, duplicating every command Spring Shell already
// defines as a proper `@Command` in cli.commands (DesktopCommands,
// DotfilesCommands) -- and never actually exercising that Spring Shell path
// except for `shell`. This now just translates the small set of legacy
// top-level flags (`-h`, `-v`, `-u`, bare `deploy`-by-default) into the
// equivalent Spring Shell command line and delegates everything else
// straight through to it, so there's exactly one place commands are
// implemented.
object Cli {
    const val VERSION: String = "1.3.0"
    const val PROGRAM_NAME: String = "imp"

    @JvmStatic
    fun main(argv: Array<String>) {
        exitProcess(run(argv))
    }

    @JvmStatic
    fun run(argv: Array<String>): Int {
        // `shell` boots Spring Shell's interactive REPL, which it only does
        // when invoked with no arguments -- that's not a registered
        // command, so it's handled before translation/delegation.
        if (argv.size == 1 && argv[0] == "shell") {
            return ImpApplication.run(emptyArray())
        }
        return ImpApplication.run(translate(argv))
    }

    private fun translate(argv: Array<String>): Array<String> {
        if (argv.isEmpty()) return arrayOf("deploy")

        val resolvedCommand = when (argv[0]) {
            "-h", "--help" -> "help"
            "-v", "--version" -> "version"
            "-u", "--uninstall" -> "uninstall"
            else -> null
        }
        if (resolvedCommand != null) {
            return arrayOf(resolvedCommand, *argv.copyOfRange(1, argv.size))
        }

        // A leading flag with no explicit command word (e.g. bare `-n`,
        // `--dry-run`, `-q`) implies the historical default action: deploy.
        if (argv[0].startsWith("-")) {
            return arrayOf("deploy", *argv)
        }

        return argv
    }
}
