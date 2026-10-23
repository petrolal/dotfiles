// Cli.scala --- Command-line entry point
// License: GPL-3.0-or-later
// Ported from Cli.java (docs/scala-migration-checklist.md step 8).
package dev.petrolal.dotfiles.cli

import dev.petrolal.dotfiles.core.{Orchestrator, Theme}

object Cli:

  val VERSION: String = "1.3.0"
  val PROGRAM_NAME: String = "imp"

  private def printHelp(): Unit =
    System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME)
    println(s"Deploy ${Theme.NAME} dotfiles symlinks and configure desktop settings.\n")
    println("Commands:")
    println("  deploy            perform full deployment (link, configure, reload) [default]")
    println("  uninstall         remove all deployed dotfile symlinks safely")
    println("  wallpaper         start OpenGL 3D wallpaper")
    println("  lock              start the interactive PAM-authenticated screen locker")
    println("  toggle-hud        toggle the running wallpaper's MangoHud performance overlay\n")
    println("Options:")
    println("  -u, --uninstall   remove all deployed dotfile symlinks safely")
    println("  -n, --dry-run     simulate actions without modifying filesystem or xfconf")
    println("  -q, --quiet       suppress non-error output")
    println("      --links-only  only symlink dotfiles (implies --no-xfconf --no-reload --no-wallpaper)")
    println("      --no-xfconf   do not apply XFCE desktop settings via xfconf")
    println("      --no-reload   do not reload XFCE desktop services")
    println("      --no-wallpaper  do not (re)start the 3D wallpaper daemon")
    println("      --max-fps=N   (wallpaper) cap the render loop to N frames/sec,")
    println("                    independent of vsync -- trade smoothness for")
    println("                    battery/thermal headroom on weak hardware")
    println("      --test-timeout=N  (lock, DEV/TEST ONLY) auto-unlock after N")
    println("                    seconds with no password check -- never use this")
    println("                    for a real lock, only for safely testing the grab")
    println("      --dump-frame=PATH  (lock, DEV/TEST ONLY) render one frame, save it")
    println("                    as a PNG at PATH, then exit -- lets the 3D lock UI")
    println("                    be inspected without a human watching the real screen")
    println("      --inject-password=STR  (lock, DEV/TEST ONLY) synthesize keypresses")
    println("                    for STR + Return right after the grab succeeds, driving")
    println("                    the real typing/PAM-check path with no keyboard needed --")
    println("                    STR is visible via argv/ps, only use a throwaway string")
    println("  -h, --help        display this help text and exit")
    println("  -v, --version     display version information and exit")

  private def printVersion(): Unit =
    System.out.printf("%s %s (%s / Windows 98)%n", PROGRAM_NAME, VERSION, Theme.NAME)
    println("License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.")
    println("This is free software: you are free to change and redistribute it.")
    println("There is NO WARRANTY, to the extent permitted by law.")

  private enum Action:
    case DEPLOY, UNINSTALL, WALLPAPER, TOGGLE_HUD, LOCK

  def main(argv: Array[String]): Unit =
    System.exit(run(argv))

  def run(argv: Array[String]): Int =
    var dryRun = false
    var verbose = true
    var reload = true
    var applySettings = true
    var applyWallpaper = true
    var maxFps = 0
    var testTimeoutSeconds = 0.0f
    var dumpFramePath: String = null
    var injectPassword: String = null
    var action = Action.DEPLOY

    var i = 0
    var earlyReturn: Option[Int] = None

    while i < argv.length && earlyReturn.isEmpty do
      val arg = argv(i)
      if arg.startsWith("--max-fps=") then
        val value = arg.substring("--max-fps=".length)
        maxFps = value.toIntOption.getOrElse(-1)
        if maxFps <= 0 then
          System.err.println(s"$PROGRAM_NAME: --max-fps requires a positive integer, got '$value'")
          earlyReturn = Some(1)
      else if arg.startsWith("--test-timeout=") then
        val value = arg.substring("--test-timeout=".length)
        testTimeoutSeconds = value.toFloatOption.getOrElse(-1.0f)
        if testTimeoutSeconds <= 0.0f then
          System.err.println(s"$PROGRAM_NAME: --test-timeout requires a positive number, got '$value'")
          earlyReturn = Some(1)
      else if arg.startsWith("--dump-frame=") then
        dumpFramePath = arg.substring("--dump-frame=".length)
        if dumpFramePath.isBlank then
          System.err.println(s"$PROGRAM_NAME: --dump-frame requires a file path")
          earlyReturn = Some(1)
      else if arg.startsWith("--inject-password=") then
        injectPassword = arg.substring("--inject-password=".length)
        if injectPassword.isEmpty then
          System.err.println(s"$PROGRAM_NAME: --inject-password requires a non-empty string")
          earlyReturn = Some(1)
      else
        arg match
          case "-h" | "--help" =>
            printHelp()
            earlyReturn = Some(0)
          case "-v" | "--version" =>
            printVersion()
            earlyReturn = Some(0)
          case "-n" | "--dry-run" => dryRun = true
          case "-q" | "--quiet" => verbose = false
          case "--links-only" =>
            applySettings = false
            reload = false
            applyWallpaper = false
          case "--no-xfconf" => applySettings = false
          case "--no-reload" => reload = false
          case "--no-wallpaper" => applyWallpaper = false
          case "-u" | "--uninstall" | "uninstall" => action = Action.UNINSTALL
          case "deploy" => action = Action.DEPLOY
          case "wallpaper" => action = Action.WALLPAPER
          case "lock" => action = Action.LOCK
          case "toggle-hud" => action = Action.TOGGLE_HUD
          case _ =>
            System.err.println(s"$PROGRAM_NAME: unrecognized option '$arg'")
            System.err.println(s"Try '$PROGRAM_NAME --help' for more information.")
            earlyReturn = Some(1)
      i += 1

    earlyReturn match
      case Some(code) => code
      case None =>
        val ok = action match
          case Action.UNINSTALL => Orchestrator.uninstall(dryRun, verbose).ok
          case Action.DEPLOY => Orchestrator.deploy(dryRun, verbose, reload, applySettings, applyWallpaper).ok
          case Action.WALLPAPER => Orchestrator.set3DWallpaper(dryRun, verbose, maxFps).ok
          case Action.LOCK => Orchestrator.lockScreen(dryRun, testTimeoutSeconds, dumpFramePath, injectPassword)
          case Action.TOGGLE_HUD => Orchestrator.toggleWallpaperHud(verbose)
        if ok then 0 else 1
