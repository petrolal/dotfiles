// CommandFailedException.kt --- Signals a real command failure to Spring Shell
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli.commands

/** Thrown by a `@Command` method to report a genuine failure. Resolved by
 * [ImpCommandSupport]'s `@ExceptionResolver`, which turns it into a
 * formatted "imp: <message>" line and [exitCode] -- without this, every
 * command exits 0 regardless of outcome, since Spring Shell has no way to
 * tell a failure message apart from a success one. */
class CommandFailedException(message: String, val exitCode: Int = 1) : RuntimeException(message)
