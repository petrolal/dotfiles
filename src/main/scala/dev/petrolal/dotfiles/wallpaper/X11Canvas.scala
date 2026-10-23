package dev.petrolal.dotfiles.wallpaper

import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ListBuffer
import scala.util.Using

/**
 * X11Canvas manages the X11 display connection and, per monitor, an
 * independent desktop-background window + OpenGL context (see
 * [[createSurface]]). Each surface gets its own vsync'd `glXSwapBuffers`
 * call, so every monitor's presentation is paced by its own hardware vblank --
 * no shared buffer swap, no assumption that two monitors share a refresh rate,
 * and no hardcoded target FPS anywhere.
 * Provides query methods for global mouse and workspace state.
 */
class X11Canvas extends AutoCloseable:
  import X11Canvas.*

  private val display: MemorySegment =
    val d = XOpenDisplay.invoke(MemorySegment.NULL).asInstanceOf[MemorySegment]
    if d.equals(MemorySegment.NULL) then
      throw new IllegalStateException("Failed to open X11 Display.")
    d

  private val screen: Int = XDefaultScreen.invoke(display).asInstanceOf[Int]
  private val root: Long = XRootWindow.invoke(display, screen).asInstanceOf[Long]
  private val width: Int = XDisplayWidth.invoke(display, screen).asInstanceOf[Int]
  private val height: Int = XDisplayHeight.invoke(display, screen).asInstanceOf[Int]

  private val atomWmWindowType: Long =
    Using.resource(Arena.ofConfined()) { arena =>
      XInternAtom.invoke(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE"), 0).asInstanceOf[Long]
    }
  private val atomWmWindowTypeDesktop: Long =
    Using.resource(Arena.ofConfined()) { arena =>
      XInternAtom.invoke(display, arena.allocateFrom("_NET_WM_WINDOW_TYPE_DESKTOP"), 0).asInstanceOf[Long]
    }
  private val atomWmState: Long =
    Using.resource(Arena.ofConfined()) { arena =>
      XInternAtom.invoke(display, arena.allocateFrom("_NET_WM_STATE"), 0).asInstanceOf[Long]
    }
  private val atomWmStateBelow: Long =
    Using.resource(Arena.ofConfined()) { arena =>
      XInternAtom.invoke(display, arena.allocateFrom("_NET_WM_STATE_BELOW"), 0).asInstanceOf[Long]
    }
  private val atomTypeAtom: Long =
    Using.resource(Arena.ofConfined()) { arena =>
      XInternAtom.invoke(display, arena.allocateFrom("ATOM"), 0).asInstanceOf[Long]
    }
  private val atomCurrentDesktop: Long =
    Using.resource(Arena.ofConfined()) { arena =>
      XInternAtom.invoke(display, arena.allocateFrom("_NET_CURRENT_DESKTOP"), 0).asInstanceOf[Long]
    }

  private val surfaceWindows = ListBuffer[Long]()
  private var sharedGlContext: MemorySegment = null
  private var quadVbo: Int = -1

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

  // Direct OpenGL function handles resolved in resolveGlProcsIfNeeded()
  private var glCreateShader: MethodHandle = null
  private var glShaderSource: MethodHandle = null
  private var glCompileShader: MethodHandle = null
  private var glGetShaderiv: MethodHandle = null
  private var glGetShaderInfoLog: MethodHandle = null
  private var glCreateProgram: MethodHandle = null
  private var glAttachShader: MethodHandle = null
  private var glLinkProgram: MethodHandle = null
  private var glGetProgramiv: MethodHandle = null
  private var glGetProgramInfoLog: MethodHandle = null
  private var glUseProgram: MethodHandle = null
  private var glDeleteShader: MethodHandle = null
  private var glDeleteProgram: MethodHandle = null
  private var glGetUniformLocation: MethodHandle = null
  private var glUniform1f: MethodHandle = null
  private var glUniform2f: MethodHandle = null
  private var glUniform4f: MethodHandle = null
  private var glViewport: MethodHandle = null
  private var glGenBuffers: MethodHandle = null
  private var glBindBuffer: MethodHandle = null
  private var glBufferData: MethodHandle = null
  private var glEnableVertexAttribArray: MethodHandle = null
  private var glVertexAttribPointer: MethodHandle = null
  private var glDrawArrays: MethodHandle = null
  private var glGenFramebuffers: MethodHandle = null
  private var glBindFramebuffer: MethodHandle = null
  private var glFramebufferTexture2D: MethodHandle = null
  private var glCheckFramebufferStatus: MethodHandle = null
  private var glGenTextures: MethodHandle = null
  private var glBindTexture: MethodHandle = null
  private var glTexImage2D: MethodHandle = null
  private var glTexParameteri: MethodHandle = null
  private var glActiveTexture: MethodHandle = null
  private var glUniform1i: MethodHandle = null
  private var glGetError: MethodHandle = null
  private var glPixelStorei: MethodHandle = null
  private var glReadPixels: MethodHandle = null

  private var glProcsResolved: Boolean = false

  def getWidth: Int = width
  def getHeight: Int = height

  def initGL(x: Int, y: Int, w: Int, h: Int): Surface =
    val first = createSurface(x, y, w, h)
    resolveGlProcsIfNeeded()
    createSharedQuadBuffer()
    bindQuadAttribs()
    first

  private def resolveGlProcsIfNeeded(): Unit =
    if glProcsResolved then return
    glProcsResolved = true

    val linker = Linker.nativeLinker()
    glCreateShader = getProc(linker, "glCreateShader", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glShaderSource = getProc(linker, "glShaderSource", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
    glCompileShader = getProc(linker, "glCompileShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glGetShaderiv = getProc(linker, "glGetShaderiv", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glGetShaderInfoLog = getProc(linker, "glGetShaderInfoLog", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
    glCreateProgram = getProc(linker, "glCreateProgram", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    glAttachShader = getProc(linker, "glAttachShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glLinkProgram = getProc(linker, "glLinkProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glGetProgramiv = getProc(linker, "glGetProgramiv", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glGetProgramInfoLog = getProc(linker, "glGetProgramInfoLog", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
    glUseProgram = getProc(linker, "glUseProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glDeleteShader = getProc(linker, "glDeleteShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glDeleteProgram = getProc(linker, "glDeleteProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glGetUniformLocation = getProc(linker, "glGetUniformLocation", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glUniform1f = getProc(linker, "glUniform1f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT))
    glUniform2f = getProc(linker, "glUniform2f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT))
    glUniform4f = getProc(linker, "glUniform4f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT))
    glViewport = getProc(linker, "glViewport", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glGenBuffers = getProc(linker, "glGenBuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glBindBuffer = getProc(linker, "glBindBuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glBufferData = getProc(linker, "glBufferData", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
    glEnableVertexAttribArray = getProc(linker, "glEnableVertexAttribArray", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glVertexAttribPointer = getProc(linker, "glVertexAttribPointer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glDrawArrays = getProc(linker, "glDrawArrays", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glGenFramebuffers = getProc(linker, "glGenFramebuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glBindFramebuffer = getProc(linker, "glBindFramebuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glFramebufferTexture2D = getProc(linker, "glFramebufferTexture2D", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glCheckFramebufferStatus = getProc(linker, "glCheckFramebufferStatus", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glGenTextures = getProc(linker, "glGenTextures", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glBindTexture = getProc(linker, "glBindTexture", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glTexImage2D = getProc(linker, "glTexImage2D", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    glTexParameteri = getProc(linker, "glTexParameteri", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glActiveTexture = getProc(linker, "glActiveTexture", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    glUniform1i = getProc(linker, "glUniform1i", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glGetError = getProc(linker, "glGetError", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    glPixelStorei = getProc(linker, "glPixelStorei", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    glReadPixels = getProc(linker, "glReadPixels", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

  def createSurface(x: Int, y: Int, w: Int, h: Int): Surface =
    createSurface(x, y, w, h, if sharedGlContext == null then 1 else 0)

  def createSurface(x: Int, y: Int, w: Int, h: Int, swapInterval: Int): Surface =
    val window = XCreateWindow.invoke(
      display, root, x, y, w, h, 0,
      COPY_FROM_PARENT, COPY_FROM_PARENT, MemorySegment.NULL, 0L, MemorySegment.NULL
    ).asInstanceOf[Long]

    Using.resource(Arena.ofConfined()) { arena =>
      val propType = arena.allocateFrom(ValueLayout.JAVA_LONG, atomWmWindowTypeDesktop)
      val _ = XChangeProperty.invoke(display, window, atomWmWindowType, atomTypeAtom, 32, 0, propType, 1).asInstanceOf[Int]

      val propBelow = arena.allocateFrom(ValueLayout.JAVA_LONG, atomWmStateBelow)
      val _ = XChangeProperty.invoke(display, window, atomWmState, atomTypeAtom, 32, 0, propBelow, 1).asInstanceOf[Int]
    }

    val _ = XMapWindow.invoke(display, window).asInstanceOf[Int]
    surfaceWindows += window
    lowerWindow(window)

    val glContext = Using.resource(Arena.ofConfined()) { arena =>
      val glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, GLX_RGBA, GLX_DOUBLEBUFFER, 0)
      val visInfo = glXChooseVisual.invoke(display, screen, glAttrs).asInstanceOf[MemorySegment]
      if visInfo.equals(MemorySegment.NULL) then
        throw new IllegalStateException(
          "glXChooseVisual found no matching GLX visual (RGBA, double-buffered) for this X11 screen."
        )
      val shareWith = if sharedGlContext == null then MemorySegment.NULL else sharedGlContext
      val ctx = glXCreateContext.invoke(display, visInfo, shareWith, 1).asInstanceOf[MemorySegment]
      val _ = glXMakeCurrent.invoke(display, window, ctx).asInstanceOf[Int]
      val _ = XFree.invoke(visInfo).asInstanceOf[Int]
      ctx
    }

    if sharedGlContext == null then
      sharedGlContext = glContext
    else
      bindQuadAttribs()

    setSwapInterval(window, swapInterval)
    Surface(window, glContext)

  def setSwapInterval(drawable: Long, interval: Int): Unit =
    val linker = Linker.nativeLinker()
    try
      Using.resource(Arena.ofConfined()) { arena =>
        val extPtr = glXGetProcAddress.invoke(arena.allocateFrom("glXSwapIntervalEXT")).asInstanceOf[MemorySegment]
        if !extPtr.equals(MemorySegment.NULL) && extPtr.address() != 0 then
          val handle = linker.downcallHandle(
            extPtr,
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
          )
          handle.invoke(display, drawable, interval).asInstanceOf[Unit]
        else
          val mesaPtr = glXGetProcAddress.invoke(arena.allocateFrom("glXSwapIntervalMESA")).asInstanceOf[MemorySegment]
          if !mesaPtr.equals(MemorySegment.NULL) && mesaPtr.address() != 0 then
            val handle = linker.downcallHandle(
              mesaPtr,
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            )
            val _ = handle.invoke(interval).asInstanceOf[Int]
          else
            val sgiPtr = glXGetProcAddress.invoke(arena.allocateFrom("glXSwapIntervalSGI")).asInstanceOf[MemorySegment]
            if !sgiPtr.equals(MemorySegment.NULL) && sgiPtr.address() != 0 then
              val handle = linker.downcallHandle(
                sgiPtr,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
              )
              val _ = handle.invoke(interval).asInstanceOf[Int]
      }
    catch
      case _: Throwable => ()

  private def createSharedQuadBuffer(): Unit =
    val quadVertices = Array[Float](-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
    Using.resource(Arena.ofConfined()) { arena =>
      val vboBuf = arena.allocate(ValueLayout.JAVA_INT)
      glGenBuffers.invoke(1, vboBuf).asInstanceOf[Unit]
      quadVbo = vboBuf.get(ValueLayout.JAVA_INT, 0)

      glBindBuffer.invoke(GL_ARRAY_BUFFER, quadVbo).asInstanceOf[Unit]
      val quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, quadVertices*)
      glBufferData.invoke(GL_ARRAY_BUFFER, quadVertices.length * 4L, quadData, GL_STATIC_DRAW).asInstanceOf[Unit]
    }

  private def bindQuadAttribs(): Unit =
    glBindBuffer.invoke(GL_ARRAY_BUFFER, quadVbo).asInstanceOf[Unit]
    glEnableVertexAttribArray.invoke(0).asInstanceOf[Unit]
    glVertexAttribPointer.invoke(0, 2, GL_FLOAT, 0.toByte, 0, MemorySegment.NULL).asInstanceOf[Unit]

  private def getProc(linker: Linker, name: String, desc: FunctionDescriptor): MethodHandle =
    Using.resource(Arena.ofConfined()) { arena =>
      val ptr = glXGetProcAddress.invoke(arena.allocateFrom(name)).asInstanceOf[MemorySegment]
      linker.downcallHandle(ptr, desc)
    }

  def queryMouse(): MouseInfo =
    try
      val ok = XQueryPointer.invoke(
        display, root, mouseRootRet, mouseChildRet, mouseRootX, mouseRootY, mouseWinX, mouseWinY, mouseMask
      ).asInstanceOf[Int]
      if ok != 0 then
        val rx = mouseRootX.get(ValueLayout.JAVA_INT, 0)
        val ry = mouseRootY.get(ValueLayout.JAVA_INT, 0)
        val m = mouseMask.get(ValueLayout.JAVA_INT, 0)
        val left = (m & MOUSE_BUTTON1_MASK) != 0
        val right = (m & MOUSE_BUTTON3_MASK) != 0
        return MouseInfo(rx.toFloat, ry.toFloat, left, right)
    catch
      case _: Throwable => ()
    MouseInfo(0.0f, 0.0f, false, false)

  def queryWorkspace(): Int =
    try
      val status = XGetWindowProperty.invoke(
        display, root, atomCurrentDesktop,
        0L, 1L, 0, 0L, workspaceActualType, workspaceActualFormat, workspaceNitems, workspaceBytesAfter, workspacePropPtr
      ).asInstanceOf[Int]

      if status == 0 then
        val data = workspacePropPtr.get(ValueLayout.ADDRESS, 0)
        if !data.equals(MemorySegment.NULL) then
          val desktop = data.get(ValueLayout.JAVA_LONG, 0)
          val _ = XFree.invoke(data).asInstanceOf[Int]
          return desktop.toInt
    catch
      case _: Throwable => ()
    0

  private def lowerWindow(window: Long): Unit =
    try
      val _ = XLowerWindow.invoke(display, window).asInstanceOf[Int]
    catch
      case _: Throwable => ()

  def lowerToBackground(): Unit =
    for window <- surfaceWindows do
      lowerWindow(window)

  def makeCurrent(surface: Surface): Unit =
    val _ = glXMakeCurrent.invoke(display, surface.window, surface.glContext).asInstanceOf[Int]

  def setViewport(x: Int, y: Int, width: Int, height: Int): Unit =
    glViewport.invoke(x, y, width, height).asInstanceOf[Unit]

  def drawQuad(): Unit =
    glDrawArrays.invoke(GL_TRIANGLE_STRIP, 0, 4).asInstanceOf[Unit]

  def createShader(`type`: Int): Int =
    glCreateShader.invoke(`type`).asInstanceOf[Int]

  def shaderSource(shader: Int, source: String): Unit =
    Using.resource(Arena.ofConfined()) { arena =>
      val ptr = arena.allocateFrom(source)
      val ptrArray = arena.allocateFrom(ValueLayout.ADDRESS, ptr)
      glShaderSource.invoke(shader, 1, ptrArray, MemorySegment.NULL).asInstanceOf[Unit]
    }

  def compileShader(shader: Int): Unit =
    glCompileShader.invoke(shader).asInstanceOf[Unit]

  def getShaderCompileStatus(shader: Int): Boolean =
    Using.resource(Arena.ofConfined()) { arena =>
      val status = arena.allocate(ValueLayout.JAVA_INT)
      glGetShaderiv.invoke(shader, GL_COMPILE_STATUS, status).asInstanceOf[Unit]
      status.get(ValueLayout.JAVA_INT, 0) == 0
    }

  def getShaderInfoLog(shader: Int): String =
    fetchInfoLog(glGetShaderInfoLog, shader)

  def deleteShader(shader: Int): Unit =
    glDeleteShader.invoke(shader).asInstanceOf[Unit]

  def createProgram(): Int =
    glCreateProgram.invoke().asInstanceOf[Int]

  def attachShader(program: Int, shader: Int): Unit =
    glAttachShader.invoke(program, shader).asInstanceOf[Unit]

  def linkProgram(program: Int): Unit =
    glLinkProgram.invoke(program).asInstanceOf[Unit]

  def getProgramLinkStatus(program: Int): Boolean =
    Using.resource(Arena.ofConfined()) { arena =>
      val status = arena.allocate(ValueLayout.JAVA_INT)
      glGetProgramiv.invoke(program, GL_LINK_STATUS, status).asInstanceOf[Unit]
      status.get(ValueLayout.JAVA_INT, 0) != 0
    }

  def getProgramInfoLog(program: Int): String =
    fetchInfoLog(glGetProgramInfoLog, program)

  def deleteProgram(program: Int): Unit =
    glDeleteProgram.invoke(program).asInstanceOf[Unit]

  def useProgram(program: Int): Unit =
    glUseProgram.invoke(program).asInstanceOf[Unit]

  def getUniformLocation(program: Int, name: String): Int =
    Using.resource(Arena.ofConfined()) { arena =>
      glGetUniformLocation.invoke(program, arena.allocateFrom(name)).asInstanceOf[Int]
    }

  def uniform1f(location: Int, v: Float): Unit =
    if location >= 0 then glUniform1f.invoke(location, v).asInstanceOf[Unit]

  def uniform2f(location: Int, a: Float, b: Float): Unit =
    if location >= 0 then glUniform2f.invoke(location, a, b).asInstanceOf[Unit]

  def uniform4f(location: Int, a: Float, b: Float, c: Float, d: Float): Unit =
    if location >= 0 then glUniform4f.invoke(location, a, b, c, d).asInstanceOf[Unit]

  def uniform1i(location: Int, v: Int): Unit =
    if location >= 0 then glUniform1i.invoke(location, v).asInstanceOf[Unit]

  def createRenderTarget(width: Int, height: Int): RenderTarget =
    Using.resource(Arena.ofConfined()) { arena =>
      val texBuf = arena.allocate(ValueLayout.JAVA_INT)
      glGenTextures.invoke(1, texBuf).asInstanceOf[Unit]
      val texture = texBuf.get(ValueLayout.JAVA_INT, 0)

      glBindTexture.invoke(GL_TEXTURE_2D, texture).asInstanceOf[Unit]
      glTexImage2D.invoke(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemorySegment.NULL).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE).asInstanceOf[Unit]

      val fboBuf = arena.allocate(ValueLayout.JAVA_INT)
      glGenFramebuffers.invoke(1, fboBuf).asInstanceOf[Unit]
      val framebuffer = fboBuf.get(ValueLayout.JAVA_INT, 0)

      glBindFramebuffer.invoke(GL_FRAMEBUFFER, framebuffer).asInstanceOf[Unit]
      glFramebufferTexture2D.invoke(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0).asInstanceOf[Unit]
      val status = glCheckFramebufferStatus.invoke(GL_FRAMEBUFFER).asInstanceOf[Int]
      if status != GL_FRAMEBUFFER_COMPLETE then
        throw new IllegalStateException(
          s"Offscreen framebuffer incomplete (status 0x${Integer.toHexString(status)})"
        )
      glBindFramebuffer.invoke(GL_FRAMEBUFFER, 0).asInstanceOf[Unit]

      RenderTarget(framebuffer, texture, width, height)
    }

  def createTextureRGBA(width: Int, height: Int, rgba: Array[Byte]): Int =
    Using.resource(Arena.ofConfined()) { arena =>
      val texBuf = arena.allocate(ValueLayout.JAVA_INT)
      glGenTextures.invoke(1, texBuf).asInstanceOf[Unit]
      val texture = texBuf.get(ValueLayout.JAVA_INT, 0)

      val pixels = arena.allocateFrom(ValueLayout.JAVA_BYTE, rgba*)
      glActiveTexture.invoke(GL_TEXTURE0).asInstanceOf[Unit]
      glBindTexture.invoke(GL_TEXTURE_2D, texture).asInstanceOf[Unit]
      glPixelStorei.invoke(GL_UNPACK_ALIGNMENT, 1).asInstanceOf[Unit]
      glTexImage2D.invoke(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE).asInstanceOf[Unit]
      texture
    }

  def createSourceTexture(r: Int, g: Int, b: Int, a: Int): Int =
    Using.resource(Arena.ofConfined()) { arena =>
      val texBuf = arena.allocate(ValueLayout.JAVA_INT)
      glGenTextures.invoke(1, texBuf).asInstanceOf[Unit]
      val texture = texBuf.get(ValueLayout.JAVA_INT, 0)

      val pixel = arena.allocateFrom(ValueLayout.JAVA_BYTE, r.toByte, g.toByte, b.toByte, a.toByte)
      glBindTexture.invoke(GL_TEXTURE_2D, texture).asInstanceOf[Unit]
      glTexImage2D.invoke(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE).asInstanceOf[Unit]
      glTexParameteri.invoke(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE).asInstanceOf[Unit]
      texture
    }

  def getError(): Int =
    glGetError.invoke().asInstanceOf[Int]

  def bindFramebuffer(framebuffer: Int): Unit =
    glBindFramebuffer.invoke(GL_FRAMEBUFFER, framebuffer).asInstanceOf[Unit]

  def readPixelsRGBA(width: Int, height: Int): Array[Byte] =
    Using.resource(Arena.ofConfined()) { arena =>
      val buffer = arena.allocate(width.toLong * height * 4)
      glPixelStorei.invoke(GL_PACK_ALIGNMENT, 1).asInstanceOf[Unit]
      glReadPixels.invoke(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer).asInstanceOf[Unit]
      val raw = new Array[Byte](width * height * 4)
      MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, raw, 0, raw.length)
      val flipped = new Array[Byte](raw.length)
      val rowBytes = width * 4
      var y = 0
      while y < height do
        System.arraycopy(raw, y * rowBytes, flipped, (height - 1 - y) * rowBytes, rowBytes)
        y += 1
      flipped
    }

  def bindTexture(texture: Int): Unit =
    glActiveTexture.invoke(GL_TEXTURE0).asInstanceOf[Unit]
    glBindTexture.invoke(GL_TEXTURE_2D, texture).asInstanceOf[Unit]

  def bindTextureUnit(unit: Int, texture: Int): Unit =
    glActiveTexture.invoke(GL_TEXTURE0 + unit).asInstanceOf[Unit]
    glBindTexture.invoke(GL_TEXTURE_2D, texture).asInstanceOf[Unit]

  private def fetchInfoLog(getInfoLog: MethodHandle, id: Int): String =
    val maxLength = 4096
    Using.resource(Arena.ofConfined()) { arena =>
      val buffer = arena.allocate(maxLength)
      val lengthOut = arena.allocate(ValueLayout.JAVA_INT)
      getInfoLog.invoke(id, maxLength, lengthOut, buffer).asInstanceOf[Unit]
      val length = lengthOut.get(ValueLayout.JAVA_INT, 0)
      if length > 0 then buffer.getString(0) else "(no log)"
    }

  def swapBuffers(surface: Surface): Unit =
    glXSwapBuffers.invoke(display, surface.window).asInstanceOf[Unit]

  // Locker-only members
  private val eventBuf: MemorySegment = persistentArena.allocate(192)
  private val lookupTextBuf: MemorySegment = persistentArena.allocate(32)
  private val lookupKeysymBuf: MemorySegment = persistentArena.allocate(ValueLayout.JAVA_LONG)

  def createLockWindow(): Surface =
    val window = Using.resource(Arena.ofConfined()) { arena =>
      val attrs = arena.allocate(112)
      attrs.set(ValueLayout.JAVA_INT, 88, 1) // override_redirect = True
      attrs.set(ValueLayout.JAVA_LONG, 72, KEY_PRESS_MASK | KEY_RELEASE_MASK) // event_mask

      XCreateWindow.invoke(
        display, root, 0, 0, width, height, 0,
        COPY_FROM_PARENT, COPY_FROM_PARENT, MemorySegment.NULL,
        CW_OVERRIDE_REDIRECT | CW_EVENT_MASK, attrs
      ).asInstanceOf[Long]
    }

    val glContext = Using.resource(Arena.ofConfined()) { arena =>
      val glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, GLX_RGBA, GLX_DOUBLEBUFFER, 0)
      val visInfo = glXChooseVisual.invoke(display, screen, glAttrs).asInstanceOf[MemorySegment]
      if visInfo.equals(MemorySegment.NULL) then
        throw new IllegalStateException("glXChooseVisual found no matching GLX visual for the lock window.")
      val shareWith = if sharedGlContext == null then MemorySegment.NULL else sharedGlContext
      val ctx = glXCreateContext.invoke(display, visInfo, shareWith, 1).asInstanceOf[MemorySegment]
      val _ = glXMakeCurrent.invoke(display, window, ctx).asInstanceOf[Int]
      val _ = XFree.invoke(visInfo).asInstanceOf[Int]
      ctx
    }

    resolveGlProcsIfNeeded()
    if sharedGlContext == null then
      sharedGlContext = glContext
      createSharedQuadBuffer()
      bindQuadAttribs()
    else
      bindQuadAttribs()

    val _ = XMapWindow.invoke(display, window).asInstanceOf[Int]
    val _ = XRaiseWindow.invoke(display, window).asInstanceOf[Int]
    val _ = XSetInputFocus.invoke(display, window, 0, CURRENT_TIME).asInstanceOf[Int]
    val _ = XFlush.invoke(display).asInstanceOf[Int]

    setSwapInterval(window, 1)
    Surface(window, glContext)

  def grabInputWithRetry(surface: Surface): Boolean =
    var attempt = 0
    var grabbed = false
    while attempt < 20 && !grabbed do
      val kbGrab = XGrabKeyboard.invoke(
        display, surface.window, 1, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, CURRENT_TIME
      ).asInstanceOf[Int]
      if kbGrab == GRAB_SUCCESS then
        val ptrGrab = XGrabPointer.invoke(
          display, surface.window, 1, 0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, 0L, 0L, CURRENT_TIME
        ).asInstanceOf[Int]
        if ptrGrab == GRAB_SUCCESS then
          grabbed = true
        else
          val _ = XUngrabKeyboard.invoke(display, CURRENT_TIME).asInstanceOf[Int]
      if !grabbed then
        Thread.sleep(50)
        attempt += 1
    grabbed

  def ungrabInput(): Unit =
    val _ = XUngrabKeyboard.invoke(display, CURRENT_TIME).asInstanceOf[Int]
    val _ = XUngrabPointer.invoke(display, CURRENT_TIME).asInstanceOf[Int]

  def pollKeyEvent(): KeyEvent =
    val pending = XPending.invoke(display).asInstanceOf[Int]
    if pending <= 0 then return null

    val _ = XNextEvent.invoke(display, eventBuf).asInstanceOf[Int]
    val `type` = eventBuf.get(ValueLayout.JAVA_INT, 0)
    if `type` != KEY_PRESS && `type` != KEY_RELEASE then
      return pollKeyEvent()

    val len = XLookupString.invoke(
      eventBuf, lookupTextBuf, lookupTextBuf.byteSize().toInt, lookupKeysymBuf, MemorySegment.NULL
    ).asInstanceOf[Int]
    val keysym = lookupKeysymBuf.get(ValueLayout.JAVA_LONG, 0)
    val text =
      if len > 0 then
        new String(lookupTextBuf.toArray(ValueLayout.JAVA_BYTE), 0, len, java.nio.charset.StandardCharsets.ISO_8859_1)
      else ""
    KeyEvent(`type` == KEY_PRESS, keysym, text)

  def injectTestKeyPress(keysym: Long): Unit =
    Using.resource(Arena.ofConfined()) { arena =>
      val ev = arena.allocate(192)
      val rawKeycode: Int = XKeysymToKeycode.invoke(display, keysym).asInstanceOf[Int]
      val keycode = rawKeycode & 0xFF
      ev.set(ValueLayout.JAVA_INT, 0, KEY_PRESS)
      ev.set(ValueLayout.JAVA_INT, 16, 0)
      ev.set(ValueLayout.JAVA_LONG, 24, display.address())
      ev.set(ValueLayout.JAVA_INT, 80, 0)
      ev.set(ValueLayout.JAVA_INT, 84, keycode)
      val _ = XPutBackEvent.invoke(display, ev).asInstanceOf[Int]
    }

  def destroyLockWindow(surface: Surface): Unit =
    try
      ungrabInput()
      val _ = XUnmapWindow.invoke(display, surface.window).asInstanceOf[Int]
      val _ = XDestroyWindow.invoke(display, surface.window).asInstanceOf[Int]
      val _ = XFlush.invoke(display).asInstanceOf[Int]
    catch
      case _: Throwable => ()

  private val closed = new AtomicBoolean(false)

  override def close(): Unit =
    if !closed.compareAndSet(false, true) then return
    try
      val _ = XCloseDisplay.invoke(display).asInstanceOf[Int]
    catch
      case _: Throwable => ()
    try
      persistentArena.close()
    catch
      case _: Throwable => ()

object X11Canvas:
  // OpenGL & GLX Constants
  val GL_VERTEX_SHADER = 0x8B31
  val GL_FRAGMENT_SHADER = 0x8B30
  val GL_ARRAY_BUFFER = 0x8892
  val GL_STATIC_DRAW = 0x88E4
  val GL_FLOAT = 0x1406
  val GL_TRIANGLE_STRIP = 0x0005
  val GL_COMPILE_STATUS = 0x8B81
  val GL_LINK_STATUS = 0x8B82

  val GL_FRAMEBUFFER = 0x8D40
  val GL_PACK_ALIGNMENT = 0x0D05
  val GL_COLOR_ATTACHMENT0 = 0x8CE0
  val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
  val GL_TEXTURE_2D = 0x0DE1
  val GL_RGBA = 0x1908
  val GL_UNSIGNED_BYTE = 0x1401
  val GL_TEXTURE_MIN_FILTER = 0x2801
  val GL_TEXTURE_MAG_FILTER = 0x2800
  val GL_NEAREST = 0x2600
  val GL_LINEAR = 0x2601
  val GL_UNPACK_ALIGNMENT = 0x0CF5
  val GL_TEXTURE_WRAP_S = 0x2802
  val GL_TEXTURE_WRAP_T = 0x2803
  val GL_CLAMP_TO_EDGE = 0x812F
  val GL_TEXTURE0 = 0x84C0

  val GLX_RGBA = 4
  val GLX_DOUBLEBUFFER = 5

  private val COPY_FROM_PARENT = 0

  val MOUSE_BUTTON1_MASK = 1 << 8
  val MOUSE_BUTTON3_MASK = 1 << 10

  val CW_EVENT_MASK = 1L << 11
  val CW_OVERRIDE_REDIRECT = 1L << 9
  val KEY_PRESS_MASK = 1L
  val KEY_RELEASE_MASK = 1L << 1
  val GRAB_MODE_ASYNC = 1
  val GRAB_SUCCESS = 0
  val CURRENT_TIME = 0L
  val KEY_PRESS = 2
  val KEY_RELEASE = 3

  val XK_BACKSPACE = 0xFF08L
  val XK_RETURN = 0xFF0DL
  val XK_ESCAPE = 0xFF1BL
  val XK_KP_ENTER = 0xFF8DL

  case class MouseInfo(x: Float, y: Float, leftDown: Boolean, rightDown: Boolean)
  case class KeyEvent(press: Boolean, keysym: Long, text: String)
  case class Surface(window: Long, glContext: MemorySegment)
  case class RenderTarget(framebuffer: Int, texture: Int, width: Int, height: Int)

  private def loadLibrary(libName: String, arena: Arena): SymbolLookup =
    try SymbolLookup.libraryLookup(libName, arena)
    catch
      case e: IllegalArgumentException =>
        val fallbacks = Array("/run/current-system/sw/lib", "/usr/lib", "/usr/local/lib")
        fallbacks
          .map(dir => Path.of(dir, libName))
          .find(Files.exists(_))
          .map(p => SymbolLookup.libraryLookup(p, arena))
          .getOrElse(throw e)

  private def findOrThrow(lookup: SymbolLookup, symbolName: String): MemorySegment =
    lookup.find(symbolName).orElseThrow(() =>
      new UnsatisfiedLinkError(s"Native symbol not found: $symbolName")
    )

  private val linker = Linker.nativeLinker()
  private val x11 = loadLibrary("libX11.so", Arena.global())
  private val gl = loadLibrary("libGL.so", Arena.global())

  // Bind X11
  private val XOpenDisplay: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XOpenDisplay"),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
  )
  private val XDefaultScreen: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XDefaultScreen"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
  )
  private val XRootWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XRootWindow"),
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val XDisplayWidth: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XDisplayWidth"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val XDisplayHeight: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XDisplayHeight"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val XCreateWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XCreateWindow"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
      ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
      ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
      ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
    )
  )
  private val XMapWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XMapWindow"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XInternAtom: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XInternAtom"),
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val XChangeProperty: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XChangeProperty"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
      ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
      ValueLayout.ADDRESS, ValueLayout.JAVA_INT
    )
  )
  private val XCloseDisplay: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XCloseDisplay"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
  )
  private val XLowerWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XLowerWindow"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XQueryPointer: MethodHandle = linker.downcallHandle(
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
  private val XGetWindowProperty: MethodHandle = linker.downcallHandle(
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
  private val XFree: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XFree"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
  )

  private val XGrabKeyboard: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XGrabKeyboard"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
      ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG
    )
  )
  private val XUngrabKeyboard: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XUngrabKeyboard"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XGrabPointer: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XGrabPointer"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
      ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
      ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG
    )
  )
  private val XUngrabPointer: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XUngrabPointer"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XSelectInput: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XSelectInput"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
  )
  private val XPending: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XPending"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
  )
  private val XNextEvent: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XNextEvent"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
  )
  private val XLookupString: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XLookupString"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
    )
  )
  private val XSetInputFocus: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XSetInputFocus"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
      ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG
    )
  )
  private val XRaiseWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XRaiseWindow"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XUnmapWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XUnmapWindow"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XDestroyWindow: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XDestroyWindow"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XFlush: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XFlush"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
  )
  private val XKeysymToKeycode: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XKeysymToKeycode"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val XPutBackEvent: MethodHandle = linker.downcallHandle(
    findOrThrow(x11, "XPutBackEvent"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
  )

  // Bind GLX
  private val glXChooseVisual: MethodHandle = linker.downcallHandle(
    findOrThrow(gl, "glXChooseVisual"),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
  )
  private val glXCreateContext: MethodHandle = linker.downcallHandle(
    findOrThrow(gl, "glXCreateContext"),
    FunctionDescriptor.of(
      ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
      ValueLayout.ADDRESS, ValueLayout.JAVA_INT
    )
  )
  private val glXMakeCurrent: MethodHandle = linker.downcallHandle(
    findOrThrow(gl, "glXMakeCurrent"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
  )
  private val glXSwapBuffers: MethodHandle = linker.downcallHandle(
    findOrThrow(gl, "glXSwapBuffers"),
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )
  private val glXGetProcAddress: MethodHandle = linker.downcallHandle(
    findOrThrow(gl, "glXGetProcAddress"),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
  )
