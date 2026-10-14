package dev.petrolal.dotfiles.wallpaper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.domain.Result;
import dev.petrolal.dotfiles.system.ProcessRunner;

/**
 * Universal wallpaper controller:
 * - Dynamically adapts to ANY multi-monitor count (1, 2, 3, or more displays)
 * - Computes OpenGL bottom-left viewports correctly for arbitrary layouts (horizontal, vertical, offset)
 * - Frame pacing is driven entirely by vsync (swapBuffers blocks on vblank, see
 *   X11Canvas.setSwapInterval), so it naturally tracks whichever display's
 *   refresh rate the driver syncs the drawable to
 * - Live hot-reloading & safe uniform dispatches per monitor
 */
public final class Wallpaper {

    public record Monitor(int id, int x, int y, int width, int height) {}

    private Path resolveShader(String name, String fallbackName) {
        Path p = resolveShader(name);
        if (Files.exists(p)) return p;
        if (fallbackName != null) {
            Path fb = resolveShader(fallbackName);
            if (Files.exists(fb)) return fb;
        }
        return p;
    }

    private Path resolveShader(String name) {
        Path direct = Path.of(name);
        if (Files.exists(direct)) return direct;
        Path inStaticDir = Path.of("static", name);
        if (Files.exists(inStaticDir)) return inStaticDir;
        Path root = DotfilePaths.findDotfilesRoot();
        Path inStatic = root.resolve("src/main/resources/static").resolve(name);
        if (Files.exists(inStatic)) return inStatic;
        Path inRoot = root.resolve(name);
        if (Files.exists(inRoot)) return inRoot;
        Path homeDotfiles = DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources/static").resolve(name);
        if (Files.exists(homeDotfiles)) return homeDotfiles;
        return direct;
    }

    /** Forces every xfce4-desktop image-style/color-style property to "none"
     * (0) / "solid" (3) so the OpenGL wallpaper shows through instead of
     * being painted over by xfdesktop's own background. Driven entirely from
     * Java so each xfconf-query call's exit code is individually visible,
     * rather than as one opaque shell pipeline. */
    private void configureXfdesktopTransparency() {
        if (!DotfilePaths.commandExists("xfconf-query")) return;

        Result<String> listResult = ProcessRunner.captureOutput(
                List.of("xfconf-query", "-c", "xfce4-desktop", "-l"));
        listResult.ifOk(output -> {
            for (String property : output.lines().toList()) {
                if (property.contains("image-style")) {
                    ProcessRunner.run(List.of("xfconf-query", "-c", "xfce4-desktop", "-p", property, "-s", "0"));
                } else if (property.contains("color-style")) {
                    ProcessRunner.run(List.of("xfconf-query", "-c", "xfce4-desktop", "-p", property, "-s", "3"));
                }
            }
        });

        if (DotfilePaths.commandExists("xfdesktop")) {
            ProcessRunner.run(List.of("xfdesktop", "--reload"));
        }
    }

    /**
     * Dynamically detects all connected monitors' real geometry via xrandr.
     * Works on any screen configuration (single, dual, triple, vertical, mixed
     * resolution) because every monitor's position and size always comes from
     * a live query -- nothing about monitor count, size, or layout is assumed
     * or guessed anywhere in this method.
     */
    public List<Monitor> detectMonitors(int totalWidth, int totalHeight) {
        List<Monitor> list = parseListMonitors();
        if (list.isEmpty()) {
            list = parseXrandrQuery();
        }

        if (!list.isEmpty()) {
            list.sort(Comparator.comparingInt(Monitor::x).thenComparingInt(Monitor::y));
            return list;
        }

        // xrandr unavailable or gave nothing usable (e.g. a bare Xvfb test
        // display): treat the whole X screen as one monitor. Never guess a
        // monitor count or split -- an incorrect guess is worse than one
        // monitor-sized window covering everything.
        return List.of(new Monitor(0, 0, 0, totalWidth, totalHeight));
    }

