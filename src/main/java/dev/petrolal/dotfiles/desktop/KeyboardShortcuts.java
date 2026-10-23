// KeyboardShortcuts.java --- XFCE custom keyboard shortcut catalog
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop;

import java.util.ArrayList;
import java.util.List;

/**
 * Every XFCE custom keyboard shortcut this theme defines. Split out of
 * {@link Xfconf} (which owns GTK/panel/backdrop settings) so adding,
 * removing, or re-binding a shortcut touches exactly one class with exactly
 * one reason to change. This class only knows WHAT the shortcuts are --
 * Xfconf still owns HOW an xfconf-query call is actually made.
 */
public final class KeyboardShortcuts {
    private KeyboardShortcuts() {}

    public record Shortcut(String property, String command) {}

    /** The performance-HUD toggle runs our own compiled binary (see
     * Cli.java's "toggle-hud" command / Orchestrator.toggleWallpaperHud) --
     * never a standalone shell script. */
    private static final String HUD_TOGGLE_COMMAND = "~/dotfiles/bin/imp toggle-hud";

    public static final List<Shortcut> ALL = buildShortcuts();

    private static List<Shortcut> buildShortcuts() {
        List<Shortcut> s = new ArrayList<>(List.of(
                new Shortcut("/commands/custom/<Super>Return", "xfce4-terminal"),
                new Shortcut("/commands/custom/<Primary><Alt>t", "xfce4-terminal"),
                new Shortcut("/commands/custom/<Super>e", "thunar"),
                new Shortcut("/commands/custom/<Super>r", "xfce4-appfinder"),
                new Shortcut("/commands/custom/<Shift><Super>s", "xfce4-screenshooter -r"),
                new Shortcut("/commands/custom/<Shift><Super>r", "gpu-screen-recorder-gtk"),
                new Shortcut("/commands/custom/<Shift><Super>p", HUD_TOGGLE_COMMAND),
                new Shortcut("/xfwm4/custom/<Super>q", "close_window_key"),
                new Shortcut("/xfwm4/custom/<Super>f", "fullscreen_key"),

                // Multimedia keys (PipeWire via wpctl, playerctl)
                new Shortcut("/commands/custom/XF86AudioRaiseVolume", "~/.config/xfce4/scripts/volume.sh up"),
                new Shortcut("/commands/custom/XF86AudioLowerVolume", "~/.config/xfce4/scripts/volume.sh down"),
                new Shortcut("/commands/custom/XF86AudioMute", "~/.config/xfce4/scripts/volume.sh mute"),
                new Shortcut("/commands/custom/XF86AudioMicMute", "~/.config/xfce4/scripts/volume.sh mic-mute"),
                new Shortcut("/commands/custom/XF86AudioPlay", "playerctl play-pause"),
                new Shortcut("/commands/custom/XF86AudioNext", "playerctl next"),
                new Shortcut("/commands/custom/XF86AudioPrev", "playerctl previous")));

        for (int i = 1; i <= 4; i++) {
            s.add(new Shortcut("/xfwm4/custom/<Super>" + i, "workspace_" + i + "_key"));
            s.add(new Shortcut("/xfwm4/custom/<Shift><Super>" + i, "move_window_workspace_" + i + "_key"));
        }
        return List.copyOf(s);
    }
}
