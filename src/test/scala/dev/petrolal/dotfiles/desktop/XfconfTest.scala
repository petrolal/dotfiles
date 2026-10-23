package dev.petrolal.dotfiles.desktop

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import dev.petrolal.dotfiles.core.DotfilePaths

class XfconfTest:

  @Test
  def expandHomeTildeExpandsLeadingTildeSlash(): Unit =
    val home = DotfilePaths.userHomeDirectory().toString
    val trimmedHome = if home.endsWith("/") then home.substring(0, home.length - 1) else home
    assertEquals(trimmedHome + "/Pictures/wallpaper.png", Xfconf.expandHomeTilde("~/Pictures/wallpaper.png"))
    assertEquals(trimmedHome + "/.config/xfce4/scripts/volume.sh up", Xfconf.expandHomeTilde("~/.config/xfce4/scripts/volume.sh up"))

  @Test
  def expandHomeTildeLeavesOtherValuesUnchanged(): Unit =
    assertEquals("Imp98", Xfconf.expandHomeTilde("Imp98"))
    assertEquals("/absolute/path", Xfconf.expandHomeTilde("/absolute/path"))
    assertEquals("~notarealhome", Xfconf.expandHomeTilde("~notarealhome"))
