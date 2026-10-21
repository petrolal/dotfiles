// ScreenLocker.java --- Interactive X11 screen locker: full-screen raymarched
// cyber-horror UI, keyboard/pointer grab, PAM-backed password check.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.List;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.wallpaper.BlitPipeline;
import dev.petrolal.dotfiles.wallpaper.ShaderPipeline;
import dev.petrolal.dotfiles.wallpaper.Wallpaper;
import dev.petrolal.dotfiles.wallpaper.X11Canvas;

import sun.misc.Signal;

/**
 * Phase 1 of the screen-locker spec: a real PAM-authenticated, input-grabbing
 * full-screen lock, reusing the existing raymarch shader pipeline with a
 * minimal reactive UI (password-buffer dots, error flash, verifying static
 * -- see wallpaper.frag's "Screen-Locker Reactive UI" section). The full
 * cyber-monolith / floating-rune SDF terminal from the spec is intentionally
 * deferred to a follow-up pass once this core lock/unlock path is verified
 * safe and correct.
 */
public final class ScreenLocker {

    /** 0.5s own-interval cap for the error-flash decay and password-dot
     * animation -- this loop otherwise runs at whatever rate vsync paces it. */
    private static final float ERROR_DECAY_SECONDS = 1.2f;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private Path resolveShader(String name) {
        Path direct = Path.of("shaders", name);
        if (Files.exists(direct)) return direct;
        Path root = DotfilePaths.findDotfilesRoot();
        Path inRes = root.resolve("src/main/resources/shaders").resolve(name);
        if (Files.exists(inRes)) return inRes;
        Path homeRes = DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources/shaders").resolve(name);
        if (Files.exists(homeRes)) return homeRes;
        return direct;
    }

