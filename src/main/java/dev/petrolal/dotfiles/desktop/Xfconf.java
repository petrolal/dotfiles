// Xfconf.java --- XFCE / GNOME desktop settings
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop;

import java.util.ArrayList;
import java.util.List;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.core.Theme;
import dev.petrolal.dotfiles.domain.Result;
import dev.petrolal.dotfiles.system.ProcessRunner;

public final class Xfconf {
    private Xfconf() {}

    record XfceSetting(String channel, String property, String type, String value) {}

    record GnomeSetting(String schema, String key, String value) {}

    // Panel plugin ids (xfce4-panel /plugins/plugin-N)
    private static final int START = 1, TASKLIST = 2, SPACER = 3, PAGER = 4, SYSTRAY = 6, CLOCK = 8;
    private static final int FIRST_LAUNCHER = 19;

    /** Quick Launch favourites on the taskbar; each gets its own launcher plugin. */
    private static final List<String> LAUNCHERS = List.of("filemanager", "webbrowser", "appfinder", "terminal");

    /** Desktop backdrop nodes whose image/colour style must be flat ("none"). */
    private static final List<String> BACKDROPS = List.of(
            "monitor0/workspace0", "monitoreDP-1/workspace0", "monitoreDP-1/workspace1");

    private static final List<String> BACKDROP_COLOR = List.of("0.094", "0.235", "0.259", "1.0"); // #183c42

    private static XfceSetting setting(String channel, String property, String type, String value) {
        return new XfceSetting(channel, property, type, value);
    }

    private static XfceSetting panel(String property, String type, String value) {
        return setting("xfce4-panel", property, type, value);
    }

    private static XfceSetting plugin(int id, String key, String type, String value) {
        return panel("/plugins/plugin-" + id + "/" + key, type, value);
    }

    private static XfceSetting xfwm(String key, String type, String value) {
        return setting("xfwm4", "/general/" + key, type, value);
    }

    private static XfceSetting shortcut(String property, String command) {
        return setting("xfce4-keyboard-shortcuts", property, "string", command);
    }

    static final List<XfceSetting> XFCE_SETTINGS = buildXfceSettings();

