// PamAuthService.scala --- Linux PAM authentication via the stable FFM API
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

import java.lang.foreign.*
import java.lang.invoke.{MethodHandle, MethodHandles, MethodType}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ExecutorService, Executors, Future}
import scala.util.Using

/**
 * Minimal libpam binding (pam_start / pam_authenticate / pam_end) using the
 * same raw java.lang.foreign style as [[dev.petrolal.dotfiles.wallpaper.X11Canvas]]
 * -- no JNA dependency, consistent with the rest of this codebase.
 *
 * The PAM "conversation" callback is a native upcall into
 * [[conversation]], which answers every PAM_PROMPT_ECHO_OFF /
 * PAM_PROMPT_ECHO_ON message with the password currently held in
 * `pendingPassword` (confined to the single-threaded executor below --
 * only one authenticate() call is ever in flight at a time).
 *
 * All native calls run on a dedicated single-thread executor so a slow or
 * hung PAM stack (network-backed modules, deliberately-slowed brute-force
 * mitigation, etc.) never blocks the 60 FPS render loop.
 */
final class PamAuthService extends AutoCloseable:
  import PamAuthService.*

  private var pendingPassword: Array[Char] = new Array[Char](0)

  private val pamExecutor: ExecutorService =
    Executors.newSingleThreadExecutor(r => {
      val t = new Thread(r, "pam-auth-worker")
      t.setDaemon(true)
      t
    })

  /**
   * Synchronous PAM conversation callback, invoked by libpam from inside
   * pam_authenticate (same native call stack, same Java thread -- never
   * concurrent with another authenticate() call because pamExecutor is
   * single-threaded).
   *
   * Signature: int conv(int num_msg, const struct pam_message **msg,
   *                      struct pam_response **resp, void *appdata_ptr)
   */
  def conversation(numMsg: Int, msgArray: MemorySegment, respOut: MemorySegment, appdataPtr: MemorySegment): Int =
    try
      if numMsg <= 0 then return PAM_SUCCESS

      val respArraySeg = malloc.invoke(numMsg.toLong * 16L).asInstanceOf[MemorySegment]
      val respArrayAddr = respArraySeg.address()
      if respArrayAddr == 0 then return 1

      val respArray = MemorySegment.ofAddress(respArrayAddr).reinterpret(numMsg.toLong * 16L)
      val msgs = msgArray.reinterpret(numMsg.toLong * ValueLayout.ADDRESS.byteSize())

      val passwordUtf8 = toUtf8(pendingPassword)
      try
        for i <- 0 until numMsg do
          val msgPtr = msgs.getAtIndex(ValueLayout.ADDRESS, i).reinterpret(16)
          val style = msgPtr.get(ValueLayout.JAVA_INT, 0)

          val respOffset = i.toLong * 16L
          var respStr = MemorySegment.NULL
          if style == PAM_PROMPT_ECHO_OFF || style == PAM_PROMPT_ECHO_ON then
            val bufSeg = malloc.invoke(passwordUtf8.length.toLong + 1L).asInstanceOf[MemorySegment]
            val bufAddr = bufSeg.address()
            if bufAddr != 0 then
              respStr = MemorySegment.ofAddress(bufAddr).reinterpret(passwordUtf8.length + 1L)
              MemorySegment.copy(MemorySegment.ofArray(passwordUtf8), 0, respStr, 0, passwordUtf8.length)
              respStr.set(ValueLayout.JAVA_BYTE, passwordUtf8.length, 0.toByte)

          respArray.set(ValueLayout.ADDRESS, respOffset, respStr)
          respArray.set(ValueLayout.JAVA_INT, respOffset + 8, 0)
      finally
        java.util.Arrays.fill(passwordUtf8, 0.toByte)

      respOut.reinterpret(ValueLayout.ADDRESS.byteSize()).set(
        ValueLayout.ADDRESS, 0, MemorySegment.ofAddress(respArrayAddr)
      )
      PAM_SUCCESS
    catch
      case t: Throwable =>
        System.err.println(s"[locker] PAM conversation callback threw: $t")
        1

  private def authenticateBlocking(user: String, password: Array[Char]): Status =
    this.pendingPassword = password
    try
      Using.resource(Arena.ofConfined()) { arena =>
        val bound = MethodHandles.lookup().bind(
          this, "conversation", MethodType.methodType(
            classOf[Int], classOf[Int], classOf[MemorySegment], classOf[MemorySegment], classOf[MemorySegment]
          )
        )
        val convStub = Linker.nativeLinker().upcallStub(
          bound,
          FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS
          ),
          arena
        )

        val pamConv = arena.allocate(16)
        pamConv.set(ValueLayout.ADDRESS, 0, convStub)
        pamConv.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL)

        val serviceName = arena.allocateFrom("xfce4-screensaver")
        val userName = arena.allocateFrom(user)
        val pamhOut = arena.allocate(ValueLayout.ADDRESS)

        val startResult = pam_start.invoke(serviceName, userName, pamConv, pamhOut).asInstanceOf[Int]
        if startResult != PAM_SUCCESS then
          Status.ERROR
        else
          val pamh = pamhOut.get(ValueLayout.ADDRESS, 0)
          var authResult = -1
          try
            authResult = pam_authenticate.invoke(pamh, 0).asInstanceOf[Int]
            if authResult != PAM_SUCCESS then
              val msg = pam_strerror.invoke(pamh, authResult).asInstanceOf[MemorySegment]
              val reason = if msg.equals(MemorySegment.NULL) then "(unknown)" else msg.reinterpret(256).getString(0)
              System.err.println(s"[locker] pam_authenticate returned $authResult: $reason")
          finally
            val _ = pam_end.invoke(pamh, 0).asInstanceOf[Int]

          if authResult == PAM_SUCCESS then Status.SUCCESS else Status.FAILURE
      }
    catch
      case t: Throwable =>
        System.err.println(s"[locker] PAM authentication threw: $t")
        Status.ERROR
    finally
      java.util.Arrays.fill(password, '\u0000')
      this.pendingPassword = new Array[Char](0)

  def authenticateAsync(user: String, password: Array[Char]): Future[Status] =
    pamExecutor.submit(() => authenticateBlocking(user, password))

  override def close(): Unit =
    pamExecutor.shutdownNow()

