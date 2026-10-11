package dev.petrolal.dotfiles.wallpaper.nativebidings;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class X11DesktopWindow implements AutoCloseable {
    
    private final MemorySegment display;
    private final int screen;
    private final int width;
    private final int height;
    private final long window;

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

    private static SymbolLookup loadLibrary(String libName, Arena arena) {
        try {
            return SymbolLookup.libraryLookup(libName, arena);
        } catch (IllegalArgumentException e) {
            String[] fallbackDirs = {
                "/run/current-system/sw/lib",
                "/usr/lib",
                "/usr/local/lib"
            };
            for (String dir : fallbackDirs) {
                java.nio.file.Path path = java.nio.file.Path.of(dir, libName);
                if (java.nio.file.Files.exists(path)) {
                    return SymbolLookup.libraryLookup(path, arena);
                }
            }
            throw e;
        }
    }

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup x11 = loadLibrary("libX11.so", Arena.global());

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
    }

    public X11DesktopWindow() throws Throwable {
        display = (MemorySegment) XOpenDisplay.invokeExact(MemorySegment.NULL);
        if (display.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Failed to open X11 Display.");
        }

        screen = (int) XDefaultScreen.invokeExact(display);
        long root = (long) XRootWindow.invokeExact(display, screen);
        width = (int) XDisplayWidth.invokeExact(display, screen);
        height = (int) XDisplayHeight.invokeExact(display, screen);

        window = (long) XCreateSimpleWindow.invokeExact(display, root, 0, 0, width, height, 0, 0L, 0L);

        try (Arena arena = Arena.ofConfined()) {
            long atomType = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE"), 0);
            long atomDesktop = (long) XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE_DESKTOP"), 0);
            long atomAtom = (long) XInternAtom.invokeExact(display, arena.allocateFrom("ATOM"), 0);

            MemorySegment propVal = arena.allocateFrom(ValueLayout.JAVA_LONG, atomDesktop);
            int ignored = (int) XChangeProperty.invokeExact(display, window, atomType, atomAtom, 32, 0, propVal, 1);
        }

        int ignored = (int) XMapWindow.invokeExact(display, window);
    }

    public MemorySegment getDisplay() { return display; }
    public int getScreen() { return screen; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getWindow() { return window; }

    @Override
    public void close() throws Exception {
        try {
            int ignored = (int) XCloseDisplay.invokeExact(display);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