    private static List<XfceSetting> buildXfceSettings() {
        List<XfceSetting> s = new ArrayList<>(List.of(
                // GTK, icons, fonts
                setting("xsettings", "/Net/ThemeName", "string", Theme.NAME),
                setting("xsettings", "/Net/IconThemeName", "string", Theme.ICONS),
                setting("xsettings", "/Gtk/CursorThemeSize", "int", "24"),
                setting("xsettings", "/Gtk/FontName", "string", "Sans 9"),
                setting("xsettings", "/Gtk/ApplicationPreferDarkTheme", "bool", "false"),

                // Notification daemon: solid, bottom-right above the tray
                setting("xfce4-notifyd", "/theme", "string", "Default"),
                setting("xfce4-notifyd", "/initial-opacity", "double", "1.0"),
                setting("xfce4-notifyd", "/notify-location", "uint", "3"),

                // Window manager: classic 3D frame, no shadows, flush margins
                xfwm("theme", "string", Theme.NAME),
                xfwm("title_font", "string", "Sans Bold 9"),
                xfwm("title_alignment", "string", "left"),
                xfwm("button_layout", "string", "O|HMC"),
                xfwm("show_frame_shadow", "bool", "false"),
                xfwm("show_popup_shadow", "bool", "false"),
                xfwm("show_dock_shadow", "bool", "false"),
                xfwm("easy_click", "string", "Super"),
                xfwm("snap_to_windows", "bool", "true"),
                xfwm("snap_to_border", "bool", "true"),
                xfwm("snap_width", "int", "10"),
                xfwm("use_compositing", "bool", "true"),
                xfwm("margin_bottom", "int", "0"),
                xfwm("margin_left", "int", "0"),
                xfwm("margin_right", "int", "0"),
                xfwm("margin_top", "int", "0"),

                // Start button (Whisker Menu)
                panel("/panels/dark-mode", "bool", "false"),
                panel("/plugins/plugin-" + START, "string", "whiskermenu"),
                plugin(START, "button-title", "string", "Start"),
                plugin(START, "button-icon", "string", "~/.themes/" + Theme.NAME + "/gtk-3.0/assets/start-here.png"),
                plugin(START, "show-button-title", "bool", "true"),
                plugin(START, "show-button-icon", "bool", "true"),
                plugin(START, "view-mode", "int", "1"),
                plugin(START, "launcher-show-name", "bool", "true"),
                plugin(START, "launcher-show-description", "bool", "true"),
                plugin(START, "launcher-icon-size", "int", "2"),
                plugin(START, "category-icon-size", "int", "1"),
                plugin(START, "position-categories-alternate", "bool", "true"),
                plugin(START, "position-categories-horizontal", "bool", "false"),
                plugin(START, "position-search-alternate", "bool", "true"),
                plugin(START, "position-commands-alternate", "bool", "false"),
                plugin(START, "position-profile-alternate", "bool", "false"),
                plugin(START, "show-command-settings", "bool", "true"),
                plugin(START, "show-command-lockscreen", "bool", "true"),
                plugin(START, "show-command-switchuser", "bool", "false"),
                plugin(START, "show-command-logoutuser", "bool", "false"),
                plugin(START, "show-command-logout", "bool", "true"),
                plugin(START, "menu-width", "int", "450"),
                plugin(START, "menu-height", "int", "530"),

                // Taskbar: ungrouped 3D buttons, 1-row pager, 1-line clock
                plugin(TASKLIST, "grouping", "uint", "0"),
                plugin(TASKLIST, "flat-buttons", "bool", "false"),
                plugin(TASKLIST, "show-handle", "bool", "true"),
                plugin(SPACER, "expand", "bool", "true"),
                plugin(SPACER, "style", "uint", "0"),
                plugin(PAGER, "rows", "uint", "1"),
                plugin(PAGER, "miniature-view", "bool", "false"),
                plugin(CLOCK, "mode", "uint", "2"),
                plugin(CLOCK, "digital-layout", "uint", "3"),
                plugin(CLOCK, "digital-time-format", "string", "%I:%M %p"),
                plugin(CLOCK, "digital-time-font", "string", "Sans 8.5"),

                // Panel 1: bottom, full-width taskbar
                panel("/panels/panel-1/mode", "uint", "0"),
                panel("/panels/panel-1/position", "string", "p=8;x=0;y=0"),
                panel("/panels/panel-1/length", "uint", "100"),
                panel("/panels/panel-1/position-locked", "bool", "true"),
                panel("/panels/panel-1/size", "uint", "28"),
                panel("/panels/panel-1/icon-size", "uint", "16"),

                // Keybindings
                shortcut("/commands/custom/<Super>Return", "xfce4-terminal"),
                shortcut("/commands/custom/<Primary><Alt>t", "xfce4-terminal"),
                shortcut("/commands/custom/<Super>e", "thunar"),
                shortcut("/commands/custom/<Super>r", "xfce4-appfinder"),
                shortcut("/commands/custom/<Shift><Super>s", "xfce4-screenshooter -r"),
                shortcut("/xfwm4/custom/<Super>q", "close_window_key"),
                shortcut("/xfwm4/custom/<Super>f", "fullscreen_key"),

                // Multimedia keys (PipeWire via wpctl, playerctl)
                shortcut("/commands/custom/XF86AudioRaiseVolume", "~/.config/xfce4/scripts/volume.sh up"),
                shortcut("/commands/custom/XF86AudioLowerVolume", "~/.config/xfce4/scripts/volume.sh down"),
                shortcut("/commands/custom/XF86AudioMute", "~/.config/xfce4/scripts/volume.sh mute"),
                shortcut("/commands/custom/XF86AudioMicMute", "~/.config/xfce4/scripts/volume.sh mic-mute"),
                shortcut("/commands/custom/XF86AudioPlay", "playerctl play-pause"),
                shortcut("/commands/custom/XF86AudioNext", "playerctl next"),
                shortcut("/commands/custom/XF86AudioPrev", "playerctl previous")));

        for (int i = 1; i <= 4; i++) {
            s.add(shortcut("/xfwm4/custom/<Super>" + i, "workspace_" + i + "_key"));
            s.add(shortcut("/xfwm4/custom/<Shift><Super>" + i, "move_window_workspace_" + i + "_key"));
        }
        for (int i = 0; i < LAUNCHERS.size(); i++) {
            s.add(panel("/plugins/plugin-" + (FIRST_LAUNCHER + i), "string", "launcher"));
        }
        for (String node : BACKDROPS) {
            s.add(setting("xfce4-desktop", "/backdrop/screen0/" + node + "/image-style", "int", "0"));
            s.add(setting("xfce4-desktop", "/backdrop/screen0/" + node + "/color-style", "int", "0"));
        }
        return List.copyOf(s);
    }