    /**
     * @param testTimeoutSeconds DEV/TEST-ONLY escape hatch: if positive, the
     *                           lock auto-releases after this many seconds
     *                           with no password, independent of PAM. Loudly
     *                           logged. Exists solely so a grab/event-loop
     *                           bug can never require a hard reboot to
     *                           recover from while this is being developed
     *                           -- never pass this for a real lock.
     * @param dumpFramePath DEV/TEST-ONLY escape hatch: if non-null, grabs
     *                      input and renders as normal, but once the scene
     *                      has had a moment to settle (0.3s in) saves one
     *                      frame as a PNG at this path and exits -- lets the
     *                      3D lock UI be inspected (by a human or by Claude's
     *                      own Read tool) without anyone having to watch the
     *                      real X11 output live. Combine with
     *                      testTimeoutSeconds as a safety net if desired;
     *                      never pass this for a real lock.
     * @param injectPassword DEV/TEST-ONLY escape hatch: if non-null,
     *                       synthesizes keypresses for every character (via
     *                       X11Canvas.injectTestKeyPress, no real keyboard or
     *                       xdotool/XTest tool required) followed by Return,
     *                       right after the grab succeeds -- drives the real
     *                       typing -> PAM-check -> error/unlock path exactly
     *                       as a physical test would, so it can be exercised
     *                       by an automated test or CI. Only Latin-1
     *                       printable characters are supported. This string
     *                       is visible via argv/`ps`/shell history on most
     *                       systems -- only ever pass an intentionally wrong,
     *                       throwaway test string here, never a real
     *                       password, and never use this for a real lock.
     */
    public void start(float testTimeoutSeconds, String dumpFramePath, String injectPassword) throws Throwable {
        Path vertPath = resolveShader("wallpaper.vert");
        Path fragPath = resolveShader("wallpaper.frag");
        Path blitFrag = resolveShader("blit.frag");
        String user = System.getProperty("user.name");

        if (testTimeoutSeconds > 0) {
            System.err.println("[locker] TEST MODE: auto-unlocking after " + testTimeoutSeconds
                    + "s with NO password check. Do not use this flag for a real lock.");
        }
        if (dumpFramePath != null) {
            System.err.println("[locker] TEST MODE: will dump one frame to " + dumpFramePath
                    + " and exit. Do not use this flag for a real lock.");
        }
        if (injectPassword != null) {
            System.err.println("[locker] TEST MODE: will synthesize " + injectPassword.length()
                    + " keypress(es) + Return -- never pass a real password here, it is visible via argv/ps.");
        }

        try (X11Canvas canvas = new X11Canvas();
             PamAuthService pam = new PamAuthService()) {

            X11Canvas.Surface surface = canvas.createLockWindow();
            boolean grabbed = canvas.grabInputWithRetry(surface);
            if (!grabbed) {
                canvas.destroyLockWindow(surface);
                throw new IllegalStateException(
                        "Could not grab keyboard/pointer -- refusing to show a lock screen that input could bypass.");
            }

            if (injectPassword != null) {
                // XPutBackEvent pushes to the FRONT of Xlib's local event
                // queue, not the back -- so events must be injected in
                // reverse order (Return first, then the password's
                // characters back-to-front) for pollKeyEvent() to drain
                // them in the order a human would have typed them.
                canvas.injectTestKeyPress(X11Canvas.XK_RETURN);
                for (int i = injectPassword.length() - 1; i >= 0; i--) {
                    canvas.injectTestKeyPress(injectPassword.charAt(i));
                }
            }

            // Everything from here on must guarantee the grab is released
            // and the window destroyed on ANY exit path -- including a
            // setup failure (shader compile error, texture/font loading
            // trouble) that happens before the render loop's own try/finally
            // below even starts. Without this outer finally, such a failure
            // would leave the keyboard/pointer grab held until the JVM
            // process actually exits (the X server does auto-release a
            // client's grabs when its connection closes, so this was never
            // an unrecoverable hang -- but relying on that implicitly rather
            // than cleaning up explicitly is fragile).
            try {
            ShaderPipeline pipeline = new ShaderPipeline(canvas, vertPath, fragPath);
            BlitPipeline blitPipeline = new BlitPipeline(canvas, vertPath, blitFrag);
            X11Canvas.RenderTarget target = canvas.createRenderTarget(
                    Math.max(1, canvas.getWidth() / 2), Math.max(1, canvas.getHeight() / 2));

            // The real Win98 "Enter Password" dialog chrome, rasterized once
            // (not per-frame -- it's static) and uploaded as a texture unit
            // 1 the raymarch shader samples on the 3D monitor's front face.
            LoginPromptTexture.Layout loginLayout = LoginPromptTexture.render(user);
            int loginTexture = canvas.createTextureRGBA(
                    LoginPromptTexture.WIDTH, LoginPromptTexture.HEIGHT, loginLayout.rgba());
            // Bound once to unit 1 and left there -- nothing else in this
            // pipeline's render loop touches that unit, so it stays bound
            // across every subsequent frame without needing a rebind.
            canvas.bindTextureUnit(1, loginTexture);
            pipeline.bind();
            pipeline.setLoginPromptTexture(1, loginLayout.fieldU0(), loginLayout.fieldV0(),
                    loginLayout.fieldU1(), loginLayout.fieldV1());

            // The lock window spans every monitor at once (see
            // X11Canvas.createLockWindow), but with two or more screens the
            // scene should still read clearly on one of them rather than
            // straddling the bezel between two -- so recenter it on
            // whichever monitor xrandr reports as primary (the same
            // detection Wallpaper.java already uses). On a single-monitor
            // setup this primary rect is just the whole display, so the
            // uniforms below are a no-op (see wallpaper.frag).
            List<Wallpaper.Monitor> monitors = new Wallpaper().detectMonitors(canvas.getWidth(), canvas.getHeight());
            Wallpaper.Monitor primaryMonitor = monitors.get(0);
            for (Wallpaper.Monitor mon : monitors) {
                if (mon.primary()) {
                    primaryMonitor = mon;
                    break;
                }
            }
            final float primaryOffsetX = primaryMonitor.x();
            final float primaryOffsetY = canvas.getHeight() - (primaryMonitor.y() + primaryMonitor.height());
            final float primaryResW = primaryMonitor.width();
            final float primaryResH = primaryMonitor.height();

            AtomicBoolean shutdownRequested = new AtomicBoolean(false);
            AtomicBoolean unlocked = new AtomicBoolean(false);
            Thread renderThread = Thread.currentThread();

            // Mirrors Wallpaper.start()'s shutdown-hook pattern: SIGINT/SIGTERM
            // (e.g. `kill <pid>` from another terminal while testing) always
            // works regardless of X11 grab state -- a keyboard/pointer grab
            // only affects which window X routes input events to, never
            // process signal delivery, so this is a safe way to recover from
            // a misbehaving lock screen without touching the grab/event code.
            Signal.handle(new Signal("TERM"), _ -> { shutdownRequested.set(true); });
            Signal.handle(new Signal("INT"), _ -> { shutdownRequested.set(true); });

            char[] passwordBuffer = new char[0];
            float authError = 0.0f;
            boolean authenticating = false;
            boolean frameDumped = false;
            Future<PamAuthService.Status> pendingAuth = null;

            long startNanos = System.nanoTime();
            long lastFrameNanos = startNanos;
            float viewportOffsetX = 0.0f;
            float viewportOffsetY = 0.0f;
            final float renderScale = 0.5f;

            try {
                while (!shutdownRequested.get() && !unlocked.get()) {
                    long now = System.nanoTime();
                    float elapsed = (now - startNanos) / 1_000_000_000.0f;
                    float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
                    lastFrameNanos = now;

                    if (testTimeoutSeconds > 0 && elapsed >= testTimeoutSeconds) {
                        System.err.println("[locker] TEST MODE timeout reached -- auto-unlocking without a password check.");
                        unlocked.set(true);
                        break;
                    }

                    // Drain every queued X11 key event this frame -- fast typing
                    // can queue several KeyPress/KeyRelease pairs between frames.
                    X11Canvas.KeyEvent ev;
                    while ((ev = canvas.pollKeyEvent()) != null) {
                        if (!ev.press()) continue; // only act on key-down

                        if (ev.keysym() == X11Canvas.XK_ESCAPE) {
                            passwordBuffer = clearBuffer(passwordBuffer);
                        } else if (ev.keysym() == X11Canvas.XK_BACKSPACE) {
                            if (passwordBuffer.length > 0) {
                                char[] shorter = new char[passwordBuffer.length - 1];
                                System.arraycopy(passwordBuffer, 0, shorter, 0, shorter.length);
                                passwordBuffer[passwordBuffer.length - 1] = '\0';
                                passwordBuffer = shorter;
                            }
                        } else if (ev.keysym() == X11Canvas.XK_RETURN || ev.keysym() == X11Canvas.XK_KP_ENTER) {
                            if (!authenticating) {
                                System.err.println("[locker] Enter pressed (" + passwordBuffer.length
                                        + " chars buffered) -- checking against PAM for user '" + user + "'...");
                                authenticating = true;
                                char[] toCheck = passwordBuffer;
                                passwordBuffer = new char[0]; // PamAuthService takes ownership and zeroes toCheck
                                pendingAuth = pam.authenticateAsync(user, toCheck);
                            }
                        } else if (!ev.text().isEmpty()) {
                            char c = ev.text().charAt(0);
                            if (c >= 0x20 && c != 0x7F && passwordBuffer.length < MAX_PASSWORD_LENGTH) {
                                char[] longer = new char[passwordBuffer.length + 1];
                                System.arraycopy(passwordBuffer, 0, longer, 0, passwordBuffer.length);
                                longer[passwordBuffer.length] = c;
                                passwordBuffer = longer;
                            }
                        }
                    }

                    if (authenticating && pendingAuth != null && pendingAuth.isDone()) {
                        PamAuthService.Status status;
                        try {
                            status = pendingAuth.get();
                        } catch (Exception ex) {
                            status = PamAuthService.Status.ERROR;
                        }
                        authenticating = false;
                        pendingAuth = null;
                        if (status == PamAuthService.Status.SUCCESS) {
                            System.err.println("[locker] PAM authentication succeeded -- unlocking.");
                            unlocked.set(true);
                        } else {
                            System.err.println("[locker] PAM authentication failed (" + status + ") -- staying locked.");
                            authError = 1.0f;
                        }
                    }

                    authError = Math.max(0.0f, authError - dt / ERROR_DECAY_SECONDS);

                    X11Canvas.MouseInfo mouse = canvas.queryMouse();
                    float mouseNormX = canvas.getWidth() > 0 ? mouse.x() / canvas.getWidth() : 0.5f;
                    float mouseNormY = canvas.getHeight() > 0 ? mouse.y() / canvas.getHeight() : 0.5f;

                    canvas.makeCurrent(surface);

                    canvas.bindFramebuffer(target.framebuffer());
                    canvas.setViewport(0, 0, target.width(), target.height());
                    pipeline.bind();
                    var state = new dev.petrolal.dotfiles.wallpaper.WallpaperEvents.WallpaperState(
                            canvas.getWidth(), canvas.getHeight(), elapsed,
                            mouse.x(), mouse.y(), mouse.leftDown(), mouse.rightDown(),
                            0, 0.0f, 0.0f, 0.0f);
                    pipeline.updateUniforms(state, viewportOffsetX, viewportOffsetY, renderScale);
                    pipeline.setResolution(target.width(), target.height());
                    // gl_FragCoord during this pass ranges over the low-res
                    // RenderTarget (target.width/height), not the full
                    // canvas -- the primary-monitor rect must be scaled down
                    // to match, the same way u_resolution above is already
                    // target.width/height rather than the full canvas size.
                    pipeline.updateLockUniforms(passwordBuffer.length, authError,
                            authenticating ? 1.0f : 0.0f, mouseNormX, mouseNormY,
                            primaryOffsetX * renderScale, primaryOffsetY * renderScale,
                            primaryResW * renderScale, primaryResH * renderScale);
                    canvas.drawQuad();

                    canvas.bindFramebuffer(0);
                    canvas.setViewport(0, 0, canvas.getWidth(), canvas.getHeight());
                    blitPipeline.bind(target.texture());
                    canvas.drawQuad();

                    boolean readyToDump = injectPassword != null ? authError > 0.0f : elapsed >= 0.3f;
                    if (dumpFramePath != null && !frameDumped && readyToDump) {
                        frameDumped = true;
                        dumpFrame(canvas, dumpFramePath);
                        System.err.println("[locker] Dumped one frame to " + dumpFramePath
                                + (injectPassword != null
                                        ? " -- letting the injected PAM check finish before exiting."
                                        : " -- unlocking."));
                        if (injectPassword == null) {
                            unlocked.set(true);
                        }
                    }

                    canvas.swapBuffers(surface);
                }
            } finally {
                java.util.Arrays.fill(passwordBuffer, '\0');
            }
            } finally {
                canvas.destroyLockWindow(surface);
            }
        }
    }

    private static char[] clearBuffer(char[] buffer) {
        java.util.Arrays.fill(buffer, '\0');
        return new char[0];
    }

    /** Reads the just-drawn default framebuffer back and saves it as a PNG
     * -- see {@code --dump-frame} on the {@code lock} CLI command. Only
     * ImageIO/BufferedImage are used here, never java.awt.Font/drawString
     * (see LoginPromptTexture's header comment for why that combination
     * crashes on this JDK build). */
    private static void dumpFrame(X11Canvas canvas, String path) throws Throwable {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        byte[] rgba = canvas.readPixelsRGBA(w, h);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            int o = i * 4;
            int r = rgba[o] & 0xFF, g = rgba[o + 1] & 0xFF, b = rgba[o + 2] & 0xFF;
            argb[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        img.setRGB(0, 0, w, h, argb, 0, w);
        java.io.File file = new java.io.File(path);
        javax.imageio.ImageIO.write(img, "png", file);
    }
}
