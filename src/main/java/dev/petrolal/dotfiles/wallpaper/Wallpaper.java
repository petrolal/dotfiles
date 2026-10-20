package dev.petrolal.dotfiles.wallpaper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.domain.Result;
import dev.petrolal.dotfiles.system.ProcessRunner;

import sun.misc.Signal;

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

    public record Monitor(int id, int x, int y, int width, int height, boolean primary) {}

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
        return List.of(new Monitor(0, 0, 0, totalWidth, totalHeight, true));
    }

    /** Primary detection path: `xrandr --listmonitors`. Output lines look
     * like "0: +*eDP-1 1920/344x1080/193+1920+0  eDP-1" -- the '*' (present
     * only on the xrandr-designated primary output) sits right after the
     * leading '+', so it's captured explicitly rather than swallowed by a
     * generic \S+ match. */
    private List<Monitor> parseListMonitors() {
        List<Monitor> list = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xrandr", "--listmonitors").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern pat = Pattern.compile("(\\d+):\\s+\\+(\\*?)\\S*\\s+(\\d+)/\\d+x(\\d+)/\\d+\\+(\\d+)\\+(\\d+)");
                int autoId = 0;
                while ((line = reader.readLine()) != null) {
                    Matcher m = pat.matcher(line);
                    if (m.find()) {
                        boolean primary = !m.group(2).isEmpty();
                        int w = Integer.parseInt(m.group(3));
                        int h = Integer.parseInt(m.group(4));
                        int x = Integer.parseInt(m.group(5));
                        int y = Integer.parseInt(m.group(6));
                        list.add(new Monitor(autoId++, x, y, w, h, primary));
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
                Pattern pat = Pattern.compile("^\\S+\\s+connected\\s+(primary\\s+)?(\\d+)x(\\d+)\\+(\\d+)\\+(\\d+)");
                int autoId = 0;
                while ((line = reader.readLine()) != null) {
                    Matcher m = pat.matcher(line);
                    if (m.find()) {
                        boolean primary = m.group(1) != null;
                        int w = Integer.parseInt(m.group(2));
                        int h = Integer.parseInt(m.group(3));
                        int x = Integer.parseInt(m.group(4));
                        int y = Integer.parseInt(m.group(5));
                        list.add(new Monitor(autoId++, x, y, w, h, primary));
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return list;
    }

    /** Fraction of each monitor's real resolution the expensive raymarch
     * shader actually renders at, before a GL_NEAREST upscale blit back to
     * full size. The dominant per-frame cost is the raymarch, which runs
     * once per rendered pixel -- halving each axis cuts that work 4x, which
     * is what makes this wallpaper viable on entry-level/integrated GPUs
     * instead of requiring a discrete one. 0.5 was chosen as a default that
     * still upscales to a reasonably detailed image; this is also now the
     * ONLY source of the retro chunky-pixel look (see wallpaper.frag). */
    private static final float RENDER_SCALE = 0.5f;

    private static int scaledSize(int realSize) {
        return Math.max(1, Math.round(realSize * RENDER_SCALE));
    }

    /** Optional cap independent of vsync: trades motion smoothness for
     * battery/thermal headroom on weak hardware. The primary surface's vsync
     * already paces the loop; this only adds a floor under that pace when the
     * caller wants fewer frames/sec than vsync alone would deliver -- it
     * never speeds anything up. Not a busy-wait: frameStartNanos is the start
     * of a frame that already did real GPU work, and this sleeps once for
     * whatever's left of the target interval, not in a spin-check loop. */
    private static void throttleToMaxFps(long frameStartNanos, int maxFps) {
        if (maxFps <= 0) return;
        long targetFrameNanos = 1_000_000_000L / maxFps;
        long remainingNanos = frameStartNanos + targetFrameNanos - System.nanoTime();
        if (remainingNanos <= 0) return;
        try {
            Thread.sleep(remainingNanos / 1_000_000L, (int) (remainingNanos % 1_000_000L));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** @param maxFps Caps the render loop to this many frames/sec, independent
     *                of vsync -- useful on weak hardware to trade motion
     *                smoothness for battery/thermal headroom even below what
     *                RENDER_SCALE alone saves. 0 (or less) disables the cap,
     *                leaving the primary surface's vsync as the only pacing. */
    public void start(int maxFps) throws Throwable {
        Path vertPath = resolveShader("wallpaper.vert");
        Path panoramaFrag = resolveShader("wallpaper.frag");
        Path blitFrag = resolveShader("blit.frag");

        // Ensure XFCE desktop background is transparent if xfdesktop is active
        configureXfdesktopTransparency();

        try (X11Canvas canvas = new X11Canvas()) {
            List<Monitor> monitors = detectMonitors(canvas.getWidth(), canvas.getHeight());
            int totalScreenHeight = canvas.getHeight();

            // One independent window + GL context per monitor (see X11Canvas):
            // The primary surface is vsync'd to pace the render loop to its hardware
            // vblank (e.g. 165Hz), while secondary surfaces present without blocking (swapInterval=0).
            // This prevents serial vblank waiting from dragging mixed-refresh-rate setups down to ~40 FPS.
            //
            // "Primary" here means xrandr's actual designated primary output,
            // not whichever monitor happens to sort first by position -- a
            // monitor's physical/virtual placement (leftmost, topmost) has no
            // relationship to its refresh rate or which one the user wants
            // driving the pace. Picking the wrong one silently caps every
            // monitor's frame rate to that of an unrelated, possibly much
            // slower, secondary display.
            int primaryIndex = 0;
            for (int i = 0; i < monitors.size(); i++) {
                if (monitors.get(i).primary()) {
                    primaryIndex = i;
                    break;
                }
            }

            // Each monitor also gets its own low-resolution offscreen render
            // target (see RENDER_SCALE). Framebuffer Objects are NOT shared
            // across GL contexts the way programs/textures/buffers are (they're
            // "container objects"), so each one must be created while that
            // monitor's own context is current -- which createSurface/initGL
            // already leave current right when they return, hence creating it
            // immediately, inline, rather than in a separate pass afterward.
            List<X11Canvas.Surface> surfaces = new ArrayList<>(Collections.nCopies(monitors.size(), null));
            List<X11Canvas.RenderTarget> renderTargets = new ArrayList<>(Collections.nCopies(monitors.size(), null));

            Monitor primaryMon = monitors.get(primaryIndex);
            X11Canvas.Surface primarySurface =
                    canvas.initGL(primaryMon.x(), primaryMon.y(), primaryMon.width(), primaryMon.height());
            surfaces.set(primaryIndex, primarySurface);
            renderTargets.set(primaryIndex, canvas.createRenderTarget(
                    scaledSize(primaryMon.width()), scaledSize(primaryMon.height())));

            for (int i = 0; i < monitors.size(); i++) {
                if (i == primaryIndex) continue;
                Monitor mon = monitors.get(i);
                surfaces.set(i, canvas.createSurface(mon.x(), mon.y(), mon.width(), mon.height(), 0));
                renderTargets.set(i, canvas.createRenderTarget(scaledSize(mon.width()), scaledSize(mon.height())));
            }

            // One shared raymarch program for every surface: programs (unlike
            // FBOs) ARE shared across contexts in the same GL object namespace
            // (see X11Canvas), so it's a single continuous panorama scene,
            // rendered by one camera and split only by the bezels. It draws
            // into each monitor's small RenderTarget; sharedBlitPipeline then
            // upscales that into the real window-sized backbuffer.
            ShaderPipeline sharedPipeline = new ShaderPipeline(canvas, vertPath, panoramaFrag);
            BlitPipeline sharedBlitPipeline = new BlitPipeline(canvas, vertPath, blitFrag);

            try (WallpaperEvents events = new WallpaperEvents(canvas)) {
                // A background-launched process typically has SIGINT left at
                // its inherited disposition (often SIG_IGN, per job-control
                // convention), and GraalVM native-image's built-in SIGTERM
                // handling has been observed not to cover SIGINT either.
                // Install our own handler explicitly so Ctrl+C always works
                // regardless of how this process was started, routed through
                // the same System.exit() -> shutdown-hook path as SIGTERM.
                Signal.handle(new Signal("INT"), _ -> System.exit(0));

                // SIGINT/SIGTERM run shutdown hooks on a separate thread while
                // this render loop keeps executing concurrently -- Xlib/GLX
                // calls and the X11Canvas native state are not safe to touch
                // from two threads at once (no XInitThreads(), and closing
                // the shared Arena while this thread still dereferences its
                // segments is undefined behavior). So the hook never touches
                // canvas state itself: it only flips a flag and blocks the
                // JVM's exit until this thread observes it, exits the loop,
                // and runs its own try-with-resources close() below.
                //
                // That handoff only works if the render thread is actually
                // executing Java bytecode to observe the flag -- if it's
                // blocked inside a native GL call (e.g. a vsync'd
                // glXSwapBuffers with no vblank source to return from, which
                // happens under some virtualized/software display setups),
                // it can never get back to the flag check, and join() would
                // hang forever, leaving the daemon only killable with
                // SIGKILL. Bound the wait: if the render thread hasn't
                // stopped within the grace period, give up on a clean close()
                // and let the JVM exit anyway rather than hang indefinitely.
                final long shutdownGraceMillis = 2000;
                AtomicBoolean shutdownRequested = new AtomicBoolean(false);
                Thread renderThread = Thread.currentThread();
                Thread shutdownHook = new Thread(() -> {
                    shutdownRequested.set(true);
                    try {
                        renderThread.join(shutdownGraceMillis);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    if (renderThread.isAlive()) {
                        System.err.println("[wallpaper] render thread did not stop within "
                                + shutdownGraceMillis + "ms (likely blocked in a native GL call) -- "
                                + "exiting without finishing cleanup.");
                    }
                }, "wallpaper-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);

                long startTimeNanos = System.nanoTime();
                long lastLowerNanos = System.nanoTime();
                final long lowerIntervalNanos = 3_000_000_000L;
                long lastReloadCheckNanos = System.nanoTime();
                final long reloadCheckIntervalNanos = 1_000_000_000L;

                // STOPGAP for suspected secondary-monitor GPU/compositor flooding:
                // secondary surfaces are swapInterval=0 (no vsync of their own), so
                // without a cap they get re-rendered+re-presented as fast as the
                // primary paces the loop (e.g. 165Hz) even though the secondary
                // display can only ever show a fraction of that (e.g. 60Hz) --
                // every extra swap is pure wasted GPU work and an extra
                // recomposite for that output, which can starve the compositor
                // enough to visibly stutter cursor movement over that monitor.
                // Cap each secondary monitor to a conservative 60Hz cadence of its
                // own, independent of the primary's pace, by skipping its entire
                // render+present (not just the swap) until its own interval has
                // elapsed. This is deliberately a blunt, temporary throttle to
                // confirm that diagnosis -- the real fix is giving every monitor
                // its own vsync'd render thread instead of sharing one loop.
                final long secondaryIntervalNanos = 1_000_000_000L / 60;
                long[] lastSecondarySwapNanos = new long[monitors.size()];

                try {
                    while (!shutdownRequested.get()) {
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
                            if (i != primaryIndex) {
                                long nowForSecondary = System.nanoTime();
                                if (nowForSecondary - lastSecondarySwapNanos[i] < secondaryIntervalNanos) {
                                    continue; // not this secondary monitor's turn yet
                                }
                                lastSecondarySwapNanos[i] = nowForSecondary;
                            }

                            Monitor mon = monitors.get(i);
                            X11Canvas.Surface surface = surfaces.get(i);
                            X11Canvas.RenderTarget target = renderTargets.get(i);

                            float viewportOffsetX = mon.x();
                            float viewportOffsetY = totalScreenHeight - (mon.y() + mon.height());

                            canvas.makeCurrent(surface);

                            // Pass 1: the expensive raymarch, at reduced resolution.
                            canvas.bindFramebuffer(target.framebuffer());
                            canvas.setViewport(0, 0, target.width(), target.height());
                            sharedPipeline.bind();
                            sharedPipeline.updateUniforms(rawState, viewportOffsetX, viewportOffsetY, RENDER_SCALE);
                            canvas.drawQuad();

                            // Pass 2: cheap GL_NEAREST upscale of that into the real backbuffer.
                            canvas.bindFramebuffer(0);
                            canvas.setViewport(0, 0, mon.width(), mon.height());
                            sharedBlitPipeline.bind(target.texture());
                            canvas.drawQuad();

                            canvas.swapBuffers(surface);
                        }

                        throttleToMaxFps(frameStartNanos, maxFps);
                    }
                } finally {
                    // Fails with IllegalStateException when the JVM is already
                    // shutting down (the hook that is about to join us is
                    // already running) -- that's expected on the signal path,
                    // not an error.
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook);
                    } catch (IllegalStateException ignored) {
                    }
                }
            }
        }
    }
}
