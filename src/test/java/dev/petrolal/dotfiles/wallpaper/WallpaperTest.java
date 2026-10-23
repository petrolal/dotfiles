package dev.petrolal.dotfiles.wallpaper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WallpaperTest {

    @Test
    void detectMonitorsReturnsNonEmptyList() {
        Wallpaper wallpaper = new Wallpaper();
        List<Wallpaper.Monitor> monitors = wallpaper.detectMonitors(1920, 1080);
        assertNotNull(monitors);
        assertFalse(monitors.isEmpty());

        for (Wallpaper.Monitor m : monitors) {
            assertTrue(m.width() > 0, "Monitor width must be positive");
            assertTrue(m.height() > 0, "Monitor height must be positive");
            assertTrue(m.x() >= 0, "Monitor x coordinate must be non-negative");
            assertTrue(m.y() >= 0, "Monitor y coordinate must be non-negative");
        }
    }

    @Test
    void detectMonitorsFallbackWhenZeroGeometry() {
        Wallpaper wallpaper = new Wallpaper();
        List<Wallpaper.Monitor> monitors = wallpaper.detectMonitors(3840, 1080);
        assertNotNull(monitors);
        assertFalse(monitors.isEmpty());
    }
}
