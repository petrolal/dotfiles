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
		System.out.println("  wallpaper         start OpenGL 3D wallpaper\n");
		System.out.println("Options:");
		System.out.println("  -u, --uninstall   remove all deployed dotfile symlinks safely");
		System.out.println("  -n, --dry-run     simulate actions without modifying filesystem or xfconf");
		System.out.println("  -q, --quiet       suppress non-error output");
		System.out.println("      --links-only  only symlink dotfiles (implies --no-xfconf --no-reload)");
		System.out.println("      --no-xfconf   do not apply XFCE desktop settings via xfconf");
		System.out.println("      --no-reload   do not reload XFCE desktop services");
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
		DEPLOY, UNINSTALL, WALLPAPER
	}

	public static void main(String[] argv) {
		System.exit(run(argv));
	}

	public static int run(String[] argv) {
		boolean dryRun = false;
		boolean verbose = true;
		boolean reload = true;
		boolean applySettings = true;
		Action action = Action.DEPLOY;

		for (String arg : argv) {
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
			}
			case "--no-xfconf" -> applySettings = false;
			case "--no-reload" -> reload = false;
			case "-u", "--uninstall", "uninstall" -> action = Action.UNINSTALL;
			case "deploy" -> action = Action.DEPLOY;
			case "wallpaper" -> action = Action.WALLPAPER;
			default -> {
				System.err.println(PROGRAM_NAME + ": unrecognized option '" + arg + "'");
				System.err.println("Try '" + PROGRAM_NAME + " --help' for more information.");
				return 1;
			}
			}
		}

		boolean ok = switch (action) {
		case UNINSTALL -> Orchestrator.uninstall(dryRun, verbose).ok();
		case DEPLOY -> Orchestrator.deploy(dryRun, verbose, reload, applySettings).ok();
		case WALLPAPER -> Orchestrator.set3DWallpaper(dryRun, verbose).ok();
		};
		return ok ? 0 : 1;
	}
}
