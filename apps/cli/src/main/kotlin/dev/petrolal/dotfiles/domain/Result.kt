// Result.java --- Functional error handling
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.domain

import java.util.Objects

sealed interface Result<T> {
    @JvmRecord
    data class Ok<T>(val value: T) : Result<T>

    @JvmRecord
    data class Err<T>(val error: DotfileError) : Result<T>

    fun isOk(): Boolean = this is Ok<*>

    fun isErr(): Boolean = this is Err<*>

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun orElse(fallback: T): T = when (this) {
        is Ok -> value
        is Err -> fallback
    }

    companion object {
        @JvmStatic
        fun <T> ok(value: T): Result<T> {
            return Ok(value)
        }

        @JvmStatic
        fun <T> err(error: DotfileError): Result<T> {
            return Err(Objects.requireNonNull(error))
        }
    }
}

