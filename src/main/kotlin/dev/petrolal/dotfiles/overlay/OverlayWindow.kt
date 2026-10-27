package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.astroland.AstroGlobals
import dev.petrolal.dotfiles.overlay.astroland.AstroScene
import org.lwjgl.glfw.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil

/**
 * Astroland: a Kotlin/LWJGL port of zacoons' astroland_dots Hyprland plugin
 * (https://codeberg.org/zacoons/astroland_dots) -- a top-down ship-and-moons
 * arcade scene (see [dev.petrolal.dotfiles.overlay.astroland.AstroScene] and
 * its sibling classes for the gameplay port). Replaces the earlier
 * wireframe-orrery app-launcher concept outright, not kept alongside it.
 * The original hooks Hyprland's own compositor render/input pipeline so
 * desktop windows themselves become physics bodies you fly into and shoot;
 * that half has no equivalent in a standalone overlay process and is left
 * out (see [AstroGlobals]'s class doc) -- everything else (ship flight,
 * moon spawning/combat, bullets, camera shake) is a faithful port.
 *
 * Run via `imp overlay` (or the `quick-overlay` Gradle task for plain-JVM
 * development, no native-image build); WASD flies the ship, mouse aims,
 * left click shoots, Escape closes.
 */
class OverlayWindow {
    private var windowHandle: Long = 0
    private lateinit var scene: AstroScene
    private lateinit var postProcessPipeline: PostProcessPipeline
    private var framebufferWidth = 0
    private var framebufferHeight = 0

    private var cursorX = 0.0
    private var cursorY = 0.0
    private var upDown = false
    private var downDown = false
    private var leftDown = false
    private var rightDown = false

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

        scene = AstroScene().apply { resize(framebufferWidth, framebufferHeight) }
        postProcessPipeline = PostProcessPipeline(framebufferWidth, framebufferHeight)

        val initialCursorX = DoubleArray(1)
        val initialCursorY = DoubleArray(1)
        GLFW.glfwGetCursorPos(windowHandle, initialCursorX, initialCursorY)
        cursorX = initialCursorX[0]
        cursorY = initialCursorY[0]

        GLFW.glfwSetKeyCallback(windowHandle) { window, key, _, action, _ ->
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                GLFW.glfwSetWindowShouldClose(window, true)
            }
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
                val isPressed = action == GLFW.GLFW_PRESS
                when (key) {
                    GLFW.GLFW_KEY_W -> upDown = isPressed
                    GLFW.GLFW_KEY_S -> downDown = isPressed
                    GLFW.GLFW_KEY_A -> leftDown = isPressed
                    GLFW.GLFW_KEY_D -> rightDown = isPressed
                }
                scene.setInputVec(upDown, downDown, leftDown, rightDown)
            }
        }

        GLFW.glfwSetCursorPosCallback(windowHandle) { _, xpos, ypos ->
            cursorX = xpos
            cursorY = ypos
        }

        GLFW.glfwSetMouseButtonCallback(windowHandle) { _, button, action, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                scene.setShooting(action == GLFW.GLFW_PRESS)
            }
        }

        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        // Registered only after GL.createCapabilities()/the renderers
        // exist: on Wayland, glfwShowWindow below fires this callback
        // synchronously (an immediate compositor configure event), so
        // anything it touches must already be ready.
        GLFW.glfwSetFramebufferSizeCallback(windowHandle) { _, w, h ->
            if (w > 0 && h > 0) {
                framebufferWidth = w
                framebufferHeight = h
                GL11.glViewport(0, 0, w, h)
                scene.resize(w, h)
                postProcessPipeline.resize(w, h)
            }
        }

        GLFW.glfwSwapInterval(1)
        GLFW.glfwShowWindow(windowHandle)
    }

    private fun loop() {
        GL11.glClearColor(0.01f, 0f, 0.02f, 1.0f)
        var lastTime = GLFW.glfwGetTime()

        while (!GLFW.glfwWindowShouldClose(windowHandle)) {
            val now = GLFW.glfwGetTime()
            val dt = (now - lastTime).toFloat()
            lastTime = now

            scene.setCursorScreenPos(cursorX.toFloat(), cursorY.toFloat())
            scene.tick(dt)

            postProcessPipeline.beginSceneRender()
            scene.draw()
            postProcessPipeline.endSceneRenderAndComposite(now, 0f, framebufferWidth, framebufferHeight)

            GLFW.glfwSwapBuffers(windowHandle)
            GLFW.glfwPollEvents()
        }
    }

    private fun destroy() {
        scene.destroy()
        postProcessPipeline.destroy()
        Callbacks.glfwFreeCallbacks(windowHandle)
        GLFW.glfwDestroyWindow(windowHandle)
        GLFW.glfwTerminate()

        val previous = GLFW.glfwSetErrorCallback(null)
        previous?.free()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            OverlayWindow().start()
        }
    }
}
