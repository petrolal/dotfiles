// ScreenLocker.scala --- Interactive X11 screen locker: full-screen raymarched
// cyber-horror UI, keyboard/pointer grab, PAM-backed password check.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

import java.nio.file.{Files, Path}
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Using
import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.wallpaper.{BlitPipeline, ShaderPipeline, Wallpaper, X11Canvas}
import sun.misc.Signal

final class ScreenLocker:
  import ScreenLocker.*

  private def resolveShader(name: String): Path =
    val direct = Path.of("shaders", name)
    if Files.exists(direct) then return direct
    val root = DotfilePaths.findDotfilesRoot()
    val inRes = root.resolve("src/main/resources/shaders").resolve(name)
    if Files.exists(inRes) then return inRes
    val homeRes = DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources/shaders").resolve(name)
    if Files.exists(homeRes) then return homeRes
    direct

  def start(testTimeoutSeconds: Float, dumpFramePath: String, injectPassword: String): Unit =
    val vertPath = resolveShader("wallpaper.vert")
    val fragPath = resolveShader("wallpaper.frag")
    val blitFrag = resolveShader("blit.frag")
    val user = System.getProperty("user.name")

    if testTimeoutSeconds > 0 then
      System.err.println(
        s"[locker] TEST MODE: auto-unlocking after ${testTimeoutSeconds}s with NO password check. Do not use this flag for a real lock."
      )
    if dumpFramePath != null then
      System.err.println(
        s"[locker] TEST MODE: will dump one frame to $dumpFramePath and exit. Do not use this flag for a real lock."
      )
    if injectPassword != null then
      System.err.println(
        s"[locker] TEST MODE: will synthesize ${injectPassword.length} keypress(es) + Return -- never pass a real password here, it is visible via argv/ps."
      )

    Using.resource(new X11Canvas()) { canvas =>
      Using.resource(new PamAuthService()) { pam =>
        val surface = canvas.createLockWindow()
        val grabbed = canvas.grabInputWithRetry(surface)
        if !grabbed then
          canvas.destroyLockWindow(surface)
          throw new IllegalStateException(
            "Could not grab keyboard/pointer -- refusing to show a lock screen that input could bypass."
          )

        if injectPassword != null then
          canvas.injectTestKeyPress(X11Canvas.XK_RETURN)
          var i = injectPassword.length - 1
          while i >= 0 do
            canvas.injectTestKeyPress(injectPassword.charAt(i).toLong)
            i -= 1

        try
          val pipeline = new ShaderPipeline(canvas, vertPath, fragPath)
          val blitPipeline = new BlitPipeline(canvas, vertPath, blitFrag)
          val target = canvas.createRenderTarget(
            math.max(1, canvas.getWidth / 2), math.max(1, canvas.getHeight / 2)
          )

          val loginLayout = LoginPromptTexture.render(user)
          val loginTexture = canvas.createTextureRGBA(
            LoginPromptTexture.WIDTH, LoginPromptTexture.HEIGHT, loginLayout.rgba
          )
          canvas.bindTextureUnit(1, loginTexture)
          pipeline.bind()
          pipeline.setLoginPromptTexture(1, loginLayout.fieldU0, loginLayout.fieldV0, loginLayout.fieldU1, loginLayout.fieldV1)

          val monitors = new Wallpaper().detectMonitors(canvas.getWidth, canvas.getHeight)
          var primaryMonitor = monitors.head
          for mon <- monitors do
            if mon.primary then primaryMonitor = mon

          val primaryOffsetX = primaryMonitor.x.toFloat
          val primaryOffsetY = (canvas.getHeight - (primaryMonitor.y + primaryMonitor.height)).toFloat
          val primaryResW = primaryMonitor.width.toFloat
          val primaryResH = primaryMonitor.height.toFloat

          val shutdownRequested = new AtomicBoolean(false)
          val unlocked = new AtomicBoolean(false)

          Signal.handle(new Signal("TERM"), _ => shutdownRequested.set(true))
          Signal.handle(new Signal("INT"), _ => shutdownRequested.set(true))

          var passwordBuffer = new Array[Char](0)
          var authError = 0.0f
          var authenticating = false
          var frameDumped = false
          var pendingAuth: Future[PamAuthService.Status] = null

          val startNanos = System.nanoTime()
          var lastFrameNanos = startNanos
          val viewportOffsetX = 0.0f
          val viewportOffsetY = 0.0f
          val renderScale = 0.5f

          try
            while !shutdownRequested.get() && !unlocked.get() do
              val now = System.nanoTime()
              val elapsed = (now - startNanos) / 1_000_000_000.0f
              val dt = (now - lastFrameNanos) / 1_000_000_000.0f
              lastFrameNanos = now

              if testTimeoutSeconds > 0 && elapsed >= testTimeoutSeconds then
                System.err.println("[locker] TEST MODE timeout reached -- auto-unlocking without a password check.")
                unlocked.set(true)

              if !unlocked.get() then
                var ev = canvas.pollKeyEvent()
                while ev != null do
                  if ev.press then
                    if ev.keysym == X11Canvas.XK_ESCAPE then
                      passwordBuffer = clearBuffer(passwordBuffer)
                    else if ev.keysym == X11Canvas.XK_BACKSPACE then
                      if passwordBuffer.length > 0 then
                        val shorter = new Array[Char](passwordBuffer.length - 1)
                        System.arraycopy(passwordBuffer, 0, shorter, 0, shorter.length)
                        passwordBuffer(passwordBuffer.length - 1) = '\u0000'
                        passwordBuffer = shorter
                    else if ev.keysym == X11Canvas.XK_RETURN || ev.keysym == X11Canvas.XK_KP_ENTER then
                      if !authenticating then
                        System.err.println(
                          s"[locker] Enter pressed (${passwordBuffer.length} chars buffered) -- checking against PAM for user '$user'..."
                        )
                        authenticating = true
                        val toCheck = passwordBuffer
                        passwordBuffer = new Array[Char](0)
                        pendingAuth = pam.authenticateAsync(user, toCheck)
                    else if ev.text.nonEmpty then
                      val c = ev.text.charAt(0)
                      if c >= 0x20 && c != 0x7F && passwordBuffer.length < MAX_PASSWORD_LENGTH then
                        val longer = new Array[Char](passwordBuffer.length + 1)
                        System.arraycopy(passwordBuffer, 0, longer, 0, passwordBuffer.length)
                        longer(passwordBuffer.length) = c
                        passwordBuffer = longer
                  ev = canvas.pollKeyEvent()

                if authenticating && pendingAuth != null && pendingAuth.isDone then
                  val status =
                    try pendingAuth.get()
                    catch case _: Exception => PamAuthService.Status.ERROR
                  authenticating = false
                  pendingAuth = null
                  if status == PamAuthService.Status.SUCCESS then
                    System.err.println("[locker] PAM authentication succeeded -- unlocking.")
                    unlocked.set(true)
                  else
                    System.err.println(s"[locker] PAM authentication failed ($status) -- staying locked.")
                    authError = 1.0f

                authError = math.max(0.0f, authError - dt / ERROR_DECAY_SECONDS)

                val mouse = canvas.queryMouse()
                val mouseNormX = if canvas.getWidth > 0 then mouse.x / canvas.getWidth else 0.5f
                val mouseNormY = if canvas.getHeight > 0 then mouse.y / canvas.getHeight else 0.5f

                canvas.makeCurrent(surface)

                canvas.bindFramebuffer(target.framebuffer)
                canvas.setViewport(0, 0, target.width, target.height)
                pipeline.bind()
                val state = dev.petrolal.dotfiles.wallpaper.WallpaperEvents.WallpaperState(
                  canvas.getWidth.toFloat, canvas.getHeight.toFloat, elapsed,
                  mouse.x, mouse.y, mouse.leftDown, mouse.rightDown,
                  0, 0.0f, 0.0f, 0.0f
                )
                pipeline.updateUniforms(state, viewportOffsetX, viewportOffsetY, renderScale)
                pipeline.setResolution(target.width.toFloat, target.height.toFloat)
                pipeline.updateLockUniforms(
                  passwordBuffer.length, authError,
                  if authenticating then 1.0f else 0.0f, mouseNormX, mouseNormY,
                  primaryOffsetX * renderScale, primaryOffsetY * renderScale,
                  primaryResW * renderScale, primaryResH * renderScale
                )
                canvas.drawQuad()

                canvas.bindFramebuffer(0)
                canvas.setViewport(0, 0, canvas.getWidth, canvas.getHeight)
                blitPipeline.bind(target.texture)
                canvas.drawQuad()

                val readyToDump = if injectPassword != null then authError > 0.0f else elapsed >= 0.3f
                if dumpFramePath != null && !frameDumped && readyToDump then
                  frameDumped = true
                  dumpFrame(canvas, dumpFramePath)
                  System.err.println(
                    s"[locker] Dumped one frame to $dumpFramePath" +
                      (if injectPassword != null then " -- letting the injected PAM check finish before exiting."
                       else " -- unlocking.")
                  )
                  if injectPassword == null then
                    unlocked.set(true)

                canvas.swapBuffers(surface)
          finally
            java.util.Arrays.fill(passwordBuffer, '\u0000')
        finally
          canvas.destroyLockWindow(surface)
      }
    }

  private def clearBuffer(buffer: Array[Char]): Array[Char] =
    java.util.Arrays.fill(buffer, '\u0000')
    new Array[Char](0)

  private def dumpFrame(canvas: X11Canvas, path: String): Unit =
    val w = canvas.getWidth
    val h = canvas.getHeight
    val rgba = canvas.readPixelsRGBA(w, h)
    PngWriter.write(rgba, w, h, Path.of(path))

object ScreenLocker:
  private val ERROR_DECAY_SECONDS = 1.2f
  private val MAX_PASSWORD_LENGTH = 128
