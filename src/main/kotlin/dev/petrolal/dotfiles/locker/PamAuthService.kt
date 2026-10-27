// PamAuthService.java --- Linux PAM authentication via the stable FFM API
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

import java.lang.AutoCloseable
import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.function.Supplier

/**
 * Minimal libpam binding (pam_start / pam_authenticate / pam_end) using the
 * raw java.lang.foreign API directly -- no JNA dependency, consistent with
 * the rest of this codebase.
 * 
 * 
 * The PAM "conversation" callback is a native upcall into
 * [.conversation], which answers every PAM_PROMPT_ECHO_OFF /
 * PAM_PROMPT_ECHO_ON message with the password currently held in
 * [.pendingPassword] (confined to the single-threaded executor below --
 * only one authenticate() call is ever in flight at a time).
 * 
 * 
 * All native calls run on a dedicated single-thread executor so a slow or
 * hung PAM stack (network-backed modules, deliberately-slowed brute-force
 * mitigation, etc.) never blocks the 60 FPS render loop.
 */
class PamAuthService : AutoCloseable {
    enum class Status {
        SUCCESS, FAILURE, ERROR
    }

    // Confined to pamExecutor's single thread: conversation() (itself only
    // ever invoked synchronously from inside pam_authenticate, on this same
    // thread) reads this to answer PAM's password prompts.
    private var pendingPassword = CharArray(0)

