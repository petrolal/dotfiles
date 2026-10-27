// ScreenLocker.kt --- PAM-backed lock/unlock state machine
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

import java.util.concurrent.Future

/**
 * The reusable core of the screen locker: password-buffer handling and
 * async PAM authentication, with no window/rendering/input-grab code of its
 * own. Intentionally decoupled from any particular UI or input backend (it
 * was previously tied to an X11 raymarch shader UI, since removed) so it can
 * be driven by whatever owns the actual lock screen -- e.g. the overlay
 * launcher's own GLFW event loop -- via [onChar]/[onBackspace]/[onSubmit]/
 * [onCancel] and polled once per frame via [update].
 */
class ScreenLocker(private val user: String, private val pam: PamAuthService) {
    enum class Outcome { PENDING, UNLOCKED }

    private var passwordBuffer = CharArray(0)
    private var pendingAuth: Future<PamAuthService.Status?>? = null

    var authError = 0.0f
        private set
    var authenticating = false
        private set

    val passwordLength: Int
        get() = passwordBuffer.size

    /** Appends one typed character to the password buffer, ignoring input while a check is in flight. */
    fun onChar(c: Char) {
        if (authenticating) return
        if (c.code in 0x20..0x7E && passwordBuffer.size < MAX_PASSWORD_LENGTH) {
            val longer = CharArray(passwordBuffer.size + 1)
            System.arraycopy(passwordBuffer, 0, longer, 0, passwordBuffer.size)
            longer[passwordBuffer.size] = c
            passwordBuffer = longer
        }
    }

    fun onBackspace() {
        if (authenticating || passwordBuffer.isEmpty()) return
        val shorter = CharArray(passwordBuffer.size - 1)
        System.arraycopy(passwordBuffer, 0, shorter, 0, shorter.size)
        passwordBuffer[passwordBuffer.size - 1] = '\u0000'
        passwordBuffer = shorter
    }

    /** Clears the buffer without submitting it (e.g. on Escape). */
    fun onCancel() {
        passwordBuffer.fill('\u0000')
        passwordBuffer = CharArray(0)
    }

    /** Submits the current buffer to PAM asynchronously; result is observed via [update]. */
    fun onSubmit() {
        if (authenticating) return
        authenticating = true
        val toCheck = passwordBuffer
        passwordBuffer = CharArray(0) // PamAuthService takes ownership and zeroes toCheck
        pendingAuth = pam.authenticateAsync(user, toCheck)
    }

    /** Call once per frame with the frame's dt (seconds); decays the error flash and reports when PAM succeeds. */
    fun update(dt: Float): Outcome {
        val auth = pendingAuth
        if (authenticating && auth?.isDone == true) {
            val status = try {
                auth.get()
            } catch (_: Exception) {
                PamAuthService.Status.ERROR
            }
            authenticating = false
            pendingAuth = null
            if (status == PamAuthService.Status.SUCCESS) {
                return Outcome.UNLOCKED
            } else {
                authError = 1.0f
            }
        }
        authError = maxOf(0.0f, authError - dt / ERROR_DECAY_SECONDS)
        return Outcome.PENDING
    }

    /** Zeroes any buffered password. Call when the lock screen is torn down. */
    fun shutdown() {
        passwordBuffer.fill('\u0000')
        passwordBuffer = CharArray(0)
    }

    companion object {
        /** Own-interval cap for the error-flash decay animation. */
        private const val ERROR_DECAY_SECONDS = 1.2f
        private const val MAX_PASSWORD_LENGTH = 128
    }
}
