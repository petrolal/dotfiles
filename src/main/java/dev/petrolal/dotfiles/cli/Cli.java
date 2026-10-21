// Cli.java --- Command-line entry point
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.cli;

import dev.petrolal.dotfiles.core.Orchestrator;
import dev.petrolal.dotfiles.core.Theme;

public final class Cli {
	private Cli() {
	}

	static final String VERSION = "1.3.0";
	static final String PROGRAM_NAME = "invoker";

	private static void printHelp() {
		System.out.printf("Usage: %s [COMMAND|OPTION]...%n%n", PROGRAM_NAME);
		System.out.println("Deploy " + Theme.NAME + " dotfiles symlinks and configure desktop settings.\n");
		System.out.println("Commands:");
		System.out.println("  deploy            perform full deployment (link, configure, reload) [default]");
		System.out.println("  uninstall         remove all deployed dotfile symlinks safely");
		System.out.println("  wallpaper         start OpenGL 3D wallpaper");
		System.out.println("  lock              start the interactive PAM-authenticated screen locker");
		System.out.println("  toggle-hud        toggle the running wallpaper's MangoHud performance overlay\n");
		System.out.println("Options:");
		System.out.println("  -u, --uninstall   remove all deployed dotfile symlinks safely");
		System.out.println("  -n, --dry-run     simulate actions without modifying filesystem or xfconf");
		System.out.println("  -q, --quiet       suppress non-error output");
		System.out.println("      --links-only  only symlink dotfiles (implies --no-xfconf --no-reload --no-wallpaper)");
		System.out.println("      --no-xfconf   do not apply XFCE desktop settings via xfconf");
		System.out.println("      --no-reload   do not reload XFCE desktop services");
		System.out.println("      --no-wallpaper  do not (re)start the 3D wallpaper daemon");
		System.out.println("      --max-fps=N   (wallpaper) cap the render loop to N frames/sec,");
		System.out.println("                    independent of vsync -- trade smoothness for");
		System.out.println("                    battery/thermal headroom on weak hardware");
		System.out.println("      --test-timeout=N  (lock, DEV/TEST ONLY) auto-unlock after N");
		System.out.println("                    seconds with no password check -- never use this");
		System.out.println("                    for a real lock, only for safely testing the grab");
		System.out.println("  -h, --help        display this help text and exit");
		System.out.println("  -v, --version     display version information and exit");
	}

	private static void printVersion() {
		System.out.printf("%s %s (%s / Windows 98)%n", PROGRAM_NAME, VERSION, Theme.NAME);
		System.out.println("License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.");
		System.out.println("This is free software: you are free to change and redistribute it.");
		System.out.println("There is NO WARRANTY, to the extent permitted by law.");
	}

	private enum Action {
		DEPLOY, UNINSTALL, WALLPAPER, TOGGLE_HUD, LOCK
	}

	static void main(String[] argv) {
		System.exit(run(argv));
	}

	public static int run(String[] argv) {
		boolean dryRun = false;
		boolean verbose = true;
		boolean reload = true;
		boolean applySettings = true;
		boolean applyWallpaper = true;
		int maxFps = 0;
		float testTimeoutSeconds = 0f;
		Action action = Action.DEPLOY;

		for (String arg : argv) {
			if (arg.startsWith("--max-fps=")) {
				String value = arg.substring("--max-fps=".length());
				try {
					maxFps = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					maxFps = -1;
				}
				if (maxFps <= 0) {
					System.err.println(PROGRAM_NAME + ": --max-fps requires a positive integer, got '" + value + "'");
					return 1;
				}
				continue;
			}
			if (arg.startsWith("--test-timeout=")) {
				String value = arg.substring("--test-timeout=".length());
				try {
					testTimeoutSeconds = Float.parseFloat(value);
				} catch (NumberFormatException e) {
					testTimeoutSeconds = -1f;
				}
				if (testTimeoutSeconds <= 0f) {
					System.err.println(PROGRAM_NAME + ": --test-timeout requires a positive number, got '" + value + "'");
					return 1;
				}
				continue;
			}
			switch (arg) {
			case "-h", "--help" -> {
				printHelp();
				return 0;
			}
			case "-v", "--version" -> {
				printVersion();
				return 0;
			}
			case "-n", "--dry-run" -> dryRun = true;
			case "-q", "--quiet" -> verbose = false;
			case "--links-only" -> {
				applySettings = false;
				reload = false;
				applyWallpaper = false;
			}
			case "--no-xfconf" -> applySettings = false;
			case "--no-reload" -> reload = false;
			case "--no-wallpaper" -> applyWallpaper = false;
			case "-u", "--uninstall", "uninstall" -> action = Action.UNINSTALL;
			case "deploy" -> action = Action.DEPLOY;
			case "wallpaper" -> action = Action.WALLPAPER;
			case "lock" -> action = Action.LOCK;
			case "toggle-hud" -> action = Action.TOGGLE_HUD;
			default -> {
				System.err.println(PROGRAM_NAME + ": unrecognized option '" + arg + "'");
				System.err.println("Try '" + PROGRAM_NAME + " --help' for more information.");
				return 1;
			}
			}
		}

		boolean ok = switch (action) {
		case UNINSTALL -> Orchestrator.uninstall(dryRun, verbose).ok();
		case DEPLOY -> Orchestrator.deploy(dryRun, verbose, reload, applySettings, applyWallpaper).ok();
		case WALLPAPER -> Orchestrator.set3DWallpaper(dryRun, verbose, maxFps).ok();
		case LOCK -> Orchestrator.lockScreen(dryRun, testTimeoutSeconds);
		case TOGGLE_HUD -> Orchestrator.toggleWallpaperHud(verbose);
		};
		return ok ? 0 : 1;
	}
}