    /** Primary detection path: `xrandr --listmonitors`. */
    private List<Monitor> parseListMonitors() {
        List<Monitor> list = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xrandr", "--listmonitors").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern pat = Pattern.compile("(\\d+):\\s+\\S+\\s+(\\d+)/\\d+x(\\d+)/\\d+\\+(\\d+)\\+(\\d+)");
                int autoId = 0;
                while ((line = reader.readLine()) != null) {
                    Matcher m = pat.matcher(line);
                    if (m.find()) {
                        int w = Integer.parseInt(m.group(2));
                        int h = Integer.parseInt(m.group(3));
                        int x = Integer.parseInt(m.group(4));
                        int y = Integer.parseInt(m.group(5));
                        list.add(new Monitor(autoId++, x, y, w, h));
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return list;
    }

    /**
     * Secondary detection path: `xrandr --query`, parsing each connected
     * output's own geometry line (e.g. "eDP-1 connected 1920x1080+0+0 ...").
     * Covers setups where --listmonitors isn't supported, with real detected
     * geometry rather than an assumed layout.
     */
    private List<Monitor> parseXrandrQuery() {
        List<Monitor> list = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xrandr", "--query").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern pat = Pattern.compile("^\\S+\\s+connected\\s+(?:primary\\s+)?(\\d+)x(\\d+)\\+(\\d+)\\+(\\d+)");
                int autoId = 0;
                while ((line = reader.readLine()) != null) {
                    Matcher m = pat.matcher(line);
                    if (m.find()) {
                        int w = Integer.parseInt(m.group(1));
                        int h = Integer.parseInt(m.group(2));
                        int x = Integer.parseInt(m.group(3));
                        int y = Integer.parseInt(m.group(4));
                        list.add(new Monitor(autoId++, x, y, w, h));
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return list;
    }

    public void start() throws Throwable {
        Path vertPath = resolveShader("wallpaper.vert");
        Path panoramaFrag = resolveShader("wallpaper.frag");

        // Ensure XFCE desktop background is transparent if xfdesktop is active
        configureXfdesktopTransparency();

        try (X11Canvas canvas = new X11Canvas()) {
            List<Monitor> monitors = detectMonitors(canvas.getWidth(), canvas.getHeight());
            int totalScreenHeight = canvas.getHeight();

            // One independent window + GL context per monitor (see X11Canvas):
            // The primary surface is vsync'd to pace the render loop to its hardware
            // vblank (e.g. 165Hz), while secondary surfaces present without blocking (swapInterval=0).
            // This prevents serial vblank waiting from dragging mixed-refresh-rate setups down to ~40 FPS.
            List<X11Canvas.Surface> surfaces = new ArrayList<>();
            Monitor firstMon = monitors.get(0);
            surfaces.add(canvas.initGL(firstMon.x(), firstMon.y(), firstMon.width(), firstMon.height()));
            for (int i = 1; i < monitors.size(); i++) {
                Monitor mon = monitors.get(i);
                surfaces.add(canvas.createSurface(mon.x(), mon.y(), mon.width(), mon.height(), 0));
            }

            // One shared program for every surface: all contexts share the same
            // GL object namespace (see X11Canvas), so it's a single continuous
            // panorama scene, rendered by one camera and split only by the bezels.
            ShaderPipeline sharedPipeline = new ShaderPipeline(canvas, vertPath, panoramaFrag);

            try (WallpaperEvents events = new WallpaperEvents(canvas)) {
                // SIGINT/SIGTERM halt the JVM via shutdown hooks, not by
                // unwinding the stack -- the try-with-resources close() calls
                // below never run on that path. Release the X11/GL state
                // explicitly so a killed daemon doesn't leak the display
                // connection, GLX contexts, and override-redirect windows.
                Thread shutdownHook = new Thread(canvas::close, "wallpaper-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);

                long startTimeNanos = System.nanoTime();
                long lastLowerNanos = System.nanoTime();
                final long lowerIntervalNanos = 3_000_000_000L;
                long lastReloadCheckNanos = System.nanoTime();
                final long reloadCheckIntervalNanos = 1_000_000_000L;

                try {
                    while (true) {
                        // Hot-reload is a developer convenience, not a per-frame need:
                        // stat()-ing the shader file 60x/sec forever is wasted syscalls.
                        long nowForReload = System.nanoTime();
                        if (nowForReload - lastReloadCheckNanos > reloadCheckIntervalNanos) {
                            sharedPipeline.reloadIfModified();
                            lastReloadCheckNanos = nowForReload;
                        }

                        // Desktop icons / panel widgets can get (re)mapped above us after
                        // startup; periodically re-assert that we stay at the bottom of
                        // the window stack instead of covering them.
                        long nowForLower = System.nanoTime();
                        if (nowForLower - lastLowerNanos > lowerIntervalNanos) {
                            canvas.lowerToBackground();
                            lastLowerNanos = nowForLower;
                        }

                        long frameStartNanos = System.nanoTime();
                        float elapsed = (frameStartNanos - startTimeNanos) / 1_000_000_000.0f;

                        // Throttled non-blocking query strictly bound to render frame cadence
                        WallpaperEvents.WallpaperState rawState = events.pollState(canvas.getWidth(), canvas.getHeight(), elapsed);

                        // Render + present each monitor independently, on its own surface.
                        for (int i = 0; i < monitors.size(); i++) {
                            Monitor mon = monitors.get(i);
                            X11Canvas.Surface surface = surfaces.get(i);

                            float viewportOffsetX = mon.x();
                            float viewportOffsetY = totalScreenHeight - (mon.y() + mon.height());

                            canvas.makeCurrent(surface);
                            canvas.setViewport(0, 0, mon.width(), mon.height());
                            sharedPipeline.bind();
                            sharedPipeline.updateUniforms(rawState, viewportOffsetX, viewportOffsetY);
                            canvas.drawQuad();
                            canvas.swapBuffers(surface);
                        }
                    }
                } finally {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
            }
        }
    }
}
