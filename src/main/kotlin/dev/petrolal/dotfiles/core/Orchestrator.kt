// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core

import dev.petrolal.dotfiles.linker.Linker
import dev.petrolal.dotfiles.linker.Linker.UnlinkOutcome


/**
 * Composes the focused, single-purpose collaborators below into the CLI's
 * commands (deploy, uninstall, overlay). This class's one reason to change
 * is how those commands are sequenced -- not how linking actually works,
 * which lives in its own class:
 *
 *  * [Linker] -- symlink discovery/creation/removal
 *
 */
object Orchestrator {
    fun deploy(dryRun: Boolean, verbose: Boolean): DeployResult {
        if (verbose) println("=== Deploying Dotfiles ===")
        val root = DotfilePaths.findDotfilesRoot()
        val home = DotfilePaths.userHomeDirectory()
        var failures = 0
        var successes = 0
        if (verbose) {
            println("Root:   $root")
            println("Target: $home")
        }

        for ((from, to) in Linker.collectAllMappings(root)) {
            if (Linker.linkFile(from, to, root, home, dryRun, verbose).isOk()) successes++
            else failures++
        }

        if (verbose) {
            if (failures == 0) {
                println("Deployment finished successfully.")
            } else {
                System.err.println("Deployment finished with $failures failure(s).")
            }
        }
        return DeployResult(failures == 0, failures, successes)
    }

    fun uninstall(dryRun: Boolean, verbose: Boolean): UninstallResult {
        if (verbose) println("=== Removing Dotfiles Symlinks ===")
        val root = DotfilePaths.findDotfilesRoot()
        val home = DotfilePaths.userHomeDirectory()
        var failures = 0
        var removedCount = 0
        if (verbose) println("Target: $home")

        for (mapping in Linker.collectAllMappings(root)) {
            when (Linker.unlinkFile(mapping.to, home, dryRun, verbose)) {
                UnlinkOutcome.REMOVED -> removedCount++
                UnlinkOutcome.FAILED -> failures++
                UnlinkOutcome.SKIPPED -> {
                }
            }
        }

        if (verbose) {
            if (dryRun) {
                println("Dry-run complete. $removedCount symlink(s) would be removed.")
            } else {
                val failureSuffix = if (failures > 0) ", $failures failure(s)" else ""
                println(
                    "Uninstallation complete. $removedCount symlink(s) removed$failureSuffix."
                )
            }
        }
        return UninstallResult(failures == 0, failures, removedCount)
    }



    @JvmRecord
    data class DeployResult(val ok: Boolean, val failures: Int, val successes: Int)

    @JvmRecord
    data class UninstallResult(val ok: Boolean, val failures: Int, val removed: Int)
}
