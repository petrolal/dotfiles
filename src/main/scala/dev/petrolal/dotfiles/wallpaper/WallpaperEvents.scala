package dev.petrolal.dotfiles.wallpaper

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/**
 * WallpaperEvents manages and aggregates all user input and system telemetry:
 * - Mouse cursor position & left/right click states (global desktop query)
 * - Active X11 virtual workspace index
 * - Audio / media playback detection (Linux ALSA / PipeWire / Pulse)
 * - System CPU & RAM telemetry
 *
 * Utilizes a background Virtual Thread for procfs file I/O to ensure the
 * OpenGL render loop never blocks on disk or kernel file reads.
 */
class WallpaperEvents(canvas: X11Canvas) extends AutoCloseable:
  import WallpaperEvents.*

  @volatile private var running: Boolean = true

  // Asynchronously updated telemetry cache
  @volatile private var cachedAudio: Float = 0.0f
  @volatile private var cachedCpu: Float = 0.0f
  @volatile private var cachedRam: Float = 0.0f

  // Audio envelope state (confined to the virtual background thread)
  private var smoothedAudio: Float = 0.0f

  // CPU telemetry state (confined to the virtual background thread)
  private var prevIdle: Long = 0L
  private var prevTotal: Long = 0L

  // Workspace query throttle (confined to the render thread via pollState).
  private var cachedWorkspace: Int = 0
  private var lastWorkspaceCheckNanos: Long = Long.MinValue
  private val WORKSPACE_CHECK_INTERVAL_NANOS: Long = 500_000_000L

  // Start background virtual thread for off-thread procfs reading
  private val pollerThread: Thread =
    Thread.ofVirtual()
      .name("wallpaper-telemetry-sensor")
      .start(() => telemetryLoop())

  private def telemetryLoop(): Unit =
    var lastSysTelemetryCheck = 0L
    while running do
      // 1. Audio Activity (10Hz sampling)
      val target = if isAlsaPcmActive() then 1.0f else 0.0f
      smoothedAudio += (target - smoothedAudio) * 0.15f
      cachedAudio = smoothedAudio

      // 2. System CPU & RAM (2Hz sampling)
      val now = System.currentTimeMillis()
      if now - lastSysTelemetryCheck > 500 then
        lastSysTelemetryCheck = now
        cachedCpu = readCpuUsage()
        cachedRam = readRamUsage()

      try Thread.sleep(100)
      catch case _: InterruptedException => return

  def pollState(width: Float, height: Float, elapsedSeconds: Float): WallpaperState =
    val mouse = canvas.queryMouse()

    val now = System.nanoTime()
    if now - lastWorkspaceCheckNanos > WORKSPACE_CHECK_INTERVAL_NANOS then
      cachedWorkspace = canvas.queryWorkspace()
      lastWorkspaceCheckNanos = now

    WallpaperState(
      width = width,
      height = height,
      time = elapsedSeconds,
      mouseX = mouse.x,
      mouseY = mouse.y,
      mouseLeftDown = mouse.leftDown,
      mouseRightDown = mouse.rightDown,
      workspace = cachedWorkspace,
      audioActivity = cachedAudio,
      cpu = cachedCpu,
      ram = cachedRam
    )

  // ALSA topology cache (confined to the virtual background thread)
  private var cachedAlsaStatusFiles: List[File] = null
  private var lastAlsaTopologyScanMs: Long = 0L
  private val ALSA_TOPOLOGY_RESCAN_INTERVAL_MS: Long = 5000L

  private def scanAlsaStatusFiles(): List[File] =
    val result = ListBuffer[File]()
    val asoundDir = new File("/proc/asound")
    val cards = asoundDir.listFiles((_, name) => name.startsWith("card") && name.length > 4 && name.charAt(4).isDigit)
    if cards == null then return Nil

    for card <- cards do
      val pcms = card.listFiles((_, name) => name.startsWith("pcm") && name.endsWith("p"))
      if pcms != null then
        for pcm <- pcms do
          val subs = pcm.listFiles((_, name) => name.startsWith("sub"))
          if subs != null then
            for sub <- subs do
              val statusFile = new File(sub, "status")
              if statusFile.exists() then result += statusFile

    result.toList

  private def isAlsaPcmActive(): Boolean =
    try
      val now = System.currentTimeMillis()
      if cachedAlsaStatusFiles == null || now - lastAlsaTopologyScanMs > ALSA_TOPOLOGY_RESCAN_INTERVAL_MS then
        cachedAlsaStatusFiles = scanAlsaStatusFiles()
        lastAlsaTopologyScanMs = now

      var active = false
      val iter = cachedAlsaStatusFiles.iterator
      while iter.hasNext && !active do
        val statusFile = iter.next()
        if statusFile.exists() then
          val lines = Files.readAllLines(statusFile.toPath)
          val lineIter = lines.iterator()
          while lineIter.hasNext && !active do
            if lineIter.next().contains("state: RUNNING") then
              active = true
      active
    catch
      case _: Exception => false

  private def readCpuUsage(): Float =
    try
      val lineOpt = Files.lines(Path.of("/proc/stat")).findFirst()
      val line = if lineOpt.isPresent then lineOpt.get() else ""
      val parts = line.trim.split("\\s+")
      if parts.length >= 8 then
        val u = parts(1).toLong
        val n = parts(2).toLong
        val s = parts(3).toLong
        val idle = parts(4).toLong + parts(5).toLong
        val total = u + n + s + idle + parts(6).toLong + parts(7).toLong

        val diffIdle = idle - prevIdle
        val diffTotal = total - prevTotal
        prevIdle = idle
        prevTotal = total

        if diffTotal == 0 then 0.0f
        else math.max(0.0f, math.min(1.0f, (diffTotal - diffIdle).toFloat / diffTotal))
      else 0.0f
    catch
      case _: Exception => 0.0f

  private def readRamUsage(): Float =
    try
      var total = 0L
      var avail = 0L
      val lines = Files.readAllLines(Path.of("/proc/meminfo"))
      val iter = lines.iterator()
      while iter.hasNext && (total == 0 || avail == 0) do
        val l = iter.next()
        if l.startsWith("MemTotal:") then total = l.replaceAll("\\D+", "").toLong
        if l.startsWith("MemAvailable:") then avail = l.replaceAll("\\D+", "").toLong
      if total == 0 then 0.0f else (total - avail).toFloat / total
    catch
      case _: Exception => 0.0f

  override def close(): Unit =
    running = false
    if pollerThread != null then
      pollerThread.interrupt()

object WallpaperEvents:
  /**
   * Immutable snapshot of all event & telemetry states dispatched to the shader.
   */
  case class WallpaperState(
    width: Float,          // Always the FULL multi-monitor desktop resolution
    height: Float,
    time: Float,
    mouseX: Float,         // Raw global desktop cursor position (X11 root-window coordinates)
    mouseY: Float,
    mouseLeftDown: Boolean,
    mouseRightDown: Boolean,
    workspace: Int,
    audioActivity: Float, // 0.0 = idle, 1.0 = audio/media playing
    cpu: Float,           // 0.0 to 1.0
    ram: Float            // 0.0 to 1.0
  )
