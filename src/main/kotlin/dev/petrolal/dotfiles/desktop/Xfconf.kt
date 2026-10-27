// Xfconf.java --- XFCE / GNOME desktop settings
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.core.Theme
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner
import java.util.concurrent.*

object Xfconf {
    // Panel plugin ids (xfce4-panel /plugins/plugin-N)
    private const val START = 1
    private const val TASKLIST = 2
    private const val SPACER = 3
    private const val PAGER = 4
    private const val SYSTRAY = 6
    private const val CLOCK = 8
    private const val FIRST_LAUNCHER = 19

    /** Quick Launch favourites on the taskbar; each gets its own launcher plugin.  */
    private val LAUNCHERS: List<String> = listOf("filemanager", "webbrowser", "appfinder", "terminal")

    /** Desktop backdrop nodes whose image/colour style must be flat ("none").  */
    private val BACKDROPS: List<String> = listOf(
        "monitor0/workspace0", "monitoreDP-1/workspace0", "monitoreDP-1/workspace1"
    )

    private val BACKDROP_COLOR: List<String> = listOf("0.094", "0.235", "0.259", "1.0") // #183c42

    private fun setting(channel: String, property: String, type: String, value: String): XfceSetting {
        return XfceSetting(channel, property, type, value)
    }

    private fun panel(property: String, type: String, value: String): XfceSetting {
        return setting("xfce4-panel", property, type, value)
    }

    private fun plugin(id: Int, key: String, type: String, value: String): XfceSetting {
        return panel("/plugins/plugin-$id/$key", type, value)
    }

    private fun xfwm(key: String, type: String, value: String): XfceSetting {
        return setting("xfwm4", "/general/$key", type, value)
    }

    @JvmField
    val XFCE_SETTINGS: List<XfceSetting> = buildXfceSettings()

    private fun buildXfceSettings(): List<XfceSetting> {
        val s = mutableListOf(
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
            setting("xfce4-notifyd", "/expire-timeout", "int", "5"),
            setting("xfce4-notifyd", "/expire-timeout-allow-override", "bool", "false"),

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
            panel("/plugins/plugin-$START", "string", "whiskermenu"),
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
            plugin(START, "show-command-logout", "bool", "true"),
            plugin(START, "show-command-menueditor", "bool", "true"),

            // Taskbar / spacer / pager / tray / clock plugins
            panel("/plugins/plugin-$TASKLIST", "string", "tasklist"),
            plugin(TASKLIST, "flat-buttons", "bool", "false"),
            plugin(TASKLIST, "show-labels", "bool", "true"),
            plugin(TASKLIST, "grouping", "uint", "0"),
            plugin(TASKLIST, "show-handle", "bool", "false"),
            panel("/plugins/plugin-$SPACER", "string", "separator"),
            plugin(SPACER, "expand", "bool", "true"),
            plugin(SPACER, "style", "uint", "0"),
            panel("/plugins/plugin-$PAGER", "string", "pager"),
            plugin(PAGER, "rows", "uint", "1"),
            plugin(PAGER, "miniature-view", "bool", "false"),
            panel("/plugins/plugin-$SYSTRAY", "string", "systray"),
            plugin(SYSTRAY, "square-icons", "bool", "true"),
            panel("/plugins/plugin-$CLOCK", "string", "clock"),
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
            panel("/panels/panel-1/icon-size", "uint", "16")
        )

        for (sc in KeyboardShortcuts.ALL) {
            s.add(setting("xfce4-keyboard-shortcuts", sc.property, "string", sc.command))
        }
        for (i in LAUNCHERS.indices) {
            s.add(panel("/plugins/plugin-" + (FIRST_LAUNCHER + i), "string", "launcher"))
        }
        for (node in BACKDROPS) {
            s.add(setting("xfce4-desktop", "/backdrop/screen0/$node/image-style", "int", "0"))
            s.add(setting("xfce4-desktop", "/backdrop/screen0/$node/color-style", "int", "0"))
        }
        return s.toList()
    }

