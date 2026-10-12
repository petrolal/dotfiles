package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WaylandCanvas puts one fullscreen OpenGL-rendered zwlr_layer_shell_v1
 * {@code LAYER_BACKGROUND} surface on a given Wayland output (monitor), with
 * an EGL context bound to it. Protocol marshaling that libwayland-client only
 * exposes through its variadic wl_proxy_marshal_flags() C API (registry
 * binding, layer-surface setup, seat/pointer listeners) is delegated to the
 * small native bridge compiled from src/main/native/wayland-shim/wlshim.c
 * (./gradlew compileWlshim); everything else -- EGL context creation, every
 * GL 3.3 call -- is a direct FFM downcall against libEGL.so/libGL.so, the
 * same pattern {@link X11Canvas} uses for GLX.
 *
 * One instance == one output. For multi-monitor setups, create one
 * WaylandCanvas per output index (see {@link #detectOutputCount()}).
 */
public class WaylandCanvas extends GlCanvas {

    // ---- wlshim (native/wayland-shim/wlshim.c) ----
    private static final MethodHandle wlshimConnect;
    private static final MethodHandle wlshimOutputCount;
    private static final MethodHandle wlshimGetOutput;
    private static final MethodHandle wlshimCreateBackgroundSurface;
    private static final MethodHandle wlshimSurfaceWidth;
    private static final MethodHandle wlshimSurfaceHeight;
    private static final MethodHandle wlshimDispatch;
    private static final MethodHandle wlshimGetPointer;
    private static final MethodHandle wlshimDestroy;

    // ---- EGL ----
    private static final MethodHandle eglGetPlatformDisplay;
    private static final MethodHandle eglInitialize;
    private static final MethodHandle eglBindAPI;
    private static final MethodHandle eglChooseConfig;
    private static final MethodHandle eglCreateWindowSurface;
    private static final MethodHandle eglCreateContext;
    private static final MethodHandle eglMakeCurrent;
    private static final MethodHandle eglSwapBuffers;
    private static final MethodHandle eglSwapInterval;
    private static final MethodHandle eglGetProcAddress;

    private static final int EGL_PLATFORM_WAYLAND_KHR = 0x31D8;
    private static final int EGL_SURFACE_TYPE = 0x3033;
    private static final int EGL_WINDOW_BIT = 0x0004;
    private static final int EGL_RENDERABLE_TYPE = 0x3040;
    private static final int EGL_OPENGL_BIT = 0x0008;
    private static final int EGL_RED_SIZE = 0x3024;
    private static final int EGL_GREEN_SIZE = 0x3023;
    private static final int EGL_BLUE_SIZE = 0x3022;
    private static final int EGL_ALPHA_SIZE = 0x3021;
    private static final int EGL_NONE = 0x3038;
    private static final int EGL_CONTEXT_MAJOR_VERSION = 0x3098;
    private static final int EGL_CONTEXT_MINOR_VERSION = 0x30FB;
    private static final int EGL_OPENGL_API = 0x30A2;

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

    /** Locates libwlshim.so next to the dotfiles-deployer invoker or under the repo's bin/. */
    private static SymbolLookup loadWlshim(Arena arena) {
        String[] candidates = {
            "libwlshim.so",
            System.getProperty("user.dir") + "/bin/libwlshim.so",
        };
        for (String c : candidates) {
            try {
                return SymbolLookup.libraryLookup(c, arena);
            } catch (IllegalArgumentException ignored) {
                // try next candidate
            }
        }
        Path root = dev.petrolal.dotfiles.core.DotfilePaths.findDotfilesRoot();
        Path inBin = root.resolve("bin/libwlshim.so");
        if (Files.exists(inBin)) {
            return SymbolLookup.libraryLookup(inBin, arena);
        }
        throw new IllegalStateException(
                "libwlshim.so not found. Build it with './gradlew compileWlshim' first.");
    }

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup wlshim = loadWlshim(Arena.global());
        SymbolLookup egl = loadLibrary("libEGL.so", Arena.global());

        wlshimConnect = linker.downcallHandle(wlshim.find("wlshim_connect").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        wlshimOutputCount = linker.downcallHandle(wlshim.find("wlshim_output_count").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        wlshimGetOutput = linker.downcallHandle(wlshim.find("wlshim_get_output").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        wlshimCreateBackgroundSurface = linker.downcallHandle(wlshim.find("wlshim_create_background_surface").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        wlshimSurfaceWidth = linker.downcallHandle(wlshim.find("wlshim_surface_width").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        wlshimSurfaceHeight = linker.downcallHandle(wlshim.find("wlshim_surface_height").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        wlshimDispatch = linker.downcallHandle(wlshim.find("wlshim_dispatch").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        wlshimGetPointer = linker.downcallHandle(wlshim.find("wlshim_get_pointer").get(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        wlshimDestroy = linker.downcallHandle(wlshim.find("wlshim_destroy").get(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        eglGetPlatformDisplay = linker.downcallHandle(egl.find("eglGetPlatformDisplay").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        eglInitialize = linker.downcallHandle(egl.find("eglInitialize").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        eglBindAPI = linker.downcallHandle(egl.find("eglBindAPI").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        eglChooseConfig = linker.downcallHandle(egl.find("eglChooseConfig").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        eglCreateWindowSurface = linker.downcallHandle(egl.find("eglCreateWindowSurface").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        eglCreateContext = linker.downcallHandle(egl.find("eglCreateContext").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        eglMakeCurrent = linker.downcallHandle(egl.find("eglMakeCurrent").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        eglSwapBuffers = linker.downcallHandle(egl.find("eglSwapBuffers").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        eglSwapInterval = linker.downcallHandle(egl.find("eglSwapInterval").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        eglGetProcAddress = linker.downcallHandle(egl.find("eglGetProcAddress").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    /** Connects standalone, counts outputs, disconnects. Called once by Wallpaper.java before
     *  creating one WaylandCanvas per monitor. */
    public static int detectOutputCount() throws Throwable {
        MemorySegment ctx = (MemorySegment) wlshimConnect.invokeExact();
        if (ctx.equals(MemorySegment.NULL)) return 0;
        try {
            return (int) wlshimOutputCount.invokeExact(ctx);
        } finally {
            wlshimDestroy.invokeExact(ctx);
        }
    }

    private record Init(MemorySegment ctx, MemorySegment wlSurface, MemorySegment eglDisplay,
                         MemorySegment eglSurface, int width, int height) {}

    private final MemorySegment ctx;
    private final MemorySegment wlSurface;
    private final MemorySegment eglDisplay;
    private final MemorySegment eglSurface;
    private final int width;
    private final int height;

    public WaylandCanvas(int outputIndex) throws Throwable {
        this(nativeInit(outputIndex));
    }

    private WaylandCanvas(Init init) throws Throwable {
        super(WaylandCanvas::resolveGlProc);
        this.ctx = init.ctx();
        this.wlSurface = init.wlSurface();
        this.eglDisplay = init.eglDisplay();
        this.eglSurface = init.eglSurface();
        this.width = init.width();
        this.height = init.height();

        int ignoredInterval = (int) eglSwapInterval.invokeExact(eglDisplay, 0);
        setupFullscreenQuad();
    }

    private static MemorySegment resolveGlProc(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return (MemorySegment) eglGetProcAddress.invokeExact(arena.allocateFrom(name));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to resolve GL proc " + name, t);
        }
    }

    private static Init nativeInit(int outputIndex) throws Throwable {
        MemorySegment ctx = (MemorySegment) wlshimConnect.invokeExact();
        if (ctx.equals(MemorySegment.NULL)) {
            throw new IllegalStateException(
                    "wlshim: could not connect to the Wayland display. Is this running under " +
                    "Wayfire (or another wlroots compositor exposing zwlr_layer_shell_v1)?");
        }

        try (Arena arena = Arena.ofConfined()) {
            int outputCount = (int) wlshimOutputCount.invokeExact(ctx);
            MemorySegment output = (outputIndex < outputCount)
                    ? (MemorySegment) wlshimGetOutput.invokeExact(ctx, outputIndex)
                    : MemorySegment.NULL;

            MemorySegment outDisplay = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment outEglWindow = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment wlSurface = (MemorySegment) wlshimCreateBackgroundSurface.invokeExact(
                    ctx, output, outDisplay, outEglWindow);
            if (wlSurface.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("wlshim: failed to create the layer-shell background surface");
            }
            MemorySegment wlDisplay = outDisplay.get(ValueLayout.ADDRESS, 0);
            MemorySegment eglWindow = outEglWindow.get(ValueLayout.ADDRESS, 0);

            int width = (int) wlshimSurfaceWidth.invokeExact(ctx, wlSurface);
            int height = (int) wlshimSurfaceHeight.invokeExact(ctx, wlSurface);

            MemorySegment eglDisplay = (MemorySegment) eglGetPlatformDisplay.invokeExact(
                    EGL_PLATFORM_WAYLAND_KHR, wlDisplay, MemorySegment.NULL);
            if (eglDisplay.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("eglGetPlatformDisplay failed for the Wayland platform");
            }
            int initOk = (int) eglInitialize.invokeExact(eglDisplay, MemorySegment.NULL, MemorySegment.NULL);
            if (initOk == 0) {
                throw new IllegalStateException("eglInitialize failed");
            }
            int boundOk = (int) eglBindAPI.invokeExact(EGL_OPENGL_API);
            if (boundOk == 0) {
                throw new IllegalStateException("eglBindAPI(EGL_OPENGL_API) failed");
            }

            MemorySegment configAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, new int[] {
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT,
                    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                    EGL_NONE
            });
            MemorySegment configOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment numConfigOut = arena.allocate(ValueLayout.JAVA_INT);
            int chooseOk = (int) eglChooseConfig.invokeExact(eglDisplay, configAttrs, configOut, 1, numConfigOut);
            if (chooseOk == 0 || numConfigOut.get(ValueLayout.JAVA_INT, 0) == 0) {
                throw new IllegalStateException("eglChooseConfig found no matching EGL config");
            }
            MemorySegment config = configOut.get(ValueLayout.ADDRESS, 0);

            MemorySegment eglSurface = (MemorySegment) eglCreateWindowSurface.invokeExact(
                    eglDisplay, config, eglWindow, MemorySegment.NULL);
            if (eglSurface.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("eglCreateWindowSurface failed");
            }

            MemorySegment ctxAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, new int[] {
                    EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 3, EGL_NONE
            });
            MemorySegment eglContext = (MemorySegment) eglCreateContext.invokeExact(
                    eglDisplay, config, MemorySegment.NULL, ctxAttrs);
            if (eglContext.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("eglCreateContext failed (no OpenGL 3.3 core context available)");
            }

            int currentOk = (int) eglMakeCurrent.invokeExact(eglDisplay, eglSurface, eglSurface, eglContext);
            if (currentOk == 0) {
                throw new IllegalStateException("eglMakeCurrent failed");
            }

            return new Init(ctx, wlSurface, eglDisplay, eglSurface, width, height);
        }
    }

    @Override
    public MouseInfo queryMouse() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment x = arena.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment y = arena.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment left = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment right = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment over = arena.allocate(ValueLayout.JAVA_INT);
            wlshimGetPointer.invokeExact(ctx, wlSurface, x, y, left, right, over);
            if (over.get(ValueLayout.JAVA_INT, 0) == 0) {
                // Pointer isn't over this output's surface (e.g. a window covers it there,
                // or the cursor is on another monitor) -- same off-screen sentinel X11Canvas
                // never needed because it queried the whole root window at once.
                return new MouseInfo(-1000.0f, -1000.0f, false, false);
            }
            return new MouseInfo(
                    x.get(ValueLayout.JAVA_FLOAT, 0),
                    y.get(ValueLayout.JAVA_FLOAT, 0),
                    left.get(ValueLayout.JAVA_INT, 0) != 0,
                    right.get(ValueLayout.JAVA_INT, 0) != 0);
        } catch (Throwable ignored) {}
        return new MouseInfo(-1000.0f, -1000.0f, false, false);
    }

    @Override
    public int queryWorkspace() {
        // wlroots/Wayfire has no standardized cross-compositor workspace-index protocol
        // (unlike X11's _NET_CURRENT_DESKTOP). Left at 0 pending a Wayfire-specific IPC query.
        return 0;
    }

    @Override
    public int getWidth() { return width; }

    @Override
    public int getHeight() { return height; }

    @Override
    public void swapBuffers() throws Throwable {
        int ignored = (int) eglSwapBuffers.invokeExact(eglDisplay, eglSurface);
        int ignoredDispatch = (int) wlshimDispatch.invokeExact(ctx);
    }

    @Override
    public void close() {
        try {
            wlshimDestroy.invokeExact(ctx);
        } catch (Throwable ignored) {}
    }
}
