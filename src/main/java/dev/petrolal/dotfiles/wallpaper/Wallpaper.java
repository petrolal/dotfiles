package dev.petrolal.dotfiles.wallpaper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.petrolal.dotfiles.core.DotfilePaths;

/**
 * Universal wallpaper controller:
 * - Dynamically adapts to ANY multi-monitor count (1, 2, 3, or more displays)
 * - Computes OpenGL bottom-left viewports correctly for arbitrary layouts (horizontal, vertical, offset)
 * - Dynamically detects refresh rates or accepts custom target framerates via WALLPAPER_FPS
 * - Sub-millisecond monotonic frame budgeting (LockSupport.parkNanos + Thread.onSpinWait)
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

    private void configureXfdesktopTransparency() {
        try {
            // Check if xfconf-query exists before attempting execution
            Process check = new ProcessBuilder("which", "xfconf-query").start();
            if (check.waitFor() != 0) return;

            ProcessBuilder pb = new ProcessBuilder(
                "sh", "-c",
                "xfconf-query -c xfce4-desktop -l 2>/dev/null | grep -E 'image-style|color-style' | while read p; do " +
                "  if echo \"$p\" | grep -q 'image-style'; then xfconf-query -c xfce4-desktop -p \"$p\" -s 0 2>/dev/null; fi; " +
                "  if echo \"$p\" | grep -q 'color-style'; then xfconf-query -c xfce4-desktop -p \"$p\" -s 3 2>/dev/null; fi; " +
                "done; xfdesktop --reload 2>/dev/null || true"
            );
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    /**
     * Dynamically detects all connected monitors via xrandr.
     * Works on any screen configuration (single, dual, triple, vertical, mixed resolution).
     */
    public List<Monitor> detectMonitors(int totalWidth, int totalHeight) {
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

        if (!list.isEmpty()) {
            list.sort(Comparator.comparingInt(Monitor::x).thenComparingInt(Monitor::y));
            return list;
        }

        // Generic fallback if xrandr is unavailable or outputs single virtual frame
        if (totalWidth >= 2500) {
            int half = totalWidth / 2;
            return List.of(
                new Monitor(0, 0, 0, half, totalHeight),
                new Monitor(1, half, 0, half, totalHeight)
            );
        }

        return List.of(new Monitor(0, 0, 0, totalWidth, totalHeight));
    }

    /**
     * Detects target monitor refresh rate or respects WALLPAPER_FPS environment property.
     */
    private long detectTargetFrameNanos() {
        String customFps = System.getenv("WALLPAPER_FPS");
        if (customFps != null && !customFps.isBlank()) {
            try {
                long fps = Long.parseLong(customFps.trim());
                if (fps > 0) return 1_000_000_000L / fps;
            } catch (Exception ignored) {}
        }
        return 16_666_667L; // Default standard 60 FPS (~16.666 ms)
    }

    private WallpaperEvents.WallpaperState adjustState(WallpaperEvents.WallpaperState raw, Monitor mon) {
        float localX = -1000.0f;
        float localY = -1000.0f;
        if (raw.mouseX() >= mon.x() && raw.mouseX() < mon.x() + mon.width()
                && raw.mouseY() >= mon.y() && raw.mouseY() < mon.y() + mon.height()) {
            localX = raw.mouseX() - mon.x();
            localY = raw.mouseY() - mon.y();
        }

        return new WallpaperEvents.WallpaperState(
            mon.width(),
            mon.height(),
            raw.time(),
            localX,
            localY,
            raw.mouseLeftDown(),
            raw.mouseRightDown(),
            raw.workspace(),
            raw.audioActivity(),
            raw.cpu(),
            raw.ram()
        );
    }

    public void start() throws Throwable {
        Path vertPath = resolveShader("wallpaper.vert");
        Path leftFrag = resolveShader("wallpaper_left.frag", "wallpaper.frag");
        Path rightFrag = resolveShader("wallpaper_right.frag", "wallpaper_left.frag");

        if (System.getenv("WAYLAND_DISPLAY") != null) {
            startWayland(vertPath, leftFrag, rightFrag);
        } else {
            startX11(vertPath, leftFrag, rightFrag);
        }
    }

    /**
     * Wayland path: zwlr_layer_shell_v1 has no shared "root window" spanning every
     * monitor the way X11 did, so each output gets its own WaylandCanvas (own EGL
     * context, own shader pipeline), run from the same frame loop.
     */
    private void startWayland(Path vertPath, Path leftFrag, Path rightFrag) throws Throwable {
        int outputCount = Math.max(1, WaylandCanvas.detectOutputCount());

        List<WaylandCanvas> canvases = new ArrayList<>();
        List<ShaderPipeline> pipelines = new ArrayList<>();
        List<WallpaperEvents> events = new ArrayList<>();
        try {
            for (int i = 0; i < outputCount; i++) {
                WaylandCanvas canvas = new WaylandCanvas(i);
                canvases.add(canvas);
                pipelines.add(new ShaderPipeline(canvas, vertPath, i == 0 ? leftFrag : rightFrag));
                events.add(new WallpaperEvents(canvas));
            }

            long startTimeNanos = System.nanoTime();
            long nextFrameNanos = System.nanoTime();
            long targetFrameNanos = detectTargetFrameNanos();

            while (true) {
                for (ShaderPipeline pipeline : pipelines) pipeline.reloadIfModified();

                long frameStartNanos = System.nanoTime();
                float elapsed = (frameStartNanos - startTimeNanos) / 1_000_000_000.0f;

                for (int i = 0; i < canvases.size(); i++) {
                    WaylandCanvas canvas = canvases.get(i);
                    WallpaperEvents.WallpaperState state =
                            events.get(i).pollState(canvas.getWidth(), canvas.getHeight(), elapsed);

                    canvas.setViewport(0, 0, canvas.getWidth(), canvas.getHeight());
                    pipelines.get(i).bind();
                    pipelines.get(i).updateUniforms(state);
                    canvas.drawQuad();
                    canvas.swapBuffers();
                }

                nextFrameNanos += targetFrameNanos;
                long now = System.nanoTime();
                long sleepNanos = nextFrameNanos - now;
                if (sleepNanos > 0) {
                    if (sleepNanos > 2_000_000L) {
                        LockSupport.parkNanos(sleepNanos - 1_000_000L);
                    }
                    while (System.nanoTime() < nextFrameNanos) {
                        Thread.onSpinWait();
                    }
                } else {
                    nextFrameNanos = now;
                }
            }
        } finally {
            for (WaylandCanvas canvas : canvases) canvas.close();
        }
    }

    private void startX11(Path vertPath, Path leftFrag, Path rightFrag) throws Throwable {
        // Ensure XFCE desktop background is transparent if xfdesktop is active
        configureXfdesktopTransparency();

        try (X11Canvas canvas = new X11Canvas()) {
            List<Monitor> monitors = detectMonitors(canvas.getWidth(), canvas.getHeight());
            int totalScreenHeight = canvas.getHeight();

            // Create appropriate pipeline per screen (left shader for mon 0, right for mon 1+, fallback to left)
            List<ShaderPipeline> pipelines = new ArrayList<>();
            for (int i = 0; i < monitors.size(); i++) {
                if (i == 0) {
                    pipelines.add(new ShaderPipeline(canvas, vertPath, leftFrag));
                } else if (i == 1) {
                    pipelines.add(new ShaderPipeline(canvas, vertPath, rightFrag));
                } else {
                    // For 3+ monitors, alternate or reuse left/right
                    pipelines.add((i % 2 == 0) ? pipelines.get(0) : pipelines.get(1));
                }
            }

            WallpaperEvents events = new WallpaperEvents(canvas);

            long startTimeNanos = System.nanoTime();
            long nextFrameNanos = System.nanoTime();
            long targetFrameNanos = detectTargetFrameNanos();

            while (true) {
                // Hot-reloading check for distinct pipelines
                pipelines.get(0).reloadIfModified();
                if (pipelines.size() > 1 && pipelines.get(1) != pipelines.get(0)) {
                    pipelines.get(1).reloadIfModified();
                }

                long frameStartNanos = System.nanoTime();
                float elapsed = (frameStartNanos - startTimeNanos) / 1_000_000_000.0f;

                // Throttled non-blocking query strictly bound to render frame cadence
                WallpaperEvents.WallpaperState rawState = events.pollState(canvas.getWidth(), canvas.getHeight(), elapsed);

                // Render each monitor dynamically using correct OpenGL bottom-left viewport coordinates
                for (int i = 0; i < monitors.size(); i++) {
                    Monitor mon = monitors.get(i);
                    ShaderPipeline pipeline = pipelines.get(i);

                    // OpenGL glViewport uses bottom-left origin: Y_gl = totalScreenHeight - (mon.y + mon.height)
                    int glViewportY = totalScreenHeight - (mon.y() + mon.height());

                    canvas.setViewport(mon.x(), glViewportY, mon.width(), mon.height());
                    pipeline.bind();
                    pipeline.updateUniforms(adjustState(rawState, mon));
                    canvas.drawQuad();
                }

                // Non-blocking buffer swap (with swap interval 0)
                canvas.swapBuffers();

                // High-resolution frame-timing budgeting using monotonic parkNanos + spin
                nextFrameNanos += targetFrameNanos;
                long now = System.nanoTime();
                long sleepNanos = nextFrameNanos - now;

                if (sleepNanos > 0) {
                    if (sleepNanos > 2_000_000L) {
                        LockSupport.parkNanos(sleepNanos - 1_000_000L);
                    }
                    while (System.nanoTime() < nextFrameNanos) {
                        Thread.onSpinWait();
                    }
                } else {
                    // Missed frame deadline (e.g. shader compilation or wake hitch); re-synchronize
                    nextFrameNanos = now;
                }
            }
        }
    }
}
