package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.telemetry.SystemVitals
import org.lwjgl.glfw.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import kotlin.concurrent.Volatile

/**
 * Astroland: a zero-asset procedural 3D launcher. Five app-planets
 * ([PlanetNode], fixed positions, see its javadoc for why) orbit a
 * telemetry-reactive central star ([StarRenderer]) inside a
 * procedural starfield ([StarfieldRenderer]), rendered into an
 * offscreen FBO and composited with chromatic aberration / vignette /
 * scanlines ([PostProcessPipeline]). Free-orbit camera via mouse drag
 * (see [OverlayCamera.applyOrbitDelta]) and scroll-wheel zoom.
 * Replaces an earlier biopunk desk scene outright (see docs/biopunk_plan.md
 * for that lineage) -- not kept alongside it.
 * 
 * 
 * Run via `imp overlay` (or the `quick-overlay` Gradle task
 * for plain-JVM development, no native-image build); press Escape to
 * close. Escape only closes this window -- [ProcessLauncher] launches
 * are detached `ProcessBuilder` processes with no reference kept
 * here, so they are never touched by [.destroy] and outlive the
 * overlay.
 */
class OverlayWindow {
    private var windowHandle: Long = 0
    private lateinit var camera: OverlayCamera
    private lateinit var starfieldRenderer: StarfieldRenderer
    private lateinit var celestialSystem: CelestialSystem
    private lateinit var postProcessPipeline: PostProcessPipeline
    private lateinit var fontAtlas: FontAtlas
    private lateinit var textRenderer: TextRenderer
    private lateinit var hudLabelRenderer: HudLabelRenderer
    private lateinit var processLauncher: ProcessLauncher
    private val vitals = SystemVitals()

    /** Sampled off the render thread -- see [.VITALS_POLL_INTERVAL_MILLIS]
     * and [.startVitalsPoller] -- the render loop must never block on
     * /proc file I/O.  */
    @Volatile
    private var cachedCpuLoad = 0f
    private var vitalsPollerThread: Thread? = null
    private var framebufferWidth = 0
    private var framebufferHeight = 0

    // Drag-vs-click and cursor-position state, all confined to the GLFW
    // callback thread (GLFW callbacks and the render loop run on the same
    // thread in this app, so no synchronization is needed here).
    private var cursorX = 0.0
    private var cursorY = 0.0
    private var leftButtonDown = false
    private var dragged = false
    private var dragDistanceAccumPixels = 0f

    /** Blocks the calling thread until the window closes (Escape or the
     * compositor requests it) -- mirrors `Wallpaper.start`/
     * `ScreenLocker.start` so `Orchestrator` can call it the
     * same way for the `imp overlay` subcommand. Routes native
     * loading via [NativeLibraryPaths] first; must happen before any
     * other `org.lwjgl.*` class in this call chain is touched.  */
    fun start() {
        NativeLibraryPaths.install()
        init()
        loop()
        destroy()
    }

    private fun init() {
        GLFWErrorCallback.createPrint(System.err).set()
        check(GLFW.glfwInit()) { "Unable to initialize GLFW" }

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)

