package dev.petrolal.dotfiles.wallpaper

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.system.ProcessRunner
import dev.petrolal.dotfiles.wallpaper.X11Canvas.RenderTarget
import sun.misc.Signal
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Universal wallpaper controller:
 * - Dynamically adapts to ANY multi-monitor count (1, 2, 3, or more displays)
 * - Computes OpenGL bottom-left viewports correctly for arbitrary layouts (horizontal, vertical, offset)
 * - Frame pacing is driven entirely by vsync (swapBuffers blocks on vblank, see
 * X11Canvas.setSwapInterval), so it naturally tracks whichever display's
 * refresh rate the driver syncs the drawable to
 * - Live hot-reloading & safe uniform dispatches per monitor
 */
class Wallpaper {
    @JvmRecord
    data class Monitor(val id: Int, val x: Int, val y: Int, val width: Int, val height: Int, val primary: Boolean)

    private fun resolveShader(name: String): Path = resolveResource("shaders", name)

    private fun resolveAsset(name: String): Path = resolveResource("assets", name)

    private fun resolveResource(dir: String, name: String): Path {
        val direct = Path.of(name)
        if (Files.exists(direct)) return direct
        val inDir = Path.of(dir, name)
        if (Files.exists(inDir)) return inDir
        val root = DotfilePaths.findDotfilesRoot()
        val inRes = root.resolve("src/main/resources").resolve(dir).resolve(name)
        if (Files.exists(inRes)) return inRes
        val inRoot = root.resolve(name)
        if (Files.exists(inRoot)) return inRoot
        val homeRes = DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources").resolve(dir).resolve(name)
        if (Files.exists(homeRes)) return homeRes
        return direct
    }

    /** Forces every xfce4-desktop image-style/color-style property to "none"
     * (0) / "solid" (3) so the OpenGL wallpaper shows through instead of
     * being painted over by xfdesktop's own background. Driven entirely from
     * Java so each xfconf-query call's exit code is individually visible,
     * rather than as one opaque shell pipeline.  */
    private fun configureXfdesktopTransparency() {
        if (!DotfilePaths.commandExists("xfconf-query")) return

        val listResult = ProcessRunner.captureOutput(
            listOf("xfconf-query", "-c", "xfce4-desktop", "-l")
        )
        listResult.ifOk { output ->
            for (property in output.lines()) {
                if (property.contains("image-style")) {
                    ProcessRunner.run(
                        listOf("xfconf-query", "-c", "xfce4-desktop", "-p", property, "-s", "0")
                    )
                } else if (property.contains("color-style")) {
                    ProcessRunner.run(
                        listOf("xfconf-query", "-c", "xfce4-desktop", "-p", property, "-s", "3")
                    )
                }
            }
        }

        if (DotfilePaths.commandExists("xfdesktop")) {
            ProcessRunner.run(listOf("xfdesktop", "--reload"))
        }
    }

    /**
     * Dynamically detects all connected monitors' real geometry via xrandr.
     * Works on any screen configuration (single, dual, triple, vertical, mixed
     * resolution) because every monitor's position and size always comes from
     * a live query -- nothing about monitor count, size, or layout is assumed
     * or guessed anywhere in this method.
     */
    fun detectMonitors(totalWidth: Int, totalHeight: Int): List<Monitor> {
        var list = parseListMonitors()
        if (list.isEmpty()) {
            list = parseXrandrQuery()
        }

        if (list.isNotEmpty()) {
            return list.sortedWith(compareBy({ it.x }, { it.y }))
        }

        // xrandr unavailable or gave nothing usable (e.g. a bare Xvfb test
        // display): treat the whole X screen as one monitor. Never guess a
        // monitor count or split -- an incorrect guess is worse than one
        // monitor-sized window covering everything.
        return listOf(Monitor(0, 0, 0, totalWidth, totalHeight, true))
    }