    @JvmField
    val GNOME_SETTINGS: List<GnomeSetting> = listOf(
        GnomeSetting("org.gnome.desktop.interface", "icon-theme", Theme.ICONS),
        GnomeSetting("org.gnome.desktop.interface", "gtk-theme", Theme.NAME),
        GnomeSetting("org.gnome.desktop.interface", "color-scheme", "default")
    )

    /** Expands a leading ~/ to $HOME.  */
    @JvmStatic
    fun expandHomeTilde(valStr: String): String {
        if (valStr.startsWith("~/")) {
            val home = DotfilePaths.userHomeDirectory().toString()
            val trimmed = if (home.endsWith("/")) home.substring(0, home.length - 1) else home
            return trimmed + valStr.substring(1)
        }
        return valStr
    }

    /** Logs the DotfileError on a failed Result, prefixed with CONTEXT, so the
     * failure actually reaches the user instead of being silently dropped.  */
    private fun logIfFailed(context: String, result: Result<Int>): Boolean {
        result.ifErr { err -> System.err.println("[FAIL] $context: ${err.describe()}") }
        return result.isOk()
    }

    @JvmStatic
    fun setXfconf(channel: String, property: String, type: String, value: String, dryRun: Boolean): Boolean {
        val valStr = expandHomeTilde(value)
        if (dryRun) {
            System.out.printf("[DRY-RUN] xfconf-query -c %s -p %s -t %s -s %s%n", channel, property, type, valStr)
            return true
        }
        return logIfFailed(
            "$channel $property",
            ProcessRunner.run(
                listOf(
                    "xfconf-query",
                    "-c",
                    channel,
                    "-p",
                    property,
                    "-s",
                    valStr,
                    "--create",
                    "-t",
                    type
                )
            )
        )
    }

    @JvmStatic
    fun resetXfconf(channel: String, property: String, dryRun: Boolean): Boolean {
        if (dryRun) {
            System.out.printf("[DRY-RUN] xfconf-query -c %s -p %s -r%n", channel, property)
            return true
        }
        ProcessRunner.run(listOf("xfconf-query", "-c", channel, "-p", property, "-r"))
        return true
    }

    @JvmStatic
    fun setXfconfArray(
        channel: String, property: String, type: String, values: List<String>,
        dryRun: Boolean, verbose: Boolean
    ): Boolean {
        val cmd = mutableListOf("xfconf-query", "-c", channel, "-p", property, "--create", "-a")
        for (v in values) {
            cmd.add("-t")
            cmd.add(type)
            cmd.add("-s")
            cmd.add(expandHomeTilde(v))
        }
        if (dryRun) {
            println("[DRY-RUN] ${cmd.joinToString(" ")}")
            return true
        }
        if (verbose) println("[OK] Setting $channel $property to $values")
        return logIfFailed("$channel $property", ProcessRunner.run(cmd))
    }

    private fun runGsettings(
        schema: String,
        key: String,
        value: String,
        dryRun: Boolean
    ): Boolean {
        if (dryRun) {
            System.out.printf("[DRY-RUN] gsettings set %s %s '%s'%n", schema, key, value)
            return true
        }
        return logIfFailed(
            "$schema $key",
            ProcessRunner.run(listOf("gsettings", "set", schema, key, value))
        )
    }

    /** Max xfconf-query/gsettings subprocesses allowed in flight at once.
     * Each setting is an independent fork+exec+D-Bus round trip with no
     * ordering dependency on any other setting, so running them one at a
     * time (as a plain loop did) made subprocess latency the dominant cost
     * of `deploy`. Bounded rather than unbounded so ~90 settings don't all
     * fork at once and hammer xfconfd/dbus-daemon simultaneously.  */
    private const val MAX_CONCURRENT_SETTINGS = 8

