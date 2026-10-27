package dev.petrolal.dotfiles.wallpaper

import dev.petrolal.dotfiles.wallpaper.WallpaperEvents.WallpaperState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalTime

/**
 * ShaderPipeline manages compilation, linking, uniform binding, and live hot-reloading
 * of OpenGL shaders. All GL access goes through X11Canvas's typed wrapper
 * methods -- this class never touches a MethodHandle, Arena, or MemorySegment.
 */
class ShaderPipeline(private val canvas: X11Canvas, private val vertPath: Path, private val fragPath: Path) {
    private var programId: Int
    private var lastMtime: FileTime

    // Uniform locations
    private var locTexture = -1
    private var locRes = -1
    private var locTimeOfDay = -1
    private var locHasTexture = -1
    private var locTime = -1
    private var locMouse = -1
    private var locWorkspace = -1
    private var locAudio = -1
    private var locCpu = -1
    private var locRam = -1
    private var locViewportOffset = -1
    private var locRenderScale = -1

    // Screen-locker state uniforms (see dev.petrolal.dotfiles.locker.ScreenLocker).
    // Unused -- and left at GL's default-zero -- for the ordinary desktop
    // wallpaper daemon, which never calls updateLockUniforms().
    private var locCharCount = -1
    private var locAuthError = -1
    private var locIsAuthenticating = -1
    private var locMousePos = -1
    private var locLockerActive = -1
    private var locPrimaryOffsetPx = -1
    private var locPrimaryResPx = -1
    private var locLoginTexture = -1
    private var locPasswordFieldUv = -1

    init {
        this.programId = compileProgram()
        this.lastMtime = Files.getLastModifiedTime(fragPath)
        cacheUniformLocations()
    }

    /** Overrides u_resolution with the real size of the target being drawn into (the low-res FBO).  */
    @Throws(Throwable::class)
    fun setResolution(w: Float, h: Float) {
        canvas.uniform2f(locRes, w, h)
    }

    /** False switches wallpaper.frag to its black + old-TV-static fallback.  */
    var hasTexture: Boolean = true

    @Throws(Throwable::class)
    fun bind() {
        canvas.useProgram(programId)
    }

    @Throws(Throwable::class)
    fun updateUniforms(
        state: WallpaperState, viewportOffsetX: Float, viewportOffsetY: Float,
        renderScale: Float
    ) {
        canvas.uniform2f(locRes, state.width, state.height)
        canvas.uniform1f(locTimeOfDay, localTimeOfDay())
        canvas.uniform1f(locHasTexture, if (hasTexture) 1.0f else 0.0f)
        canvas.uniform1f(locTime, state.time)
        canvas.uniform4f(
            locMouse, state.mouseX, state.mouseY,
            if (state.mouseLeftDown) 1.0f else 0.0f, if (state.mouseRightDown) 1.0f else 0.0f
        )
        canvas.uniform1f(locWorkspace, state.workspace.toFloat())
        canvas.uniform1f(locAudio, state.audioActivity)
        canvas.uniform1f(locCpu, state.cpu)
        canvas.uniform1f(locRam, state.ram)
        canvas.uniform2f(locViewportOffset, viewportOffsetX, viewportOffsetY)
        canvas.uniform1f(locRenderScale, renderScale)
    }

