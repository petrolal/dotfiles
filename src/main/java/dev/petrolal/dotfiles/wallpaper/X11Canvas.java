package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * X11Canvas manages the X11 display connection and, per monitor, an
 * independent desktop-background window + OpenGL context (see
 * {@link #createSurface}). Each surface gets its own vsync'd
 * {@code glXSwapBuffers} call, so every monitor's presentation is paced by
 * its own hardware vblank -- no shared buffer swap, no assumption that two
 * monitors share a refresh rate, and no hardcoded target FPS anywhere.
 * Provides query methods for global mouse and workspace state.
 */
public class X11Canvas implements AutoCloseable {

    // OpenGL & GLX Constants
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_STATIC_DRAW = 0x88E4;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_TRIANGLE_STRIP = 0x0005;
    public static final int GL_COMPILE_STATUS = 0x8B81;
    public static final int GL_LINK_STATUS = 0x8B82;

    // Offscreen render-target constants (low-res raymarch pass -> nearest-upscale blit)
    public static final int GL_FRAMEBUFFER = 0x8D40;
    public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    public static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_NEAREST = 0x2600;
    public static final int GL_LINEAR = 0x2601;
    public static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_TEXTURE0 = 0x84C0;

    public static final int GLX_RGBA = 4;
    public static final int GLX_DOUBLEBUFFER = 5;

    private static final int COPY_FROM_PARENT = 0;

    // X11 Mouse button bitmasks (Button 1 = Left, Button 3 = Right)
    public static final int MOUSE_BUTTON1_MASK = 1 << 8;
    public static final int MOUSE_BUTTON3_MASK = 1 << 10;

    // -------------------------------------------------------------------
    // Input-grab / event-loop constants, used only by the screen locker
    // (dev.petrolal.dotfiles.locker.ScreenLocker) -- the desktop-background
    // wallpaper surfaces created by createSurface() never select input or
    // grab anything.
    // -------------------------------------------------------------------
    public static final long CW_EVENT_MASK = 1L << 11;
    public static final long CW_OVERRIDE_REDIRECT = 1L << 9;
    public static final long KEY_PRESS_MASK = 1L;
    public static final long KEY_RELEASE_MASK = 1L << 1;
    public static final int GRAB_MODE_ASYNC = 1;
    public static final int GRAB_SUCCESS = 0;
    public static final long CURRENT_TIME = 0L;
    public static final int KEY_PRESS = 2;
    public static final int KEY_RELEASE = 3;

    // keysymdef.h function-key values this locker cares about.
    public static final long XK_BACKSPACE = 0xFF08L;
    public static final long XK_RETURN = 0xFF0DL;
    public static final long XK_ESCAPE = 0xFF1BL;
    public static final long XK_KP_ENTER = 0xFF8DL;

    public record MouseInfo(float x, float y, boolean leftDown, boolean rightDown) {}

    /** One decoded XKeyEvent: which keysym was pressed/released and (for a
     * KeyPress) whatever printable text XLookupString resolved it to --
     * empty for non-printable keys (arrows, function keys, modifiers...). */
    public record KeyEvent(boolean press, long keysym, String text) {}

    /** One monitor's window + GL context. All surfaces share one GL object namespace. */
    public record Surface(long window, MemorySegment glContext) {}

    private final MemorySegment display;
    private final int screen;
    private final long root;
    private final int width;
    private final int height;
    private final long atomCurrentDesktop;
    private final long atomWmWindowType;
    private final long atomWmWindowTypeDesktop;
    private final long atomWmState;
    private final long atomWmStateBelow;
    private final long atomTypeAtom;

    private final List<Long> surfaceWindows = new ArrayList<>();
    private MemorySegment sharedGlContext = null;
    private int quadVbo = -1;

    // Long-lived scratch buffers for the per-frame native queries below.
    // queryMouse()/queryWorkspace() run once per frame (60x/sec, forever) --
    // allocating a fresh Arena.ofConfined() per call here leaked native
    // memory at several MB/sec under GraalVM native-image (confirmed via
    // steadily climbing RSS), so these out-params are allocated once and
    // reused instead of allocated/freed every frame.
    private final Arena persistentArena = Arena.ofShared();
    private final MemorySegment mouseRootRet = persistentArena.allocate(ValueLayout.JAVA_LONG);
    private final MemorySegment mouseChildRet = persistentArena.allocate(ValueLayout.JAVA_LONG);
    private final MemorySegment mouseRootX = persistentArena.allocate(ValueLayout.JAVA_INT);
    private final MemorySegment mouseRootY = persistentArena.allocate(ValueLayout.JAVA_INT);
    private final MemorySegment mouseWinX = persistentArena.allocate(ValueLayout.JAVA_INT);
    private final MemorySegment mouseWinY = persistentArena.allocate(ValueLayout.JAVA_INT);
    private final MemorySegment mouseMask = persistentArena.allocate(ValueLayout.JAVA_INT);

    private final MemorySegment workspaceActualType = persistentArena.allocate(ValueLayout.JAVA_LONG);
    private final MemorySegment workspaceActualFormat = persistentArena.allocate(ValueLayout.JAVA_INT);
    private final MemorySegment workspaceNitems = persistentArena.allocate(ValueLayout.JAVA_LONG);
    private final MemorySegment workspaceBytesAfter = persistentArena.allocate(ValueLayout.JAVA_LONG);
    private final MemorySegment workspacePropPtr = persistentArena.allocate(ValueLayout.ADDRESS);

    // X11 function handles
    private static final MethodHandle XOpenDisplay;
    private static final MethodHandle XDefaultScreen;
    private static final MethodHandle XRootWindow;
    private static final MethodHandle XDisplayWidth;
    private static final MethodHandle XDisplayHeight;
    private static final MethodHandle XCreateWindow;
    private static final MethodHandle XMapWindow;
    private static final MethodHandle XInternAtom;
    private static final MethodHandle XChangeProperty;
    private static final MethodHandle XCloseDisplay;
    private static final MethodHandle XLowerWindow;
    private static final MethodHandle XQueryPointer;
    private static final MethodHandle XGetWindowProperty;
    private static final MethodHandle XFree;

    // Locker-only: input grab / event loop
    private static final MethodHandle XGrabKeyboard;
    private static final MethodHandle XUngrabKeyboard;
    private static final MethodHandle XGrabPointer;
    private static final MethodHandle XUngrabPointer;
    private static final MethodHandle XSelectInput;
    private static final MethodHandle XPending;
    private static final MethodHandle XNextEvent;
    private static final MethodHandle XLookupString;
    private static final MethodHandle XSetInputFocus;
    private static final MethodHandle XRaiseWindow;
    private static final MethodHandle XUnmapWindow;
    private static final MethodHandle XDestroyWindow;
    private static final MethodHandle XFlush;

    // GLX function handles
    private static final MethodHandle glXChooseVisual;
    private static final MethodHandle glXCreateContext;
    private static final MethodHandle glXMakeCurrent;
    private static final MethodHandle glXSwapBuffers;
    private static final MethodHandle glXGetProcAddress;

    // Direct OpenGL function handles -- resolved once in initGL(), after the
    // first surface's context is current (see initGL for why this can't
    // happen in the constructor: it needs the first monitor's real rectangle).
    // Private: callers (e.g. ShaderPipeline) go through the typed wrapper
    // methods below instead of invoking these handles directly, so GL call
    // signatures and Arena/MemorySegment bookkeeping stay in one place.
    private MethodHandle glCreateShader;
    private MethodHandle glShaderSource;
    private MethodHandle glCompileShader;
    private MethodHandle glGetShaderiv;
    private MethodHandle glGetShaderInfoLog;
    private MethodHandle glCreateProgram;
    private MethodHandle glAttachShader;
    private MethodHandle glLinkProgram;
    private MethodHandle glGetProgramiv;
    private MethodHandle glGetProgramInfoLog;
    private MethodHandle glUseProgram;
    private MethodHandle glDeleteShader;
    private MethodHandle glDeleteProgram;
    private MethodHandle glGetUniformLocation;
    private MethodHandle glUniform1f;
    private MethodHandle glUniform2f;
    private MethodHandle glUniform4f;
    private MethodHandle glViewport;
    private MethodHandle glGenBuffers;
    private MethodHandle glBindBuffer;
    private MethodHandle glBufferData;
    private MethodHandle glEnableVertexAttribArray;
    private MethodHandle glVertexAttribPointer;
    private MethodHandle glDrawArrays;
    private MethodHandle glGenFramebuffers;
    private MethodHandle glBindFramebuffer;
    private MethodHandle glFramebufferTexture2D;
    private MethodHandle glCheckFramebufferStatus;
    private MethodHandle glGenTextures;
    private MethodHandle glBindTexture;
    private MethodHandle glTexImage2D;
    private MethodHandle glTexParameteri;
    private MethodHandle glActiveTexture;
    private MethodHandle glUniform1i;
    private MethodHandle glGetError;
    private MethodHandle glPixelStorei;

    private static SymbolLookup loadLibrary(String libName, Arena arena) {
        try {
            return SymbolLookup.libraryLookup(libName, arena);
        } catch (IllegalArgumentException e) {
            String[] fallbacks = {
                "/run/current-system/sw/lib",
                "/usr/lib",
                "/usr/local/lib"
            };
            for (String dir : fallbacks) {
                Path p = Path.of(dir, libName);
                if (Files.exists(p)) {
                    return SymbolLookup.libraryLookup(p, arena);
                }
            }
            throw e;
        }
    }

    /** Bare Optional.get() throws an unhelpful "No value present" with no clue
     * which symbol was missing; this gives a real error message instead, and
     * is what every static-init symbol lookup below goes through so none of
     * them trip an Optional.get()-without-isPresent() warning. */
    private static MemorySegment findOrThrow(SymbolLookup lookup, String symbolName) {
        return lookup.find(symbolName).orElseThrow(
                () -> new UnsatisfiedLinkError("Native symbol not found: " + symbolName));
    }

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup x11 = loadLibrary("libX11.so", Arena.global());
        SymbolLookup gl = loadLibrary("libGL.so", Arena.global());

        // Bind X11
        XOpenDisplay = linker.downcallHandle(findOrThrow(x11, "XOpenDisplay"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        XDefaultScreen = linker.downcallHandle(findOrThrow(x11, "XDefaultScreen"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        XRootWindow = linker.downcallHandle(findOrThrow(x11, "XRootWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XDisplayWidth = linker.downcallHandle(findOrThrow(x11, "XDisplayWidth"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XDisplayHeight = linker.downcallHandle(findOrThrow(x11, "XDisplayHeight"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        // XCreateWindow, not XCreateSimpleWindow: only the full version lets us
        // request override-redirect (see createSurface for why that matters).
        XCreateWindow = linker.downcallHandle(findOrThrow(x11, "XCreateWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        XMapWindow = linker.downcallHandle(findOrThrow(x11, "XMapWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        XInternAtom = linker.downcallHandle(findOrThrow(x11, "XInternAtom"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XChangeProperty = linker.downcallHandle(findOrThrow(x11, "XChangeProperty"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XCloseDisplay = linker.downcallHandle(findOrThrow(x11, "XCloseDisplay"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        XLowerWindow = linker.downcallHandle(findOrThrow(x11, "XLowerWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

        XQueryPointer = linker.downcallHandle(findOrThrow(x11, "XQueryPointer"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        XGetWindowProperty = linker.downcallHandle(findOrThrow(x11, "XGetWindowProperty"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        XFree = linker.downcallHandle(findOrThrow(x11, "XFree"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // int XGrabKeyboard(Display*, Window, Bool, int, int, Time);
        XGrabKeyboard = linker.downcallHandle(findOrThrow(x11, "XGrabKeyboard"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        // int XUngrabKeyboard(Display*, Time);
        XUngrabKeyboard = linker.downcallHandle(findOrThrow(x11, "XUngrabKeyboard"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        // int XGrabPointer(Display*, Window, Bool, unsigned int, int, int, Window, Cursor, Time);
        XGrabPointer = linker.downcallHandle(findOrThrow(x11, "XGrabPointer"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        // int XUngrabPointer(Display*, Time);
        XUngrabPointer = linker.downcallHandle(findOrThrow(x11, "XUngrabPointer"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        // int XSelectInput(Display*, Window, long);
        XSelectInput = linker.downcallHandle(findOrThrow(x11, "XSelectInput"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        // int XPending(Display*); -- never blocks, just drains the local queue + a non-blocking socket check
        XPending = linker.downcallHandle(findOrThrow(x11, "XPending"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        // int XNextEvent(Display*, XEvent*); -- only called when XPending() > 0, so never actually blocks here
        XNextEvent = linker.downcallHandle(findOrThrow(x11, "XNextEvent"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        // int XLookupString(XKeyEvent*, char*, int, KeySym*, XComposeStatus*);
        XLookupString = linker.downcallHandle(findOrThrow(x11, "XLookupString"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        // int XSetInputFocus(Display*, Window, int revert_to, Time);
        XSetInputFocus = linker.downcallHandle(findOrThrow(x11, "XSetInputFocus"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        XRaiseWindow = linker.downcallHandle(findOrThrow(x11, "XRaiseWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        XUnmapWindow = linker.downcallHandle(findOrThrow(x11, "XUnmapWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        XDestroyWindow = linker.downcallHandle(findOrThrow(x11, "XDestroyWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        XFlush = linker.downcallHandle(findOrThrow(x11, "XFlush"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // Bind GLX
        glXChooseVisual = linker.downcallHandle(findOrThrow(gl, "glXChooseVisual"), FunctionDescriptor
                .of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glXCreateContext = linker.downcallHandle(findOrThrow(gl, "glXCreateContext"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        glXMakeCurrent = linker.downcallHandle(findOrThrow(gl, "glXMakeCurrent"), FunctionDescriptor
                .of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        glXSwapBuffers = linker.downcallHandle(findOrThrow(gl, "glXSwapBuffers"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        glXGetProcAddress = linker.downcallHandle(findOrThrow(gl, "glXGetProcAddress"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    public X11Canvas() throws Throwable {
        display = (MemorySegment) XOpenDisplay.invokeExact(MemorySegment.NULL);
        if (display.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Failed to open X11 Display.");
        }

        screen = (int) XDefaultScreen.invokeExact(display);
        root = (long) XRootWindow.invokeExact(display, screen);
        width = (int) XDisplayWidth.invokeExact(display, screen);
        height = (int) XDisplayHeight.invokeExact(display, screen);

        try (Arena arena = Arena.ofConfined()) {
            atomWmWindowType = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE"), 0);
            atomWmWindowTypeDesktop = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE_DESKTOP"), 0);
            atomWmState = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_STATE"), 0);
            atomWmStateBelow = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_STATE_BELOW"), 0);
            atomTypeAtom = (long) XInternAtom.invokeExact(display, arena.allocateFrom("ATOM"), 0);
            atomCurrentDesktop = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_CURRENT_DESKTOP"), 0);
        }
    }

    /**
     * Creates the first monitor's surface, sized to its real rectangle (not
     * the whole virtual desktop -- each monitor needs its own window so its
     * swapBuffers can vsync to its own vblank), resolves the GL function
     * pointers now that a context is current, and builds the shared quad
     * VBO every later {@link #createSurface} call reuses. Call once; use
     * {@link #createSurface} for every monitor after the first.
     */
    public Surface initGL(int x, int y, int w, int h) throws Throwable {
        Surface first = createSurface(x, y, w, h);
        resolveGlProcsIfNeeded();
        createSharedQuadBuffer();
        bindQuadAttribs();
        return first;
    }

    private boolean glProcsResolved = false;

    /** Resolves every direct-GL function pointer via glXGetProcAddress.
     * Idempotent and safe to call with any already-current GL context --
     * {@link #initGL} calls it for the desktop wallpaper's first surface,
     * {@link #createLockWindow} calls it for the screen locker's window,
     * whichever of the two runs first in a given process. */
    private void resolveGlProcsIfNeeded() throws Throwable {
        if (glProcsResolved) return;
        glProcsResolved = true;

        Linker linker = Linker.nativeLinker();
        glCreateShader = getProc(linker, "glCreateShader", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glShaderSource = getProc(linker, "glShaderSource", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        glCompileShader = getProc(linker, "glCompileShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glGetShaderiv = getProc(linker, "glGetShaderiv", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glGetShaderInfoLog = getProc(linker, "glGetShaderInfoLog", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        glCreateProgram = getProc(linker, "glCreateProgram", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        glAttachShader = getProc(linker, "glAttachShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glLinkProgram = getProc(linker, "glLinkProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glGetProgramiv = getProc(linker, "glGetProgramiv", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glGetProgramInfoLog = getProc(linker, "glGetProgramInfoLog", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        glUseProgram = getProc(linker, "glUseProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glDeleteShader = getProc(linker, "glDeleteShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glDeleteProgram = getProc(linker, "glDeleteProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glGetUniformLocation = getProc(linker, "glGetUniformLocation", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glUniform1f = getProc(linker, "glUniform1f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
        glUniform2f = getProc(linker, "glUniform2f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        glUniform4f = getProc(linker, "glUniform4f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        glViewport = getProc(linker, "glViewport", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glGenBuffers = getProc(linker, "glGenBuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glBindBuffer = getProc(linker, "glBindBuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glBufferData = getProc(linker, "glBufferData", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        glEnableVertexAttribArray = getProc(linker, "glEnableVertexAttribArray", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glVertexAttribPointer = getProc(linker, "glVertexAttribPointer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glDrawArrays = getProc(linker, "glDrawArrays", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glGenFramebuffers = getProc(linker, "glGenFramebuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glBindFramebuffer = getProc(linker, "glBindFramebuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glFramebufferTexture2D = getProc(linker, "glFramebufferTexture2D", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glCheckFramebufferStatus = getProc(linker, "glCheckFramebufferStatus", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glGenTextures = getProc(linker, "glGenTextures", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glBindTexture = getProc(linker, "glBindTexture", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glTexImage2D = getProc(linker, "glTexImage2D", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glTexParameteri = getProc(linker, "glTexParameteri", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glActiveTexture = getProc(linker, "glActiveTexture", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glUniform1i = getProc(linker, "glUniform1i", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glGetError = getProc(linker, "glGetError", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        glPixelStorei = getProc(linker, "glPixelStorei", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }

    /**
     * Creates one monitor's desktop-background window and GL context, sized
     * and positioned to exactly cover that monitor. Every surface after the
     * first shares the first context's GL object namespace (shaders/buffers).
     * <p>
     * Mixed refresh rates (e.g. 165Hz primary + 60Hz HDMI) cannot serially
     * block on multiple vblanks in a single render thread without dropping
     * frame rates down to ~40 FPS. Therefore, the bootstrap (primary) surface
     * paces the loop with vsync (swapInterval=1), while subsequent secondary
     * surfaces default to swapInterval=0 to avoid cross-locking delays.
     */
    public Surface createSurface(int x, int y, int w, int h) throws Throwable {
        return createSurface(x, y, w, h, sharedGlContext == null ? 1 : 0);
    }

    /**
     * Creates one monitor's desktop-background window and GL context with an
     * explicit VSync swap interval (1 for vsync-paced, 0 for immediate unblocked presentation).
     */
    public Surface createSurface(int x, int y, int w, int h, int swapInterval) throws Throwable {
        // REVERTED: override-redirect (see git history) made xfwm4 stop
        // managing this window entirely, which was meant to make our
        // lowerWindow() calls stick instead of being routed through xfwm4 as
        // an ignorable ConfigureRequest -- but it also meant xfwm4's
        // compositor (use_compositing=true) stopped compositing it, which on
        // real hardware produced a black screen instead of the rendered
        // scene: compositors commonly assume they own final screen
        // presentation via their own composited buffer, and a window that
        // bypasses their redirection may never actually get painted to the
        // display. Back to a plain CopyFromParent window (valuemask=0,
        // equivalent to the old XCreateSimpleWindow) -- WM-managed, so
        // compositing works, at the cost of reintroducing the icon-stacking
        // race this was trying to fix. See lowerToBackground()'s comment for
        // the follow-up plan on that.
        long window = (long) XCreateWindow.invokeExact(display, root, x, y, w, h, 0,
                COPY_FROM_PARENT, COPY_FROM_PARENT, MemorySegment.NULL, 0L, MemorySegment.NULL);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment propType = arena.allocateFrom(ValueLayout.JAVA_LONG, atomWmWindowTypeDesktop);
            int ignoredType = (int) XChangeProperty.invokeExact(display, window, atomWmWindowType, atomTypeAtom, 32, 0, propType, 1);

            MemorySegment propBelow = arena.allocateFrom(ValueLayout.JAVA_LONG, atomWmStateBelow);
            int ignoredBelow = (int) XChangeProperty.invokeExact(display, window, atomWmState, atomTypeAtom, 32, 0, propBelow, 1);
        }

        int ignoredMap = (int) XMapWindow.invokeExact(display, window);
        surfaceWindows.add(window);
        lowerWindow(window);

        MemorySegment glContext;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, GLX_RGBA, GLX_DOUBLEBUFFER, 0);
            MemorySegment visInfo = (MemorySegment) glXChooseVisual.invokeExact(display, screen, glAttrs);
            if (visInfo.equals(MemorySegment.NULL)) {
                throw new IllegalStateException(
                        "glXChooseVisual found no matching GLX visual (RGBA, double-buffered) for this X11 screen.");
            }
            MemorySegment shareWith = sharedGlContext == null ? MemorySegment.NULL : sharedGlContext;
            glContext = (MemorySegment) glXCreateContext.invokeExact(display, visInfo, shareWith, 1);
            int ignoredCurrent = (int) glXMakeCurrent.invokeExact(display, window, glContext);
            // XVisualInfo* is server-allocated by Xlib; it's our responsibility to free it.
            int ignoredFree = (int) XFree.invokeExact(visInfo);
        }

        if (sharedGlContext == null) {
            sharedGlContext = glContext;
        } else {
            // Non-bootstrap surface: the shared quad buffer object already
            // exists, but buffer binding + vertex attrib state is per-context.
            bindQuadAttribs();
        }

        // Pacing strategy: The primary surface (swapInterval=1) paces the render loop
        // to its hardware vblank, while secondary surfaces (swapInterval=0) present
        // immediately without blocking the single thread.
        setSwapInterval(window, swapInterval);

        return new Surface(window, glContext);
    }

    /**
     * Disables (0) or enables (1, the default we use) VSync for one drawable.
     * Resolves glXSwapIntervalEXT, glXSwapIntervalMESA, or glXSwapIntervalSGI dynamically.
     */
    public void setSwapInterval(long drawable, int interval) {
        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            // 1. Try glXSwapIntervalEXT (Display*, GLXDrawable, int)
            MemorySegment extPtr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalEXT"));
            if (!extPtr.equals(MemorySegment.NULL) && extPtr.address() != 0) {
                MethodHandle handle = linker.downcallHandle(extPtr,
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                handle.invokeExact(display, drawable, interval);
                return;
            }

            // 2. Try glXSwapIntervalMESA (int) -- process-wide, not per-drawable
            MemorySegment mesaPtr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalMESA"));
            if (!mesaPtr.equals(MemorySegment.NULL) && mesaPtr.address() != 0) {
                MethodHandle handle = linker.downcallHandle(mesaPtr,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                int ignored = (int) handle.invokeExact(interval);
                return;
            }

            // 3. Try glXSwapIntervalSGI (int) -- process-wide, not per-drawable
            MemorySegment sgiPtr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalSGI"));
            if (!sgiPtr.equals(MemorySegment.NULL) && sgiPtr.address() != 0) {
                MethodHandle handle = linker.downcallHandle(sgiPtr,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                int ignored = (int) handle.invokeExact(interval);
            }
        } catch (Throwable ignored) {}
    }

    private void createSharedQuadBuffer() throws Throwable {
        float[] quadVertices = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment vboBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenBuffers.invokeExact(1, vboBuf);
            quadVbo = vboBuf.get(ValueLayout.JAVA_INT, 0);

            glBindBuffer.invokeExact(GL_ARRAY_BUFFER, quadVbo);
            MemorySegment quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, quadVertices);
            glBufferData.invokeExact(GL_ARRAY_BUFFER, quadVertices.length * 4L, quadData, GL_STATIC_DRAW);
        }
    }

    /** Buffer binding + vertex attrib state belongs to the context, not the shared object namespace. */
    private void bindQuadAttribs() throws Throwable {
        glBindBuffer.invokeExact(GL_ARRAY_BUFFER, quadVbo);
        glEnableVertexAttribArray.invokeExact(0);
        glVertexAttribPointer.invokeExact(0, 2, GL_FLOAT, (byte) 0, 0, MemorySegment.NULL);
    }

    private MethodHandle getProc(Linker linker, String name, FunctionDescriptor desc) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ptr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom(name));
            return linker.downcallHandle(ptr, desc);
        }
    }

    public MouseInfo queryMouse() {
        try {
            int ok = (int) XQueryPointer.invokeExact(display, root, mouseRootRet, mouseChildRet, mouseRootX, mouseRootY, mouseWinX, mouseWinY, mouseMask);
            if (ok != 0) {
                int rx = mouseRootX.get(ValueLayout.JAVA_INT, 0);
                int ry = mouseRootY.get(ValueLayout.JAVA_INT, 0);
                int m = mouseMask.get(ValueLayout.JAVA_INT, 0);
                boolean left = (m & MOUSE_BUTTON1_MASK) != 0;
                boolean right = (m & MOUSE_BUTTON3_MASK) != 0;
                return new MouseInfo((float) rx, (float) ry, left, right);
            }
        } catch (Throwable ignored) {}
        return new MouseInfo(0.0f, 0.0f, false, false);
    }

    public int queryWorkspace() {
        try {
            int status = (int) XGetWindowProperty.invokeExact(
                display, root, atomCurrentDesktop,
                0L, 1L, 0, 0L, workspaceActualType, workspaceActualFormat, workspaceNitems, workspaceBytesAfter, workspacePropPtr
            );

            if (status == 0) {
                MemorySegment data = workspacePropPtr.get(ValueLayout.ADDRESS, 0);
                if (!data.equals(MemorySegment.NULL)) {
                    long desktop = data.get(ValueLayout.JAVA_LONG, 0);
                    int ignored = (int) XFree.invokeExact(data);
                    return (int) desktop;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private void lowerWindow(long window) {
        try {
            int ignored = (int) XLowerWindow.invokeExact(display, window);
        } catch (Throwable ignored) {}
    }

    /**
     * Re-asserts that every surface's window sits at the bottom of the stack.
     * A single XLowerWindow call at startup isn't enough on desktops where
     * xfdesktop (icons) or panel widgets get (re)mapped/raised after us --
     * they can end up stacked below our windows instead of above them.
     * Called once at startup and then periodically from the render loop.
     */
    public void lowerToBackground() {
        for (long window : surfaceWindows) {
            lowerWindow(window);
        }
    }

    public void makeCurrent(Surface surface) throws Throwable {
        int ignored = (int) glXMakeCurrent.invokeExact(display, surface.window(), surface.glContext());
    }

    public void setViewport(int x, int y, int width, int height) throws Throwable {
        glViewport.invokeExact(x, y, width, height);
    }

    public void drawQuad() throws Throwable {
        glDrawArrays.invokeExact(GL_TRIANGLE_STRIP, 0, 4);
    }

    // -------------------------------------------------------------------
    // Shader / program lifecycle -- the only GL surface ShaderPipeline
    // needs; it never touches a MethodHandle, Arena, or MemorySegment.
    // -------------------------------------------------------------------

    public int createShader(int type) throws Throwable {
        return (int) glCreateShader.invokeExact(type);
    }

    public void shaderSource(int shader, String source) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ptr = arena.allocateFrom(source);
            MemorySegment ptrArray = arena.allocateFrom(ValueLayout.ADDRESS, ptr);
            glShaderSource.invokeExact(shader, 1, ptrArray, MemorySegment.NULL);
        }
    }

    public void compileShader(int shader) throws Throwable {
        glCompileShader.invokeExact(shader);
    }

    public boolean getShaderCompileStatus(int shader) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = arena.allocate(ValueLayout.JAVA_INT);
            glGetShaderiv.invokeExact(shader, GL_COMPILE_STATUS, status);
            return status.get(ValueLayout.JAVA_INT, 0) == 0;
        }
    }

    public String getShaderInfoLog(int shader) throws Throwable {
        return fetchInfoLog(glGetShaderInfoLog, shader);
    }

    public void deleteShader(int shader) throws Throwable {
        glDeleteShader.invokeExact(shader);
    }

    public int createProgram() throws Throwable {
        return (int) glCreateProgram.invokeExact();
    }

    public void attachShader(int program, int shader) throws Throwable {
        glAttachShader.invokeExact(program, shader);
    }

    public void linkProgram(int program) throws Throwable {
        glLinkProgram.invokeExact(program);
    }

    public boolean getProgramLinkStatus(int program) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = arena.allocate(ValueLayout.JAVA_INT);
            glGetProgramiv.invokeExact(program, GL_LINK_STATUS, status);
            return status.get(ValueLayout.JAVA_INT, 0) != 0;
        }
    }

    public String getProgramInfoLog(int program) throws Throwable {
        return fetchInfoLog(glGetProgramInfoLog, program);
    }

    public void deleteProgram(int program) throws Throwable {
        glDeleteProgram.invokeExact(program);
    }

    public void useProgram(int program) throws Throwable {
        glUseProgram.invokeExact(program);
    }

    public int getUniformLocation(int program, String name) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            return (int) glGetUniformLocation.invokeExact(program, arena.allocateFrom(name));
        }
    }

    /** No-op when location is -1 (uniform not present in this shader),
     * matching GL's own "unused uniform" semantics for the caller. */
    public void uniform1f(int location, float v) throws Throwable {
        if (location >= 0) glUniform1f.invokeExact(location, v);
    }

    public void uniform2f(int location, float a, float b) throws Throwable {
        if (location >= 0) glUniform2f.invokeExact(location, a, b);
    }

    public void uniform4f(int location, float a, float b, float c, float d) throws Throwable {
        if (location >= 0) glUniform4f.invokeExact(location, a, b, c, d);
    }

    public void uniform1i(int location, int v) throws Throwable {
        if (location >= 0) glUniform1i.invokeExact(location, v);
    }

    // -------------------------------------------------------------------
    // Offscreen render targets: the expensive raymarch shader draws into a
    // reduced-resolution texture instead of the real window-sized backbuffer,
    // which is then upscaled with GL_NEAREST filtering (see blit.frag) --
    // cutting actual fragment-shader invocations for the expensive pass by
    // (1/renderScale)^2, which is the dominant cost on weak/integrated GPUs.
    // -------------------------------------------------------------------

    /** One monitor's low-resolution color target: an FBO plus its backing texture. */
    public record RenderTarget(int framebuffer, int texture, int width, int height) {}

    public RenderTarget createRenderTarget(int width, int height) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment texBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenTextures.invokeExact(1, texBuf);
            int texture = texBuf.get(ValueLayout.JAVA_INT, 0);

            glBindTexture.invokeExact(GL_TEXTURE_2D, texture);
            glTexImage2D.invokeExact(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemorySegment.NULL);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            MemorySegment fboBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenFramebuffers.invokeExact(1, fboBuf);
            int framebuffer = fboBuf.get(ValueLayout.JAVA_INT, 0);

            glBindFramebuffer.invokeExact(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D.invokeExact(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            int status = (int) glCheckFramebufferStatus.invokeExact(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Offscreen framebuffer incomplete (status 0x"
                        + Integer.toHexString(status) + ")");
            }
            glBindFramebuffer.invokeExact(GL_FRAMEBUFFER, 0);

            return new RenderTarget(framebuffer, texture, width, height);
        }
    }

    /**
     * Uploads tightly-packed RGBA pixels (first row = bottom) as a 2D texture
     * with GL_LINEAR min/mag filters (no mipmaps needed) and clamped edges.
     * Shared across surface contexts.
     */
    public int createTextureRGBA(int width, int height, byte[] rgba) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment texBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenTextures.invokeExact(1, texBuf);
            int texture = texBuf.get(ValueLayout.JAVA_INT, 0);

            MemorySegment pixels = arena.allocateFrom(ValueLayout.JAVA_BYTE, rgba);
            glActiveTexture.invokeExact(GL_TEXTURE0);
            glBindTexture.invokeExact(GL_TEXTURE_2D, texture);
            glPixelStorei.invokeExact(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D.invokeExact(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return texture;
        }
    }

    /**
     * Creates a 1x1 RGBA source texture (GL_LINEAR, no mipmaps, so it is
     * always texture-complete) holding the given colour. It is the pass-1
     * {@code u_texture} input: binding it to unit 0 before the offscreen draw
     * guarantees unit 0 never still holds the FBO's own colour attachment from
     * the previous frame's blit, which would be an OpenGL feedback loop.
     * Texture objects are shared across the surfaces' contexts.
     */
    public int createSourceTexture(int r, int g, int b, int a) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment texBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenTextures.invokeExact(1, texBuf);
            int texture = texBuf.get(ValueLayout.JAVA_INT, 0);

            MemorySegment pixel = arena.allocateFrom(ValueLayout.JAVA_BYTE,
                    (byte) r, (byte) g, (byte) b, (byte) a);
            glBindTexture.invokeExact(GL_TEXTURE_2D, texture);
            glTexImage2D.invokeExact(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return texture;
        }
    }

    /** Pops one pending GL error (0 == GL_NO_ERROR). */
    public int getError() throws Throwable {
        return (int) glGetError.invokeExact();
    }

    public void bindFramebuffer(int framebuffer) throws Throwable {
        glBindFramebuffer.invokeExact(GL_FRAMEBUFFER, framebuffer);
    }

    /** Binds TEXTURE to texture unit 0 (the only unit the blit shader uses). */
    public void bindTexture(int texture) throws Throwable {
        glActiveTexture.invokeExact(GL_TEXTURE0);
        glBindTexture.invokeExact(GL_TEXTURE_2D, texture);
    }

    /** Binds TEXTURE to an arbitrary texture unit (e.g. 1 for the screen
     * locker's login-dialog texture, keeping unit 0 free -- the raymarch
     * program doesn't otherwise sample anything there, but this leaves
     * headroom instead of assuming that stays true forever). */
    public void bindTextureUnit(int unit, int texture) throws Throwable {
        glActiveTexture.invokeExact(GL_TEXTURE0 + unit);
        glBindTexture.invokeExact(GL_TEXTURE_2D, texture);
    }

    /** Reads an info log via either glGetShaderInfoLog or glGetProgramInfoLog
     * (both share the (id, maxLength, *length, *infoLog) signature). */
    private String fetchInfoLog(MethodHandle getInfoLog, int id) throws Throwable {
        final int maxLength = 4096;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(maxLength);
            MemorySegment lengthOut = arena.allocate(ValueLayout.JAVA_INT);
            getInfoLog.invokeExact(id, maxLength, lengthOut, buffer);
            int length = lengthOut.get(ValueLayout.JAVA_INT, 0);
            return length > 0 ? buffer.getString(0) : "(no log)";
        }
    }

    public void swapBuffers(Surface surface) throws Throwable {
        glXSwapBuffers.invokeExact(display, surface.window());
    }

    // -------------------------------------------------------------------
    // Locker-only: an always-on-top, input-grabbing window spanning the
    // whole X screen. Deliberately a separate code path from createSurface()
    // (desktop-background, WM-managed, lowered) -- this window needs the
    // opposite properties (override-redirect so it bypasses the WM/panel
    // stacking entirely and always wins focus+raise, selects key events,
    // and is the target of the keyboard/pointer grabs below).
    // -------------------------------------------------------------------

    private final MemorySegment eventBuf = persistentArena.allocate(192); // sizeof(XEvent) == pad[24] of long
    private final MemorySegment lookupTextBuf = persistentArena.allocate(32);
    private final MemorySegment lookupKeysymBuf = persistentArena.allocate(ValueLayout.JAVA_LONG);

    /** Creates (but does not yet map/grab) the full-screen override-redirect
     * lock window, sized to the whole X display (every monitor at once --
     * one continuous lock surface, matching how the wallpaper already treats
     * the display as one panorama). */
    public Surface createLockWindow() throws Throwable {
        long window;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment attrs = arena.allocate(112);
            attrs.set(ValueLayout.JAVA_INT, 88, 1); // override_redirect = True
            attrs.set(ValueLayout.JAVA_LONG, 72, KEY_PRESS_MASK | KEY_RELEASE_MASK); // event_mask

            window = (long) XCreateWindow.invokeExact(display, root, 0, 0, width, height, 0,
                    COPY_FROM_PARENT, COPY_FROM_PARENT, MemorySegment.NULL,
                    CW_OVERRIDE_REDIRECT | CW_EVENT_MASK, attrs);
        }

        MemorySegment glContext;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, GLX_RGBA, GLX_DOUBLEBUFFER, 0);
            MemorySegment visInfo = (MemorySegment) glXChooseVisual.invokeExact(display, screen, glAttrs);
            if (visInfo.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("glXChooseVisual found no matching GLX visual for the lock window.");
            }
            MemorySegment shareWith = sharedGlContext == null ? MemorySegment.NULL : sharedGlContext;
            glContext = (MemorySegment) glXCreateContext.invokeExact(display, visInfo, shareWith, 1);
            int ignoredCurrent = (int) glXMakeCurrent.invokeExact(display, window, glContext);
            int ignoredFree = (int) XFree.invokeExact(visInfo);
        }
        resolveGlProcsIfNeeded();
        if (sharedGlContext == null) {
            sharedGlContext = glContext;
            createSharedQuadBuffer();
            bindQuadAttribs();
        } else {
            bindQuadAttribs();
        }

        int ignoredMap = (int) XMapWindow.invokeExact(display, window);
        int ignoredRaise = (int) XRaiseWindow.invokeExact(display, window);
        int ignoredFocus = (int) XSetInputFocus.invokeExact(display, window, 0 /* RevertToNone */, CURRENT_TIME);
        int ignoredFlush = (int) XFlush.invokeExact(display);

        setSwapInterval(window, 1);
        return new Surface(window, glContext);
    }

    /**
     * Grabs the keyboard and pointer exclusively for {@code window}, retrying
     * for up to ~1s: right at startup the window manager or a just-released
     * grab from the previous session can hold a transient grab that makes
     * the very first XGrabKeyboard/XGrabPointer call return AlreadyGrabbed
     * even though nothing is actually wrong. Returns false (never throws) if
     * the grab still can't be obtained after retrying, so the caller can
     * refuse to treat the screen as locked rather than show a lock UI that
     * input can bypass.
     */
    public boolean grabInputWithRetry(Surface surface) throws Throwable {
        for (int attempt = 0; attempt < 20; attempt++) {
            int kbGrab = (int) XGrabKeyboard.invokeExact(display, surface.window(), 1,
                    GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, CURRENT_TIME);
            if (kbGrab == GRAB_SUCCESS) {
                int ptrGrab = (int) XGrabPointer.invokeExact(display, surface.window(), 1,
                        0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, 0L, 0L, CURRENT_TIME);
                if (ptrGrab == GRAB_SUCCESS) {
                    return true;
                }
                int ignored = (int) XUngrabKeyboard.invokeExact(display, CURRENT_TIME);
            }
            Thread.sleep(50);
        }
        return false;
    }

    public void ungrabInput() throws Throwable {
        int ignored1 = (int) XUngrabKeyboard.invokeExact(display, CURRENT_TIME);
        int ignored2 = (int) XUngrabPointer.invokeExact(display, CURRENT_TIME);
    }

    /** Non-blocking: returns null immediately if no key event is queued.
     * Only ever called from the single render thread that owns {@code display}. */
    public KeyEvent pollKeyEvent() throws Throwable {
        int pending = (int) XPending.invokeExact(display);
        if (pending <= 0) return null;

        int ignored = (int) XNextEvent.invokeExact(display, eventBuf);
        int type = eventBuf.get(ValueLayout.JAVA_INT, 0);
        if (type != KEY_PRESS && type != KEY_RELEASE) {
            return pollKeyEvent(); // drain non-key events (e.g. stray Expose) without surfacing them
        }

        int len = (int) XLookupString.invokeExact(eventBuf, lookupTextBuf, (int) lookupTextBuf.byteSize(),
                lookupKeysymBuf, MemorySegment.NULL);
        long keysym = lookupKeysymBuf.get(ValueLayout.JAVA_LONG, 0);
        String text = len > 0 ? new String(lookupTextBuf.toArray(ValueLayout.JAVA_BYTE), 0, len,
                java.nio.charset.StandardCharsets.ISO_8859_1) : "";
        return new KeyEvent(type == KEY_PRESS, keysym, text);
    }

    public void destroyLockWindow(Surface surface) {
        try {
            ungrabInput();
            int ignored1 = (int) XUnmapWindow.invokeExact(display, surface.window());
            int ignored2 = (int) XDestroyWindow.invokeExact(display, surface.window());
            int ignored3 = (int) XFlush.invokeExact(display);
        } catch (Throwable ignored) {}
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Idempotent and safe to call from both the normal try-with-resources
     * path and a JVM shutdown hook racing it on SIGINT/SIGTERM -- only the
     * first caller actually tears down the X11 connection and native arena. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            int ignored = (int) XCloseDisplay.invokeExact(display);
        } catch (Throwable ignored) {}
        try {
            persistentArena.close();
        } catch (Throwable ignored) {}
    }
}