    static final List<GnomeSetting> GNOME_SETTINGS = List.of(
            new GnomeSetting("org.gnome.desktop.interface", "icon-theme", Theme.ICONS),
            new GnomeSetting("org.gnome.desktop.interface", "gtk-theme", Theme.NAME),
            new GnomeSetting("org.gnome.desktop.interface", "color-scheme", "default")
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

    private static boolean runGsettings(String schema, String key, String value, boolean dryRun) {
        if (dryRun) {
            System.out.printf("[DRY-RUN] gsettings set %s %s '%s'%n", schema, key, value);
            return true;
        }
        return logIfFailed(schema + " " + key, ProcessRunner.run(List.of("gsettings", "set", schema, key, value)));
    }

    /** Returns the number of gsettings "set" calls that failed. */
    static int applyGnomeSettings(boolean dryRun, boolean verbose) {
        if (!DotfilePaths.commandExists("gsettings")) return 0;
        if (verbose) System.out.println("Syncing GSettings for GNOME/GTK apps...");
        int failures = 0;
        for (GnomeSetting s : GNOME_SETTINGS) {
            if (!runGsettings(s.schema(), s.key(), s.value(), dryRun)) failures++;
        }
        return failures;
    }

    /** Applies every XFCE/GNOME desktop setting and returns the number of
     * underlying subprocess calls that failed, so a deploy cannot report
     * success while every xfconf-query call silently failed. */
    public static int applyXfceSettings(boolean dryRun, boolean verbose) {
        if (!DotfilePaths.commandExists("xfconf-query")) {
            if (verbose) System.out.println("[SKIP] xfconf-query not found, skipping desktop theme configuration.");
            return 0;
        }
        if (verbose) System.out.println("Applying XFCE desktop settings...");
        int failures = 0;
        for (XfceSetting s : XFCE_SETTINGS) {
            if (!setXfconf(s.channel(), s.property(), s.type(), s.value(), dryRun)) failures++;
        }

        List<String> launcherItems = new ArrayList<>();
        List<String> pluginIds = new ArrayList<>(List.of(String.valueOf(START)));
        for (int i = 0; i < LAUNCHERS.size(); i++) {
            int id = FIRST_LAUNCHER + i;
            pluginIds.add(String.valueOf(id));
            launcherItems.add(LAUNCHERS.get(i) + ".desktop");
            if (!setXfconfArray("xfce4-panel", "/plugins/plugin-" + id + "/items", "string",
                    List.of(launcherItems.get(i)), dryRun, verbose)) {
                failures++;
            }
        }
        for (int id : new int[] {TASKLIST, SPACER, SYSTRAY, CLOCK}) {
            pluginIds.add(String.valueOf(id));
        }
        if (!setXfconfArray("xfce4-panel", "/panels", "int", List.of("1"), dryRun, verbose)) failures++;
        if (!setXfconfArray("xfce4-panel", "/panels/panel-1/plugin-ids", "int", pluginIds, dryRun, verbose)) failures++;

        for (String node : BACKDROPS) {
            if (!setXfconfArray("xfce4-desktop", "/backdrop/screen0/" + node + "/rgba1", "double",
                    BACKDROP_COLOR, dryRun, false)) {
                failures++;
            }
        }
        failures += applyGnomeSettings(dryRun, verbose);
        return failures;
    }
}
