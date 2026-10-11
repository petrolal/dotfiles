package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * X11Canvas manages the native X11 desktop background window and its OpenGL context.
 * Provides query methods for global mouse and workspace states.
 */
public class X11Canvas implements AutoCloseable {

    public record MouseInfo(float x, float y, boolean leftDown, boolean rightDown) {}

    private final MemorySegment display;
    private final int screen;
    private final long root;
    private final int width;
    private final int height;
    private final long window;
    private final MemorySegment glContext;
    private final long atomCurrentDesktop;

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

    // Direct OpenGL function handles
    public final MethodHandle glCreateShader;
    public final MethodHandle glShaderSource;
    public final MethodHandle glCompileShader;
    public final MethodHandle glCreateProgram;
    public final MethodHandle glAttachShader;
    public final MethodHandle glLinkProgram;
    public final MethodHandle glUseProgram;
    public final MethodHandle glDeleteShader;
    public final MethodHandle glDeleteProgram;
    public final MethodHandle glGetUniformLocation;
    public final MethodHandle glUniform1f;
    public final MethodHandle glUniform2f;
    public final MethodHandle glUniform4f;
    public final MethodHandle glViewport;
    public final MethodHandle glGenBuffers;
    public final MethodHandle glBindBuffer;
    public final MethodHandle glBufferData;
    public final MethodHandle glEnableVertexAttribArray;
    public final MethodHandle glVertexAttribPointer;
    public final MethodHandle glDrawArrays;

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

        window = (long) XCreateSimpleWindow.invokeExact(display, root, 0, 0, width, height, 0, 0L, 0L);

        // Mark window as X11 desktop background & layer below
        try (Arena arena = Arena.ofConfined()) {
            long atomType = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE"), 0);
            long atomDesktop = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE_DESKTOP"), 0);
            long atomState = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_STATE"), 0);
            long atomBelow = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_STATE_BELOW"), 0);
            long atomAtom = (long) XInternAtom.invokeExact(display, arena.allocateFrom("ATOM"), 0);
            atomCurrentDesktop = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_CURRENT_DESKTOP"), 0);

            MemorySegment propType = arena.allocateFrom(ValueLayout.JAVA_LONG, atomDesktop);
            int ignoredType = (int) XChangeProperty.invokeExact(display, window, atomType, atomAtom, 32, 0, propType, 1);

            MemorySegment propBelow = arena.allocateFrom(ValueLayout.JAVA_LONG, atomBelow);
            int ignoredBelow = (int) XChangeProperty.invokeExact(display, window, atomState, atomAtom, 32, 0, propBelow, 1);
        }

        int ignoredMap = (int) XMapWindow.invokeExact(display, window);
        int ignoredLower = (int) XLowerWindow.invokeExact(display, window);

        // Initialize OpenGL via GLX
        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, 4 /* GLX_RGBA */, 5 /* GLX_DOUBLEBUFFER */, 0);
            MemorySegment visInfo = (MemorySegment) glXChooseVisual.invokeExact(display, screen, glAttrs);
            glContext = (MemorySegment) glXCreateContext.invokeExact(display, visInfo, MemorySegment.NULL, 1);
            int ignoredCurrent = (int) glXMakeCurrent.invokeExact(display, window, glContext);
        }

        // Initialize GL function pointers
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

        // Create fullscreen quad VBO
        setupFullscreenQuad();
    }

    private void setupFullscreenQuad() throws Throwable {
        float[] quadVertices = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment vboBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenBuffers.invokeExact(1, vboBuf);
            int vbo = vboBuf.get(ValueLayout.JAVA_INT, 0);

            glBindBuffer.invokeExact(0x8892 /* GL_ARRAY_BUFFER */, vbo);
            MemorySegment quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, quadVertices);
            glBufferData.invokeExact(0x8892, quadVertices.length * 4L, quadData, 0x88E4 /* GL_STATIC_DRAW */);

            glEnableVertexAttribArray.invokeExact(0);
            glVertexAttribPointer.invokeExact(0, 2, 0x1406 /* GL_FLOAT */, (byte) 0, 0, MemorySegment.NULL);
        }
    }

    private MethodHandle getProc(Linker linker, String name, FunctionDescriptor desc) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ptr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom(name));
            return linker.downcallHandle(ptr, desc);
        }
    }

    public MouseInfo queryMouse() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rootRet = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment childRet = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment rootX = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment rootY = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment winX = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment winY = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment mask = arena.allocate(ValueLayout.JAVA_INT);

            int ok = (int) XQueryPointer.invokeExact(display, root, rootRet, childRet, rootX, rootY, winX, winY, mask);
            if (ok != 0) {
                int rx = rootX.get(ValueLayout.JAVA_INT, 0);
                int ry = rootY.get(ValueLayout.JAVA_INT, 0);
                int m = mask.get(ValueLayout.JAVA_INT, 0);
                boolean left = (m & (1 << 8)) != 0;
                boolean right = (m & (1 << 10)) != 0;
                return new MouseInfo((float) rx, (float) ry, left, right);
            }
        } catch (Throwable ignored) {}
        return new MouseInfo(0.0f, 0.0f, false, false);
    }

    public int queryWorkspace() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment actualType = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment actualFormat = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment nitems = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment bytesAfter = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment propPtr = arena.allocate(ValueLayout.ADDRESS);

            int status = (int) XGetWindowProperty.invokeExact(
                display, root, atomCurrentDesktop,
                0L, 1L, 0, 0L, actualType, actualFormat, nitems, bytesAfter, propPtr
            );

            if (status == 0) {
                MemorySegment data = propPtr.get(ValueLayout.ADDRESS, 0);
                if (!data.equals(MemorySegment.NULL)) {
                    long desktop = data.get(ValueLayout.JAVA_LONG, 0);
                    int ignored = (int) XFree.invokeExact(data);
                    return (int) desktop;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public void setViewport(int x, int y, int width, int height) throws Throwable {
        glViewport.invokeExact(x, y, width, height);
    }

    public void drawQuad() throws Throwable {
        glDrawArrays.invokeExact(0x0005 /* GL_TRIANGLE_STRIP */, 0, 4);
    }

    public void swapBuffers() throws Throwable {
        glXSwapBuffers.invokeExact(display, window);
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
