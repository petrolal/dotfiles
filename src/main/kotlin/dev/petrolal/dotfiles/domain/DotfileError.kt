// DotfileError.java --- Typed domain errors for Result<T>
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.domain

import java.nio.file.Path


sealed interface DotfileError {
    @JvmRecord
    data class CommandFailed(val command: String?, val exitCode: Int) : DotfileError

    @JvmRecord
    data class FileNotFound(val path: Path?) : DotfileError

    @JvmRecord
    data class IoError(val message: String?) : DotfileError

    @JvmRecord
    data class ParseError(val message: String?) : DotfileError

    fun describe(): String {
        return when (this) {
            is CommandFailed -> "command failed (" + exitCode + "): " + command
            is FileNotFound -> "file not found: " + path
            is IoError -> "I/O error: " + message
            is ParseError -> "parse error: " + message
        }
    }
}
