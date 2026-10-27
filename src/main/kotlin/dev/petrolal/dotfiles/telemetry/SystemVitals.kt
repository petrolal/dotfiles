// SystemVitals.java --- Host CPU/RAM sampling, display-server-agnostic
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.telemetry

import java.nio.file.Files
import java.nio.file.Path

/**
 * Samples overall CPU and RAM utilization from procfs. Extracted from
 * `wallpaper.WallpaperEvents` (which still owns the X11-specific
 * mouse/workspace/audio polling) so this reading has no X11Canvas/display
 * dependency and can be reused by any render surface -- the Wayland/Hyprland
 * overlay included, which has no equivalent telemetry source today.
 *
 * One instance per consumer: CPU delta requires state (previous idle/total
 * jiffies) between samples, so sharing an instance across unrelated
 * consumers would make each one's reading depend on how often the other
 * polls.
 */
class SystemVitals {
    private var prevIdle: Long = 0L
    private var prevTotal: Long = 0L

    /** Reads /proc/stat to compute overall CPU utilization between 0.0 and
     * 1.0, as a delta against the previous call -- the first call on a fresh
     * instance always returns 0.0 since there is no prior sample yet.  */
    fun readCpuUsage(): Float {
        try {
            val line = Files.lines(Path.of("/proc/stat")).findFirst().orElse("")
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 8) return 0.0f
            val u = parts[1].toLong()
            val n = parts[2].toLong()
            val s = parts[3].toLong()
            val idle = parts[4].toLong() + parts[5].toLong()
            val total = u + n + s + idle + parts[6].toLong() + parts[7].toLong()

            val diffIdle = idle - prevIdle
            val diffTotal = total - prevTotal
            prevIdle = idle
            prevTotal = total

            if (diffTotal == 0L) return 0.0f
            return ((diffTotal - diffIdle).toFloat() / diffTotal).coerceIn(0.0f, 1.0f)
        } catch (_: Exception) {
            return 0.0f
        }
    }

    /** Reads /proc/meminfo to compute overall RAM utilization between 0.0
     * and 1.0. Stateless, unlike [.readCpuUsage].  */
    fun readRamUsage(): Float {
        try {
            var total: Long = 0L
            var avail: Long = 0L
            for (l in Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (l.startsWith("MemTotal:")) total = l.replace(Regex("\\D+"), "").toLong()
                if (l.startsWith("MemAvailable:")) avail = l.replace(Regex("\\D+"), "").toLong()
                if (total > 0L && avail > 0L) break
            }
            if (total == 0L) return 0.0f
            return (total - avail).toFloat() / total
        } catch (_: Exception) {
            return 0.0f
        }
    }
}
