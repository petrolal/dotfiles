package dev.petrolal.dotfiles.wallpaper

import java.lang.AutoCloseable
import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * X11Canvas manages the X11 display connection and, per monitor, an
 * independent desktop-background window + OpenGL context (see
 * [.createSurface]). Each surface gets its own vsync'd
 * `glXSwapBuffers` call, so every monitor's presentation is paced by
 * its own hardware vblank -- no shared buffer swap, no assumption that two
 * monitors share a refresh rate, and no hardcoded target FPS anywhere.
 * Provides query methods for global mouse and workspace state.
 */
class X11Canvas : AutoCloseable {
    @JvmRecord
    data class MouseInfo(val x: Float, val y: Float, val leftDown: Boolean, val rightDown: Boolean)

    /** One decoded XKeyEvent: which keysym was pressed/released and (for a
     * KeyPress) whatever printable text XLookupString resolved it to --
     * empty for non-printable keys (arrows, function keys, modifiers...).  */
    @JvmRecord
    data class KeyEvent(val press: Boolean, val keysym: Long, val text: String?)

    /** One monitor's window + GL context. All surfaces share one GL object namespace.  */
    @JvmRecord
    data class Surface(val window: Long, val glContext: MemorySegment?)

    private val display: MemorySegment
    private val screen: Int
    private val root: Long
    val width: Int
    val height: Int
    private val atomCurrentDesktop: Long
    private val atomWmWindowType: Long
    private val atomWmWindowTypeDesktop: Long
    private val atomWmState: Long
    private val atomWmStateBelow: Long
    private val atomTypeAtom: Long

    private val surfaceWindows = mutableListOf<Long>()
    private var sharedGlContext: MemorySegment? = null
    private var quadVbo = -1

