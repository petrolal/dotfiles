package dev.petrolal.dotfiles.overlay;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import dev.petrolal.dotfiles.telemetry.SystemVitals;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Astroland: a zero-asset procedural 3D launcher. Five app-planets
 * ({@link PlanetNode}, fixed positions, see its javadoc for why) orbit a
 * telemetry-reactive central star ({@link StarRenderer}) inside a
 * procedural starfield ({@link StarfieldRenderer}), rendered into an
 * offscreen FBO and composited with chromatic aberration / vignette /
 * scanlines ({@link PostProcessPipeline}). Free-orbit camera via mouse drag
 * (see {@link OverlayCamera#applyOrbitDelta}) and scroll-wheel zoom.
 * Replaces an earlier biopunk desk scene outright (see docs/biopunk_plan.md
 * for that lineage) -- not kept alongside it.
 *
 * <p>Run via {@code imp overlay} (or the {@code quick-overlay} Gradle task
 * for plain-JVM development, no native-image build); press Escape to
 * close. Escape only closes this window -- {@link ProcessLauncher} launches
 * are detached {@code ProcessBuilder} processes with no reference kept
 * here, so they are never touched by {@link #destroy()} and outlive the
 * overlay.
 */
public final class OverlayWindow {

    private static final String LABEL_FONT_FAMILY = "JetBrainsMono Nerd Font Mono";
    private static final float LABEL_PIXEL_HEIGHT = 18f;
    /** How often the background vitals poller re-samples /proc -- cheap,
     * but still disk/kernel I/O the render thread must never block on. */
    private static final long VITALS_POLL_INTERVAL_MILLIS = 500L;
    /** Radians of camera orbit per pixel of mouse drag. */
    private static final float ORBIT_SENSITIVITY = 0.006f;
    /** Orbit-distance units per scroll-wheel notch. */
    private static final float ZOOM_SENSITIVITY = 0.25f;
    /** Pixels of cumulative mouse movement before a press-then-move is
     * treated as a camera drag instead of a click -- without this, a
     * sub-pixel jitter during an intended click would silently swallow it. */
    private static final float DRAG_THRESHOLD_PIXELS = 4f;

    private long windowHandle;
    private OverlayCamera camera;
    private StarfieldRenderer starfieldRenderer;
    private CelestialSystem celestialSystem;
    private PostProcessPipeline postProcessPipeline;
    private FontAtlas fontAtlas;
    private TextRenderer textRenderer;
    private HudLabelRenderer hudLabelRenderer;
    private ProcessLauncher processLauncher;
    private final SystemVitals vitals = new SystemVitals();
    /** Sampled off the render thread -- see {@link #VITALS_POLL_INTERVAL_MILLIS}
     * and {@link #startVitalsPoller()} -- same reasoning as
     * {@code wallpaper.WallpaperEvents}'s own background telemetry thread:
     * the render loop must never block on /proc file I/O. */
    private volatile float cachedCpuLoad = 0f;
    private Thread vitalsPollerThread;
    private int framebufferWidth;
    private int framebufferHeight;

    // Drag-vs-click and cursor-position state, all confined to the GLFW
    // callback thread (GLFW callbacks and the render loop run on the same
    // thread in this app, so no synchronization is needed here).
    private double cursorX;
    private double cursorY;
    private boolean leftButtonDown = false;
    private boolean dragged = false;
    private float dragDistanceAccumPixels = 0f;

    public static void main(String[] args) {
        new OverlayWindow().start();
    }

    /** Blocks the calling thread until the window closes (Escape or the
     * compositor requests it) -- mirrors {@code Wallpaper.start}/
     * {@code ScreenLocker.start} so {@code Orchestrator} can call it the
     * same way for the {@code imp overlay} subcommand. Routes native
     * loading via {@link NativeLibraryPaths} first; must happen before any
     * other {@code org.lwjgl.*} class in this call chain is touched. */
    public void start() {
        NativeLibraryPaths.install();
        init();
        loop();
        destroy();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        // Must match the `class:^(imp-overlay)$` window rule in
        // config/hypr/hyprland.lua (see docs/migration_plan.md) for
        // Hyprland to apply it.
        glfwWindowHintString(GLFW_X11_CLASS_NAME, "imp-overlay");
        glfwWindowHintString(GLFW_X11_INSTANCE_NAME, "imp-overlay");

        windowHandle = glfwCreateWindow(1920, 1080, "imp overlay", NULL, NULL);
        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();

        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(windowHandle, width, height);
        framebufferWidth = width[0];
        framebufferHeight = height[0];
        camera = new OverlayCamera(framebufferWidth, framebufferHeight);
        starfieldRenderer = new StarfieldRenderer();
        processLauncher = new ProcessLauncher();
        celestialSystem = new CelestialSystem(processLauncher);
        postProcessPipeline = new PostProcessPipeline(framebufferWidth, framebufferHeight);
        fontAtlas = new FontAtlas(LABEL_FONT_FAMILY, LABEL_PIXEL_HEIGHT);
        textRenderer = new TextRenderer(fontAtlas);
        textRenderer.resize(framebufferWidth, framebufferHeight);
        hudLabelRenderer = new HudLabelRenderer();
        hudLabelRenderer.resize(framebufferWidth, framebufferHeight);

        double[] initialCursorX = new double[1];
        double[] initialCursorY = new double[1];
        glfwGetCursorPos(windowHandle, initialCursorX, initialCursorY);
        cursorX = initialCursorX[0];
        cursorY = initialCursorY[0];

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true);
            }
        });

        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            float dx = (float) (xpos - cursorX);
            float dy = (float) (ypos - cursorY);
            cursorX = xpos;
            cursorY = ypos;

            if (leftButtonDown) {
                camera.applyOrbitDelta(dx * ORBIT_SENSITIVITY, -dy * ORBIT_SENSITIVITY);
                dragDistanceAccumPixels += (float) Math.sqrt(dx * dx + dy * dy);
                if (dragDistanceAccumPixels > DRAG_THRESHOLD_PIXELS) {
                    dragged = true;
                }
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (button != GLFW_MOUSE_BUTTON_LEFT) {
                return;
            }
            if (action == GLFW_PRESS) {
                leftButtonDown = true;
                dragged = false;
                dragDistanceAccumPixels = 0f;
            } else if (action == GLFW_RELEASE) {
                leftButtonDown = false;
                if (!dragged) {
                    celestialSystem.clickHovered(glfwGetTime());
                }
            }
        });

        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) ->
                camera.zoomBy((float) -yoffset * ZOOM_SENSITIVITY));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // text alpha is the glyph coverage
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Registered only after GL.createCapabilities()/the renderers
        // exist: on Wayland, glfwShowWindow below fires this callback
        // synchronously (an immediate compositor configure event), so
        // anything it touches must already be ready.
        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
            if (w > 0 && h > 0) {
                framebufferWidth = w;
                framebufferHeight = h;
                glViewport(0, 0, w, h);
                camera.resize(w, h);
                textRenderer.resize(w, h);
                hudLabelRenderer.resize(w, h);
                postProcessPipeline.resize(w, h);
            }
        });

        glfwSwapInterval(1);
        glfwShowWindow(windowHandle);

        startVitalsPoller();
    }

    /** Virtual thread sampling CPU every {@link #VITALS_POLL_INTERVAL_MILLIS}
     * so {@link StarRenderer} always has a recent-enough reading without
     * the render thread ever touching /proc directly. Interrupted (not
     * joined) from {@link #destroy()} -- a slightly stale last reading on
     * the way out is harmless since nothing reads the cached field after
     * that point. */
    private void startVitalsPoller() {
        vitalsPollerThread = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                cachedCpuLoad = vitals.readCpuUsage();
                try {
                    Thread.sleep(VITALS_POLL_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private void loop() {
        glClearColor(0.01f, 0f, 0.02f, 1.0f);

        while (!glfwWindowShouldClose(windowHandle)) {
            double elapsedSeconds = glfwGetTime();

            OverlayCamera.Ray hoverRay = camera.unproject((float) cursorX, (float) cursorY,
                    framebufferWidth, framebufferHeight);
            celestialSystem.updateHover(hoverRay);

            postProcessPipeline.beginSceneRender();

            // Background pass: depth writes off so the starfield never
            // occludes (or is occluded by) the actual 3D scene drawn right
            // after it into the same, already-cleared depth buffer.
            glDepthMask(false);
            starfieldRenderer.draw(elapsedSeconds, framebufferWidth, framebufferHeight);
            glDepthMask(true);

            celestialSystem.draw(camera, elapsedSeconds, cachedCpuLoad);

            postProcessPipeline.endSceneRenderAndComposite(elapsedSeconds, cachedCpuLoad, framebufferWidth, framebufferHeight);

            // HUD labels drawn directly to the default framebuffer, after
            // compositing -- stays crisp (no chromatic-aberration
            // fringing) and doesn't fight the 3D scene's own depth buffer
            // (which belongs to the offscreen FBO, not this one). Per
            // CelestialSystem.HudEntry's contract: skip any entry whose
            // planet projected behind the camera plane rather than drawing
            // it at a garbage position.
            glDisable(GL_DEPTH_TEST);
            for (CelestialSystem.HudEntry hud : celestialSystem.hudEntries(camera, framebufferWidth, framebufferHeight)) {
                if (!hud.visible()) {
                    continue;
                }
                var glow = hud.glowColor();
                float lineAlpha = hud.hovered() ? 0.9f : 0.5f;
                hudLabelRenderer.drawConnectingLine(hud.planetScreenX(), hud.planetScreenY(),
                        hud.anchorScreenX(), hud.anchorScreenY(), glow.x, glow.y, glow.z, lineAlpha);
                hudLabelRenderer.drawPillBorder(hud.anchorScreenX(), hud.anchorScreenY(), hud.label(),
                        glow.x, glow.y, glow.z, hud.hovered());

                float pillWidth = HudLabelRenderer.pillWidth(hud.label(), hud.hovered());
                float textX = hud.anchorScreenX() - pillWidth / 2f + 12f;
                textRenderer.draw(hud.label(), textX, hud.anchorScreenY() + 5f, 1f, 1f, 1f, 1f);
                if (hud.hovered()) {
                    textRenderer.draw(hud.exec(), textX, hud.anchorScreenY() + 24f,
                            glow.x, glow.y, glow.z, 0.9f);
                }
            }
            glEnable(GL_DEPTH_TEST);

            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
    }

    private void destroy() {
        if (vitalsPollerThread != null) {
            vitalsPollerThread.interrupt();
        }
        hudLabelRenderer.destroy();
        textRenderer.destroy();
        fontAtlas.destroy();
        postProcessPipeline.destroy();
        celestialSystem.destroy();
        starfieldRenderer.destroy();
        Callbacks.glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback previous = glfwSetErrorCallback(null);
        if (previous != null) {
            previous.free();
        }
    }
}
