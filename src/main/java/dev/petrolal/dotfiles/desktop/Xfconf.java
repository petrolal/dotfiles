// Xfconf.java --- XFCE / GNOME desktop settings
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.domain.Result;
import dev.petrolal.dotfiles.system.ProcessRunner;

public final class Xfconf {
    private Xfconf() {}

    record XfceSetting(String channel, String property, String type, String value) {}

    record GnomeSetting(String schema, String key, String value) {}

    static final List<XfceSetting> XFCE_SETTINGS = List.of(
            // GTK and Theme Configuration (Windows 98 / Redmond97 Dark theme)
            new XfceSetting("xsettings", "/Net/ThemeName", "string", "Redmond97"),
            new XfceSetting("xsettings", "/Net/IconThemeName", "string", "Chicago95"),
            new XfceSetting("xsettings", "/Gtk/CursorThemeSize", "int", "24"),
            new XfceSetting("xsettings", "/Gtk/FontName", "string", "Sans 9"),
            new XfceSetting("xsettings", "/Gtk/ApplicationPreferDarkTheme", "bool", "true"),

            // Notification Daemon Styling (xfce4-notifyd: 100% solid opacity, bottom-right above tray)
            new XfceSetting("xfce4-notifyd", "/theme", "string", "Default"),
            new XfceSetting("xfce4-notifyd", "/initial-opacity", "double", "1.0"),
            new XfceSetting("xfce4-notifyd", "/notify-location", "uint", "3"),

            // Window Manager Behavior (Redmond97 / Windows 98 classic 3D frame)
            new XfceSetting("xfwm4", "/general/theme", "string", "Redmond97"),
            new XfceSetting("xfwm4", "/general/title_font", "string", "Sans Bold 9"),
            new XfceSetting("xfwm4", "/general/title_alignment", "string", "left"),
            new XfceSetting("xfwm4", "/general/button_layout", "string", "O|HMC"),
            new XfceSetting("xfwm4", "/general/show_frame_shadow", "bool", "false"),
            new XfceSetting("xfwm4", "/general/show_popup_shadow", "bool", "false"),
            new XfceSetting("xfwm4", "/general/show_dock_shadow", "bool", "false"),
            new XfceSetting("xfwm4", "/general/easy_click", "string", "Super"),
            new XfceSetting("xfwm4", "/general/snap_to_windows", "bool", "true"),
            new XfceSetting("xfwm4", "/general/snap_to_border", "bool", "true"),
            new XfceSetting("xfwm4", "/general/snap_width", "int", "10"),
            new XfceSetting("xfwm4", "/general/use_compositing", "bool", "true"),

            // Desktop Screen Margins: strictly 0px to ensure flush window tiling and full maximization
            new XfceSetting("xfwm4", "/general/margin_bottom", "int", "0"),
            new XfceSetting("xfwm4", "/general/margin_left", "int", "0"),
            new XfceSetting("xfwm4", "/general/margin_right", "int", "0"),
            new XfceSetting("xfwm4", "/general/margin_top", "int", "0"),

            // Panel global & Start Button (Windows 98 style)
            new XfceSetting("xfce4-panel", "/panels/dark-mode", "bool", "false"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-1/button-title", "string", "Start"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-1/button-icon", "string", "start-here"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-1/show-button-title", "bool", "true"),

            // Taskbar plugins: classic ungrouped 3D window buttons, 1-row workspace buttons, and 1-line tray clock
            new XfceSetting("xfce4-panel", "/plugins/plugin-2/grouping", "uint", "0"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-2/flat-buttons", "bool", "false"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-2/show-handle", "bool", "true"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-3/expand", "bool", "true"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-3/style", "uint", "0"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-4/rows", "uint", "1"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-4/miniature-view", "bool", "false"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-8/mode", "uint", "2"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-8/digital-layout", "uint", "3"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-8/digital-time-format", "string", "%I:%M %p"),
            new XfceSetting("xfce4-panel", "/plugins/plugin-8/digital-time-font", "string", "Sans 8.5"),

            // Panel 1: Windows 98-style bottom taskbar (horizontal, full-width, bottom-left snap p=8)
            new XfceSetting("xfce4-panel", "/panels/panel-1/mode", "uint", "0"),
            new XfceSetting("xfce4-panel", "/panels/panel-1/position", "string", "p=8;x=0;y=0"),
            new XfceSetting("xfce4-panel", "/panels/panel-1/length", "uint", "100"),
            new XfceSetting("xfce4-panel", "/panels/panel-1/position-locked", "bool", "true"),
            new XfceSetting("xfce4-panel", "/panels/panel-1/size", "uint", "28"),
            new XfceSetting("xfce4-panel", "/panels/panel-1/icon-size", "uint", "16"),

            // Panel 2: Dock on the left side of the screen (vertical, left-center snap p=7)
            new XfceSetting("xfce4-panel", "/panels/panel-2/mode", "uint", "1"),
            new XfceSetting("xfce4-panel", "/panels/panel-2/position", "string", "p=7;x=0;y=0"),
            new XfceSetting("xfce4-panel", "/panels/panel-2/length", "uint", "1"),
            new XfceSetting("xfce4-panel", "/panels/panel-2/position-locked", "bool", "true"),
            new XfceSetting("xfce4-panel", "/panels/panel-2/size", "uint", "48"),
            new XfceSetting("xfce4-panel", "/panels/panel-2/autohide-behavior", "uint", "1"),

            // Dark Retro Teal (#183c42) Desktop Background
            new XfceSetting("xfce4-desktop", "/backdrop/screen0/monitor0/workspace0/image-style", "int", "0"),
            new XfceSetting("xfce4-desktop", "/backdrop/screen0/monitor0/workspace0/color-style", "int", "0"),
            new XfceSetting("xfce4-desktop", "/backdrop/screen0/monitoreDP-1/workspace0/image-style", "int", "0"),
            new XfceSetting("xfce4-desktop", "/backdrop/screen0/monitoreDP-1/workspace0/color-style", "int", "0"),
            new XfceSetting("xfce4-desktop", "/backdrop/screen0/monitoreDP-1/workspace1/image-style", "int", "0"),
            new XfceSetting("xfce4-desktop", "/backdrop/screen0/monitoreDP-1/workspace1/color-style", "int", "0"),

            // Keybindings
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Super>Return", "string", "xfce4-terminal"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Primary><Alt>t", "string", "xfce4-terminal"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Super>e", "string", "thunar"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Super>r", "string", "wofi --show drun"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/<Shift><Super>s", "string", "xfce4-screenshooter -r"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>q", "string", "close_window_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>f", "string", "fullscreen_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>1", "string", "workspace_1_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>2", "string", "workspace_2_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>3", "string", "workspace_3_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Super>4", "string", "workspace_4_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>1", "string", "move_window_workspace_1_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>2", "string", "move_window_workspace_2_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>3", "string", "move_window_workspace_3_key"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/xfwm4/custom/<Shift><Super>4", "string", "move_window_workspace_4_key"),

            // Multimedia & Volume Keys (PipeWire / wpctl with native OSD notifications & playerctl)
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioRaiseVolume", "string", "~/.config/xfce4/scripts/volume.sh up"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioLowerVolume", "string", "~/.config/xfce4/scripts/volume.sh down"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioMute", "string", "~/.config/xfce4/scripts/volume.sh mute"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioMicMute", "string", "~/.config/xfce4/scripts/volume.sh mic-mute"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioPlay", "string", "playerctl play-pause"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioNext", "string", "playerctl next"),
            new XfceSetting("xfce4-keyboard-shortcuts", "/commands/custom/XF86AudioPrev", "string", "playerctl previous")
    );

    static final List<GnomeSetting> GNOME_SETTINGS = List.of(
            new GnomeSetting("org.gnome.desktop.interface", "icon-theme", "Chicago95"),
            new GnomeSetting("org.gnome.desktop.interface", "gtk-theme", "Redmond97"),
            new GnomeSetting("org.gnome.desktop.interface", "color-scheme", "prefer-dark")
    );

    /** Expands a leading ~/ to $HOME. */
    static String expandHomeTilde(String valStr) {
        if (valStr.length() >= 2 && valStr.startsWith("~/")) {
            String home = DotfilePaths.userHomeDirectory().toString();
            String trimmed = home.endsWith("/") ? home.substring(0, home.length() - 1) : home;
            return trimmed + valStr.substring(1);
        }
        return valStr;
    }

    record XfceReset(String channel, String property) {}

    private static final List<XfceReset> XFCE_RESETS = List.of(
            new XfceReset("xfwm4", "/general/button_spacing"),
            new XfceReset("xfwm4", "/general/button_offset"),
            new XfceReset("xfwm4", "/general/full_width_title"),
            new XfceReset("xfwm4", "/general/borderless_maximize"),
            new XfceReset("xfwm4", "/general/shadow_delta_x"),
            new XfceReset("xfwm4", "/general/shadow_delta_y"),
            new XfceReset("xfwm4", "/general/shadow_opacity"),
            new XfceReset("xfwm4", "/general/shadow_delta_width"),
            new XfceReset("xfwm4", "/general/shadow_delta_height"),
            new XfceReset("xfwm4", "/general/frame_opacity"),
            new XfceReset("xfwm4", "/general/inactive_opacity")
    );

    private static void resetXfconf(String channel, String property, boolean dryRun) {
        if (dryRun) {
            System.out.printf("[DRY-RUN] xfconf-query -c %s -p %s -r%n", channel, property);
        } else {
            ProcessRunner.run(List.of("xfconf-query", "-c", channel, "-p", property, "-r"));
        }
    }

    /** Logs the DotfileError on a failed Result, prefixed with CONTEXT, so the
     * failure actually reaches the user instead of being silently dropped. */
    private static boolean logIfFailed(String context, Result<Integer> result) {
        result.ifErr(err -> System.err.println("[FAIL] " + context + ": " + err.describe()));
        return result.isOk();
    }

    static boolean setXfconf(String channel, String property, String type, String value, boolean dryRun) {
        String valStr = expandHomeTilde(value);
        if (dryRun) {
            System.out.printf("[DRY-RUN] xfconf-query -c %s -p %s -t %s -s %s%n", channel, property, type, valStr);
            return true;
        }
        return logIfFailed(channel + " " + property,
                ProcessRunner.run(List.of("xfconf-query", "-c", channel, "-p", property, "-s", valStr, "--create", "-t", type)));
    }

    static boolean setXfconfArray(String channel, String property, String type, List<String> values,
                                   boolean dryRun, boolean verbose) {
        List<String> cmd = new ArrayList<>(List.of("xfconf-query", "-c", channel, "-p", property, "--create", "-a"));
        for (String v : values) {
            cmd.add("-t");
            cmd.add(type);
            cmd.add("-s");
            cmd.add(expandHomeTilde(v));
        }
        if (dryRun) {
            System.out.println("[DRY-RUN] " + String.join(" ", cmd));
            return true;
        }
        if (verbose) System.out.println("[OK] Setting " + channel + " " + property + " to " + values);
        return logIfFailed(channel + " " + property, ProcessRunner.run(cmd));
    }

    /** Detects the current display resolution and enforces 0px window manager
     * margins via xfconf. Returns the number of xfconf-query calls that failed. */
    public static int applyDynamicResolutionScaling(boolean dryRun, boolean verbose) {
        Display.PrimaryResolution res = Display.determinePrimaryResolution();
        if (verbose) {
            System.out.printf("Display detection: %dx%d%s -> WM margins: 0px%n",
                    res.width(), res.height(), res.output() != null ? " (" + res.output() + ")" : "");
        }
        int failures = 0;
        if (!setXfconf("xfwm4", "/general/margin_bottom", "int", "0", dryRun)) failures++;
        if (!setXfconf("xfwm4", "/general/margin_left", "int", "0", dryRun)) failures++;
        if (!setXfconf("xfwm4", "/general/margin_right", "int", "0", dryRun)) failures++;
        if (!setXfconf("xfwm4", "/general/margin_top", "int", "0", dryRun)) failures++;
        return failures;
    }

    private static boolean runGsettings(String schema, String key, String value, boolean dryRun) {
        if (dryRun) {
            System.out.printf("[DRY-RUN] gsettings set %s %s '%s'%n", schema, key, value);
            return true;
        }
        return logIfFailed(schema + " " + key, ProcessRunner.run(List.of("gsettings", "set", schema, key, value)));
    }

    private static final List<GnomeSetting> GNOME_RESETS = List.of(
            new GnomeSetting("org.gnome.desktop.interface", "font-name", ""),
            new GnomeSetting("org.gnome.desktop.wm.preferences", "titlebar-font", ""),
            new GnomeSetting("org.gnome.desktop.wm.preferences", "button-layout", "")
    );

    /** Returns the number of gsettings "set" calls that failed. Resets are
     * best-effort (a schema/key not present on this system is expected, not
     * a failure) and are not counted. */
    static int applyGnomeSettings(boolean dryRun, boolean verbose) {
        if (!DotfilePaths.commandExists("gsettings")) return 0;
        if (verbose) System.out.println("Syncing GSettings for GNOME/GTK apps...");
        for (GnomeSetting s : GNOME_RESETS) {
            if (dryRun) {
                System.out.printf("[DRY-RUN] gsettings reset %s %s%n", s.schema(), s.key());
            } else {
                ProcessRunner.run(List.of("gsettings", "reset", s.schema(), s.key()));
            }
        }
        int failures = 0;
        for (GnomeSetting s : GNOME_SETTINGS) {
            if (!runGsettings(s.schema(), s.key(), s.value(), dryRun)) failures++;
        }
        return failures;
    }

    /** Applies every XFCE/GNOME desktop setting and returns the number of
     * underlying subprocess calls that failed (resets are best-effort and
     * excluded). A deploy that reports success no longer does so while every
     * xfconf-query call silently failed underneath it. */
    public static int applyXfceSettings(Path root, boolean dryRun, boolean verbose) {
        if (!DotfilePaths.commandExists("xfconf-query")) {
            if (verbose) System.out.println("[SKIP] xfconf-query not found, skipping desktop theme configuration.");
            return 0;
        }
        if (verbose) System.out.println("Applying XFCE desktop settings...");
        for (XfceReset r : XFCE_RESETS) {
            resetXfconf(r.channel(), r.property(), dryRun);
        }
        int failures = 0;
        for (XfceSetting s : XFCE_SETTINGS) {
            if (!setXfconf(s.channel(), s.property(), s.type(), s.value(), dryRun)) failures++;
        }
        // Panel 1 plugin order: Start (1), Workspaces (4), Window Buttons (2), Expanding Spacer (3), Systray (6), Clock (8)
        if (!setXfconfArray("xfce4-panel", "/panels/panel-1/plugin-ids", "int",
                List.of("1", "4", "2", "3", "6", "8"), dryRun, verbose)) {
            failures++;
        }
        // Dark Retro Teal (#183c42) desktop color arrays
        List<String> darkRetroTeal = List.of("0.094", "0.235", "0.259", "1.0");
        setXfconfArray("xfce4-desktop", "/backdrop/screen0/monitor0/workspace0/rgba1", "double", darkRetroTeal, dryRun, false);
        setXfconfArray("xfce4-desktop", "/backdrop/screen0/monitoreDP-1/workspace0/rgba1", "double", darkRetroTeal, dryRun, false);
        setXfconfArray("xfce4-desktop", "/backdrop/screen0/monitoreDP-1/workspace1/rgba1", "double", darkRetroTeal, dryRun, false);
        failures += XfconfXml.applyExportedXfconfFiles(root, dryRun, verbose);
        failures += applyDynamicResolutionScaling(dryRun, verbose);
        failures += applyGnomeSettings(dryRun, verbose);
        return failures;
    }
}
