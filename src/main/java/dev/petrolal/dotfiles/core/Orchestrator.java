// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.petrolal.dotfiles.desktop.Xfconf;
import dev.petrolal.dotfiles.linker.Linker;
import dev.petrolal.dotfiles.system.ProcessRunner;
import dev.petrolal.dotfiles.wallpaper.Wallpaper;

import sun.misc.Signal;

public final class Orchestrator {
	private Orchestrator() {
	}

	public record DeployResult(boolean ok, int failures, int successes) {
	}

	public record UninstallResult(boolean ok, int failures, int removed) {
	}

	public record WallpaperResult(boolean ok, int failures) {
	}

	/** Spawned with Theme.ENV by ProcessRunner.spawnDetached, so restarted
	 * components pick up the theme regardless of the session environment. */
	private static final List<List<String>> RELOAD_COMMANDS = List.of(
			List.of("xfsettingsd", "--replace"),
			List.of("sh", "-c", "if pgrep -x xfce4-panel >/dev/null 2>&1; then xfce4-panel -r; else xfce4-panel & fi"),
			List.of("xfwm4", "--replace"),
			List.of("pkill", "-f", "xfce4-notifyd"),
			List.of("thunar", "-q"));

	/** Reloads active XFCE desktop components if running in an X11 session. */
	public static void reloadDesktopServices(boolean dryRun, boolean verbose) {
		if (System.getenv("DISPLAY") == null) {
			if (verbose)
				System.out.println("[SKIP] No DISPLAY available, skipping desktop reload.");
			return;
		}
		if (verbose)
			System.out.println("Reloading XFCE services...");

		for (List<String> args : RELOAD_COMMANDS) {
			String binary = args.get(0);
			if (!DotfilePaths.commandExists(binary)) {
				if (verbose)
					System.out.println("[SKIP] " + binary + " not found, skipping.");
			} else if (dryRun) {
				System.out.println("[DRY-RUN] Would execute: " + String.join(" ", args));
			} else {
				ProcessRunner.spawnDetached(args);
			}
		}
	}

	public static DeployResult deploy(boolean dryRun, boolean verbose, boolean reload, boolean applySettings,
			boolean applyWallpaper) {
		if (verbose)
			System.out.println("=== Deploying " + Theme.NAME + " Dotfiles ===");
		Path root = DotfilePaths.findDotfilesRoot();
		Path home = DotfilePaths.userHomeDirectory();
		int failures = 0;
		int successes = 0;
		if (verbose) {
			System.out.println("Root:   " + root);
			System.out.println("Target: " + home);
		}

		for (Linker.Mapping mapping : Linker.collectAllMappings(root)) {
			if (Linker.linkFile(mapping.from(), mapping.to(), root, home, dryRun, verbose).isOk())
				successes++;
			else
				failures++;
		}

		if (applySettings) {
			failures += Xfconf.applyXfceSettings(dryRun, verbose);
			if (reload)
				reloadDesktopServices(dryRun, verbose);
		}

		// The config/autostart/invoker-wallpaper.desktop entry (symlinked by
		// the loop above like any other config/ file) covers future logins;
		// this covers the current session, so `deploy` leaves the freshest
		// build actually running immediately instead of only promising it for
		// next time.
		if (applyWallpaper) {
			if (System.getenv("DISPLAY") == null) {
				if (verbose)
					System.out.println("[SKIP] No DISPLAY available, skipping wallpaper daemon (re)start.");
			} else if (dryRun) {
				if (verbose)
					System.out.println("[DRY-RUN] Would (re)start the 3D wallpaper daemon.");
			} else {
				restartWallpaperDaemon(verbose);
			}
		}

		if (verbose) {
			if (failures == 0) {
				System.out.println("Deployment finished successfully.");
			} else {
				System.err.println("Deployment finished with " + failures + " failure(s).");
			}
		}
		return new DeployResult(failures == 0, failures, successes);
	}

	/** Stops any already-running `invoker wallpaper` daemon (so a redeploy
	 * actually picks up a freshly rebuilt binary or shader instead of leaving
	 * the old process running alongside a new one) and starts a fresh one,
	 * detached from this process. */
	private static void restartWallpaperDaemon(boolean verbose) {
		stopRunningWallpaperDaemons(verbose);

		Optional<String> exePath = ProcessHandle.current().info().command();
		if (exePath.isEmpty()) {
			if (verbose)
				System.out.println("[SKIP] Could not determine this executable's path; not starting the wallpaper daemon.");
			return;
		}
		if (verbose)
			System.out.println("Starting 3D wallpaper daemon...");
		ProcessRunner.spawnDetached(List.of(exePath.get(), "wallpaper", "--quiet"));
	}

	private static boolean isWallpaperDaemon(ProcessHandle ph) {
		String commandLine = ph.info().commandLine().orElse("");
		return commandLine.contains("invoker") && commandLine.contains("wallpaper");
	}

	private static void stopRunningWallpaperDaemons(boolean verbose) {
		List<ProcessHandle> existing = ProcessHandle.allProcesses()
				.filter(Orchestrator::isWallpaperDaemon)
				.toList();
		if (existing.isEmpty())
			return;

		if (verbose)
			System.out.println("Stopping " + existing.size() + " running wallpaper daemon process(es)...");
		List<CompletableFuture<ProcessHandle>> exits = new ArrayList<>();
		for (ProcessHandle ph : existing) {
			ph.destroy();
			exits.add(ph.onExit());
		}
		try {
			// Our own daemon's shutdown-hook watchdog gives up after 2s (see
			// Wallpaper.start()); wait slightly longer than that for a clean
			// handoff, but this is best-effort -- if it's still not gone, the
			// new instance starts anyway rather than blocking deploy on it.
			CompletableFuture.allOf(exits.toArray(new CompletableFuture[0])).get(3, TimeUnit.SECONDS);
		} catch (Exception ignored) {
		}
	}

