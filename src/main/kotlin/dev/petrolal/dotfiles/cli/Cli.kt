// Cli.kt --- Command-line entry point
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli

import kotlin.system.exitProcess

// The native-image entrypoint (see build.gradle's `mainClass`). It used to
// hand-roll its own argument parser and call BrowserLauncher/VhsShader/
// Orchestrator/etc. directly, duplicating every command Spring Shell already
// defines as a proper `@Command` in cli.commands (DesktopCommands,
// DotfilesCommands) -- and never actually exercising that Spring Shell path
// except for `shell`.
//
// `-v`/`--version` and `-u`/`--uninstall` need no translation here anymore --
// they're registered as Spring Shell `alias`es directly on the `version`/
// `uninstall` `@Command`s, so Spring Shell itself resolves them. What's left
// is the two things Spring Shell genuinely has no native equivalent for:
// bare top-level `-h`/`--help` (Spring Shell only supports `--help` *after*
// a command, e.g. `imp version --help`, not on its own), and the historical
// "no arguments means deploy" default (Spring Shell's own default for zero
// args is to start the interactive REPL, which `imp shell` already covers
// explicitly).
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

    // Flags that are Spring Shell command aliases in their own right
    // (`version`/`uninstall` in DotfilesCommands.kt) -- these must reach
    // Spring Shell untouched so its own alias resolution handles them,
    // rather than being swept up by the generic "leading flag" fallback
    // below, which would otherwise misread them as deploy options.
    private val COMMAND_ALIASES = setOf("-v", "--version", "-u", "--uninstall")

    private fun translate(argv: Array<String>): Array<String> {
        if (argv.isEmpty()) return arrayOf("deploy")

        if (argv[0] == "-h" || argv[0] == "--help") {
            return arrayOf("help", *argv.copyOfRange(1, argv.size))
        }

        if (argv[0] in COMMAND_ALIASES) return argv

        // A leading flag with no explicit command word (e.g. bare `-n`,
        // `--dry-run`, `-q`) implies the historical default action: deploy.
        if (argv[0].startsWith("-")) {
            return arrayOf("deploy", *argv)
        }

        return argv
    }
}
