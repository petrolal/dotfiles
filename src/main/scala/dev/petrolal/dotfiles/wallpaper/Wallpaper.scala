package dev.petrolal.dotfiles.wallpaper

import java.io.{BufferedReader, InputStreamReader}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable.ArrayBuffer
import scala.util.Using
import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner
import sun.misc.Signal

/**
 * Universal wallpaper controller:
 * - Dynamically adapts to ANY multi-monitor count (1, 2, 3, or more displays)
 * - Computes OpenGL bottom-left viewports correctly for arbitrary layouts (horizontal, vertical, offset)
 * - Frame pacing is driven entirely by vsync (swapBuffers blocks on vblank, see
 *   X11Canvas.setSwapInterval), so it naturally tracks whichever display's
 *   refresh rate the driver syncs the drawable to
 * - Live hot-reloading & safe uniform dispatches per monitor
 */
final class Wallpaper:
  import Wallpaper.*

  private def resolveShader(name: String): Path =
    resolveResource("shaders", name)

  private def resolveAsset(name: String): Path =
    resolveResource("assets", name)

  private def resolveResource(dir: String, name: String): Path =
    val direct = Path.of(name)
    if Files.exists(direct) then return direct
    val inDir = Path.of(dir, name)
    if Files.exists(inDir) then return inDir
    val root = DotfilePaths.findDotfilesRoot()
    val inRes = root.resolve("src/main/resources").resolve(dir).resolve(name)
    if Files.exists(inRes) then return inRes
    val inRoot = root.resolve(name)
    if Files.exists(inRoot) then return inRoot
    val homeRes = DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources").resolve(dir).resolve(name)
    if Files.exists(homeRes) then return homeRes
    direct

  private def configureXfdesktopTransparency(): Unit =
    if !DotfilePaths.commandExists("xfconf-query") then return

    val listResult = ProcessRunner.captureOutput(
      List("xfconf-query", "-c", "xfce4-desktop", "-l")
    )
    listResult.ifOk { output =>
      for property <- output.linesIterator do
        if property.contains("image-style") then
          ProcessRunner.run(List("xfconf-query", "-c", "xfce4-desktop", "-p", property, "-s", "0"))
        else if property.contains("color-style") then
          ProcessRunner.run(List("xfconf-query", "-c", "xfce4-desktop", "-p", property, "-s", "3"))
    }

    if DotfilePaths.commandExists("xfdesktop") then
      ProcessRunner.run(List("xfdesktop", "--reload"))

  def detectMonitors(totalWidth: Int, totalHeight: Int): List[Monitor] =
    var list = parseListMonitors()
    if list.isEmpty then
      list = parseXrandrQuery()

    if list.nonEmpty then
      return list.sortBy(m => (m.x, m.y))

    List(Monitor(0, 0, 0, totalWidth, totalHeight, true))

  private def parseListMonitors(): List[Monitor] =
    val list = ArrayBuffer[Monitor]()
    try
      val p = ProcessBuilder("xrandr", "--listmonitors").start()
      Using.resource(BufferedReader(InputStreamReader(p.getInputStream))) { reader =>
        val pat = Pattern.compile("(\\d+):\\s+\\+(\\*?)\\S*\\s+(\\d+)/\\d+x(\\d+)/\\d+\\+(\\d+)\\+(\\d+)")
        var autoId = 0
        var line = reader.readLine()
        while line != null do
          val m = pat.matcher(line)
          if m.find() then
            val primary = !m.group(2).isEmpty
            val w = m.group(3).toInt
            val h = m.group(4).toInt
            val x = m.group(5).toInt
            val y = m.group(6).toInt
            list += Monitor(autoId, x, y, w, h, primary)
            autoId += 1
          line = reader.readLine()
      }
      p.waitFor()
    catch
      case _: Exception => ()
    list.toList

  private def parseXrandrQuery(): List[Monitor] =
    val list = ArrayBuffer[Monitor]()
    try
      val p = ProcessBuilder("xrandr", "--query").start()
      Using.resource(BufferedReader(InputStreamReader(p.getInputStream))) { reader =>
        val pat = Pattern.compile("^\\S+\\s+connected\\s+(primary\\s+)?(\\d+)x(\\d+)\\+(\\d+)\\+(\\d+)")
        var autoId = 0
        var line = reader.readLine()
        while line != null do
          val m = pat.matcher(line)
          if m.find() then
            val primary = m.group(1) != null
            val w = m.group(2).toInt
            val h = m.group(3).toInt
            val x = m.group(4).toInt
            val y = m.group(5).toInt
            list += Monitor(autoId, x, y, w, h, primary)
            autoId += 1
          line = reader.readLine()
      }
      p.waitFor()
    catch
      case _: Exception => ()
    list.toList

  def start(maxFps: Int): Unit =
    val vertPath = resolveShader("wallpaper.vert")
    val panoramaFrag = resolveShader("wallpaper.frag")
    val blitFrag = resolveShader("blit.frag")

    configureXfdesktopTransparency()

    Using.resource(new X11Canvas()) { canvas =>
      val monitors = detectMonitors(canvas.getWidth, canvas.getHeight)
      val totalScreenHeight = canvas.getHeight

      var primaryIndex = 0
      var found = false
      var idx = 0
      while idx < monitors.length && !found do
        if monitors(idx).primary then
          primaryIndex = idx
          found = true
        idx += 1

      val surfaces = new Array[X11Canvas.Surface](monitors.size)
      val renderTargets = new Array[X11Canvas.RenderTarget](monitors.size)

      val primaryMon = monitors(primaryIndex)
      val primarySurface = canvas.initGL(primaryMon.x, primaryMon.y, primaryMon.width, primaryMon.height)
      surfaces(primaryIndex) = primarySurface
      renderTargets(primaryIndex) = canvas.createRenderTarget(
        scaledSize(primaryMon.width), scaledSize(primaryMon.height)
      )

      for i <- monitors.indices do
        if i != primaryIndex then
          val mon = monitors(i)
          surfaces(i) = canvas.createSurface(mon.x, mon.y, mon.width, mon.height, 0)
          renderTargets(i) = canvas.createRenderTarget(scaledSize(mon.width), scaledSize(mon.height))

      val sharedPipeline = new ShaderPipeline(canvas, vertPath, panoramaFrag)
      val sharedBlitPipeline = new BlitPipeline(canvas, vertPath, blitFrag)

      var glErrorChecks = 120

      Using.resource(new WallpaperEvents(canvas)) { events =>
        Signal.handle(new Signal("INT"), _ => System.exit(0))

        val shutdownGraceMillis = 2000L
        val shutdownRequested = new AtomicBoolean(false)
        val renderThread = Thread.currentThread()
        val shutdownHook = new Thread(() => {
          shutdownRequested.set(true)
          try renderThread.join(shutdownGraceMillis)
          catch case _: InterruptedException => Thread.currentThread().interrupt()
          if renderThread.isAlive then
            System.err.println(
              s"[wallpaper] render thread did not stop within ${shutdownGraceMillis}ms (likely blocked in a native GL call) -- exiting without finishing cleanup."
            )
        }, "wallpaper-shutdown")
        Runtime.getRuntime.addShutdownHook(shutdownHook)

        val startTimeNanos = System.nanoTime()
        var lastLowerNanos = System.nanoTime()
        val lowerIntervalNanos = 3_000_000_000L
        var lastReloadCheckNanos = System.nanoTime()
        val reloadCheckIntervalNanos = 1_000_000_000L

        val secondaryIntervalNanos = 1_000_000_000L / 60
        val lastSecondarySwapNanos = new Array[Long](monitors.size)

        try
          while !shutdownRequested.get() do
            val nowForReload = System.nanoTime()
            if nowForReload - lastReloadCheckNanos > reloadCheckIntervalNanos then
              sharedPipeline.reloadIfModified()
              lastReloadCheckNanos = nowForReload

            val nowForLower = System.nanoTime()
            if nowForLower - lastLowerNanos > lowerIntervalNanos then
              canvas.lowerToBackground()
              lastLowerNanos = nowForLower

            val frameStartNanos = System.nanoTime()
            val elapsed = (frameStartNanos - startTimeNanos) / 1_000_000_000.0f

            val rawState = events.pollState(canvas.getWidth.toFloat, canvas.getHeight.toFloat, elapsed)

            for i <- monitors.indices do
              var renderThis = true
              if i != primaryIndex then
                val nowForSecondary = System.nanoTime()
                if nowForSecondary - lastSecondarySwapNanos(i) < secondaryIntervalNanos then
                  renderThis = false
                else
                  lastSecondarySwapNanos(i) = nowForSecondary

              if renderThis then
                val mon = monitors(i)
                val surface = surfaces(i)
                val target = renderTargets(i)

                val viewportOffsetX = mon.x.toFloat
                val viewportOffsetY = (totalScreenHeight - (mon.y + mon.height)).toFloat

                canvas.makeCurrent(surface)

                // Pass 1: procedural raymarch at reduced resolution
                canvas.bindFramebuffer(target.framebuffer)
                canvas.setViewport(0, 0, target.width, target.height)
                sharedPipeline.bind()
                sharedPipeline.updateUniforms(rawState, viewportOffsetX, viewportOffsetY, RENDER_SCALE)
                sharedPipeline.setResolution(target.width.toFloat, target.height.toFloat)
                canvas.drawQuad()
                if glErrorChecks > 0 then
                  glErrorChecks -= 1
                  val err = canvas.getError()
                  if err != 0 then
                    System.err.println(s"[GL] error 0x${Integer.toHexString(err)} after pass 1")

                // Pass 2: upscale into real backbuffer
                canvas.bindFramebuffer(0)
                canvas.setViewport(0, 0, mon.width, mon.height)
                sharedBlitPipeline.bind(target.texture)
                canvas.drawQuad()

                canvas.swapBuffers(surface)

            throttleToMaxFps(frameStartNanos, maxFps)
        finally
          try Runtime.getRuntime.removeShutdownHook(shutdownHook)
          catch case _: IllegalStateException => ()
      }
    }

object Wallpaper:
  case class Monitor(id: Int, x: Int, y: Int, width: Int, height: Int, primary: Boolean)

  private val RENDER_SCALE = 0.5f

  private def scaledSize(realSize: Int): Int =
    math.max(1, math.round(realSize * RENDER_SCALE))

  private def throttleToMaxFps(frameStartNanos: Long, maxFps: Int): Unit =
    if maxFps <= 0 then return
    val targetFrameNanos = 1_000_000_000L / maxFps
    val remainingNanos = frameStartNanos + targetFrameNanos - System.nanoTime()
    if remainingNanos <= 0 then return
    try Thread.sleep(remainingNanos / 1_000_000L, (remainingNanos % 1_000_000L).toInt)
    catch case _: InterruptedException => Thread.currentThread().interrupt()
