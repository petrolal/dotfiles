package dev.petrolal.dotfiles.wallpaper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WallpaperTest:

  @Test
  def detectMonitorsReturnsNonEmptyList(): Unit =
    val wallpaper = new Wallpaper()
    val monitors = wallpaper.detectMonitors(1920, 1080)
    assertNotNull(monitors)
    assertFalse(monitors.isEmpty)

    for m <- monitors do
      assertTrue(m.width > 0, "Monitor width must be positive")
      assertTrue(m.height > 0, "Monitor height must be positive")
      assertTrue(m.x >= 0, "Monitor x coordinate must be non-negative")
      assertTrue(m.y >= 0, "Monitor y coordinate must be non-negative")

  @Test
  def detectMonitorsFallbackWhenZeroGeometry(): Unit =
    val wallpaper = new Wallpaper()
    val monitors = wallpaper.detectMonitors(3840, 1080)
    assertNotNull(monitors)
    assertFalse(monitors.isEmpty)
