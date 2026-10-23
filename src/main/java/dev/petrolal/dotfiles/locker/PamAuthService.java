// PamAuthService.java --- Linux PAM authentication via the stable FFM API
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Minimal libpam binding (pam_start / pam_authenticate / pam_end) using the
 * same raw java.lang.foreign style as {@link dev.petrolal.dotfiles.wallpaper.X11Canvas}
 * -- no JNA dependency, consistent with the rest of this codebase.
 * <p>
 * The PAM "conversation" callback is a native upcall into
 * {@link #conversation}, which answers every PAM_PROMPT_ECHO_OFF /
 * PAM_PROMPT_ECHO_ON message with the password currently held in
 * {@link #pendingPassword} (confined to the single-threaded executor below --
 * only one authenticate() call is ever in flight at a time).
 * <p>
 * All native calls run on a dedicated single-thread executor so a slow or
 * hung PAM stack (network-backed modules, deliberately-slowed brute-force
 * mitigation, etc.) never blocks the 60 FPS render loop.
 */
public final class PamAuthService implements AutoCloseable {

    public enum Status { SUCCESS, FAILURE, ERROR }

    private static final int PAM_SUCCESS = 0;
    private static final int PAM_PROMPT_ECHO_OFF = 1;
    private static final int PAM_PROMPT_ECHO_ON = 2;

    private static final MethodHandle pam_start;
    private static final MethodHandle pam_authenticate;
    private static final MethodHandle pam_end;
    private static final MethodHandle pam_strerror;
    private static final MethodHandle malloc;

    // Confined to pamExecutor's single thread: conversation() (itself only
    // ever invoked synchronously from inside pam_authenticate, on this same
    // thread) reads this to answer PAM's password prompts.
    private char[] pendingPassword = new char[0];

    private final ExecutorService pamExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "pam-auth-worker");
                t.setDaemon(true);
                return t;
            });

    private static SymbolLookup loadLibrary(String libName, Arena arena) {
        try {
            return SymbolLookup.libraryLookup(libName, arena);
        } catch (IllegalArgumentException e) {
            String[] fallbacks = { "/run/current-system/sw/lib", "/usr/lib", "/usr/local/lib" };
            for (String dir : fallbacks) {
                Path p = Path.of(dir, libName);
                if (Files.exists(p)) {
                    return SymbolLookup.libraryLookup(p, arena);
                }
            }
            throw e;
        }
    }

    private static MemorySegment findOrThrow(SymbolLookup lookup, String name) {
        return lookup.find(name).orElseThrow(
                () -> new UnsatisfiedLinkError("Native symbol not found: " + name));
    }

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup pam = loadLibrary("libpam.so.0", Arena.global());

        // int pam_start(const char *service_name, const char *user,
        //               const struct pam_conv *pam_conversation, pam_handle_t **pamh);
        pam_start = linker.downcallHandle(findOrThrow(pam, "pam_start"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        // int pam_authenticate(pam_handle_t *pamh, int flags);
        pam_authenticate = linker.downcallHandle(findOrThrow(pam, "pam_authenticate"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        // int pam_end(pam_handle_t *pamh, int pam_status);
        pam_end = linker.downcallHandle(findOrThrow(pam, "pam_end"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        // const char *pam_strerror(pam_handle_t *pamh, int errnum); -- diagnostics only
        pam_strerror = linker.downcallHandle(findOrThrow(pam, "pam_strerror"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // malloc is already resident in every process via libc -- no explicit
        // libraryLookup needed. Response buffers MUST come from here, not a
        // Java Arena: libpam (or the auth module) free()s them itself once
        // it's done reading the answers, and mixing allocators is undefined
        // behavior.
        malloc = linker.downcallHandle(linker.defaultLookup().find("malloc").orElseThrow(
                        () -> new UnsatisfiedLinkError("Native symbol not found: malloc")),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    }

    /**
     * Synchronous PAM conversation callback, invoked by libpam from inside
     * pam_authenticate (same native call stack, same Java thread -- never
     * concurrent with another authenticate() call because pamExecutor is
     * single-threaded).
     * <p>
     * Signature: int conv(int num_msg, const struct pam_message **msg,
     *                      struct pam_response **resp, void *appdata_ptr)
     */
    private int conversation(int numMsg, MemorySegment msgArray, MemorySegment respOut, MemorySegment appdataPtr) {
        try {
            if (numMsg <= 0) return PAM_SUCCESS;

            MemorySegment respArraySeg = (MemorySegment) malloc.invokeExact((long) numMsg * 16L);
            long respArrayAddr = respArraySeg.address();
            if (respArrayAddr == 0) return 1; // PAM_CONV_ERR-ish; 0 bytes available is already fatal upstream

            MemorySegment respArray = MemorySegment.ofAddress(respArrayAddr).reinterpret((long) numMsg * 16L);
            MemorySegment msgs = msgArray.reinterpret((long) numMsg * ValueLayout.ADDRESS.byteSize());

            byte[] passwordUtf8 = toUtf8(pendingPassword);
            try {
                for (int i = 0; i < numMsg; i++) {
                    MemorySegment msgPtr = msgs.getAtIndex(ValueLayout.ADDRESS, i)
                            .reinterpret(16);
                    int style = msgPtr.get(ValueLayout.JAVA_INT, 0);

                    long respOffset = (long) i * 16L;
                    MemorySegment respStr = MemorySegment.NULL;
                    if (style == PAM_PROMPT_ECHO_OFF || style == PAM_PROMPT_ECHO_ON) {
                        MemorySegment bufSeg = (MemorySegment) malloc.invokeExact((long) passwordUtf8.length + 1);
                        long bufAddr = bufSeg.address();
                        if (bufAddr != 0) {
                            respStr = MemorySegment.ofAddress(bufAddr).reinterpret(passwordUtf8.length + 1);
                            MemorySegment.copy(passwordUtf8, 0, respStr, ValueLayout.JAVA_BYTE, 0, passwordUtf8.length);
                            respStr.set(ValueLayout.JAVA_BYTE, passwordUtf8.length, (byte) 0);
                        }
                    }
                    respArray.set(ValueLayout.ADDRESS, respOffset, respStr);
                    respArray.set(ValueLayout.JAVA_INT, respOffset + 8, 0);
                }
            } finally {
                java.util.Arrays.fill(passwordUtf8, (byte) 0);
            }

            respOut.reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0,
                    MemorySegment.ofAddress(respArrayAddr));
            return PAM_SUCCESS;
        } catch (Throwable t) {
            // Never swallow this silently: a bug here (or in the malloc
            // bindings it depends on) makes every single PAM attempt fail
            // indistinguishably from a wrong password, with no visible
            // error anywhere unless this is logged.
            System.err.println("[locker] PAM conversation callback threw: " + t);
            return 1;
        }
    }

    private static byte[] toUtf8(char[] password) {
        java.nio.ByteBuffer buf = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password));
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    /**
     * Blocking PAM authentication for {@code user} against service
     * {@code "xfce4-screensaver"} (already shipped in /etc/pam.d on this
     * NixOS desktop, delegating to the setuid unix_chkpwd helper so no
     * extra privilege plumbing is required on our side). Call only from
     * the executor thread (see {@link #authenticateAsync}).
     */
    private Status authenticateBlocking(String user, char[] password) {
        this.pendingPassword = password;
        try (Arena arena = Arena.ofConfined()) {
            // struct pam_conv { int (*conv)(...); void *appdata_ptr; };
            MethodHandle bound = MethodHandles.lookup()
                    .bind(this, "conversation", MethodType.methodType(
                            int.class, int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment convStub = Linker.nativeLinker().upcallStub(bound,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                    arena);

            MemorySegment pamConv = arena.allocate(16);
            pamConv.set(ValueLayout.ADDRESS, 0, convStub);
            pamConv.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);

            MemorySegment serviceName = arena.allocateFrom("xfce4-screensaver");
            MemorySegment userName = arena.allocateFrom(user);
            MemorySegment pamhOut = arena.allocate(ValueLayout.ADDRESS);

            int startResult = (int) pam_start.invokeExact(serviceName, userName, pamConv, pamhOut);
            if (startResult != PAM_SUCCESS) {
                return Status.ERROR;
            }

            MemorySegment pamh = pamhOut.get(ValueLayout.ADDRESS, 0);
            int authResult;
            try {
                authResult = (int) pam_authenticate.invokeExact(pamh, 0);
                if (authResult != PAM_SUCCESS) {
                    MemorySegment msg = (MemorySegment) pam_strerror.invokeExact(pamh, authResult);
                    String reason = msg.equals(MemorySegment.NULL) ? "(unknown)" : msg.reinterpret(256).getString(0);
                    System.err.println("[locker] pam_authenticate returned " + authResult + ": " + reason);
                }
            } finally {
                int ignored = (int) pam_end.invokeExact(pamh, 0);
            }
            return authResult == PAM_SUCCESS ? Status.SUCCESS : Status.FAILURE;
        } catch (Throwable t) {
            System.err.println("[locker] PAM authentication threw: " + t);
            return Status.ERROR;
        } finally {
            java.util.Arrays.fill(password, '\0');
            this.pendingPassword = new char[0];
        }
    }

    /**
     * Submits a password check to the dedicated PAM worker thread. The
     * caller (render loop) polls the returned Future without blocking --
     * see {@link ScreenLocker} for how u_isAuthenticating tracks this.
     * Takes ownership of {@code password} and zeroes it once used.
     */
    public Future<Status> authenticateAsync(String user, char[] password) {
        return pamExecutor.submit(() -> authenticateBlocking(user, password));
    }

    @Override
    public void close() {
        pamExecutor.shutdownNow();
    }
}
