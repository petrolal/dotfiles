package dev.petrolal.dotfiles.wallpaper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
public class WallpaperEvents implements AutoCloseable {

    /**
     * Immutable snapshot of all event & telemetry states dispatched to the shader.
     */
    public record WallpaperState(
        float width,          // Always the FULL multi-monitor desktop resolution (panorama uses one continuous uv space)
        float height,
        float time,
        float mouseX,         // Raw global desktop cursor position (X11 root-window coordinates)
        float mouseY,
        boolean mouseLeftDown,
        boolean mouseRightDown,
        int workspace,
        float audioActivity, // 0.0 = idle, 1.0 = audio/media playing
        float cpu,           // 0.0 to 1.0
        float ram            // 0.0 to 1.0
    ) {}

    private final X11Canvas canvas;
    private final Thread pollerThread;
    private volatile boolean running = true;

    // Asynchronously updated telemetry cache
    private volatile float cachedAudio = 0.0f;
    private volatile float cachedCpu = 0.0f;
    private volatile float cachedRam = 0.0f;

    // Audio envelope state (confined to the virtual background thread)
    private float smoothedAudio = 0.0f;

    // CPU telemetry state (confined to the virtual background thread)
    private long prevIdle = 0;
    private long prevTotal = 0;

    // Workspace query throttle (confined to the render thread via pollState).
    // The virtual-desktop index changes maybe a handful of times per session,
    // not 60-165x/sec -- unlike the mouse, querying it every frame is a
    // wasted XGetWindowProperty round trip to the X server on every frame.
    private int cachedWorkspace = 0;
    private long lastWorkspaceCheckNanos = Long.MIN_VALUE;
    private static final long WORKSPACE_CHECK_INTERVAL_NANOS = 500_000_000L;

    public WallpaperEvents(X11Canvas canvas) {
        this.canvas = canvas;
        // Start background virtual thread for off-thread procfs reading
        this.pollerThread = Thread.ofVirtual()
                .name("wallpaper-telemetry-sensor")
                .start(this::telemetryLoop);
    }

    /**
     * Background telemetry loop running on a Virtual Thread.
     * Continuously samples ALSA status and system memory/CPU statistics
     * without blocking the OpenGL presentation cadence.
     */
    private void telemetryLoop() {
        long lastSysTelemetryCheck = 0;
        while (running) {
            // 1. Audio Activity (10Hz sampling)
            float target = isAlsaPcmActive() ? 1.0f : 0.0f;
            smoothedAudio += (target - smoothedAudio) * 0.15f;
            cachedAudio = smoothedAudio;

            // 2. System CPU & RAM (2Hz sampling)
            long now = System.currentTimeMillis();
            if (now - lastSysTelemetryCheck > 500) {
                lastSysTelemetryCheck = now;
                cachedCpu = readCpuUsage();
                cachedRam = readRamUsage();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException _) {
                break;
            }
        }
    }

    /**
     * Fast, non-blocking state poll invoked on the OpenGL render thread.
     * Queries X11 pointer/workspace atoms and performs volatile reads
     * of cached background telemetry (0ms I/O latency).
     */
    public WallpaperState pollState(float width, float height, float elapsedSeconds) {
        X11Canvas.MouseInfo mouse = canvas.queryMouse();

        long now = System.nanoTime();
        if (now - lastWorkspaceCheckNanos > WORKSPACE_CHECK_INTERVAL_NANOS) {
            cachedWorkspace = canvas.queryWorkspace();
            lastWorkspaceCheckNanos = now;
        }

        return new WallpaperState(
            width,
            height,
            elapsedSeconds,
            mouse.x(),
            mouse.y(),
            mouse.leftDown(),
            mouse.rightDown(),
            cachedWorkspace,
            cachedAudio,
            cachedCpu,
            cachedRam
        );
    }

    // ALSA topology cache (confined to the virtual background thread): the
    // set of card/pcm/sub "status" files basically never changes except on
    // device hotplug, but isAlsaPcmActive() runs at 10Hz forever. Walking the
    // full /proc/asound tree (listFiles() at every level) on every tick was
    // pure wasted syscalls -- only the known status files' *contents* need
    // reading that often, not the topology itself.
    private List<File> cachedAlsaStatusFiles = null;
    private long lastAlsaTopologyScanMs = 0;
    private static final long ALSA_TOPOLOGY_RESCAN_INTERVAL_MS = 5_000;

    private List<File> scanAlsaStatusFiles() {
        List<File> result = new ArrayList<>();
        File asoundDir = new File("/proc/asound");
        File[] cards = asoundDir.listFiles((_, name) -> name.startsWith("card") && Character.isDigit(name.charAt(4)));
        if (cards == null) return result;

        for (File card : cards) {
            File[] pcms = card.listFiles((_, name) -> name.startsWith("pcm") && name.endsWith("p"));
            if (pcms == null) continue;

            for (File pcm : pcms) {
                File[] subs = pcm.listFiles((_, name) -> name.startsWith("sub"));
                if (subs == null) continue;

                for (File sub : subs) {
                    File statusFile = new File(sub, "status");
                    if (statusFile.exists()) result.add(statusFile);
                }
            }
        }
        return result;
    }

    private boolean isAlsaPcmActive() {
        try {
            long now = System.currentTimeMillis();
            if (cachedAlsaStatusFiles == null || now - lastAlsaTopologyScanMs > ALSA_TOPOLOGY_RESCAN_INTERVAL_MS) {
                cachedAlsaStatusFiles = scanAlsaStatusFiles();
                lastAlsaTopologyScanMs = now;
            }

            for (File statusFile : cachedAlsaStatusFiles) {
                if (!statusFile.exists()) continue; // device removed since last scan
                for (String line : Files.readAllLines(statusFile.toPath())) {
                    if (line.contains("state: RUNNING")) {
                        return true;
                    }
                }
            }
        } catch (Exception _) {}
        return false;
    }

    /**
     * Reads /proc/stat to compute overall CPU utilization between 0.0 and 1.0.
     */
    private float readCpuUsage() {
        try {
            String line = Files.lines(Path.of("/proc/stat")).findFirst().orElse("");
            String[] parts = line.trim().split("\\s+");
            long u = Long.parseLong(parts[1]);
            long n = Long.parseLong(parts[2]);
            long s = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]) + Long.parseLong(parts[5]);
            long total = u + n + s + idle + Long.parseLong(parts[6]) + Long.parseLong(parts[7]);

            long diffIdle = idle - prevIdle;
            long diffTotal = total - prevTotal;
            prevIdle = idle;
            prevTotal = total;

            if (diffTotal == 0) return 0.0f;
            return Math.clamp((float) (diffTotal - diffIdle) / diffTotal, 0.0f, 1.0f);
        } catch (Exception _) {
            return 0.0f;
        }
    }

    /**
     * Reads /proc/meminfo to compute overall RAM utilization between 0.0 and 1.0.
     */
    private float readRamUsage() {
        try {
            long total = 0, avail = 0;
            for (String l : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (l.startsWith("MemTotal:")) total = Long.parseLong(l.replaceAll("\\D+", ""));
                if (l.startsWith("MemAvailable:")) avail = Long.parseLong(l.replaceAll("\\D+", ""));
                if (total > 0 && avail > 0) break;
            }
            if (total == 0) return 0.0f;
            return (float) (total - avail) / total;
        } catch (Exception _) {
            return 0.0f;
        }
    }

    @Override
    public void close() {
        running = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
        }
    }
}
