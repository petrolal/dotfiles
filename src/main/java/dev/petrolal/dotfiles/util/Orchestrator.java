// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.util;


import java.nio.file.Path;
import java.util.List;

import dev.petrolal.dotfiles.conf.DotfilePaths;
import dev.petrolal.dotfiles.conf.Xfconf;
import dev.petrolal.dotfiles.domain.Result;
import dev.petrolal.dotfiles.terminal.Shell;

public final class Orchestrator {
    private Orchestrator() {}

    record DeployResult(boolean ok, int failures, int successes) {}

    record UninstallResult(boolean ok, int failures, int removed) {}

    private static final List<List<String>> RELOAD_COMMANDS = List.of(
            List.of("xfsettingsd", "--replace"),
            List.of("xfce4-panel", "-r"),
            List.of("xfwm4", "--replace"),
            List.of("pkill", "-f", "xfce4-notifyd"),
            List.of("thunar", "-q")
    );

    /** Reloads active XFCE desktop components if running in an X11 session. */
    static void reloadDesktopServices(boolean dryRun, boolean verbose) {
        if (System.getenv("DISPLAY") == null) {
            if (verbose) System.out.println("[SKIP] No DISPLAY available, skipping desktop reload.");
            return;
        }
        if (verbose) System.out.println("Reloading XFCE services...");
        for (List<String> args : RELOAD_COMMANDS) {
            String binary = args.get(0);
            if (!DotfilePaths.commandExists(binary)) {
                if (verbose) System.out.println("[SKIP] " + binary + " not found, skipping.");
            } else if (dryRun) {
                System.out.println("[DRY-RUN] Would execute: " + String.join(" ", args));
            } else {
                Shell.spawnDetached(args);
            }
        }
    }

    static DeployResult deploy(boolean dryRun, boolean verbose, boolean reload,
                                boolean applySettings, boolean generateConfigs) {
        if (verbose) System.out.println("=== Deploying Abyssal Biopunk / Infernal Retro Dotfiles ===");
        Path root = DotfilePaths.findDotfilesRoot();
        Path home = DotfilePaths.userHomeDirectory();
        int failures = 0;
        int successes = 0;
        if (verbose) {
            System.out.println("Root:   " + root);
            System.out.println("Target: " + home);
        }
        if (generateConfigs) {
            if (Generators.generateAllConfigs(verbose)) successes++; else failures++;
        }
        Linker.cleanupLegacyTargets(home, dryRun, verbose);
        for (Linker.Mapping mapping : Linker.collectAllMappings(root)) {
            Result<Void> r = Linker.linkFile(mapping.from(), mapping.to(), root, home, dryRun, verbose);
            if (r instanceof Result.Ok<Void>) successes++; else failures++;
        }
        if (applySettings) {
            Xfconf.applyXfceSettings(root, dryRun, verbose);
        }
        if (reload && applySettings) {
            reloadDesktopServices(dryRun, verbose);
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

    static UninstallResult uninstall(boolean dryRun, boolean verbose) {
        if (verbose) System.out.println("=== Removing Abyssal Biopunk / Infernal Retro Dotfiles Symlinks ===");
        Path root = DotfilePaths.findDotfilesRoot();
        Path home = DotfilePaths.userHomeDirectory();
        int failures = 0;
        int removedCount = 0;
        if (verbose) System.out.println("Target: " + home);
        for (Linker.Mapping mapping : Linker.collectAllMappings(root)) {
            Linker.UnlinkOutcome outcome = Linker.unlinkFile(mapping.to(), home, dryRun, verbose);
            if (outcome == Linker.UnlinkOutcome.REMOVED) removedCount++;
            else if (outcome == Linker.UnlinkOutcome.FAILED) failures++;
        }
        removedCount += Linker.cleanupLegacyTargets(home, dryRun, verbose);
        if (verbose) {
            if (dryRun) {
                System.out.println("Dry-run complete. " + removedCount + " symlink(s) would be removed.");
            } else {
                String failureSuffix = failures > 0 ? ", " + failures + " failure(s)" : "";
                System.out.println("Uninstallation complete. " + removedCount + " symlink(s) removed" + failureSuffix + ".");
            }
        }
        return new UninstallResult(failures == 0, failures, removedCount);
    }
}
