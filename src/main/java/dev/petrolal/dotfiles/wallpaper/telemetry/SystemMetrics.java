package dev.petrolal.dotfiles.wallpaper.telemetry;

import java.nio.file.Files;
import java.nio.file.Path;

public class SystemMetrics {

	private long prevIdle = 0;
	private long prevTotal = 0;

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
        } catch (Exception e) {
            return 0.0f;
        }
    }

	public float getRamUsage() {
		try {
			long total = 0, avail = 0;
			for (String l : Files.readAllLines(Path.of("/proc/meminfo"))) {
				if (l.startsWith("MemTotal:"))
					total = Long.parseLong(l.replaceAll("\\D+", ""));
				if (l.startsWith("MemAvailable:"))
					avail = Long.parseLong(l.replaceAll("\\D+", ""));
				if (total > 0 && avail > 0)
					break;
			}
			if (total == 0)
				return 0.0f;
			return (float) (total - avail) / total;
		} catch (Exception e) {
			return 0.0f;
		}
	}
}