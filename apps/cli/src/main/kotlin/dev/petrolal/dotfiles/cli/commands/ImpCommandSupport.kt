// ImpCommandSupport.kt --- Shared CommandFailedException -> exit-code mapping
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli.commands

import dev.petrolal.dotfiles.cli.Cli
import dev.petrolal.dotfiles.domain.Result
import org.springframework.shell.command.CommandHandlingResult
import org.springframework.shell.command.annotation.ExceptionResolver

/** Extended by every `@Command`-annotated class (DesktopCommands,
 * DotfilesCommands) so one handler covers all of them. Spring Shell
 * discovers `@ExceptionResolver` methods per command-bean class, including
 * inherited ones, the same way Spring MVC resolves `@ExceptionHandler` --
 * so this only needs to exist once. */
abstract class ImpCommandSupport {
    @ExceptionResolver(CommandFailedException::class)
    fun handleCommandFailed(ex: CommandFailedException): CommandHandlingResult =
        CommandHandlingResult.of("${Cli.PROGRAM_NAME}: ${ex.message}", ex.exitCode)
}

/** Unwraps a successful [Result], or throws [CommandFailedException] with
 * the error's description -- the one place a command decides a failed
 * `Result` becomes a failed process. */
fun <T> Result<T>.orThrow(): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> throw CommandFailedException(error.describe())
}
