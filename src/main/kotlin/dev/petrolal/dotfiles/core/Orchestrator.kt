// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core

import dev.petrolal.dotfiles.linker.Linker
import dev.petrolal.dotfiles.linker.Linker.UnlinkOutcome
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


/**
 * Composes the focused, single-purpose collaborators below into the CLI's
 * commands (deploy, uninstall). This class's one reason to change is how
 * those commands are sequenced -- not how linking actually works, which
 * lives in its own class:
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

        // Mappings are independent, mostly-blocking filesystem I/O, so they're linked
        // concurrently on virtual threads; ensuredDirs must then be a thread-safe set.
        val ensuredDirs: MutableSet<Path> = ConcurrentHashMap.newKeySet()
        val mappings = Linker.collectAllMappings(root)
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val outcomes = mappings
                .map { (from, to) -> executor.submit(Callable { Linker.linkFile(from, to, root, home, dryRun, verbose, ensuredDirs).isOk() }) }
                .map { it.get() }
            successes = outcomes.count { it }
            failures = outcomes.size - successes
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

        val mappings = Linker.collectAllMappings(root)
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val outcomes = mappings
                .map { mapping -> executor.submit(Callable { Linker.unlinkFile(mapping.to, home, dryRun, verbose) }) }
                .map { it.get() }
            removedCount = outcomes.count { it == UnlinkOutcome.REMOVED }
            failures = outcomes.count { it == UnlinkOutcome.FAILED }
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