        // Must match the `class:^(imp-overlay)$` window rule in
        // config/hypr/hyprland.lua (see docs/migration_plan.md) for
        // Hyprland to apply it.
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_CLASS_NAME, "imp-overlay")
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_INSTANCE_NAME, "imp-overlay")

        windowHandle = GLFW.glfwCreateWindow(1920, 1080, "imp overlay", MemoryUtil.NULL, MemoryUtil.NULL)
        check(windowHandle != MemoryUtil.NULL) { "Failed to create GLFW window" }

        GLFW.glfwMakeContextCurrent(windowHandle)
        GL.createCapabilities()

        val width = IntArray(1)
        val height = IntArray(1)
        GLFW.glfwGetFramebufferSize(windowHandle, width, height)
        framebufferWidth = width[0]
        framebufferHeight = height[0]
        camera = OverlayCamera(framebufferWidth, framebufferHeight)
        starfieldRenderer = StarfieldRenderer()
        val launcher = ProcessLauncher()
        processLauncher = launcher
        celestialSystem = CelestialSystem(launcher)
        postProcessPipeline = PostProcessPipeline(framebufferWidth, framebufferHeight)
        val atlas = FontAtlas(LABEL_FONT_FAMILY, LABEL_PIXEL_HEIGHT)
        fontAtlas = atlas
        textRenderer = TextRenderer(atlas).apply {
            resize(framebufferWidth, framebufferHeight)
        }
        hudLabelRenderer = HudLabelRenderer().apply {
            resize(framebufferWidth, framebufferHeight)
        }

        val initialCursorX = DoubleArray(1)
        val initialCursorY = DoubleArray(1)
        GLFW.glfwGetCursorPos(windowHandle, initialCursorX, initialCursorY)
        cursorX = initialCursorX[0]
        cursorY = initialCursorY[0]

        GLFW.glfwSetKeyCallback(windowHandle) { window, key, _, action, _ ->
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                GLFW.glfwSetWindowShouldClose(window, true)
            }
        }

        GLFW.glfwSetCursorPosCallback(windowHandle) { _, xpos, ypos ->
            val dx = (xpos - cursorX).toFloat()
            val dy = (ypos - cursorY).toFloat()
            cursorX = xpos
            cursorY = ypos
            if (leftButtonDown) {
                camera.applyOrbitDelta(dx * ORBIT_SENSITIVITY, -dy * ORBIT_SENSITIVITY)
                dragDistanceAccumPixels += kotlin.math.hypot(dx, dy)
                if (dragDistanceAccumPixels > DRAG_THRESHOLD_PIXELS) {
                    dragged = true
                }
            }
        }

        GLFW.glfwSetMouseButtonCallback(windowHandle) { _, button, action, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    leftButtonDown = true
                    dragged = false
                    dragDistanceAccumPixels = 0f
                } else if (action == GLFW.GLFW_RELEASE) {
                    leftButtonDown = false
                    if (!dragged) {
                        celestialSystem.clickHovered(GLFW.glfwGetTime())
                    }
                }
            }
        }

        GLFW.glfwSetScrollCallback(windowHandle) { _, _, yoffset ->
            camera.zoomBy(-yoffset.toFloat() * ZOOM_SENSITIVITY)
        }

        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA) // text alpha is the glyph coverage
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        GL11.glDepthFunc(GL11.GL_LEQUAL)
        GL11.glEnable(GL11.GL_CULL_FACE)
        GL11.glCullFace(GL11.GL_BACK)

        // Registered only after GL.createCapabilities()/the renderers
        // exist: on Wayland, glfwShowWindow below fires this callback
        // synchronously (an immediate compositor configure event), so
        // anything it touches must already be ready.
        GLFW.glfwSetFramebufferSizeCallback(windowHandle) { _, w, h ->
            if (w > 0 && h > 0) {
                framebufferWidth = w
                framebufferHeight = h
                GL11.glViewport(0, 0, w, h)
                camera.resize(w, h)
                textRenderer.resize(w, h)
                hudLabelRenderer.resize(w, h)
                postProcessPipeline.resize(w, h)
            }
        }

        GLFW.glfwSwapInterval(1)
        GLFW.glfwShowWindow(windowHandle)

        startVitalsPoller()
    }

    /** Virtual thread sampling CPU every [.VITALS_POLL_INTERVAL_MILLIS]
     * so [StarRenderer] always has a recent-enough reading without
     * the render thread ever touching /proc directly. Interrupted (not
     * joined) from [.destroy] -- a slightly stale last reading on
     * the way out is harmless since nothing reads the cached field after
     * that point.  */
    private fun startVitalsPoller() {
        vitalsPollerThread = Thread.ofVirtual().start {
            while (!Thread.currentThread().isInterrupted) {
                cachedCpuLoad = vitals.readCpuUsage()
                try {
                    Thread.sleep(VITALS_POLL_INTERVAL_MILLIS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun loop() {
        GL11.glClearColor(0.01f, 0f, 0.02f, 1.0f)

        while (!GLFW.glfwWindowShouldClose(windowHandle)) {
            val elapsedSeconds = GLFW.glfwGetTime()

            val hoverRay = camera.unproject(
                cursorX.toFloat(), cursorY.toFloat(),
                framebufferWidth, framebufferHeight
            )
            celestialSystem.updateHover(hoverRay)

            postProcessPipeline.beginSceneRender()

            // Background pass: depth writes off so the starfield never
            // occludes (or is occluded by) the actual 3D scene drawn right
            // after it into the same, already-cleared depth buffer.
            GL11.glDepthMask(false)
            starfieldRenderer.draw(elapsedSeconds, framebufferWidth, framebufferHeight)
            GL11.glDepthMask(true)

            celestialSystem.draw(camera, elapsedSeconds, cachedCpuLoad)

            postProcessPipeline.endSceneRenderAndComposite(
                elapsedSeconds,
                cachedCpuLoad,
                framebufferWidth,
                framebufferHeight
            )

            // HUD labels drawn directly to the default framebuffer, after
            // compositing -- stays crisp (no chromatic-aberration
            // fringing) and doesn't fight the 3D scene's own depth buffer
            // (which belongs to the offscreen FBO, not this one). Per
            // CelestialSystem.HudEntry's contract: skip any entry whose
            // planet projected behind the camera plane rather than drawing
            // it at a garbage position.
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            for (hud in celestialSystem.hudEntries(camera, framebufferWidth, framebufferHeight)) {
                if (!hud.visible) {
                    continue
                }
                val glow = hud.glowColor
                val lineAlpha = if (hud.hovered) 0.9f else 0.5f
                hudLabelRenderer.drawConnectingLine(
                    hud.planetScreenX, hud.planetScreenY,
                    hud.anchorScreenX, hud.anchorScreenY, glow.x, glow.y, glow.z, lineAlpha
                )
                hudLabelRenderer.drawPillBorder(
                    hud.anchorScreenX, hud.anchorScreenY, hud.label,
                    glow.x, glow.y, glow.z, hud.hovered
                )

                val pillWidth = HudLabelRenderer.pillWidth(hud.label, hud.hovered)
                val textX = hud.anchorScreenX - pillWidth / 2f + 12f
                textRenderer.draw(hud.label, textX, hud.anchorScreenY + 5f, 1f, 1f, 1f, 1f)
                if (hud.hovered) {
                    textRenderer.draw(
                        hud.exec, textX, hud.anchorScreenY + 24f,
                        glow.x, glow.y, glow.z, 0.9f
                    )
                }
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST)

            GLFW.glfwSwapBuffers(windowHandle)
            GLFW.glfwPollEvents()
        }
    }

    private fun destroy() {
        vitalsPollerThread?.interrupt()
        hudLabelRenderer.destroy()
        textRenderer.destroy()
        fontAtlas.destroy()
        postProcessPipeline.destroy()
        celestialSystem.destroy()
        starfieldRenderer.destroy()
        Callbacks.glfwFreeCallbacks(windowHandle)
        GLFW.glfwDestroyWindow(windowHandle)
        GLFW.glfwTerminate()

        val previous = GLFW.glfwSetErrorCallback(null)
        previous?.free()
    }

    companion object {
        private const val LABEL_FONT_FAMILY = "JetBrainsMono Nerd Font Mono"
        private const val LABEL_PIXEL_HEIGHT = 18f

        /** How often the background vitals poller re-samples /proc -- cheap,
         * but still disk/kernel I/O the render thread must never block on.  */
        private const val VITALS_POLL_INTERVAL_MILLIS = 500L

        /** Radians of camera orbit per pixel of mouse drag.  */
        private const val ORBIT_SENSITIVITY = 0.006f

        /** Orbit-distance units per scroll-wheel notch.  */
        private const val ZOOM_SENSITIVITY = 0.25f

        /** Pixels of cumulative mouse movement before a press-then-move is
         * treated as a camera drag instead of a click -- without this, a
         * sub-pixel jitter during an intended click would silently swallow it.  */
        private const val DRAG_THRESHOLD_PIXELS = 4f

        @JvmStatic
        fun main(args: Array<String>) {
            OverlayWindow().start()
        }
    }
}