object PamAuthService:
  enum Status:
    case SUCCESS, FAILURE, ERROR

  private val PAM_SUCCESS = 0
  private val PAM_PROMPT_ECHO_OFF = 1
  private val PAM_PROMPT_ECHO_ON = 2

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

  private def findOrThrow(lookup: SymbolLookup, name: String): MemorySegment =
    lookup.find(name).orElseThrow(() =>
      new UnsatisfiedLinkError(s"Native symbol not found: $name")
    )

  private val linker = Linker.nativeLinker()
  private val pam = loadLibrary("libpam.so.0", Arena.global())

  private val pam_start: MethodHandle = linker.downcallHandle(
    findOrThrow(pam, "pam_start"),
    FunctionDescriptor.of(
      ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
      ValueLayout.ADDRESS, ValueLayout.ADDRESS
    )
  )
  private val pam_authenticate: MethodHandle = linker.downcallHandle(
    findOrThrow(pam, "pam_authenticate"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val pam_end: MethodHandle = linker.downcallHandle(
    findOrThrow(pam, "pam_end"),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val pam_strerror: MethodHandle = linker.downcallHandle(
    findOrThrow(pam, "pam_strerror"),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  )
  private val malloc: MethodHandle = linker.downcallHandle(
    linker.defaultLookup().find("malloc").orElseThrow(() =>
      new UnsatisfiedLinkError("Native symbol not found: malloc")
    ),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
  )

  private def toUtf8(password: Array[Char]): Array[Byte] =
    val buf = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
    val out = new Array[Byte](buf.remaining())
    buf.get(out)
    out