    /** Runs TASKS -- each an independent "apply one setting, report success"
     * call -- serially during a dry-run (so the printed plan stays in a
     * readable, deterministic order) or concurrently, bounded to
     * [.MAX_CONCURRENT_SETTINGS] at a time, otherwise.  */
    private fun runTasks(tasks: List<Callable<Boolean>>, dryRun: Boolean): Int {
        if (dryRun) {
            var failures = 0
            for (task in tasks) {
                try {
                    if (!task.call()) failures++
                } catch (e: Exception) {
                    failures++
                }
            }
            return failures
        }

        val concurrencyLimit = Semaphore(MAX_CONCURRENT_SETTINGS)
        val futures = ArrayList<Future<Boolean>>(tasks.size)
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            for (task in tasks) {
                futures.add(executor.submit(Callable {
                    concurrencyLimit.acquire()
                    try {
                        task.call()
                    } finally {
                        concurrencyLimit.release()
                    }
                }))
            }
            var failures = 0
            for (future in futures) {
                try {
                    if (!future.get()) failures++
                } catch (e: InterruptedException) {
                    failures++
                } catch (e: ExecutionException) {
                    failures++
                }
            }
            return failures
        }
    }

    /** Returns the number of gsettings "set" calls that failed.  */
    @JvmStatic
    fun applyGnomeSettings(dryRun: Boolean, verbose: Boolean): Int {
        if (!DotfilePaths.commandExists("gsettings")) return 0
        if (verbose) println("Syncing GSettings for GNOME/GTK apps...")
        val tasks = ArrayList<Callable<Boolean>>()
        for (s in GNOME_SETTINGS) {
            tasks.add(Callable { runGsettings(s.schema, s.key, s.value, dryRun) })
        }
        return runTasks(tasks, dryRun)
    }

    /** Applies every XFCE/GNOME desktop setting and returns the number of
     * underlying subprocess calls that failed, so a deploy cannot report
     * success while every xfconf-query call silently failed.  */
    @JvmStatic
    fun applyXfceSettings(dryRun: Boolean, verbose: Boolean): Int {
        if (!DotfilePaths.commandExists("xfconf-query")) {
            if (verbose) println("[SKIP] xfconf-query not found, skipping desktop theme configuration.")
            return 0
        }
        if (verbose) println("Applying XFCE desktop settings...")

        val tasks = ArrayList<Callable<Boolean>>()
        for (s in XFCE_SETTINGS) {
            tasks.add(Callable { setXfconf(s.channel, s.property, s.type, s.value, dryRun) })
        }
        for (prop in KeyboardShortcuts.DISABLED) {
            tasks.add(Callable { resetXfconf("xfce4-keyboard-shortcuts", prop, dryRun) })
        }

        val pluginIds = mutableListOf(START.toString())
        for (i in LAUNCHERS.indices) {
            val id = FIRST_LAUNCHER + i
            pluginIds.add(id.toString())
            val item = LAUNCHERS[i] + ".desktop"
            tasks.add(Callable {
                setXfconfArray(
                    "xfce4-panel", "/plugins/plugin-$id/items", "string",
                    listOf(item), dryRun, verbose
                )
            })
        }
        for (id in intArrayOf(TASKLIST, SPACER, SYSTRAY, CLOCK)) {
            pluginIds.add(id.toString())
        }
        tasks.add(Callable {
            setXfconfArray(
                "xfce4-panel",
                "/panels",
                "int",
                listOf("1"),
                dryRun,
                verbose
            )
        })
        tasks.add(Callable {
            setXfconfArray(
                "xfce4-panel",
                "/panels/panel-1/plugin-ids",
                "int",
                pluginIds,
                dryRun,
                verbose
            )
        })

        for (node in BACKDROPS) {
            tasks.add(Callable {
                setXfconfArray(
                    "xfce4-desktop", "/backdrop/screen0/$node/rgba1", "double",
                    BACKDROP_COLOR, dryRun, false
                )
            })
        }

        var failures = runTasks(tasks, dryRun)
        failures += applyGnomeSettings(dryRun, verbose)
        return failures
    }

    @JvmRecord
    data class XfceSetting(
        val channel: String,
        val property: String,
        val type: String,
        val value: String
    )

    @JvmRecord
    data class GnomeSetting(val schema: String, val key: String, val value: String)
}
