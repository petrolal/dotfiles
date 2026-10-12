package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * X11Canvas manages the native X11 desktop background window and its OpenGL context.
 * Provides query methods for global mouse and workspace states.
 */
public class X11Canvas extends GlCanvas {

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

    /** Everything that must happen before the GlCanvas super constructor can resolve GL procs. */
    private record Init(MemorySegment display, int screen, long root, int width, int height, long window,
                         MemorySegment glContext, long atomCurrentDesktop) {}

    private static Init nativeInit() throws Throwable {
        MemorySegment display = (MemorySegment) XOpenDisplay.invokeExact(MemorySegment.NULL);
        if (display.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Failed to open X11 Display.");
        }

        int screen = (int) XDefaultScreen.invokeExact(display);
        long root = (long) XRootWindow.invokeExact(display, screen);
        int width = (int) XDisplayWidth.invokeExact(display, screen);
        int height = (int) XDisplayHeight.invokeExact(display, screen);

        long window = (long) XCreateSimpleWindow.invokeExact(display, root, 0, 0, width, height, 0, 0L, 0L);

        long atomCurrentDesktop;
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
        MemorySegment glContext;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, 4 /* GLX_RGBA */, 5 /* GLX_DOUBLEBUFFER */, 0);
            MemorySegment visInfo = (MemorySegment) glXChooseVisual.invokeExact(display, screen, glAttrs);
            glContext = (MemorySegment) glXCreateContext.invokeExact(display, visInfo, MemorySegment.NULL, 1);
            int ignoredCurrent = (int) glXMakeCurrent.invokeExact(display, window, glContext);
        }

        return new Init(display, screen, root, width, height, window, glContext, atomCurrentDesktop);
    }

    public X11Canvas() throws Throwable {
        this(nativeInit());
    }

    private X11Canvas(Init init) throws Throwable {
        super(X11Canvas::resolveGlProc);
        this.display = init.display();
        this.screen = init.screen();
        this.root = init.root();
        this.width = init.width();
        this.height = init.height();
        this.window = init.window();
        this.glContext = init.glContext();
        this.atomCurrentDesktop = init.atomCurrentDesktop();

        // Explicitly disable GLX VSync blocking across display screens
        setSwapInterval(0);

        // Create fullscreen quad VBO
        setupFullscreenQuad();
    }

    private static MemorySegment resolveGlProc(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom(name));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to resolve GL proc " + name, t);
        }
    }

    /**
     * Disables VSync blocking by setting swap interval to 0.
     * Resolves glXSwapIntervalEXT, glXSwapIntervalMESA, or glXSwapIntervalSGI dynamically.
     */
    public void setSwapInterval(int interval) {
        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            // 1. Try glXSwapIntervalEXT (Display*, GLXDrawable, int)
            MemorySegment extPtr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalEXT"));
            if (!extPtr.equals(MemorySegment.NULL) && extPtr.address() != 0) {
                MethodHandle handle = linker.downcallHandle(extPtr,
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                handle.invokeExact(display, window, interval);
                return;
            }

            // 2. Try glXSwapIntervalMESA (int)
            MemorySegment mesaPtr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalMESA"));
            if (!mesaPtr.equals(MemorySegment.NULL) && mesaPtr.address() != 0) {
                MethodHandle handle = linker.downcallHandle(mesaPtr,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                int ignored = (int) handle.invokeExact(interval);
                return;
            }

            // 3. Try glXSwapIntervalSGI (int)
            MemorySegment sgiPtr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalSGI"));
            if (!sgiPtr.equals(MemorySegment.NULL) && sgiPtr.address() != 0) {
                MethodHandle handle = linker.downcallHandle(sgiPtr,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                int ignored = (int) handle.invokeExact(interval);
            }
        } catch (Throwable ignored) {}
    }

    @Override
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

    @Override
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

    @Override
    public void swapBuffers() throws Throwable {
        glXSwapBuffers.invokeExact(display, window);
    }

    @Override
    public int getWidth() { return width; }

    @Override
    public int getHeight() { return height; }

    @Override
    public void close() {
        try {
            int ignored = (int) XCloseDisplay.invokeExact(display);
        } catch (Throwable ignored) {}
    }
}
