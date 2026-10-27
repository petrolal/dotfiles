// KeyboardShortcuts.java --- XFCE custom keyboard shortcut catalog
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

/**
 * Every XFCE custom keyboard shortcut this theme defines. Split out of
 * [Xfconf] (which owns GTK/panel/backdrop settings) so adding,
 * removing, or re-binding a shortcut touches exactly one class with exactly
 * one reason to change. This class only knows WHAT the shortcuts are --
 * Xfconf still owns HOW an xfconf-query call is actually made.
 */
object KeyboardShortcuts {
    /** The performance-HUD toggle runs our own compiled binary (see
     * Cli.java's "toggle-hud" command / Orchestrator.toggleWallpaperHud) --
     * never a standalone shell script.  */
    private const val HUD_TOGGLE_COMMAND = "~/dotfiles/bin/imp toggle-hud"

    @JvmField
    val ALL: List<Shortcut> = buildShortcuts()

    @JvmField
    val DISABLED: List<String> = buildDisabled()

    private fun buildShortcuts(): List<Shortcut> {
        val s = mutableListOf(
            Shortcut("/commands/custom/<Super>Return", "xfce4-terminal"),
            Shortcut("/commands/custom/<Super>e", "thunar"),
            Shortcut("/commands/custom/<Super>l", "xflock4"),
            Shortcut("/commands/custom/<Super>r", "xfce4-appfinder"),
            Shortcut("/commands/custom/<Shift><Super>s", "xfce4-screenshooter -r"),
            Shortcut("/commands/custom/<Shift><Super>r", "gpu-screen-recorder-gtk"),
            Shortcut("/commands/custom/<Shift><Super>p", HUD_TOGGLE_COMMAND),
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

        for (i in 1..4) {
            s.add(Shortcut("/xfwm4/custom/<Super>$i", "workspace_${i}_key"))
            s.add(Shortcut("/xfwm4/custom/<Shift><Super>$i", "move_window_workspace_${i}_key"))
        }
        return s.toList()
    }

    private fun buildDisabled(): List<String> {
        val d = mutableListOf(
            // Hijacks IntelliJ "Reformat Code" (format & indent file)
            "/commands/custom/<Primary><Alt>l",
            // Hijacks IntelliJ "Surround With..." (if/try/catch)
            "/commands/custom/<Primary><Alt>t",
            // Hijacks IntelliJ "Introduce Field"
            "/commands/custom/<Primary><Alt>f",
            // Hijacks IntelliJ "Select In..." / "Open in Browser" / "Bookmarks"
            "/commands/custom/<Alt>F1",
            "/commands/custom/<Alt>F2",
            "/commands/custom/<Alt>F2/startup-notify",
            "/commands/custom/<Alt>F3",
            "/commands/custom/<Alt>F3/startup-notify",
            // Hijacks IntelliJ "Generate Code" (Constructors, Getters/Setters, Override)
            "/xfwm4/custom/<Alt>Insert",
            // Hijacks IntelliJ "Safe Delete"
            "/xfwm4/custom/<Alt>Delete",
            // Hijacks IntelliJ "Find Usages"
            "/xfwm4/custom/<Alt>F7",
            // Hijacks IntelliJ "Evaluate Expression" (Debugger)
            "/xfwm4/custom/<Alt>F8",
            // Hijacks IntelliJ "Run to Cursor" (Debugger)
            "/xfwm4/custom/<Alt>F9",
            // Hijacks IntelliJ "Show Execution Point" (Debugger)
            "/xfwm4/custom/<Alt>F10",
            // Hijacks IntelliJ "Toggle Bookmark"
            "/xfwm4/custom/<Alt>F11",
            // Hijacks IntelliJ "Open Terminal Tool Window"
            "/xfwm4/custom/<Alt>F12",
            // Hijacks IntelliJ "Navigate Back" / "Navigate Forward"
            "/xfwm4/custom/<Alt>F6",

            "/xfwm4/custom/<Primary><Alt>Left",
            "/xfwm4/custom/<Primary><Alt>Right",
            "/xfwm4/custom/<Primary><Alt>Up",
            "/xfwm4/custom/<Primary><Alt>Down",
            "/xfwm4/custom/<Primary><Alt>d",
            "/xfwm4/custom/<Primary><Shift><Alt>Left",
            "/xfwm4/custom/<Primary><Shift><Alt>Right",
            "/xfwm4/custom/<Primary><Shift><Alt>Up",
            "/xfwm4/custom/<Primary><Alt>End",
            "/xfwm4/custom/<Primary><Alt>Home"
        )

        // Hijacks IntelliJ breakpoints (Ctrl+F8), build (Ctrl+F9), file structure (Ctrl+F12), stop (Ctrl+F2)
        for (i in 1..12) {
            d.add("/xfwm4/custom/<Primary>F$i")
        }
        return d.toList()
    }

    @JvmRecord
    data class Shortcut(val property: String, val command: String)
}
