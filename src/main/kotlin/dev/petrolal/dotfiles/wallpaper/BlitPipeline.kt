package dev.petrolal.dotfiles.wallpaper

import dev.petrolal.dotfiles.wallpaper.X11Canvas.RenderTarget
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal second shader pass: samples the low-resolution offscreen
 * [RenderTarget] the expensive raymarch shader drew into and
 * writes it to the real window-sized backbuffer. Deliberately separate from
 * [ShaderPipeline] -- no hot-reload, no per-frame uniform set beyond
 * the one texture sampler, and conflating the two would make the expensive
 * shader's uniform cache responsible for a texture binding it doesn't use.
 */
class BlitPipeline(private val canvas: X11Canvas, vertPath: Path, fragPath: Path) {
    private val programId: Int
    private val locTexture: Int

    init {
        val vs = canvas.createShader(X11Canvas.GL_VERTEX_SHADER)
        canvas.shaderSource(vs, Files.readString(vertPath))
        canvas.compileShader(vs)
        if (canvas.getShaderCompileStatus(vs)) {
            val log = canvas.getShaderInfoLog(vs)
            canvas.deleteShader(vs)
            throw IllegalStateException("Blit vertex shader compile failed (${vertPath.fileName}): $log")
        }

        val fs = canvas.createShader(X11Canvas.GL_FRAGMENT_SHADER)
        canvas.shaderSource(fs, Files.readString(fragPath))
        canvas.compileShader(fs)
        if (canvas.getShaderCompileStatus(fs)) {
            val log = canvas.getShaderInfoLog(fs)
            canvas.deleteShader(fs)
            canvas.deleteShader(vs)
            throw IllegalStateException("Blit fragment shader compile failed (${fragPath.fileName}): $log")
        }

        val prog = canvas.createProgram()
        canvas.attachShader(prog, vs)
        canvas.attachShader(prog, fs)
        canvas.linkProgram(prog)
        val linked = canvas.getProgramLinkStatus(prog)

        canvas.deleteShader(vs)
        canvas.deleteShader(fs)

        if (!linked) {
            val log = canvas.getProgramInfoLog(prog)
            canvas.deleteProgram(prog)
            throw IllegalStateException("Blit program link failed (${fragPath.fileName}): $log")
        }

        this.programId = prog
        this.locTexture = canvas.getUniformLocation(programId, "u_texture")
    }

    /** Binds the program and TEXTURE (texture unit 0) in one call, ready to draw a quad.  */
    @Throws(Throwable::class)
    fun bind(texture: Int) {
        canvas.useProgram(programId)
        canvas.bindTexture(texture)
        canvas.uniform1i(locTexture, 0)
    }
}