    /** Screen-locker-only uniforms, pushed in a separate call from
     * [.updateUniforms] so the ordinary wallpaper daemon's render loop
     * never has to know these exist.
     * @param charCount number of characters currently in the password buffer
     * @param authError 1.0 right after a failed attempt, decaying to 0.0
     * @param isAuthenticating 1.0 while a PAM check is in flight
     * @param mouseNormX, mouseNormY normalized [0,1] cursor position for camera parallax
     * @param primaryOffsetX, primaryOffsetY primary monitor's bottom-left corner, in GL
     * (bottom-left-origin) pixels within the locker's whole-virtual-desktop window
     * @param primaryResW, primaryResH primary monitor's own pixel width/height -- recenters
     * the lock scene on one physical screen instead of the combined virtual desktop
     */
    @Throws(Throwable::class)
    fun updateLockUniforms(
        charCount: Int, authError: Float, isAuthenticating: Float,
        mouseNormX: Float, mouseNormY: Float,
        primaryOffsetX: Float, primaryOffsetY: Float,
        primaryResW: Float, primaryResH: Float
    ) {
        canvas.uniform1i(locCharCount, charCount)
        canvas.uniform1f(locAuthError, authError)
        canvas.uniform1f(locIsAuthenticating, isAuthenticating)
        canvas.uniform2f(locMousePos, mouseNormX, mouseNormY)
        canvas.uniform1f(locLockerActive, 1.0f)
        canvas.uniform2f(locPrimaryOffsetPx, primaryOffsetX, primaryOffsetY)
        canvas.uniform2f(locPrimaryResPx, primaryResW, primaryResH)
    }

    /** One-time setup for the screen locker's baked login-dialog texture
     * (see dev.petrolal.dotfiles.locker.LoginPromptTexture) -- the sampler
     * binding and the dialog's own password-field rect never change for the
     * life of a lock session, so this is called once, not every frame.  */
    @Throws(Throwable::class)
    fun setLoginPromptTexture(
        textureUnit: Int, fieldU0: Float, fieldV0: Float,
        fieldU1: Float, fieldV1: Float
    ) {
        canvas.uniform1i(locLoginTexture, textureUnit)
        canvas.uniform4f(locPasswordFieldUv, fieldU0, fieldV0, fieldU1, fieldV1)
    }

    /**
     * Checks if the fragment shader file was modified on disk and recompiles it live.
     */
    fun reloadIfModified() {
        try {
            val currentMtime = Files.getLastModifiedTime(fragPath)
            if (currentMtime > lastMtime) {
                val newProg = compileProgram()
                canvas.deleteProgram(programId)
                programId = newProg
                bind()
                cacheUniformLocations()
                lastMtime = currentMtime
                println("[Hot-Reload] Recompiled ${fragPath.fileName}")
            }
        } catch (ex: Throwable) {
            System.err.println("[Hot-Reload Error] ${ex.message}")
        }
    }

    @Throws(Throwable::class)
    private fun compileProgram(): Int {
        val vs = compileShader(vertPath, X11Canvas.GL_VERTEX_SHADER)
        val fs: Int
        try {
            fs = compileShader(fragPath, X11Canvas.GL_FRAGMENT_SHADER)
        } catch (ex: Throwable) {
            // vs compiled fine but fs didn't -- without this, every failed
            // hot-reload attempt on a broken fragment shader leaks one more
            // vertex shader object in the driver for the life of the daemon.
            canvas.deleteShader(vs)
            throw ex
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
            throw IllegalStateException("Shader program link failed (${fragPath.fileName}): $log")
        }
        return prog
    }

    @Throws(Throwable::class)
    private fun compileShader(path: Path, type: Int): Int {
        val shader = canvas.createShader(type)
        canvas.shaderSource(shader, Files.readString(path))
        canvas.compileShader(shader)

        if (canvas.getShaderCompileStatus(shader)) {
            val log = canvas.getShaderInfoLog(shader)
            canvas.deleteShader(shader)
            throw IllegalStateException("Shader compile failed (${path.fileName}): $log")
        }
        return shader
    }

    /** Location of the `u_texture` sampler (-1 if the shader doesn't declare it).  */
    fun textureLocation(): Int = locTexture

    @Throws(Throwable::class)
    private fun cacheUniformLocations() {
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
    }

    companion object {
        /** Local wall-clock time as a fractional hour in [0, 24): hour + minute/60 + second/3600.  */
        fun localTimeOfDay(): Float {
            val now = LocalTime.now()
            return now.hour + now.minute / 60.0f + now.second / 3600.0f
        }
    }
}
