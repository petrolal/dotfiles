// SystemVitals.java --- Host CPU/RAM sampling, display-server-agnostic
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.telemetry;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Samples overall CPU and RAM utilization from procfs. Extracted from
 * {@code wallpaper.WallpaperEvents} (which still owns the X11-specific
 * mouse/workspace/audio polling) so this reading has no X11Canvas/display
 * dependency and can be reused by any render surface -- the Wayland/Hyprland
 * overlay included, which has no equivalent telemetry source today.
 *
 * <p>One instance per consumer: CPU delta requires state (previous idle/total
 * jiffies) between samples, so sharing an instance across unrelated
 * consumers would make each one's reading depend on how often the other
 * polls.
 */
public final class SystemVitals {

    private long prevIdle = 0;
    private long prevTotal = 0;

    /** Reads /proc/stat to compute overall CPU utilization between 0.0 and
     * 1.0, as a delta against the previous call -- the first call on a fresh
     * instance always returns 0.0 since there is no prior sample yet. */
    public float readCpuUsage() {
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

    /** Reads /proc/meminfo to compute overall RAM utilization between 0.0
     * and 1.0. Stateless, unlike {@link #readCpuUsage()}. */
    public float readRamUsage() {
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
}
