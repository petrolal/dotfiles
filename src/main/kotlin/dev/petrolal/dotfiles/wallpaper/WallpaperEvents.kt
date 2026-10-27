package dev.petrolal.dotfiles.wallpaper

import dev.petrolal.dotfiles.telemetry.SystemVitals
import java.io.File
import java.nio.file.Files

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
class WallpaperEvents(private val canvas: X11Canvas) : AutoCloseable {
    /**
     * Immutable snapshot of all event & telemetry states dispatched to the shader.
     */
    @JvmRecord
    data class WallpaperState(
        val width: Float,  // Always the FULL multi-monitor desktop resolution (panorama uses one continuous uv space)
        val height: Float,
        val time: Float,
        val mouseX: Float,  // Raw global desktop cursor position (X11 root-window coordinates)
        val mouseY: Float,
        val mouseLeftDown: Boolean,
        val mouseRightDown: Boolean,
        val workspace: Int,
        val audioActivity: Float,  // 0.0 = idle, 1.0 = audio/media playing
        val cpu: Float,  // 0.0 to 1.0
        val ram: Float // 0.0 to 1.0
    )

    private val pollerThread: Thread?

    @Volatile
    private var running = true

    // Asynchronously updated telemetry cache
    @Volatile
    private var cachedAudio = 0.0f

    @Volatile
    private var cachedCpu = 0.0f

    @Volatile
    private var cachedRam = 0.0f

    // Audio envelope state (confined to the virtual background thread)
    private var smoothedAudio = 0.0f

    // CPU/RAM telemetry (confined to the virtual background thread) -- see
    // dev.petrolal.dotfiles.telemetry.SystemVitals for the display-agnostic
    // procfs reading logic itself, shared with the Hyprland overlay.
    private val vitals = SystemVitals()

    // Workspace query throttle (confined to the render thread via pollState).
    // The virtual-desktop index changes maybe a handful of times per session,
    // not 60-165x/sec -- unlike the mouse, querying it every frame is a
    // wasted XGetWindowProperty round trip to the X server on every frame.
    private var cachedWorkspace = 0
    private var lastWorkspaceCheckNanos = Long.MIN_VALUE

    init {
        // Start background virtual thread for off-thread procfs reading
        this.pollerThread = Thread.ofVirtual()
            .name("wallpaper-telemetry-sensor")
            .start { telemetryLoop() }
    }

    /**
     * Background telemetry loop running on a Virtual Thread.
     * Continuously samples ALSA status and system memory/CPU statistics
     * without blocking the OpenGL presentation cadence.
     */
    private fun telemetryLoop() {
        var lastSysTelemetryCheck = 0L
        while (running) {
            // 1. Audio Activity (10Hz sampling)
            val target = if (this.isAlsaPcmActive) 1.0f else 0.0f
            smoothedAudio += (target - smoothedAudio) * 0.15f
            cachedAudio = smoothedAudio

            // 2. System CPU & RAM (2Hz sampling)
            val now = System.currentTimeMillis()
            if (now - lastSysTelemetryCheck > 500) {
                lastSysTelemetryCheck = now
                cachedCpu = vitals.readCpuUsage()
                cachedRam = vitals.readRamUsage()
            }

            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    /**
     * Fast, non-blocking state poll invoked on the OpenGL render thread.
     * Queries X11 pointer/workspace atoms and performs volatile reads
     * of cached background telemetry (0ms I/O latency).
     */
    fun pollState(width: Float, height: Float, elapsedSeconds: Float): WallpaperState {
        val mouse = canvas.queryMouse()

        val now = System.nanoTime()
        if (now - lastWorkspaceCheckNanos > WORKSPACE_CHECK_INTERVAL_NANOS) {
            cachedWorkspace = canvas.queryWorkspace()
            lastWorkspaceCheckNanos = now
        }

        return WallpaperState(
            width,
            height,
            elapsedSeconds,
            mouse.x,
            mouse.y,
            mouse.leftDown,
            mouse.rightDown,
            cachedWorkspace,
            cachedAudio,
            cachedCpu,
            cachedRam
        )
    }

    // ALSA topology cache (confined to the virtual background thread): the
    // set of card/pcm/sub "status" files basically never changes except on
    // device hotplug, but isAlsaPcmActive() runs at 10Hz forever. Walking the
    // full /proc/asound tree (listFiles() at every level) on every tick was
    // pure wasted syscalls -- only the known status files' *contents* need
    // reading that often, not the topology itself.
    private var cachedAlsaStatusFiles: List<File>? = null
    private var lastAlsaTopologyScanMs = 0L

    private fun scanAlsaStatusFiles(): List<File> {
        val asoundDir = File("/proc/asound")
        val cards = asoundDir.listFiles { _, name ->
            name.startsWith("card") && name.getOrNull(4)?.isDigit() == true
        } ?: return emptyList()

        val result = mutableListOf<File>()
        for (card in cards) {
            val pcms = card.listFiles { _, name ->
                name.startsWith("pcm") && name.endsWith("p")
            } ?: continue

            for (pcm in pcms) {
                val subs = pcm.listFiles { _, name -> name.startsWith("sub") } ?: continue
                for (sub in subs) {
                    val statusFile = File(sub, "status")
                    if (statusFile.exists()) result.add(statusFile)
                }
            }
        }
        return result
    }

    private val isAlsaPcmActive: Boolean
        get() {
            try {
                val now = System.currentTimeMillis()
                val files = cachedAlsaStatusFiles
                val statusFiles = if (files == null || now - lastAlsaTopologyScanMs > ALSA_TOPOLOGY_RESCAN_INTERVAL_MS) {
                    scanAlsaStatusFiles().also {
                        cachedAlsaStatusFiles = it
                        lastAlsaTopologyScanMs = now
                    }
                } else {
                    files
                }

                for (statusFile in statusFiles) {
                    if (!statusFile.exists()) continue  // device removed since last scan

                    for (line in Files.readAllLines(statusFile.toPath())) {
                        if (line.contains("state: RUNNING")) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return false
        }

    override fun close() {
        running = false
        pollerThread?.interrupt()
    }

    companion object {
        private const val WORKSPACE_CHECK_INTERVAL_NANOS = 500000000L
        private const val ALSA_TOPOLOGY_RESCAN_INTERVAL_MS = 5000L
    }
}