    /** Primary detection path: `xrandr --listmonitors`. Output lines look
     * like "0: +*eDP-1 1920/344x1080/193+1920+0  eDP-1" -- the '*' (present
     * only on the xrandr-designated primary output) sits right after the
     * leading '+', so it's captured explicitly rather than swallowed by a
     * generic \S+ match.  */
    private fun parseListMonitors(): MutableList<Monitor> {
        val list = mutableListOf<Monitor>()
        try {
            val p = ProcessBuilder("xrandr", "--listmonitors").start()
            BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                var line: String?
                val pat = Pattern.compile("(\\d+):\\s+\\+(\\*?)\\S*\\s+(\\d+)/\\d+x(\\d+)/\\d+\\+(\\d+)\\+(\\d+)")
                var autoId = 0
                while (reader.readLine().also { line = it } != null) {
                    val m = pat.matcher(line ?: "")
                    if (m.find()) {
                        val primary = m.group(2).isNotEmpty()
                        val w = m.group(3).toInt()
                        val h = m.group(4).toInt()
                        val x = m.group(5).toInt()
                        val y = m.group(6).toInt()
                        list.add(Monitor(autoId++, x, y, w, h, primary))
                    }
                }
            }
            p.waitFor()
        } catch (_: Exception) {
        }
        return list
    }

    /**
     * Secondary detection path: `xrandr --query`, parsing each connected
     * output's own geometry line (e.g. "eDP-1 connected 1920x1080+0+0 ...").
     * Covers setups where --listmonitors isn't supported, with real detected
     * geometry rather than an assumed layout.
     */
    private fun parseXrandrQuery(): MutableList<Monitor> {
        val list = mutableListOf<Monitor>()
        try {
            val p = ProcessBuilder("xrandr", "--query").start()
            BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                var line: String?
                val pat = Pattern.compile("^\\S+\\s+connected\\s+(primary\\s+)?(\\d+)x(\\d+)\\+(\\d+)\\+(\\d+)")
                var autoId = 0
                while (reader.readLine().also { line = it } != null) {
                    val m = pat.matcher(line ?: "")
                    if (m.find()) {
                        val primary = m.group(1) != null
                        val w = m.group(2).toInt()
                        val h = m.group(3).toInt()
                        val x = m.group(4).toInt()
                        val y = m.group(5).toInt()
                        list.add(Monitor(autoId++, x, y, w, h, primary))
                    }
                }
            }
            p.waitFor()
        } catch (_: Exception) {
        }
        return list
    }

    /** @param maxFps Caps the render loop to this many frames/sec, independent
     * of vsync -- useful on weak hardware to trade motion
     * smoothness for battery/thermal headroom even below what
     * RENDER_SCALE alone saves. 0 (or less) disables the cap,
     * leaving the primary surface's vsync as the only pacing.
     */
    @Throws(Throwable::class)
    fun start(maxFps: Int) {
        val vertPath = resolveShader("wallpaper.vert")
        val panoramaFrag = resolveShader("wallpaper.frag")
        val blitFrag = resolveShader("blit.frag")

        // Ensure XFCE desktop background is transparent if xfdesktop is active
        configureXfdesktopTransparency()

        X11Canvas().use { canvas ->
            val monitors = detectMonitors(canvas.width, canvas.height)
            val totalScreenHeight = canvas.height

            // One independent window + GL context per monitor (see X11Canvas):
            // The primary surface is vsync'd to pace the render loop to its hardware
            // vblank (e.g. 165Hz), while secondary surfaces present without blocking (swapInterval=0).
            // This prevents serial vblank waiting from dragging mixed-refresh-rate setups down to ~40 FPS.
            val primaryIndex = monitors.indexOfFirst { it.primary }.let { if (it >= 0) it else 0 }

            val primaryMon = monitors[primaryIndex]
            val primarySurface =
                canvas.initGL(primaryMon.x, primaryMon.y, primaryMon.width, primaryMon.height)
            val primaryTarget = canvas.createRenderTarget(
                scaledSize(primaryMon.width), scaledSize(primaryMon.height)
            )

            val surfaces = Array(monitors.size) { i ->
                if (i == primaryIndex) primarySurface
                else {
                    val mon = monitors[i]
                    canvas.createSurface(mon.x, mon.y, mon.width, mon.height, 0)
                }
            }
            val renderTargets = Array(monitors.size) { i ->
                if (i == primaryIndex) primaryTarget
                else {
                    val mon = monitors[i]
                    canvas.createRenderTarget(scaledSize(mon.width), scaledSize(mon.height))
                }
            }

            // One shared raymarch program for every surface: programs (unlike
            // FBOs) ARE shared across contexts in the same GL object namespace
            // (see X11Canvas), so it's a single continuous panorama scene,
            // rendered by one camera and split only by the bezels. It draws
            // into each monitor's small RenderTarget; sharedBlitPipeline then
            // upscales that into the real window-sized backbuffer.
            val sharedPipeline = ShaderPipeline(canvas, vertPath, panoramaFrag)
            val sharedBlitPipeline = BlitPipeline(canvas, vertPath, blitFrag)

            var glErrorChecks = 120 // verify no GL errors over the first frames, then stop polling
            WallpaperEvents(canvas).use { events ->
                // Install our own handler explicitly so Ctrl+C always works
                Signal.handle(Signal("INT")) { System.exit(0) }

                val shutdownGraceMillis: Long = 2000
                val shutdownRequested = AtomicBoolean(false)
                val renderThread = Thread.currentThread()
                val shutdownHook = Thread({
                    shutdownRequested.set(true)
                    try {
                        renderThread.join(shutdownGraceMillis)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    if (renderThread.isAlive) {
                        System.err.println(
                            "[wallpaper] render thread did not stop within ${shutdownGraceMillis}ms " +
                                "(likely blocked in a native GL call) -- exiting without finishing cleanup."
                        )
                    }
                }, "wallpaper-shutdown")
                Runtime.getRuntime().addShutdownHook(shutdownHook)

                val startTimeNanos = System.nanoTime()
                var lastLowerNanos = System.nanoTime()
                val lowerIntervalNanos = 3000000000L
                var lastReloadCheckNanos = System.nanoTime()
                val reloadCheckIntervalNanos = 1000000000L

                val secondaryIntervalNanos = 1000000000L / 60
                val lastSecondarySwapNanos = LongArray(monitors.size)
                try {
                    while (!shutdownRequested.get()) {
                        val nowForReload = System.nanoTime()
                        if (nowForReload - lastReloadCheckNanos > reloadCheckIntervalNanos) {
                            sharedPipeline.reloadIfModified()
                            lastReloadCheckNanos = nowForReload
                        }

                        val nowForLower = System.nanoTime()
                        if (nowForLower - lastLowerNanos > lowerIntervalNanos) {
                            canvas.lowerToBackground()
                            lastLowerNanos = nowForLower
                        }

                        val frameStartNanos = System.nanoTime()
                        val elapsed = (frameStartNanos - startTimeNanos) / 1000000000.0f

                        val rawState =
                            events.pollState(canvas.width.toFloat(), canvas.height.toFloat(), elapsed)

                        for (i in monitors.indices) {
                            if (i != primaryIndex) {
                                val nowForSecondary = System.nanoTime()
                                if (nowForSecondary - lastSecondarySwapNanos[i] < secondaryIntervalNanos) {
                                    continue  // not this secondary monitor's turn yet
                                }
                                lastSecondarySwapNanos[i] = nowForSecondary
                            }

                            val mon = monitors[i]
                            val surface = surfaces[i]
                            val target = renderTargets[i]

                            val viewportOffsetX = mon.x.toFloat()
                            val viewportOffsetY = (totalScreenHeight - (mon.y + mon.height)).toFloat()

                            canvas.makeCurrent(surface)

                            // Pass 1: the procedural raymarch, at reduced resolution.
                            canvas.bindFramebuffer(target.framebuffer)
                            canvas.setViewport(0, 0, target.width, target.height)
                            sharedPipeline.bind()
                            sharedPipeline.updateUniforms(rawState, viewportOffsetX, viewportOffsetY, RENDER_SCALE)
                            sharedPipeline.setResolution(target.width.toFloat(), target.height.toFloat())
                            canvas.drawQuad()
                            if (glErrorChecks-- > 0) {
                                val err = canvas.error
                                if (err != 0) System.err.println("[GL] error 0x${Integer.toHexString(err)} after pass 1")
                            }

                            // Pass 2: cheap GL_NEAREST upscale of that into the real backbuffer.
                            canvas.bindFramebuffer(0)
                            canvas.setViewport(0, 0, mon.width, mon.height)
                            sharedBlitPipeline.bind(target.texture)
                            canvas.drawQuad()

                            canvas.swapBuffers(surface)
                        }

                        throttleToMaxFps(frameStartNanos, maxFps)
                    }
                } finally {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook)
                    } catch (_: IllegalStateException) {
                    }
                }
            }
        }
    }

    companion object {
        private const val RENDER_SCALE = 0.5f

        private fun scaledSize(realSize: Int): Int =
            kotlin.math.round(realSize * RENDER_SCALE).toInt().coerceAtLeast(1)

        private fun throttleToMaxFps(frameStartNanos: Long, maxFps: Int) {
            if (maxFps <= 0) return
            val targetFrameNanos = 1000000000L / maxFps
            val remainingNanos = frameStartNanos + targetFrameNanos - System.nanoTime()
            if (remainingNanos <= 0) return
            try {
                Thread.sleep(remainingNanos / 1000000L, (remainingNanos % 1000000L).toInt())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
