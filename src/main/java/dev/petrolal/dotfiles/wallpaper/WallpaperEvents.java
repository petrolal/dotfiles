package dev.petrolal.dotfiles.wallpaper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * WallpaperEvents manages and aggregates all user input and system telemetry:
 * - Mouse cursor position & left/right click states (global desktop query)
 * - Active X11 virtual workspace index
 * - Audio / media playback detection (Linux ALSA / PipeWire / Pulse)
 * - System CPU & RAM telemetry
 *
 * Designed as a clean boilerplate: easily add keyboard hotkeys, window hooks,
 * or custom sensor listeners right here.
 */
public class WallpaperEvents {

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

    // Audio envelope
    private float smoothedAudio = 0.0f;
    private long lastAudioCheck = 0;
    private boolean lastAudioState = false;

    // CPU telemetry state
    private long prevIdle = 0;
    private long prevTotal = 0;
    private long lastSysTelemetryCheck = 0;
    private float cachedCpu = 0.0f;
    private float cachedRam = 0.0f;

    public WallpaperEvents(X11Canvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Poll all current event states and return an immutable snapshot.
     */
    public WallpaperState pollState(float width, float height, float elapsedSeconds) {
        // 1. Mouse Events (Global X11 cursor & click state)
        X11Canvas.MouseInfo mouse = canvas.queryMouse();

        // 2. Active Virtual Workspace
        int workspace = canvas.queryWorkspace();

        // 3. Audio / Media Playback Activity
        float audio = pollAudioActivity();

        // 4. System Telemetry (Throttled to 500ms to eliminate per-frame procfs read overhead)
        long now = System.currentTimeMillis();
        if (now - lastSysTelemetryCheck > 500) {
            lastSysTelemetryCheck = now;
            cachedCpu = getCpuUsage();
            cachedRam = getRamUsage();
        }

        return new WallpaperState(
            width,
            height,
            elapsedSeconds,
            mouse.x(),
            mouse.y(),
            mouse.leftDown(),
            mouse.rightDown(),
            workspace,
            audio,
            cachedCpu,
            cachedRam
        );
    }

    /**
     * Detects if audio or media is actively playing on the system.
     * Checks Linux /proc/asound hardware PCM playback streams with a smooth attack/decay envelope.
     */
    private float pollAudioActivity() {
        long now = System.currentTimeMillis();
        if (now - lastAudioCheck > 100) {
            lastAudioCheck = now;
            lastAudioState = isAlsaPcmActive();
        }

        float target = lastAudioState ? 1.0f : 0.0f;
        smoothedAudio += (target - smoothedAudio) * 0.15f;
        return smoothedAudio;
    }

    private boolean isAlsaPcmActive() {
        try {
            File asoundDir = new File("/proc/asound");
            if (!asoundDir.exists()) return false;

            File[] cards = asoundDir.listFiles((dir, name) -> name.startsWith("card") && Character.isDigit(name.charAt(4)));
            if (cards == null) return false;

            for (File card : cards) {
                File[] pcms = card.listFiles((dir, name) -> name.startsWith("pcm") && name.endsWith("p"));
                if (pcms == null) continue;

                for (File pcm : pcms) {
                    File[] subs = pcm.listFiles((dir, name) -> name.startsWith("sub"));
                    if (subs == null) continue;

                    for (File sub : subs) {
                        File statusFile = new File(sub, "status");
                        if (statusFile.exists()) {
                            List<String> lines = Files.readAllLines(statusFile.toPath());
                            for (String line : lines) {
                                if (line.contains("state: RUNNING")) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Reads /proc/stat to compute overall CPU utilization between 0.0 and 1.0.
     */
    public float getCpuUsage() {
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
        } catch (Exception ignored) {
            return 0.0f;
        }
    }

    /**
     * Reads /proc/meminfo to compute overall RAM utilization between 0.0 and 1.0.
     */
    public float getRamUsage() {
        try {
            long total = 0, avail = 0;
            for (String l : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (l.startsWith("MemTotal:")) total = Long.parseLong(l.replaceAll("\\D+", ""));
                if (l.startsWith("MemAvailable:")) avail = Long.parseLong(l.replaceAll("\\D+", ""));
                if (total > 0 && avail > 0) break;
            }
            if (total == 0) return 0.0f;
            return (float) (total - avail) / total;
        } catch (Exception ignored) {
            return 0.0f;
        }
    }
}