    private val pamExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pam-auth-worker").apply { isDaemon = true }
    }

    /**
     * Synchronous PAM conversation callback, invoked by libpam from inside
     * pam_authenticate (same native call stack, same Java thread -- never
     * concurrent with another authenticate() call because pamExecutor is
     * single-threaded).
     * 
     * Signature: int conv(int num_msg, const struct pam_message **msg,
     * struct pam_response **resp, void *appdata_ptr)
     */
    private fun conversation(
        numMsg: Int,
        msgArray: MemorySegment,
        respOut: MemorySegment,
        appdataPtr: MemorySegment?
    ): Int {
        try {
            if (numMsg <= 0) return PAM_SUCCESS

            val respArraySeg = malloc.invokeExact(numMsg.toLong() * 16L) as MemorySegment
            val respArrayAddr = respArraySeg.address()
            if (respArrayAddr == 0L) return 1 // PAM_CONV_ERR-ish; 0 bytes available is already fatal upstream

            val respArray = MemorySegment.ofAddress(respArrayAddr).reinterpret(numMsg.toLong() * 16L)
            val msgs = msgArray.reinterpret(numMsg.toLong() * ValueLayout.ADDRESS.byteSize())

            val passwordUtf8: ByteArray = toUtf8(pendingPassword)
            try {
                for (i in 0..<numMsg) {
                    val msgPtr = msgs.getAtIndex(ValueLayout.ADDRESS, i.toLong())
                        .reinterpret(16)
                    val style = msgPtr.get(ValueLayout.JAVA_INT, 0)

                    val respOffset = i.toLong() * 16L
                    var respStr = MemorySegment.NULL
                    if (style == PAM_PROMPT_ECHO_OFF || style == PAM_PROMPT_ECHO_ON) {
                        val bufSeg = malloc.invokeExact(passwordUtf8.size.toLong() + 1) as MemorySegment
                        val bufAddr = bufSeg.address()
                        if (bufAddr != 0L) {
                            respStr = MemorySegment.ofAddress(bufAddr).reinterpret((passwordUtf8.size + 1).toLong())
                            MemorySegment.copy(passwordUtf8, 0, respStr, ValueLayout.JAVA_BYTE, 0, passwordUtf8.size)
                            respStr.set(ValueLayout.JAVA_BYTE, passwordUtf8.size.toLong(), 0.toByte())
                        }
                    }
                    respArray.set(ValueLayout.ADDRESS, respOffset, respStr)
                    respArray.set(ValueLayout.JAVA_INT, respOffset + 8, 0)
                }
            } finally {
                Arrays.fill(passwordUtf8, 0.toByte())
            }

            respOut.reinterpret(ValueLayout.ADDRESS.byteSize()).set(
                ValueLayout.ADDRESS, 0,
                MemorySegment.ofAddress(respArrayAddr)
            )
            return PAM_SUCCESS
        } catch (t: Throwable) {
            // Never swallow this silently: a bug here (or in the malloc
            // bindings it depends on) makes every single PAM attempt fail
            // indistinguishably from a wrong password, with no visible
            // error anywhere unless this is logged.
            System.err.println("[locker] PAM conversation callback threw: $t")
            return 1
        }
    }

    /**
     * Blocking PAM authentication for `user` against service `"imp-lock"`
     * (declared via `security.pam.services.imp-lock` in
     * `nixos/modules/hyprland.nix`, delegating to the setuid unix_chkpwd
     * helper so no extra privilege plumbing is required on our side). Call
     * only from the executor thread (see [.authenticateAsync]).
     */
    private fun authenticateBlocking(user: String?, password: CharArray): Status {
        this.pendingPassword = password
        try {
            Arena.ofConfined().use { arena ->
                // struct pam_conv { int (*conv)(...); void *appdata_ptr; };
                val bound = MethodHandles.lookup()
                    .bind(
                        this, "conversation", MethodType.methodType(
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            MemorySegment::class.java,
                            MemorySegment::class.java,
                            MemorySegment::class.java
                        )
                    )
                val convStub = Linker.nativeLinker().upcallStub(
                    bound,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS
                    ),
                    arena
                )

                val pamConv = arena.allocate(16)
                pamConv.set(ValueLayout.ADDRESS, 0, convStub)
                pamConv.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL)

                val serviceName = arena.allocateFrom("imp-lock")
                val userName = arena.allocateFrom(user)
                val pamhOut = arena.allocate(ValueLayout.ADDRESS)

                val startResult = pam_start.invokeExact(serviceName, userName, pamConv, pamhOut) as Int
                if (startResult != PAM_SUCCESS) {
                    return Status.ERROR
                }

                val pamh = pamhOut.get(ValueLayout.ADDRESS, 0)
                val authResult: Int
                try {
                    authResult = pam_authenticate.invokeExact(pamh, 0) as Int
                    if (authResult != PAM_SUCCESS) {
                        val msg = pam_strerror.invokeExact(pamh, authResult) as MemorySegment
                        val reason = if (msg == MemorySegment.NULL) "(unknown)" else msg.reinterpret(256).getString(0)
                        System.err.println("[locker] pam_authenticate returned $authResult: $reason")
                    }
                } finally {
                    val ignored = pam_end.invokeExact(pamh, 0) as Int
                }
                return if (authResult == PAM_SUCCESS) Status.SUCCESS else Status.FAILURE
            }
        } catch (t: Throwable) {
            System.err.println("[locker] PAM authentication threw: $t")
            return Status.ERROR
        } finally {
            Arrays.fill(password, '\u0000')
            this.pendingPassword = CharArray(0)
        }
    }

    /**
     * Submits a password check to the dedicated PAM worker thread. The
     * caller (render loop) polls the returned Future without blocking --
     * see [ScreenLocker] for how u_isAuthenticating tracks this.
     * Takes ownership of `password` and zeroes it once used.
     */
    fun authenticateAsync(user: String?, password: CharArray): Future<Status?> {
        return pamExecutor.submit(Callable { authenticateBlocking(user, password) })
    }

    override fun close() {
        pamExecutor.shutdownNow()
    }

    companion object {
        private const val PAM_SUCCESS = 0
        private const val PAM_PROMPT_ECHO_OFF = 1
        private const val PAM_PROMPT_ECHO_ON = 2

        private val pam_start: MethodHandle
        private val pam_authenticate: MethodHandle
        private val pam_end: MethodHandle
        private val pam_strerror: MethodHandle
        private val malloc: MethodHandle

        private fun loadLibrary(libName: String, arena: Arena): SymbolLookup {
            try {
                return SymbolLookup.libraryLookup(libName, arena)
            } catch (e: IllegalArgumentException) {
                val fallbacks = listOf("/run/current-system/sw/lib", "/usr/lib", "/usr/local/lib")
                for (dir in fallbacks) {
                    val p = Path.of(dir, libName)
                    if (Files.exists(p)) {
                        return SymbolLookup.libraryLookup(p, arena)
                    }
                }
                throw e
            }
        }

        private fun findOrThrow(lookup: SymbolLookup, name: String): MemorySegment {
            return lookup.find(name).orElseThrow {
                UnsatisfiedLinkError("Native symbol not found: $name")
            }
        }

        init {
            val linker = Linker.nativeLinker()
            val pam: SymbolLookup = loadLibrary("libpam.so.0", Arena.global())

            pam_start = linker.downcallHandle(
                findOrThrow(pam, "pam_start"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            )
            pam_authenticate = linker.downcallHandle(
                findOrThrow(pam, "pam_authenticate"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            )
            pam_end = linker.downcallHandle(
                findOrThrow(pam, "pam_end"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            )
            pam_strerror = linker.downcallHandle(
                findOrThrow(pam, "pam_strerror"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            )

            malloc = linker.downcallHandle(
                linker.defaultLookup().find("malloc").orElseThrow {
                    UnsatisfiedLinkError("Native symbol not found: malloc")
                },
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
        }

        private fun toUtf8(password: CharArray): ByteArray {
            val buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
            val out = ByteArray(buf.remaining())
            buf.get(out)
            return out
        }
    }
}