	public static UninstallResult uninstall(boolean dryRun, boolean verbose) {
		if (verbose)
			System.out.println("=== Removing " + Theme.NAME + " Dotfiles Symlinks ===");
		Path root = DotfilePaths.findDotfilesRoot();
		Path home = DotfilePaths.userHomeDirectory();
		int failures = 0;
		int removedCount = 0;
		if (verbose)
			System.out.println("Target: " + home);

		for (Linker.Mapping mapping : Linker.collectAllMappings(root)) {
			switch (Linker.unlinkFile(mapping.to(), home, dryRun, verbose)) {
			case REMOVED -> removedCount++;
			case FAILED -> failures++;
			case SKIPPED -> {
			}
			}
		}

		if (verbose) {
			if (dryRun) {
				System.out.println("Dry-run complete. " + removedCount + " symlink(s) would be removed.");
			} else {
				String failureSuffix = failures > 0 ? ", " + failures + " failure(s)" : "";
				System.out.println(
						"Uninstallation complete. " + removedCount + " symlink(s) removed" + failureSuffix + ".");
			}
		}
		return new UninstallResult(failures == 0, failures, removedCount);
	}

	public static WallpaperResult set3DWallpaper(boolean dryRun, boolean verbose, int maxFps) {
		if (verbose) {
			System.out.println("=== Applying 3D Wallpaper ===");
		}

		if (dryRun) {
			if (verbose) {
				System.out.println("[DRY-RUN] Would start OpenGL 3D wallpaper daemon.");
			}
			return new WallpaperResult(true, 0);
		}

		// On hybrid Intel+NVIDIA laptops where an output is physically wired to
		// the discrete GPU (common for HDMI/DP on mux-less gaming laptops),
		// rendering through the default (Intel) GLX driver forces every frame
		// bound for that output through a cross-GPU copy before it can scan
		// out -- confirmed on real hardware to cause visible cursor/animation
		// stutter on that monitor specifically. Relaunching through the
		// NixOS-provided `nvidia-offload` wrapper (hardware.nvidia.prime.offload
		// in nixos/modules/nvidia.nix) renders the whole scene on the GPU that
		// already owns the display pipeline instead of whichever one GLX's
		// default vendor selection happens to pick.
		//
		// __NV_PRIME_RENDER_OFFLOAD is the guard against relaunching forever:
		// nvidia-offload sets it before exec-ing into us again, so the second
		// run through here sees it already set and renders in-process as normal.
		if (System.getenv("__NV_PRIME_RENDER_OFFLOAD") == null && DotfilePaths.commandExists("nvidia-offload")) {
			Optional<String> exePath = ProcessHandle.current().info().command();
			if (exePath.isPresent()) {
				return relaunchWithNvidiaOffload(exePath.get(), verbose, maxFps);
			}
			// Can't determine our own executable path to relaunch -- fall through
			// and render in-process on whatever GPU GLX picks by default.
		}

		try {
			new Wallpaper().start(maxFps);
			return new WallpaperResult(true, 0);
		} catch (Throwable ex) {
			System.err.println("Error on wallpaper application: " + ex.getMessage());
			return new WallpaperResult(false, 1);
		}
	}

	/** Re-execs this same command through `nvidia-offload`, inheriting this
	 * process's stdio/terminal so the relaunched process stays in the same
	 * foreground process group -- Ctrl+C (terminal SIGINT) reaches both
	 * processes directly via normal job-control semantics, no forwarding
	 * needed for that case. But an explicit `kill -TERM <this-pid>` (not a
	 * terminal signal, not process-group-wide) targets only this thin
	 * launcher by default, leaving the real daemon orphaned but still
	 * running -- confirmed on real hardware before this forwarding was
	 * added. So TERM and INT are both explicitly forwarded to the child
	 * here too, making this launcher transparent to either shutdown path.
	 * Blocks until the relaunched daemon exits and relays its status. */
	private static WallpaperResult relaunchWithNvidiaOffload(String exePath, boolean verbose, int maxFps) {
		if (verbose) {
			System.out.println("Relaunching via nvidia-offload (an output on this machine is wired to the discrete GPU)...");
		}
		List<String> cmd = new ArrayList<>(List.of("nvidia-offload", exePath, "wallpaper"));
		if (!verbose)
			cmd.add("--quiet");
		if (maxFps > 0)
			cmd.add("--max-fps=" + maxFps);
		try {
			Process child = new ProcessBuilder(cmd).inheritIO().start();

			Signal.handle(new Signal("TERM"), sig -> child.destroy());
			Signal.handle(new Signal("INT"), sig -> {
				try {
					new ProcessBuilder("kill", "-INT", String.valueOf(child.pid())).start().waitFor();
				} catch (IOException | InterruptedException ignored) {
					if (ignored instanceof InterruptedException)
						Thread.currentThread().interrupt();
				}
			});

			int exitCode = child.waitFor();
			return new WallpaperResult(exitCode == 0, exitCode == 0 ? 0 : 1);
		} catch (IOException e) {
			System.err.println("Error relaunching wallpaper via nvidia-offload: " + e.getMessage());
			return new WallpaperResult(false, 1);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new WallpaperResult(false, 1);
		}
	}
}
