package dev.petrolal.dotfiles.wallpaper

import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime

/**
 * ShaderPipeline manages compilation, linking, uniform binding, and live hot-reloading
 * of OpenGL shaders. All GL access goes through X11Canvas's typed wrapper
 * methods -- this class never touches a MethodHandle, Arena, or MemorySegment.
 */
class ShaderPipeline(canvas: X11Canvas, vertPath: Path, fragPath: Path):
  import ShaderPipeline.*

  private var programId: Int = compileProgram()
  private var lastMtime: FileTime = Files.getLastModifiedTime(fragPath)

  // Uniform locations
  private var locTexture: Int = -1
  private var locRes: Int = -1
  private var locTimeOfDay: Int = -1
  private var locHasTexture: Int = -1
  private var hasTexture: Boolean = true
  private var locTime: Int = -1
  private var locMouse: Int = -1
  private var locWorkspace: Int = -1
  private var locAudio: Int = -1
  private var locCpu: Int = -1
  private var locRam: Int = -1
  private var locViewportOffset: Int = -1
  private var locRenderScale: Int = -1

  // Screen-locker state uniforms
  private var locCharCount: Int = -1
  private var locAuthError: Int = -1
  private var locIsAuthenticating: Int = -1
  private var locMousePos: Int = -1
  private var locLockerActive: Int = -1
  private var locPrimaryOffsetPx: Int = -1
  private var locPrimaryResPx: Int = -1
  private var locLoginTexture: Int = -1
  private var locPasswordFieldUv: Int = -1

  cacheUniformLocations()

  /** Overrides u_resolution with the real size of the target being drawn into (the low-res FBO). */
  def setResolution(w: Float, h: Float): Unit =
    canvas.uniform2f(locRes, w, h)

  /** False switches wallpaper.frag to its black + old-TV-static fallback. */
  def setHasTexture(value: Boolean): Unit =
    this.hasTexture = value

  def bind(): Unit =
    canvas.useProgram(programId)

  def updateUniforms(
    state: WallpaperEvents.WallpaperState,
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    renderScale: Float
  ): Unit =
    canvas.uniform2f(locRes, state.width, state.height)
    canvas.uniform1f(locTimeOfDay, localTimeOfDay())
    canvas.uniform1f(locHasTexture, if hasTexture then 1.0f else 0.0f)
    canvas.uniform1f(locTime, state.time)
    canvas.uniform4f(
      locMouse,
      state.mouseX,
      state.mouseY,
      if state.mouseLeftDown then 1.0f else 0.0f,
      if state.mouseRightDown then 1.0f else 0.0f
    )
    canvas.uniform1f(locWorkspace, state.workspace.toFloat)
    canvas.uniform1f(locAudio, state.audioActivity)
    canvas.uniform1f(locCpu, state.cpu)
    canvas.uniform1f(locRam, state.ram)
    canvas.uniform2f(locViewportOffset, viewportOffsetX, viewportOffsetY)
    canvas.uniform1f(locRenderScale, renderScale)

  /** Screen-locker-only uniforms */
  def updateLockUniforms(
    charCount: Int,
    authError: Float,
    isAuthenticating: Float,
    mouseNormX: Float,
    mouseNormY: Float,
    primaryOffsetX: Float,
    primaryOffsetY: Float,
    primaryResW: Float,
    primaryResH: Float
  ): Unit =
    canvas.uniform1i(locCharCount, charCount)
    canvas.uniform1f(locAuthError, authError)
    canvas.uniform1f(locIsAuthenticating, isAuthenticating)
    canvas.uniform2f(locMousePos, mouseNormX, mouseNormY)
    canvas.uniform1f(locLockerActive, 1.0f)
    canvas.uniform2f(locPrimaryOffsetPx, primaryOffsetX, primaryOffsetY)
    canvas.uniform2f(locPrimaryResPx, primaryResW, primaryResH)

  /** One-time setup for the screen locker's baked login-dialog texture */
  def setLoginPromptTexture(
    textureUnit: Int,
    fieldU0: Float,
    fieldV0: Float,
    fieldU1: Float,
    fieldV1: Float
  ): Unit =
    canvas.uniform1i(locLoginTexture, textureUnit)
    canvas.uniform4f(locPasswordFieldUv, fieldU0, fieldV0, fieldU1, fieldV1)

  /** Checks if the fragment shader file was modified on disk and recompiles it live. */
  def reloadIfModified(): Unit =
    try
      val currentMtime = Files.getLastModifiedTime(fragPath)
      if currentMtime.compareTo(lastMtime) > 0 then
        val newProg = compileProgram()
        canvas.deleteProgram(programId)
        programId = newProg
        bind()
        cacheUniformLocations()
        lastMtime = currentMtime
        println(s"[Hot-Reload] Recompiled ${fragPath.getFileName}")
    catch
      case ex: Throwable =>
        System.err.println(s"[Hot-Reload Error] ${ex.getMessage}")

  private def compileProgram(): Int =
    val vs = compileShader(vertPath, X11Canvas.GL_VERTEX_SHADER)
    val fs =
      try compileShader(fragPath, X11Canvas.GL_FRAGMENT_SHADER)
      catch
        case ex: Throwable =>
          canvas.deleteShader(vs)
          throw ex

    val prog = canvas.createProgram()
    canvas.attachShader(prog, vs)
    canvas.attachShader(prog, fs)
    canvas.linkProgram(prog)
    val linked = canvas.getProgramLinkStatus(prog)

    canvas.deleteShader(vs)
    canvas.deleteShader(fs)

    if !linked then
      val log = canvas.getProgramInfoLog(prog)
      canvas.deleteProgram(prog)
      throw new IllegalStateException(s"Shader program link failed (${fragPath.getFileName}): $log")
    prog

  private def compileShader(path: Path, `type`: Int): Int =
    val shader = canvas.createShader(`type`)
    canvas.shaderSource(shader, Files.readString(path))
    canvas.compileShader(shader)

    if canvas.getShaderCompileStatus(shader) then
      val log = canvas.getShaderInfoLog(shader)
      canvas.deleteShader(shader)
      throw new IllegalStateException(s"Shader compile failed (${path.getFileName}): $log")
    shader

  def textureLocation: Int = locTexture

  private def cacheUniformLocations(): Unit =
    locTexture = canvas.getUniformLocation(programId, "u_texture")
    locRes = canvas.getUniformLocation(programId, "u_resolution")
    locTimeOfDay = canvas.getUniformLocation(programId, "u_timeOfDay")
    locHasTexture = canvas.getUniformLocation(programId, "u_hasTexture")
    locTime = canvas.getUniformLocation(programId, "u_time")
    locMouse = canvas.getUniformLocation(programId, "u_mouse")
    locWorkspace = canvas.getUniformLocation(programId, "u_workspace")
    locAudio = canvas.getUniformLocation(programId, "u_audio")
    locCpu = canvas.getUniformLocation(programId, "u_cpu")
    locRam = canvas.getUniformLocation(programId, "u_ram")
    locViewportOffset = canvas.getUniformLocation(programId, "u_viewportOffset")
    locRenderScale = canvas.getUniformLocation(programId, "u_renderScale")
    locCharCount = canvas.getUniformLocation(programId, "u_charCount")
    locAuthError = canvas.getUniformLocation(programId, "u_authError")
    locIsAuthenticating = canvas.getUniformLocation(programId, "u_isAuthenticating")
    locMousePos = canvas.getUniformLocation(programId, "u_mousePos")
    locLockerActive = canvas.getUniformLocation(programId, "u_lockerActive")
    locPrimaryOffsetPx = canvas.getUniformLocation(programId, "u_primaryOffsetPx")
    locPrimaryResPx = canvas.getUniformLocation(programId, "u_primaryResPx")
    locLoginTexture = canvas.getUniformLocation(programId, "u_loginTexture")
    locPasswordFieldUv = canvas.getUniformLocation(programId, "u_passwordFieldUv")

object ShaderPipeline:
  /** Local wall-clock time as a fractional hour in [0, 24): hour + minute/60 + second/3600. */
  def localTimeOfDay(): Float =
    val now = java.time.LocalTime.now()
    now.getHour + now.getMinute / 60.0f + now.getSecond / 3600.0f
