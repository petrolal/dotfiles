package dev.petrolal.dotfiles.cli.commands

import dev.petrolal.dotfiles.cli.Cli
import dev.petrolal.dotfiles.core.Orchestrator
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option

@Command(group = "Dotfiles Management")
class DotfilesCommands {

    @Command(command = ["deploy"], description = "Perform full deployment (symlink dotfiles)")
    fun deploy(
        @Option(longNames = ["dry-run"], shortNames = ['n'], description = "Simulate actions without modifying the filesystem")
        dryRun: Boolean = false,

        @Option(longNames = ["quiet"], shortNames = ['q'], description = "Suppress non-error output")
        quiet: Boolean = false
    ): String {
        val verbose = !quiet
        val ok = Orchestrator.deploy(dryRun, verbose).ok
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



    @Command(command = ["version"], alias = ["-v"], description = "Display version information")
    fun version(): String {
        return buildString {
            appendLine("${Cli.PROGRAM_NAME} ${Cli.VERSION}")
            appendLine("License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.")
            appendLine("This is free software: you are free to change and redistribute it.")
            append("There is NO WARRANTY, to the extent permitted by law.")
        }
    }
}
