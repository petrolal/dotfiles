package dev.petrolal.dotfiles.wallpaper

import java.nio.file.{Files, Path}

/**
 * Minimal second shader pass: samples the low-resolution offscreen
 * [[X11Canvas.RenderTarget]] the expensive raymarch shader drew into and
 * writes it to the real window-sized backbuffer. Deliberately separate from
 * [[ShaderPipeline]] -- no hot-reload, no per-frame uniform set beyond
 * the one texture sampler, and conflating the two would make the expensive
 * shader's uniform cache responsible for a texture binding it doesn't use.
 */
class BlitPipeline(canvas: X11Canvas, vertPath: Path, fragPath: Path):
  private val vs = canvas.createShader(X11Canvas.GL_VERTEX_SHADER)
  canvas.shaderSource(vs, Files.readString(vertPath))
  canvas.compileShader(vs)
  if canvas.getShaderCompileStatus(vs) then
    val log = canvas.getShaderInfoLog(vs)
    canvas.deleteShader(vs)
    throw new IllegalStateException(s"Blit vertex shader compile failed (${vertPath.getFileName}): $log")

  private val fs = canvas.createShader(X11Canvas.GL_FRAGMENT_SHADER)
  canvas.shaderSource(fs, Files.readString(fragPath))
  canvas.compileShader(fs)
  if canvas.getShaderCompileStatus(fs) then
    val log = canvas.getShaderInfoLog(fs)
    canvas.deleteShader(fs)
    canvas.deleteShader(vs)
    throw new IllegalStateException(s"Blit fragment shader compile failed (${fragPath.getFileName}): $log")

  private val prog = canvas.createProgram()
  canvas.attachShader(prog, vs)
  canvas.attachShader(prog, fs)
  canvas.linkProgram(prog)
  private val linked = canvas.getProgramLinkStatus(prog)

  canvas.deleteShader(vs)
  canvas.deleteShader(fs)

  if !linked then
    val log = canvas.getProgramInfoLog(prog)
    canvas.deleteProgram(prog)
    throw new IllegalStateException(s"Blit program link failed (${fragPath.getFileName}): $log")

  private val programId: Int = prog
  private val locTexture: Int = canvas.getUniformLocation(programId, "u_texture")

  /** Binds the program and TEXTURE (texture unit 0) in one call, ready to draw a quad. */
  def bind(texture: Int): Unit =
    canvas.useProgram(programId)
    canvas.bindTexture(texture)
    canvas.uniform1i(locTexture, 0)