    // Long-lived scratch buffers for the per-frame native queries below.
    // queryMouse()/queryWorkspace() run once per frame (60x/sec, forever) --
    // allocating a fresh Arena.ofConfined() per call here leaked native
    // memory at several MB/sec under GraalVM native-image (confirmed via
    // steadily climbing RSS), so these out-params are allocated once and
    // reused instead of allocated/freed every frame.
    private val persistentArena: Arena = Arena.ofShared()
    private val mouseRootRet: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)
    private val mouseChildRet: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)
    private val mouseRootX: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_INT)
    private val mouseRootY: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_INT)
    private val mouseWinX: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_INT)
    private val mouseWinY: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_INT)
    private val mouseMask: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_INT)

    private val workspaceActualType: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)
    private val workspaceActualFormat: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_INT)
    private val workspaceNitems: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)
    private val workspaceBytesAfter: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)
    private val workspacePropPtr: MemorySegment = persistentArena.allocate(ValueLayout.ADDRESS)

    // Direct OpenGL function handles -- resolved once in initGL(), after the
    // first surface's context is current (see initGL for why this can't
    // happen in the constructor: it needs the first monitor's real rectangle).
    // Private: callers (e.g. ShaderPipeline) go through the typed wrapper
    // methods below instead of invoking these handles directly, so GL call
    // signatures and Arena/MemorySegment bookkeeping stay in one place.
    private lateinit var glCreateShader: MethodHandle
    private lateinit var glShaderSource: MethodHandle
    private lateinit var glCompileShader: MethodHandle
    private lateinit var glGetShaderiv: MethodHandle
    private lateinit var glGetShaderInfoLog: MethodHandle
    private lateinit var glCreateProgram: MethodHandle
    private lateinit var glAttachShader: MethodHandle
    private lateinit var glLinkProgram: MethodHandle
    private lateinit var glGetProgramiv: MethodHandle
    private lateinit var glGetProgramInfoLog: MethodHandle
    private lateinit var glUseProgram: MethodHandle
    private lateinit var glDeleteShader: MethodHandle
    private lateinit var glDeleteProgram: MethodHandle
    private lateinit var glGetUniformLocation: MethodHandle
    private lateinit var glUniform1f: MethodHandle
    private lateinit var glUniform2f: MethodHandle
    private lateinit var glUniform4f: MethodHandle
    private lateinit var glViewport: MethodHandle
    private lateinit var glGenBuffers: MethodHandle
    private lateinit var glBindBuffer: MethodHandle
    private lateinit var glBufferData: MethodHandle
    private lateinit var glEnableVertexAttribArray: MethodHandle
    private lateinit var glVertexAttribPointer: MethodHandle
    private lateinit var glDrawArrays: MethodHandle
    private lateinit var glGenFramebuffers: MethodHandle
    private lateinit var glBindFramebuffer: MethodHandle
    private lateinit var glFramebufferTexture2D: MethodHandle
    private lateinit var glCheckFramebufferStatus: MethodHandle
    private lateinit var glGenTextures: MethodHandle
    private lateinit var glBindTexture: MethodHandle
    private lateinit var glTexImage2D: MethodHandle
    private lateinit var glTexParameteri: MethodHandle
    private lateinit var glActiveTexture: MethodHandle
    private lateinit var glUniform1i: MethodHandle
    private lateinit var glGetError: MethodHandle
    private lateinit var glPixelStorei: MethodHandle
    private lateinit var glReadPixels: MethodHandle

    /**
     * Creates the first monitor's surface, sized to its real rectangle (not
     * the whole virtual desktop -- each monitor needs its own window so its
     * swapBuffers can vsync to its own vblank), resolves the GL function
     * pointers now that a context is current, and builds the shared quad
     * VBO every later [.createSurface] call reuses. Call once; use
     * [.createSurface] for every monitor after the first.
     */
    @Throws(Throwable::class)
    fun initGL(x: Int, y: Int, w: Int, h: Int): Surface {
        val first = createSurface(x, y, w, h)
        resolveGlProcsIfNeeded()
        createSharedQuadBuffer()
        bindQuadAttribs()
        return first
    }

    private var glProcsResolved = false

    /** Resolves every direct-GL function pointer via glXGetProcAddress.
     * Idempotent and safe to call with any already-current GL context --
     * [.initGL] calls it for the desktop wallpaper's first surface,
     * [.createLockWindow] calls it for the screen locker's window,
     * whichever of the two runs first in a given process.  */
    @Throws(Throwable::class)
    private fun resolveGlProcsIfNeeded() {
        if (glProcsResolved) return
        glProcsResolved = true

        val linker = Linker.nativeLinker()
        glCreateShader =
            getProc(linker, "glCreateShader", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glShaderSource = getProc(
            linker,
            "glShaderSource",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            )
        )
        glCompileShader = getProc(linker, "glCompileShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glGetShaderiv = getProc(
            linker,
            "glGetShaderiv",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        )
        glGetShaderInfoLog = getProc(
            linker,
            "glGetShaderInfoLog",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            )
        )
        glCreateProgram = getProc(linker, "glCreateProgram", FunctionDescriptor.of(ValueLayout.JAVA_INT))
        glAttachShader =
            getProc(linker, "glAttachShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glLinkProgram = getProc(linker, "glLinkProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glGetProgramiv = getProc(
            linker,
            "glGetProgramiv",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        )
        glGetProgramInfoLog = getProc(
            linker,
            "glGetProgramInfoLog",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            )
        )
        glUseProgram = getProc(linker, "glUseProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glDeleteShader = getProc(linker, "glDeleteShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glDeleteProgram = getProc(linker, "glDeleteProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glGetUniformLocation = getProc(
            linker,
            "glGetUniformLocation",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        )
        glUniform1f =
            getProc(linker, "glUniform1f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT))
        glUniform2f = getProc(
            linker,
            "glUniform2f",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
        )
        glUniform4f = getProc(
            linker,
            "glUniform4f",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_FLOAT,
                ValueLayout.JAVA_FLOAT,
                ValueLayout.JAVA_FLOAT,
                ValueLayout.JAVA_FLOAT
            )
        )
        glViewport = getProc(
            linker,
            "glViewport",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT
            )
        )
        glGenBuffers =
            getProc(linker, "glGenBuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
        glBindBuffer =
            getProc(linker, "glBindBuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glBufferData = getProc(
            linker,
            "glBufferData",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT
            )
        )
        glEnableVertexAttribArray =
            getProc(linker, "glEnableVertexAttribArray", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glVertexAttribPointer = getProc(
            linker,
            "glVertexAttribPointer",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS
            )
        )
        glDrawArrays = getProc(
            linker,
            "glDrawArrays",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        )
        glGenFramebuffers =
            getProc(linker, "glGenFramebuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
        glBindFramebuffer =
            getProc(linker, "glBindFramebuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glFramebufferTexture2D = getProc(
            linker,
            "glFramebufferTexture2D",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT
            )
        )
        glCheckFramebufferStatus = getProc(
            linker,
            "glCheckFramebufferStatus",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        )
        glGenTextures =
            getProc(linker, "glGenTextures", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
        glBindTexture =
            getProc(linker, "glBindTexture", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glTexImage2D = getProc(
            linker,
            "glTexImage2D",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS
            )
        )
        glTexParameteri = getProc(
            linker,
            "glTexParameteri",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        )
        glActiveTexture = getProc(linker, "glActiveTexture", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
        glUniform1i =
            getProc(linker, "glUniform1i", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glGetError = getProc(linker, "glGetError", FunctionDescriptor.of(ValueLayout.JAVA_INT))
        glPixelStorei =
            getProc(linker, "glPixelStorei", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        glReadPixels = getProc(
            linker, "glReadPixels", FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS
            )
        )
    }

    /**
     * Creates one monitor's desktop-background window and GL context with an
     * explicit VSync swap interval (1 for vsync-paced, 0 for immediate unblocked presentation).
     */
    /**
     * Creates one monitor's desktop-background window and GL context, sized
     * and positioned to exactly cover that monitor. Every surface after the
     * first shares the first context's GL object namespace (shaders/buffers).
     * 
     * 
     * Mixed refresh rates (e.g. 165Hz primary + 60Hz HDMI) cannot serially
     * block on multiple vblanks in a single render thread without dropping
     * frame rates down to ~40 FPS. Therefore, the bootstrap (primary) surface
     * paces the loop with vsync (swapInterval=1), while subsequent secondary
     * surfaces default to swapInterval=0 to avoid cross-locking delays.
     */
    @JvmOverloads
    @Throws(Throwable::class)
    fun createSurface(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        swapInterval: Int = if (sharedGlContext == null) 1 else 0
    ): Surface {
        // REVERTED: override-redirect (see git history) made xfwm4 stop
        // managing this window entirely, which was meant to make our
        // lowerWindow() calls stick instead of being routed through xfwm4 as
        // an ignorable ConfigureRequest -- but it also meant xfwm4's
        // compositor (use_compositing=true) stopped compositing it, which on
        // real hardware produced a black screen instead of the rendered
        // scene: compositors commonly assume they own final screen
        // presentation via their own composited buffer, and a window that
        // bypasses their redirection may never actually get painted to the
        // display. Back to a plain CopyFromParent window (valuemask=0,
        // equivalent to the old XCreateSimpleWindow) -- WM-managed, so
        // compositing works, at the cost of reintroducing the icon-stacking
        // race this was trying to fix. See lowerToBackground()'s comment for
        // the follow-up plan on that.
        val window = XCreateWindow.invokeExact(
            display, root, x, y, w, h, 0,
            COPY_FROM_PARENT, COPY_FROM_PARENT, MemorySegment.NULL, 0L, MemorySegment.NULL
        ) as Long

        Arena.ofConfined().use { arena ->
            val propType = arena.allocateFrom(ValueLayout.JAVA_LONG, atomWmWindowTypeDesktop)
            val ignoredType =
                XChangeProperty.invokeExact(display, window, atomWmWindowType, atomTypeAtom, 32, 0, propType, 1) as Int

            val propBelow = arena.allocateFrom(ValueLayout.JAVA_LONG, atomWmStateBelow)
            val ignoredBelow =
                XChangeProperty.invokeExact(display, window, atomWmState, atomTypeAtom, 32, 0, propBelow, 1) as Int
        }
        val ignoredMap = XMapWindow.invokeExact(display, window) as Int
        surfaceWindows.add(window)
        lowerWindow(window)

        val glContext: MemorySegment?
        Arena.ofConfined().use { arena ->
            val glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, GLX_RGBA, GLX_DOUBLEBUFFER, 0)
            val visInfo = glXChooseVisual.invokeExact(display, screen, glAttrs) as MemorySegment
            check(visInfo != MemorySegment.NULL) { "glXChooseVisual found no matching GLX visual (RGBA, double-buffered) for this X11 screen." }
            val shareWith = sharedGlContext ?: MemorySegment.NULL
            glContext = glXCreateContext.invokeExact(display, visInfo, shareWith, 1) as MemorySegment?
            val ignoredCurrent = glXMakeCurrent.invokeExact(display, window, glContext) as Int
            // XVisualInfo* is server-allocated by Xlib; it's our responsibility to free it.
            val ignoredFree = XFree.invokeExact(visInfo) as Int
        }
        if (sharedGlContext == null) {
            sharedGlContext = glContext
        } else {
            // Non-bootstrap surface: the shared quad buffer object already
            // exists, but buffer binding + vertex attrib state is per-context.
            bindQuadAttribs()
        }

        // Pacing strategy: The primary surface (swapInterval=1) paces the render loop
        // to its hardware vblank, while secondary surfaces (swapInterval=0) present
        // immediately without blocking the single thread.
        setSwapInterval(window, swapInterval)

        return Surface(window, glContext)
    }

    /**
     * Disables (0) or enables (1, the default we use) VSync for one drawable.
     * Resolves glXSwapIntervalEXT, glXSwapIntervalMESA, or glXSwapIntervalSGI dynamically.
     */
    fun setSwapInterval(drawable: Long, interval: Int) {
        val linker = Linker.nativeLinker()
        try {
            Arena.ofConfined().use { arena ->
                // 1. Try glXSwapIntervalEXT (Display*, GLXDrawable, int)
                val extPtr = glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalEXT")) as MemorySegment
                if (extPtr != MemorySegment.NULL && extPtr.address() != 0L) {
                    val handle = linker.downcallHandle(
                        extPtr,
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
                    )
                    handle.invokeExact(display, drawable, interval)
                    return
                }

                // 2. Try glXSwapIntervalMESA (int) -- process-wide, not per-drawable
                val mesaPtr = glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalMESA")) as MemorySegment
                if (mesaPtr != MemorySegment.NULL && mesaPtr.address() != 0L) {
                    val handle = linker.downcallHandle(
                        mesaPtr,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
                    )
                    val ignored = handle.invokeExact(interval) as Int
                    return
                }

                // 3. Try glXSwapIntervalSGI (int) -- process-wide, not per-drawable
                val sgiPtr = glXGetProcAddress.invokeExact(arena.allocateFrom("glXSwapIntervalSGI")) as MemorySegment
                if (sgiPtr != MemorySegment.NULL && sgiPtr.address() != 0L) {
                    val handle = linker.downcallHandle(
                        sgiPtr,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
                    )
                    val ignored = handle.invokeExact(interval) as Int
                }
            }
        } catch (ignored: Throwable) {
        }
    }

    @Throws(Throwable::class)
    private fun createSharedQuadBuffer() {
        val quadVertices = floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
        Arena.ofConfined().use { arena ->
            val vboBuf = arena.allocate(ValueLayout.JAVA_INT)
            glGenBuffers.invokeExact(1, vboBuf)
            quadVbo = vboBuf.get(ValueLayout.JAVA_INT, 0)

            glBindBuffer.invokeExact(GL_ARRAY_BUFFER, quadVbo)
            val quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, *quadVertices)
            glBufferData.invokeExact(GL_ARRAY_BUFFER, quadVertices.size * 4L, quadData, GL_STATIC_DRAW)
        }
    }

    /** Buffer binding + vertex attrib state belongs to the context, not the shared object namespace.  */
    @Throws(Throwable::class)
    private fun bindQuadAttribs() {
        glBindBuffer.invokeExact(GL_ARRAY_BUFFER, quadVbo)
        glEnableVertexAttribArray.invokeExact(0)
        glVertexAttribPointer.invokeExact(0, 2, GL_FLOAT, 0.toByte(), 0, MemorySegment.NULL)
    }

    @Throws(Throwable::class)
    private fun getProc(linker: Linker, name: String?, desc: FunctionDescriptor?): MethodHandle {
        Arena.ofConfined().use { arena ->
            val ptr = glXGetProcAddress.invokeExact(arena.allocateFrom(name)) as MemorySegment?
            return linker.downcallHandle(ptr, desc)
        }
    }

    fun queryMouse(): MouseInfo {
        try {
            val ok = XQueryPointer.invokeExact(
                display,
                root,
                mouseRootRet,
                mouseChildRet,
                mouseRootX,
                mouseRootY,
                mouseWinX,
                mouseWinY,
                mouseMask
            ) as Int
            if (ok != 0) {
                val rx = mouseRootX.get(ValueLayout.JAVA_INT, 0)
                val ry = mouseRootY.get(ValueLayout.JAVA_INT, 0)
                val m = mouseMask.get(ValueLayout.JAVA_INT, 0)
                val left = (m and MOUSE_BUTTON1_MASK) != 0
                val right = (m and MOUSE_BUTTON3_MASK) != 0
                return MouseInfo(rx.toFloat(), ry.toFloat(), left, right)
            }
        } catch (ignored: Throwable) {
        }
        return MouseInfo(0.0f, 0.0f, false, false)
    }

    fun queryWorkspace(): Int {
        try {
            val status = XGetWindowProperty.invokeExact(
                display,
                root,
                atomCurrentDesktop,
                0L,
                1L,
                0,
                0L,
                workspaceActualType,
                workspaceActualFormat,
                workspaceNitems,
                workspaceBytesAfter,
                workspacePropPtr
            ) as Int

            if (status == 0) {
                val data = workspacePropPtr.get(ValueLayout.ADDRESS, 0)
                if (data != MemorySegment.NULL) {
                    val desktop = data.get(ValueLayout.JAVA_LONG, 0)
                    val ignored = XFree.invokeExact(data) as Int
                    return desktop.toInt()
                }
            }
        } catch (ignored: Throwable) {
        }
        return 0
    }

    private fun lowerWindow(window: Long) {
        try {
            val ignored = XLowerWindow.invokeExact(display, window) as Int
        } catch (ignored: Throwable) {
        }
    }

    /**
     * Re-asserts that every surface's window sits at the bottom of the stack.
     * A single XLowerWindow call at startup isn't enough on desktops where
     * xfdesktop (icons) or panel widgets get (re)mapped/raised after us --
     * they can end up stacked below our windows instead of above them.
     * Called once at startup and then periodically from the render loop.
     */
    fun lowerToBackground() {
        for (window in surfaceWindows) {
            lowerWindow(window)
        }
    }

    @Throws(Throwable::class)
    fun makeCurrent(surface: Surface) {
        val ignored = glXMakeCurrent.invokeExact(display, surface.window, surface.glContext) as Int
    }

    @Throws(Throwable::class)
    fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        glViewport.invokeExact(x, y, width, height)
    }

    @Throws(Throwable::class)
    fun drawQuad() {
        glDrawArrays.invokeExact(GL_TRIANGLE_STRIP, 0, 4)
    }

    // -------------------------------------------------------------------
    // Shader / program lifecycle -- the only GL surface ShaderPipeline
    // needs; it never touches a MethodHandle, Arena, or MemorySegment.
    // -------------------------------------------------------------------
    @Throws(Throwable::class)
    fun createShader(type: Int): Int {
        return glCreateShader.invokeExact(type) as Int
    }

    @Throws(Throwable::class)
    fun shaderSource(shader: Int, source: String?) {
        Arena.ofConfined().use { arena ->
            val ptr = arena.allocateFrom(source)
            val ptrArray = arena.allocateFrom(ValueLayout.ADDRESS, ptr)
            glShaderSource.invokeExact(shader, 1, ptrArray, MemorySegment.NULL)
        }
    }

    @Throws(Throwable::class)
    fun compileShader(shader: Int) {
        glCompileShader.invokeExact(shader)
    }

    @Throws(Throwable::class)
    fun getShaderCompileStatus(shader: Int): Boolean {
        Arena.ofConfined().use { arena ->
            val status = arena.allocate(ValueLayout.JAVA_INT)
            glGetShaderiv.invokeExact(shader, GL_COMPILE_STATUS, status)
            return status.get(ValueLayout.JAVA_INT, 0) == 0
        }
    }

    @Throws(Throwable::class)
    fun getShaderInfoLog(shader: Int): String? {
        return fetchInfoLog(glGetShaderInfoLog, shader)
    }

    @Throws(Throwable::class)
    fun deleteShader(shader: Int) {
        glDeleteShader.invokeExact(shader)
    }

    @Throws(Throwable::class)
    fun createProgram(): Int {
        return glCreateProgram.invokeExact() as Int
    }

    @Throws(Throwable::class)
    fun attachShader(program: Int, shader: Int) {
        glAttachShader.invokeExact(program, shader)
    }

    @Throws(Throwable::class)
    fun linkProgram(program: Int) {
        glLinkProgram.invokeExact(program)
    }

    @Throws(Throwable::class)
    fun getProgramLinkStatus(program: Int): Boolean {
        Arena.ofConfined().use { arena ->
            val status = arena.allocate(ValueLayout.JAVA_INT)
            glGetProgramiv.invokeExact(program, GL_LINK_STATUS, status)
            return status.get(ValueLayout.JAVA_INT, 0) != 0
        }
    }

    @Throws(Throwable::class)
    fun getProgramInfoLog(program: Int): String? {
        return fetchInfoLog(glGetProgramInfoLog, program)
    }

    @Throws(Throwable::class)
    fun deleteProgram(program: Int) {
        glDeleteProgram.invokeExact(program)
    }

    @Throws(Throwable::class)
    fun useProgram(program: Int) {
        glUseProgram.invokeExact(program)
    }

    @Throws(Throwable::class)
    fun getUniformLocation(program: Int, name: String?): Int {
        Arena.ofConfined().use { arena ->
            return glGetUniformLocation.invokeExact(program, arena.allocateFrom(name)) as Int
        }
    }

    /** No-op when location is -1 (uniform not present in this shader),
     * matching GL's own "unused uniform" semantics for the caller.  */
    @Throws(Throwable::class)
    fun uniform1f(location: Int, v: Float) {
        if (location >= 0) glUniform1f.invokeExact(location, v)
    }

    @Throws(Throwable::class)
    fun uniform2f(location: Int, a: Float, b: Float) {
        if (location >= 0) glUniform2f.invokeExact(location, a, b)
    }

    @Throws(Throwable::class)
    fun uniform4f(location: Int, a: Float, b: Float, c: Float, d: Float) {
        if (location >= 0) glUniform4f.invokeExact(location, a, b, c, d)
    }

    @Throws(Throwable::class)
    fun uniform1i(location: Int, v: Int) {
        if (location >= 0) glUniform1i.invokeExact(location, v)
    }

    // -------------------------------------------------------------------
    // Offscreen render targets: the expensive raymarch shader draws into a
    // reduced-resolution texture instead of the real window-sized backbuffer,
    // which is then upscaled with GL_NEAREST filtering (see blit.frag) --
    // cutting actual fragment-shader invocations for the expensive pass by
    // (1/renderScale)^2, which is the dominant cost on weak/integrated GPUs.
    // -------------------------------------------------------------------
    /** One monitor's low-resolution color target: an FBO plus its backing texture.  */
    @JvmRecord
    data class RenderTarget(val framebuffer: Int, val texture: Int, val width: Int, val height: Int)

    @Throws(Throwable::class)
    fun createRenderTarget(width: Int, height: Int): RenderTarget {
        Arena.ofConfined().use { arena ->
            val texBuf = arena.allocate(ValueLayout.JAVA_INT)
            glGenTextures.invokeExact(1, texBuf)
            val texture = texBuf.get(ValueLayout.JAVA_INT, 0)

            glBindTexture.invokeExact(GL_TEXTURE_2D, texture)
            glTexImage2D.invokeExact(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                width,
                height,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                MemorySegment.NULL
            )
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

            val fboBuf = arena.allocate(ValueLayout.JAVA_INT)
            glGenFramebuffers.invokeExact(1, fboBuf)
            val framebuffer = fboBuf.get(ValueLayout.JAVA_INT, 0)

            glBindFramebuffer.invokeExact(GL_FRAMEBUFFER, framebuffer)
            glFramebufferTexture2D.invokeExact(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
            val status = glCheckFramebufferStatus.invokeExact(GL_FRAMEBUFFER) as Int
            check(status == GL_FRAMEBUFFER_COMPLETE) {
                ("Offscreen framebuffer incomplete (status 0x"
                        + Integer.toHexString(status) + ")")
            }
            glBindFramebuffer.invokeExact(GL_FRAMEBUFFER, 0)
            return RenderTarget(framebuffer, texture, width, height)
        }
    }

    /**
     * Uploads tightly-packed RGBA pixels (first row = bottom) as a 2D texture
     * with GL_LINEAR min/mag filters (no mipmaps needed) and clamped edges.
     * Shared across surface contexts.
     */
    @Throws(Throwable::class)
    fun createTextureRGBA(width: Int, height: Int, rgba: ByteArray): Int {
        Arena.ofConfined().use { arena ->
            val texBuf = arena.allocate(ValueLayout.JAVA_INT)
            glGenTextures.invokeExact(1, texBuf)
            val texture = texBuf.get(ValueLayout.JAVA_INT, 0)

            val pixels = arena.allocateFrom(ValueLayout.JAVA_BYTE, *rgba)
            glActiveTexture.invokeExact(GL_TEXTURE0)
            glBindTexture.invokeExact(GL_TEXTURE_2D, texture)
            glPixelStorei.invokeExact(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D.invokeExact(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            return texture
        }
    }

    /**
     * Creates a 1x1 RGBA source texture (GL_LINEAR, no mipmaps, so it is
     * always texture-complete) holding the given colour. It is the pass-1
     * `u_texture` input: binding it to unit 0 before the offscreen draw
     * guarantees unit 0 never still holds the FBO's own colour attachment from
     * the previous frame's blit, which would be an OpenGL feedback loop.
     * Texture objects are shared across the surfaces' contexts.
     */
    @Throws(Throwable::class)
    fun createSourceTexture(r: Int, g: Int, b: Int, a: Int): Int {
        Arena.ofConfined().use { arena ->
            val texBuf = arena.allocate(ValueLayout.JAVA_INT)
            glGenTextures.invokeExact(1, texBuf)
            val texture = texBuf.get(ValueLayout.JAVA_INT, 0)

            val pixel = arena.allocateFrom(
                ValueLayout.JAVA_BYTE,
                r.toByte(), g.toByte(), b.toByte(), a.toByte()
            )
            glBindTexture.invokeExact(GL_TEXTURE_2D, texture)
            glTexImage2D.invokeExact(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri.invokeExact(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            return texture
        }
    }

    @get:Throws(Throwable::class)
    val error: Int
        /** Pops one pending GL error (0 == GL_NO_ERROR).  */
        get() = glGetError.invokeExact() as Int

    @Throws(Throwable::class)
    fun bindFramebuffer(framebuffer: Int) {
        glBindFramebuffer.invokeExact(GL_FRAMEBUFFER, framebuffer)
    }

    /** Reads back the currently-bound framebuffer's pixels as tightly-packed
     * top-to-bottom RGBA (i.e. already row-flipped from GL's bottom-left
     * origin) -- dev-only utility for `lock --dump-frame`, letting a
     * single rendered frame of the locker be inspected as a PNG without a
     * human having to look at the real X11 output.  */
    @Throws(Throwable::class)
    fun readPixelsRGBA(width: Int, height: Int): ByteArray {
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(width.toLong() * height * 4)
            glPixelStorei.invokeExact(GL_PACK_ALIGNMENT, 1)
            glReadPixels.invokeExact(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
            val raw = ByteArray(width * height * 4)
            MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, raw, 0, raw.size)
            val flipped = ByteArray(raw.size)
            val rowBytes = width * 4
            for (y in 0..<height) {
                System.arraycopy(raw, y * rowBytes, flipped, (height - 1 - y) * rowBytes, rowBytes)
            }
            return flipped
        }
    }

    /** Binds TEXTURE to texture unit 0 (the only unit the blit shader uses).  */
    @Throws(Throwable::class)
    fun bindTexture(texture: Int) {
        glActiveTexture.invokeExact(GL_TEXTURE0)
        glBindTexture.invokeExact(GL_TEXTURE_2D, texture)
    }

    /** Binds TEXTURE to an arbitrary texture unit (e.g. 1 for the screen
     * locker's login-dialog texture, keeping unit 0 free -- the raymarch
     * program doesn't otherwise sample anything there, but this leaves
     * headroom instead of assuming that stays true forever).  */
    @Throws(Throwable::class)
    fun bindTextureUnit(unit: Int, texture: Int) {
        glActiveTexture.invokeExact(GL_TEXTURE0 + unit)
        glBindTexture.invokeExact(GL_TEXTURE_2D, texture)
    }

    /** Reads an info log via either glGetShaderInfoLog or glGetProgramInfoLog
     * (both share the (id, maxLength, *length, *infoLog) signature).  */
    @Throws(Throwable::class)
    private fun fetchInfoLog(getInfoLog: MethodHandle, id: Int): String? {
        val maxLength = 4096
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(maxLength.toLong())
            val lengthOut = arena.allocate(ValueLayout.JAVA_INT)
            getInfoLog.invokeExact(id, maxLength, lengthOut, buffer)
            val length = lengthOut.get(ValueLayout.JAVA_INT, 0)
            return if (length > 0) buffer.getString(0) else "(no log)"
        }
    }

    @Throws(Throwable::class)
    fun swapBuffers(surface: Surface) {
        glXSwapBuffers.invokeExact(display, surface.window)
    }

    // -------------------------------------------------------------------
    // Locker-only: an always-on-top, input-grabbing window spanning the
    // whole X screen. Deliberately a separate code path from createSurface()
    // (desktop-background, WM-managed, lowered) -- this window needs the
    // opposite properties (override-redirect so it bypasses the WM/panel
    // stacking entirely and always wins focus+raise, selects key events,
    // and is the target of the keyboard/pointer grabs below).
    // -------------------------------------------------------------------
    private val eventBuf: MemorySegment = persistentArena.allocate(192) // sizeof(XEvent) == pad[24] of long
    private val lookupTextBuf: MemorySegment = persistentArena.allocate(32)
    private val lookupKeysymBuf: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)

    /** Creates (but does not yet map/grab) the full-screen override-redirect
     * lock window, sized to the whole X display (every monitor at once --
     * one continuous lock surface, matching how the wallpaper already treats
     * the display as one panorama).  */
    @Throws(Throwable::class)
    fun createLockWindow(): Surface {
        val window: Long
        Arena.ofConfined().use { arena ->
            val attrs = arena.allocate(112)
            attrs.set(ValueLayout.JAVA_INT, 88, 1) // override_redirect = True
            attrs.set(ValueLayout.JAVA_LONG, 72, KEY_PRESS_MASK or KEY_RELEASE_MASK) // event_mask
            window = XCreateWindow.invokeExact(
                display, root, 0, 0, width, height, 0,
                COPY_FROM_PARENT, COPY_FROM_PARENT, MemorySegment.NULL,
                CW_OVERRIDE_REDIRECT or CW_EVENT_MASK, attrs
            ) as Long
        }
        val glContext: MemorySegment?
        Arena.ofConfined().use { arena ->
            val glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, GLX_RGBA, GLX_DOUBLEBUFFER, 0)
            val visInfo = glXChooseVisual.invokeExact(display, screen, glAttrs) as MemorySegment
            check(visInfo != MemorySegment.NULL) { "glXChooseVisual found no matching GLX visual for the lock window." }
            val shareWith = sharedGlContext ?: MemorySegment.NULL
            glContext = glXCreateContext.invokeExact(display, visInfo, shareWith, 1) as MemorySegment?
            val ignoredCurrent = glXMakeCurrent.invokeExact(display, window, glContext) as Int
            val ignoredFree = XFree.invokeExact(visInfo) as Int
        }
        resolveGlProcsIfNeeded()
        if (sharedGlContext == null) {
            sharedGlContext = glContext
            createSharedQuadBuffer()
            bindQuadAttribs()
        } else {
            bindQuadAttribs()
        }

        val ignoredMap = XMapWindow.invokeExact(display, window) as Int
        val ignoredRaise = XRaiseWindow.invokeExact(display, window) as Int
        val ignoredFocus = XSetInputFocus.invokeExact(display, window, 0,  /* RevertToNone */CURRENT_TIME) as Int
        val ignoredFlush = XFlush.invokeExact(display) as Int

        setSwapInterval(window, 1)
        return Surface(window, glContext)
    }

    /**
     * Grabs the keyboard and pointer exclusively for `window`, retrying
     * for up to ~1s: right at startup the window manager or a just-released
     * grab from the previous session can hold a transient grab that makes
     * the very first XGrabKeyboard/XGrabPointer call return AlreadyGrabbed
     * even though nothing is actually wrong. Returns false (never throws) if
     * the grab still can't be obtained after retrying, so the caller can
     * refuse to treat the screen as locked rather than show a lock UI that
     * input can bypass.
     */
    @Throws(Throwable::class)
    fun grabInputWithRetry(surface: Surface): Boolean {
        for (attempt in 0..19) {
            val kbGrab = XGrabKeyboard.invokeExact(
                display, surface.window, 1,
                GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, CURRENT_TIME
            ) as Int
            if (kbGrab == GRAB_SUCCESS) {
                val ptrGrab = XGrabPointer.invokeExact(
                    display, surface.window, 1,
                    0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, 0L, 0L, CURRENT_TIME
                ) as Int
                if (ptrGrab == GRAB_SUCCESS) {
                    return true
                }
                val ignored = XUngrabKeyboard.invokeExact(display, CURRENT_TIME) as Int
            }
            Thread.sleep(50)
        }
        return false
    }

    @Throws(Throwable::class)
    fun ungrabInput() {
        val ignored1 = XUngrabKeyboard.invokeExact(display, CURRENT_TIME) as Int
        val ignored2 = XUngrabPointer.invokeExact(display, CURRENT_TIME) as Int
    }

    /** Non-blocking: returns null immediately if no key event is queued.
     * Only ever called from the single render thread that owns `display`.  */
    @Throws(Throwable::class)
    fun pollKeyEvent(): KeyEvent? {
        val pending = XPending.invokeExact(display) as Int
        if (pending <= 0) return null

        val ignored = XNextEvent.invokeExact(display, eventBuf) as Int
        val type = eventBuf.get(ValueLayout.JAVA_INT, 0)
        if (type != KEY_PRESS && type != KEY_RELEASE) {
            return pollKeyEvent() // drain non-key events (e.g. stray Expose) without surfacing them
        }

        val len = XLookupString.invokeExact(
            eventBuf, lookupTextBuf, lookupTextBuf.byteSize().toInt(),
            lookupKeysymBuf, MemorySegment.NULL
        ) as Int
        val keysym = lookupKeysymBuf.get(ValueLayout.JAVA_LONG, 0)
        val text = if (len > 0) String(
            lookupTextBuf.toArray(ValueLayout.JAVA_BYTE), 0, len,
            StandardCharsets.ISO_8859_1
        ) else ""
        return KeyEvent(type == KEY_PRESS, keysym, text)
    }

    /** DEV/TEST ONLY: synthesizes a KeyPress event for KEYSYM (must be a
     * directly-mapped printable keysym, e.g. Latin-1 'a'-'z'/'0'-'9', or one
     * of the XK_* control keysyms already in use elsewhere in this class)
     * and pushes it straight into Xlib's local event queue via
     * XPutBackEvent -- the next pollKeyEvent() call will return it exactly
     * as if a real key had been pressed. No X server round-trip, no window
     * focus requirement, and no XTest extension dependency -- used by
     * ScreenLocker's --inject-password test flag to drive the typing/PAM
     * path from an automated test on a machine with no xdotool/XTest tool
     * installed. Never reachable from a real lock session.  */
    @Throws(Throwable::class)
    fun injectTestKeyPress(keysym: Long) {
        Arena.ofConfined().use { arena ->
            val ev = arena.allocate(192)
            val keycode = (XKeysymToKeycode.invokeExact(display, keysym) as Int) and 0xFF
            ev.set(ValueLayout.JAVA_INT, 0, KEY_PRESS) // type
            ev.set(ValueLayout.JAVA_INT, 16, 0) // send_event = False
            ev.set(ValueLayout.JAVA_LONG, 24, display.address()) // display
            ev.set(ValueLayout.JAVA_INT, 80, 0) // state
            ev.set(ValueLayout.JAVA_INT, 84, keycode) // keycode
            val ignored = XPutBackEvent.invokeExact(display, ev) as Int
        }
    }

    fun destroyLockWindow(surface: Surface) {
        try {
            ungrabInput()
            val ignored1 = XUnmapWindow.invokeExact(display, surface.window) as Int
            val ignored2 = XDestroyWindow.invokeExact(display, surface.window) as Int
            val ignored3 = XFlush.invokeExact(display) as Int
        } catch (ignored: Throwable) {
        }
    }

    private val closed = AtomicBoolean(false)

    init {
        display = XOpenDisplay.invokeExact(MemorySegment.NULL) as MemorySegment
        check(display != MemorySegment.NULL) { "Failed to open X11 Display." }

        screen = XDefaultScreen.invokeExact(display) as Int
        root = XRootWindow.invokeExact(display, screen) as Long
        width = XDisplayWidth.invokeExact(display, screen) as Int
        height = XDisplayHeight.invokeExact(display, screen) as Int

        Arena.ofConfined().use { arena ->
            atomWmWindowType = XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE"), 0) as Long
            atomWmWindowTypeDesktop =
                XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE_DESKTOP"), 0) as Long
            atomWmState = XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_STATE"), 0) as Long
            atomWmStateBelow = XInternAtom.invokeExact(display, arena.allocateFrom("_NET_WM_STATE_BELOW"), 0) as Long
            atomTypeAtom = XInternAtom.invokeExact(display, arena.allocateFrom("ATOM"), 0) as Long
            atomCurrentDesktop = XInternAtom.invokeExact(display, arena.allocateFrom("_NET_CURRENT_DESKTOP"), 0) as Long
        }
    }

    /** Idempotent and safe to call from both the normal try-with-resources
     * path and a JVM shutdown hook racing it on SIGINT/SIGTERM -- only the
     * first caller actually tears down the X11 connection and native arena.  */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            val ignored = XCloseDisplay.invokeExact(display) as Int
        } catch (ignored: Throwable) {
        }
        try {
            persistentArena.close()
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        // OpenGL & GLX Constants
        const val GL_VERTEX_SHADER: Int = 0x8B31
        const val GL_FRAGMENT_SHADER: Int = 0x8B30
        const val GL_ARRAY_BUFFER: Int = 0x8892
        const val GL_STATIC_DRAW: Int = 0x88E4
        const val GL_FLOAT: Int = 0x1406
        const val GL_TRIANGLE_STRIP: Int = 0x0005
        const val GL_COMPILE_STATUS: Int = 0x8B81
        const val GL_LINK_STATUS: Int = 0x8B82

        // Offscreen render-target constants (low-res raymarch pass -> nearest-upscale blit)
        const val GL_FRAMEBUFFER: Int = 0x8D40
        const val GL_PACK_ALIGNMENT: Int = 0x0D05
        const val GL_COLOR_ATTACHMENT0: Int = 0x8CE0
        const val GL_FRAMEBUFFER_COMPLETE: Int = 0x8CD5
        const val GL_TEXTURE_2D: Int = 0x0DE1
        const val GL_RGBA: Int = 0x1908
        const val GL_UNSIGNED_BYTE: Int = 0x1401
        const val GL_TEXTURE_MIN_FILTER: Int = 0x2801
        const val GL_TEXTURE_MAG_FILTER: Int = 0x2800
        const val GL_NEAREST: Int = 0x2600
        const val GL_LINEAR: Int = 0x2601
        const val GL_UNPACK_ALIGNMENT: Int = 0x0CF5
        const val GL_TEXTURE_WRAP_S: Int = 0x2802
        const val GL_TEXTURE_WRAP_T: Int = 0x2803
        const val GL_CLAMP_TO_EDGE: Int = 0x812F
        const val GL_TEXTURE0: Int = 0x84C0

        const val GLX_RGBA: Int = 4
        const val GLX_DOUBLEBUFFER: Int = 5

        private const val COPY_FROM_PARENT = 0

        // X11 Mouse button bitmasks (Button 1 = Left, Button 3 = Right)
        val MOUSE_BUTTON1_MASK: Int = 1 shl 8
        val MOUSE_BUTTON3_MASK: Int = 1 shl 10

        // -------------------------------------------------------------------
        // Input-grab / event-loop constants, used only by the screen locker
        // (dev.petrolal.dotfiles.locker.ScreenLocker) -- the desktop-background
        // wallpaper surfaces created by createSurface() never select input or
        // grab anything.
        // -------------------------------------------------------------------
        val CW_EVENT_MASK: Long = 1L shl 11
        val CW_OVERRIDE_REDIRECT: Long = 1L shl 9
        const val KEY_PRESS_MASK: Long = 1L
        val KEY_RELEASE_MASK: Long = 1L shl 1
        const val GRAB_MODE_ASYNC: Int = 1
        const val GRAB_SUCCESS: Int = 0
        const val CURRENT_TIME: Long = 0L
        const val KEY_PRESS: Int = 2
        const val KEY_RELEASE: Int = 3

        // keysymdef.h function-key values this locker cares about.
        const val XK_BACKSPACE: Long = 0xFF08L
        const val XK_RETURN: Long = 0xFF0DL
        const val XK_ESCAPE: Long = 0xFF1BL
        const val XK_KP_ENTER: Long = 0xFF8DL

        // X11 function handles
        private val XOpenDisplay: MethodHandle
        private val XDefaultScreen: MethodHandle
        private val XRootWindow: MethodHandle
        private val XDisplayWidth: MethodHandle
        private val XDisplayHeight: MethodHandle
        private val XCreateWindow: MethodHandle
        private val XMapWindow: MethodHandle
        private val XInternAtom: MethodHandle
        private val XChangeProperty: MethodHandle
        private val XCloseDisplay: MethodHandle
        private val XLowerWindow: MethodHandle
        private val XQueryPointer: MethodHandle
        private val XGetWindowProperty: MethodHandle
        private val XFree: MethodHandle

        // Locker-only: input grab / event loop
        private val XGrabKeyboard: MethodHandle
        private val XUngrabKeyboard: MethodHandle
        private val XGrabPointer: MethodHandle
        private val XUngrabPointer: MethodHandle
        private val XSelectInput: MethodHandle
        private val XPending: MethodHandle
        private val XNextEvent: MethodHandle
        private val XLookupString: MethodHandle
        private val XSetInputFocus: MethodHandle
        private val XRaiseWindow: MethodHandle
        private val XUnmapWindow: MethodHandle
        private val XDestroyWindow: MethodHandle
        private val XFlush: MethodHandle
        private val XKeysymToKeycode: MethodHandle
        private val XPutBackEvent: MethodHandle

        // GLX function handles
        private val glXChooseVisual: MethodHandle
        private val glXCreateContext: MethodHandle
        private val glXMakeCurrent: MethodHandle
        private val glXSwapBuffers: MethodHandle
        private val glXGetProcAddress: MethodHandle

        private fun loadLibrary(libName: String, arena: Arena): SymbolLookup {
            try {
                return SymbolLookup.libraryLookup(libName, arena)
            } catch (e: IllegalArgumentException) {
                val fallbacks = arrayOf<String?>(
                    "/run/current-system/sw/lib",
                    "/usr/lib",
                    "/usr/local/lib"
                )
                for (dir in fallbacks) {
                    val p = Path.of(dir, libName)
                    if (Files.exists(p)) {
                        return SymbolLookup.libraryLookup(p, arena)
                    }
                }
                throw e
            }
        }

        /** Bare Optional.get() throws an unhelpful "No value present" with no clue
         * which symbol was missing; this gives a real error message instead, and
         * is what every static-init symbol lookup below goes through so none of
         * them trip an Optional.get()-without-isPresent() warning.  */
        private fun findOrThrow(lookup: SymbolLookup, symbolName: String?): MemorySegment {
            return lookup.find(symbolName).orElseThrow<UnsatisfiedLinkError?>(
                Supplier { UnsatisfiedLinkError("Native symbol not found: " + symbolName) })
        }

        init {
            val linker = Linker.nativeLinker()
            val x11: SymbolLookup = loadLibrary("libX11.so", Arena.global())
            val gl: SymbolLookup = loadLibrary("libGL.so", Arena.global())

            // Bind X11
            XOpenDisplay = linker.downcallHandle(
                findOrThrow(x11, "XOpenDisplay"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            )
            XDefaultScreen = linker.downcallHandle(
                findOrThrow(x11, "XDefaultScreen"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            )
            XRootWindow = linker.downcallHandle(
                findOrThrow(x11, "XRootWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            )
            XDisplayWidth = linker.downcallHandle(
                findOrThrow(x11, "XDisplayWidth"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            )
            XDisplayHeight = linker.downcallHandle(
                findOrThrow(x11, "XDisplayHeight"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            )
            // XCreateWindow, not XCreateSimpleWindow: only the full version lets us
            // request override-redirect (see createSurface for why that matters).
            XCreateWindow = linker.downcallHandle(
                findOrThrow(x11, "XCreateWindow"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
                )
            )
            XMapWindow = linker.downcallHandle(
                findOrThrow(x11, "XMapWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            XInternAtom = linker.downcallHandle(
                findOrThrow(x11, "XInternAtom"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                )
            )
            XChangeProperty = linker.downcallHandle(
                findOrThrow(x11, "XChangeProperty"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT
                )
            )
            XCloseDisplay = linker.downcallHandle(
                findOrThrow(x11, "XCloseDisplay"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            )
            XLowerWindow = linker.downcallHandle(
                findOrThrow(x11, "XLowerWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )

            XQueryPointer = linker.downcallHandle(
                findOrThrow(x11, "XQueryPointer"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                )
            )
            XGetWindowProperty = linker.downcallHandle(
                findOrThrow(x11, "XGetWindowProperty"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                )
            )
            XFree = linker.downcallHandle(
                findOrThrow(x11, "XFree"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            )

            // int XGrabKeyboard(Display*, Window, Bool, int, int, Time);
            XGrabKeyboard = linker.downcallHandle(
                findOrThrow(x11, "XGrabKeyboard"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG
                )
            )
            // int XUngrabKeyboard(Display*, Time);
            XUngrabKeyboard = linker.downcallHandle(
                findOrThrow(x11, "XUngrabKeyboard"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            // int XGrabPointer(Display*, Window, Bool, unsigned int, int, int, Window, Cursor, Time);
            XGrabPointer = linker.downcallHandle(
                findOrThrow(x11, "XGrabPointer"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG
                )
            )
            // int XUngrabPointer(Display*, Time);
            XUngrabPointer = linker.downcallHandle(
                findOrThrow(x11, "XUngrabPointer"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            // int XSelectInput(Display*, Window, long);
            XSelectInput = linker.downcallHandle(
                findOrThrow(x11, "XSelectInput"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG
                )
            )
            // int XPending(Display*); -- never blocks, just drains the local queue + a non-blocking socket check
            XPending = linker.downcallHandle(
                findOrThrow(x11, "XPending"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            )
            // int XNextEvent(Display*, XEvent*); -- only called when XPending() > 0, so never actually blocks here
            XNextEvent = linker.downcallHandle(
                findOrThrow(x11, "XNextEvent"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            )
            // int XLookupString(XKeyEvent*, char*, int, KeySym*, XComposeStatus*);
            XLookupString = linker.downcallHandle(
                findOrThrow(x11, "XLookupString"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            )
            // int XSetInputFocus(Display*, Window, int revert_to, Time);
            XSetInputFocus = linker.downcallHandle(
                findOrThrow(x11, "XSetInputFocus"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG
                )
            )
            XRaiseWindow = linker.downcallHandle(
                findOrThrow(x11, "XRaiseWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            XUnmapWindow = linker.downcallHandle(
                findOrThrow(x11, "XUnmapWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            XDestroyWindow = linker.downcallHandle(
                findOrThrow(x11, "XDestroyWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            XFlush = linker.downcallHandle(
                findOrThrow(x11, "XFlush"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            )
            // KeyCode XKeysymToKeycode(Display*, KeySym) -- KeyCode is really an
            // unsigned char, but is declared JAVA_INT here and masked to a byte
            // at the call site, matching how this project's other narrow-return
            // Xlib calls are bound (see e.g. getError()'s GLenum handling).
            XKeysymToKeycode = linker.downcallHandle(
                findOrThrow(x11, "XKeysymToKeycode"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            // int XPutBackEvent(Display*, XEvent*) -- pushes a synthetic event
            // straight into Xlib's local queue without any server round-trip;
            // used only by injectTestKeyPress() below to drive the locker's
            // typing/PAM path from an automated test, without a real keyboard
            // or an XTest-capable tool like xdotool (neither is assumed present
            // on a minimal NixOS install).
            XPutBackEvent = linker.downcallHandle(
                findOrThrow(x11, "XPutBackEvent"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            )

            // Bind GLX
            glXChooseVisual = linker.downcallHandle(
                findOrThrow(gl, "glXChooseVisual"), FunctionDescriptor
                    .of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            )
            glXCreateContext = linker.downcallHandle(
                findOrThrow(gl, "glXCreateContext"),
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT
                )
            )
            glXMakeCurrent = linker.downcallHandle(
                findOrThrow(gl, "glXMakeCurrent"), FunctionDescriptor
                    .of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            )
            glXSwapBuffers = linker.downcallHandle(
                findOrThrow(gl, "glXSwapBuffers"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
            )
            glXGetProcAddress = linker.downcallHandle(
                findOrThrow(gl, "glXGetProcAddress"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            )
        }
    }
}
