// KeyboardShortcuts.scala --- XFCE custom keyboard shortcut catalog
// License: GPL-3.0-or-later
// Ported from KeyboardShortcuts.java (docs/scala-migration-checklist.md step 3).
//
// Every XFCE custom keyboard shortcut this theme defines. Split out of
// Xfconf (which owns GTK/panel/backdrop settings) so adding, removing, or
// re-binding a shortcut touches exactly one class with exactly one reason to
// change. This object only knows WHAT the shortcuts are -- Xfconf still
// owns HOW an xfconf-query call is actually made.
package dev.petrolal.dotfiles.desktop

object KeyboardShortcuts:

  case class Shortcut(property: String, command: String)

  // The performance-HUD toggle runs our own compiled binary (see Cli's
  // "toggle-hud" command / Orchestrator.toggleWallpaperHud) -- never a
  // standalone shell script.
  private val HudToggleCommand = "~/dotfiles/bin/imp toggle-hud"

  val ALL: List[Shortcut] = buildShortcuts()

  private def buildShortcuts(): List[Shortcut] =
    val base = List(
      Shortcut("/commands/custom/<Super>Return", "xfce4-terminal"),
      Shortcut("/commands/custom/<Primary><Alt>t", "xfce4-terminal"),
      Shortcut("/commands/custom/<Super>e", "thunar"),
      Shortcut("/commands/custom/<Super>r", "xfce4-appfinder"),
      Shortcut("/commands/custom/<Shift><Super>s", "xfce4-screenshooter -r"),
      Shortcut("/commands/custom/<Shift><Super>p", HudToggleCommand),
      Shortcut("/xfwm4/custom/<Super>q", "close_window_key"),
      Shortcut("/xfwm4/custom/<Super>f", "fullscreen_key"),

      // Multimedia keys (PipeWire via wpctl, playerctl)
      Shortcut("/commands/custom/XF86AudioRaiseVolume", "~/.config/xfce4/scripts/volume.sh up"),
      Shortcut("/commands/custom/XF86AudioLowerVolume", "~/.config/xfce4/scripts/volume.sh down"),
      Shortcut("/commands/custom/XF86AudioMute", "~/.config/xfce4/scripts/volume.sh mute"),
      Shortcut("/commands/custom/XF86AudioMicMute", "~/.config/xfce4/scripts/volume.sh mic-mute"),
      Shortcut("/commands/custom/XF86AudioPlay", "playerctl play-pause"),
      Shortcut("/commands/custom/XF86AudioNext", "playerctl next"),
      Shortcut("/commands/custom/XF86AudioPrev", "playerctl previous")
    )

    val workspaces = (1 to 4).flatMap { i =>
      List(
        Shortcut(s"/xfwm4/custom/<Super>$i", s"workspace_${i}_key"),
        Shortcut(s"/xfwm4/custom/<Shift><Super>$i", s"move_window_workspace_${i}_key")
      )
    }

    base ++ workspaces
