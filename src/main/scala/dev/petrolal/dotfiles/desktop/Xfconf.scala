// Xfconf.scala --- XFCE / GNOME desktop settings
// License: GPL-3.0-or-later
// Ported from Xfconf.java (docs/scala-migration-checklist.md step 3).
package dev.petrolal.dotfiles.desktop

import java.util.concurrent.{ExecutionException, Executors, Future, Semaphore}
import scala.collection.mutable.ListBuffer

import dev.petrolal.dotfiles.core.{DotfilePaths, Theme}
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner

object Xfconf:

  case class XfceSetting(channel: String, property: String, `type`: String, value: String)

  case class GnomeSetting(schema: String, key: String, value: String)

  // Panel plugin ids (xfce4-panel /plugins/plugin-N)
  private val Start = 1
  private val Tasklist = 2
  private val Spacer = 3
  private val Pager = 4
  private val Systray = 6
  private val Clock = 8
  private val FirstLauncher = 19

  /** Quick Launch favourites on the taskbar; each gets its own launcher plugin. */
  private val Launchers: List[String] = List("filemanager", "webbrowser", "appfinder", "terminal")

  /** Desktop backdrop nodes whose image/colour style must be flat ("none"). */
  private val Backdrops: List[String] = List(
    "monitor0/workspace0", "monitoreDP-1/workspace0", "monitoreDP-1/workspace1")

  private val BackdropColor: List[String] = List("0.094", "0.235", "0.259", "1.0") // #183c42

  private def setting(channel: String, property: String, `type`: String, value: String): XfceSetting =
    XfceSetting(channel, property, `type`, value)

  private def panel(property: String, `type`: String, value: String): XfceSetting =
    setting("xfce4-panel", property, `type`, value)

  private def plugin(id: Int, key: String, `type`: String, value: String): XfceSetting =
    panel(s"/plugins/plugin-$id/$key", `type`, value)

  private def xfwm(key: String, `type`: String, value: String): XfceSetting =
    setting("xfwm4", s"/general/$key", `type`, value)

  val XFCE_SETTINGS: List[XfceSetting] = buildXfceSettings()

  private def buildXfceSettings(): List[XfceSetting] =
    val s = ListBuffer[XfceSetting](
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
      panel(s"/plugins/plugin-$Start", "string", "whiskermenu"),
      plugin(Start, "button-title", "string", "Start"),
      plugin(Start, "button-icon", "string", s"~/.themes/${Theme.NAME}/gtk-3.0/assets/start-here.png"),
      plugin(Start, "show-button-title", "bool", "true"),
      plugin(Start, "show-button-icon", "bool", "true"),
      plugin(Start, "view-mode", "int", "1"),
      plugin(Start, "launcher-show-name", "bool", "true"),
      plugin(Start, "launcher-show-description", "bool", "true"),
      plugin(Start, "launcher-icon-size", "int", "2"),
      plugin(Start, "category-icon-size", "int", "1"),
      plugin(Start, "position-categories-alternate", "bool", "true"),
      plugin(Start, "position-categories-horizontal", "bool", "false"),
      plugin(Start, "position-search-alternate", "bool", "true"),
      plugin(Start, "position-commands-alternate", "bool", "false"),
      plugin(Start, "position-profile-alternate", "bool", "false"),
      plugin(Start, "show-command-settings", "bool", "true"),
      plugin(Start, "show-command-lockscreen", "bool", "true"),
      plugin(Start, "show-command-switchuser", "bool", "false"),
      plugin(Start, "show-command-logoutuser", "bool", "false"),
      plugin(Start, "show-command-logout", "bool", "true"),
      plugin(Start, "menu-width", "int", "450"),
      plugin(Start, "menu-height", "int", "530"),

      // Taskbar: ungrouped 3D buttons, 1-row pager, 1-line clock
      plugin(Tasklist, "grouping", "uint", "0"),
      plugin(Tasklist, "flat-buttons", "bool", "false"),
      plugin(Tasklist, "show-handle", "bool", "true"),
      plugin(Spacer, "expand", "bool", "true"),
      plugin(Spacer, "style", "uint", "0"),
      plugin(Pager, "rows", "uint", "1"),
      plugin(Pager, "miniature-view", "bool", "false"),
      plugin(Clock, "mode", "uint", "2"),
      plugin(Clock, "digital-layout", "uint", "3"),
      plugin(Clock, "digital-time-format", "string", "%I:%M %p"),
      plugin(Clock, "digital-time-font", "string", "Sans 8.5"),

      // Panel 1: bottom, full-width taskbar
      panel("/panels/panel-1/mode", "uint", "0"),
      panel("/panels/panel-1/position", "string", "p=8;x=0;y=0"),
      panel("/panels/panel-1/length", "uint", "100"),
      panel("/panels/panel-1/position-locked", "bool", "true"),
      panel("/panels/panel-1/size", "uint", "28"),
      panel("/panels/panel-1/icon-size", "uint", "16")
    )

    for sc <- KeyboardShortcuts.ALL do
      s += setting("xfce4-keyboard-shortcuts", sc.property, "string", sc.command)
    for i <- Launchers.indices do
      s += panel(s"/plugins/plugin-${FirstLauncher + i}", "string", "launcher")
    for node <- Backdrops do
      s += setting("xfce4-desktop", s"/backdrop/screen0/$node/image-style", "int", "0")
      s += setting("xfce4-desktop", s"/backdrop/screen0/$node/color-style", "int", "0")

    s.toList

  val GNOME_SETTINGS: List[GnomeSetting] = List(
    GnomeSetting("org.gnome.desktop.interface", "icon-theme", Theme.ICONS),
    GnomeSetting("org.gnome.desktop.interface", "gtk-theme", Theme.NAME),
    GnomeSetting("org.gnome.desktop.interface", "color-scheme", "default")
  )

  /** Expands a leading ~/ to $HOME. */
  def expandHomeTilde(valStr: String): String =
    if valStr.startsWith("~/") then
      val home = DotfilePaths.userHomeDirectory().toString
      val trimmed = if home.endsWith("/") then home.substring(0, home.length - 1) else home
      trimmed + valStr.substring(1)
    else valStr

  /** Logs the DotfileError on a failed Result, prefixed with context, so the
    * failure actually reaches the user instead of being silently dropped. */
  private def logIfFailed(context: String, result: Result[Int]): Boolean =
    result.ifErr(err => System.err.println(s"[FAIL] $context: ${err.describe}"))
    result.isOk

  def setXfconf(channel: String, property: String, `type`: String, value: String, dryRun: Boolean): Boolean =
    val valStr = expandHomeTilde(value)
    if dryRun then
      println(s"[DRY-RUN] xfconf-query -c $channel -p $property -t ${`type`} -s $valStr")
      true
    else
      logIfFailed(s"$channel $property",
        ProcessRunner.run(List("xfconf-query", "-c", channel, "-p", property, "-s", valStr, "--create", "-t", `type`)))

  def setXfconfArray(channel: String, property: String, `type`: String, values: List[String],
                      dryRun: Boolean, verbose: Boolean): Boolean =
    val cmd = ListBuffer[String]("xfconf-query", "-c", channel, "-p", property, "--create", "-a")
    for v <- values do
      cmd += "-t"
      cmd += `type`
      cmd += "-s"
      cmd += expandHomeTilde(v)
    if dryRun then
      println(s"[DRY-RUN] ${cmd.mkString(" ")}")
      true
    else
      if verbose then println(s"[OK] Setting $channel $property to $values")
      logIfFailed(s"$channel $property", ProcessRunner.run(cmd.toList))

  private def runGsettings(schema: String, key: String, value: String, dryRun: Boolean): Boolean =
    if dryRun then
      println(s"[DRY-RUN] gsettings set $schema $key '$value'")
      true
    else
      logIfFailed(s"$schema $key", ProcessRunner.run(List("gsettings", "set", schema, key, value)))

  /** Max xfconf-query/gsettings subprocesses allowed in flight at once.
    * Each setting is an independent fork+exec+D-Bus round trip with no
    * ordering dependency on any other setting, so running them one at a
    * time (as a plain loop did) made subprocess latency the dominant cost
    * of `deploy`. Bounded rather than unbounded so ~90 settings don't all
    * fork at once and hammer xfconfd/dbus-daemon simultaneously. */
  private val MaxConcurrentSettings = 8

  /** Runs tasks -- each an independent "apply one setting, report success"
    * call -- serially during a dry-run (so the printed plan stays in a
    * readable, deterministic order) or concurrently, bounded to
    * MaxConcurrentSettings at a time, otherwise. */
  private def runTasks(tasks: List[() => Boolean], dryRun: Boolean): Int =
    if dryRun then
      tasks.count(task => !safeCall(task))
    else
      val concurrencyLimit = new Semaphore(MaxConcurrentSettings)
      val executor = Executors.newVirtualThreadPerTaskExecutor()
      try
        val futures: List[Future[Boolean]] = tasks.map { task =>
          executor.submit(() => {
            concurrencyLimit.acquire()
            try task()
            finally concurrencyLimit.release()
          })
        }
        futures.count { future =>
          try !future.get()
          catch case _: InterruptedException | _: ExecutionException => true
        }
      finally executor.close()

  private def safeCall(task: () => Boolean): Boolean =
    try task()
    catch case _: Exception => false

  /** Returns the number of gsettings "set" calls that failed. */
  def applyGnomeSettings(dryRun: Boolean, verbose: Boolean): Int =
    if !DotfilePaths.commandExists("gsettings") then 0
    else
      if verbose then println("Syncing GSettings for GNOME/GTK apps...")
      val tasks = GNOME_SETTINGS.map(s => () => runGsettings(s.schema, s.key, s.value, dryRun))
      runTasks(tasks, dryRun)

  /** Applies every XFCE/GNOME desktop setting and returns the number of
    * underlying subprocess calls that failed, so a deploy cannot report
    * success while every xfconf-query call silently failed. */
  def applyXfceSettings(dryRun: Boolean, verbose: Boolean): Int =
    if !DotfilePaths.commandExists("xfconf-query") then
      if verbose then println("[SKIP] xfconf-query not found, skipping desktop theme configuration.")
      0
    else
      if verbose then println("Applying XFCE desktop settings...")

      val tasks = ListBuffer[() => Boolean]()
      for s <- XFCE_SETTINGS do
        tasks += (() => setXfconf(s.channel, s.property, s.`type`, s.value, dryRun))

      val pluginIds = ListBuffer[String](Start.toString)
      for i <- Launchers.indices do
        val id = FirstLauncher + i
        pluginIds += id.toString
        val item = s"${Launchers(i)}.desktop"
        tasks += (() => setXfconfArray("xfce4-panel", s"/plugins/plugin-$id/items", "string", List(item), dryRun, verbose))

      for id <- List(Tasklist, Spacer, Systray, Clock) do
        pluginIds += id.toString

      tasks += (() => setXfconfArray("xfce4-panel", "/panels", "int", List("1"), dryRun, verbose))
      tasks += (() => setXfconfArray("xfce4-panel", "/panels/panel-1/plugin-ids", "int", pluginIds.toList, dryRun, verbose))

      for node <- Backdrops do
        tasks += (() => setXfconfArray("xfce4-desktop", s"/backdrop/screen0/$node/rgba1", "double", BackdropColor, dryRun, false))

      var failures = runTasks(tasks.toList, dryRun)
      failures += applyGnomeSettings(dryRun, verbose)
      failures
