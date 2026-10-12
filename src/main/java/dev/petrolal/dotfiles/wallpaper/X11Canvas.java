package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public record MouseInfo(float x, float y, boolean leftDown, boolean rightDown) {}

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
    private static final MethodHandle XCreateSimpleWindow;
    private static final MethodHandle XMapWindow;
    private static final MethodHandle XInternAtom;
    private static final MethodHandle XChangeProperty;
    private static final MethodHandle XCloseDisplay;
    private static final MethodHandle XLowerWindow;
    private static final MethodHandle XQueryPointer;
    private static final MethodHandle XGetWindowProperty;
    private static final MethodHandle XFree;

    // GLX function handles
    private static final MethodHandle glXChooseVisual;
    private static final MethodHandle glXCreateContext;
    private static final MethodHandle glXMakeCurrent;
    private static final MethodHandle glXSwapBuffers;
    private static final MethodHandle glXGetProcAddress;

    // Direct OpenGL function handles -- resolved once in initGL(), after the
    // first surface's context is current (see initGL for why this can't
    // happen in the constructor: it needs the first monitor's real rectangle).
    public MethodHandle glCreateShader;
    public MethodHandle glShaderSource;
    public MethodHandle glCompileShader;
    public MethodHandle glCreateProgram;
    public MethodHandle glAttachShader;
    public MethodHandle glLinkProgram;
    public MethodHandle glUseProgram;
    public MethodHandle glDeleteShader;
    public MethodHandle glDeleteProgram;
    public MethodHandle glGetUniformLocation;
    public MethodHandle glUniform1f;
    public MethodHandle glUniform2f;
    public MethodHandle glUniform4f;
    public MethodHandle glViewport;
    public MethodHandle glGenBuffers;
    public MethodHandle glBindBuffer;
    public MethodHandle glBufferData;
    public MethodHandle glEnableVertexAttribArray;
    public MethodHandle glVertexAttribPointer;
    public MethodHandle glDrawArrays;

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

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup x11 = loadLibrary("libX11.so", Arena.global());
        SymbolLookup gl = loadLibrary("libGL.so", Arena.global());

        // Bind X11
        XOpenDisplay = linker.downcallHandle(x11.find("XOpenDisplay").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        XDefaultScreen = linker.downcallHandle(x11.find("XDefaultScreen").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        XRootWindow = linker.downcallHandle(x11.find("XRootWindow").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XDisplayWidth = linker.downcallHandle(x11.find("XDisplayWidth").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XDisplayHeight = linker.downcallHandle(x11.find("XDisplayHeight").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XCreateSimpleWindow = linker.downcallHandle(x11.find("XCreateSimpleWindow").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        XMapWindow = linker.downcallHandle(x11.find("XMapWindow").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        XInternAtom = linker.downcallHandle(x11.find("XInternAtom").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XChangeProperty = linker.downcallHandle(x11.find("XChangeProperty").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        XCloseDisplay = linker.downcallHandle(x11.find("XCloseDisplay").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        XLowerWindow = linker.downcallHandle(x11.find("XLowerWindow").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

        XQueryPointer = linker.downcallHandle(x11.find("XQueryPointer").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        XGetWindowProperty = linker.downcallHandle(x11.find("XGetWindowProperty").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        XFree = linker.downcallHandle(x11.find("XFree").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // Bind GLX
        glXChooseVisual = linker.downcallHandle(gl.find("glXChooseVisual").get(), FunctionDescriptor
                .of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glXCreateContext = linker.downcallHandle(gl.find("glXCreateContext").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        glXMakeCurrent = linker.downcallHandle(gl.find("glXMakeCurrent").get(), FunctionDescriptor
                .of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        glXSwapBuffers = linker.downcallHandle(gl.find("glXSwapBuffers").get(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        glXGetProcAddress = linker.downcallHandle(gl.find("glXGetProcAddress").get(),
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

        Linker linker = Linker.nativeLinker();
        glCreateShader = getProc(linker, "glCreateShader", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glShaderSource = getProc(linker, "glShaderSource", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        glCompileShader = getProc(linker, "glCompileShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glCreateProgram = getProc(linker, "glCreateProgram", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        glAttachShader = getProc(linker, "glAttachShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glLinkProgram = getProc(linker, "glLinkProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
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

        createSharedQuadBuffer();
        bindQuadAttribs();
        return first;
    }

    /**
     * Creates one monitor's desktop-background window and GL context, sized
     * and positioned to exactly cover that monitor. Every surface after the
     * first shares the first context's GL object namespace (shaders/buffers),
     * and gets its own vsync'd swap chain -- each monitor's presentation is
     * then paced purely by its own hardware vblank via {@link #swapBuffers}.
     */
    public Surface createSurface(int x, int y, int w, int h) throws Throwable {
        long window = (long) XCreateSimpleWindow.invokeExact(display, root, x, y, w, h, 0, 0L, 0L);

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
            MemorySegment glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, 4 /* GLX_RGBA */, 5 /* GLX_DOUBLEBUFFER */, 0);
            MemorySegment visInfo = (MemorySegment) glXChooseVisual.invokeExact(display, screen, glAttrs);
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

        // Vsync per surface: each monitor's swapBuffers call blocks on THAT
        // monitor's own vblank, so mixed refresh rates (e.g. 165Hz + 60Hz)
        // each get correctly paced -- automatically, with no target FPS or
        // Hz value hardcoded anywhere.
        setSwapInterval(window, 1);

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

            glBindBuffer.invokeExact(0x8892 /* GL_ARRAY_BUFFER */, quadVbo);
            MemorySegment quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, quadVertices);
            glBufferData.invokeExact(0x8892, quadVertices.length * 4L, quadData, 0x88E4 /* GL_STATIC_DRAW */);
        }
    }

    /** Buffer binding + vertex attrib state belongs to the context, not the shared object namespace. */
    private void bindQuadAttribs() throws Throwable {
        glBindBuffer.invokeExact(0x8892 /* GL_ARRAY_BUFFER */, quadVbo);
        glEnableVertexAttribArray.invokeExact(0);
        glVertexAttribPointer.invokeExact(0, 2, 0x1406 /* GL_FLOAT */, (byte) 0, 0, MemorySegment.NULL);
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
                boolean left = (m & (1 << 8)) != 0;
                boolean right = (m & (1 << 10)) != 0;
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
        glDrawArrays.invokeExact(0x0005 /* GL_TRIANGLE_STRIP */, 0, 4);
    }

    public void swapBuffers(Surface surface) throws Throwable {
        glXSwapBuffers.invokeExact(display, surface.window());
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    @Override
    public void close() {
        try {
            int ignored = (int) XCloseDisplay.invokeExact(display);
        } catch (Throwable ignored) {}
    }
}
