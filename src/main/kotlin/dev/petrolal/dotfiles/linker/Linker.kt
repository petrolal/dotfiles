// Linker.java --- Symlink discovery, creation, and removal
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.linker

import dev.petrolal.dotfiles.core.Theme
import dev.petrolal.dotfiles.domain.DotfileError.FileNotFound
import dev.petrolal.dotfiles.domain.DotfileError.IoError
import dev.petrolal.dotfiles.domain.Result
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

object Linker {
    /** Source prefixes (relative to the repo root) auto-discovered and mirrored
     * file-by-file under the given target base (relative to $HOME). Longest
     * matching prefix wins, so a more specific entry can override a general one.  */
    @JvmField
    val ROOT_TARGETS: List<Mapping> = listOf(
        Mapping("config/applications", ".local/share/applications"),
        Mapping("config/icons", ".local/share/icons"),
        Mapping("config", ".config")
    )

    /** Source paths skipped entirely during auto-discovery; a listed directory
     * is not descended into.  */
    @JvmField
    val EXCLUDES: List<String> = listOf(
        "config/gtk-2.0/gtkrc"
    )

    /** Explicit (source -> target) pairs for paths that don't fit the generic
     * root-mirrored layout: renames, or a source linked to more than one target.  */
    @JvmField
    val OVERRIDES: List<Mapping> = listOf(
        Mapping("config/gtk-2.0/gtkrc", ".gtkrc-2.0"),
        Mapping("themes/" + Theme.NAME, ".themes/" + Theme.NAME),
        Mapping("themes/" + Theme.NAME, ".local/share/themes/" + Theme.NAME)
    )

    @JvmStatic
    fun isExcluded(relpath: String): Boolean {
        for (prefix in EXCLUDES) {
            if (isPrefixMatch(prefix, relpath)) return true
        }
        return false
    }

    @JvmStatic
    fun isPrefixMatch(prefix: String, relpath: String): Boolean {
        return relpath.length >= prefix.length && relpath.startsWith(prefix)
                && (relpath.length == prefix.length || relpath[prefix.length] == '/')
    }

    @JvmStatic
    fun rootTargetFor(relpath: String): Optional<Mapping> {
        var best: Mapping? = null
        for (entry in ROOT_TARGETS) {
            if (isPrefixMatch(
                    entry.from,
                    relpath
                ) && (best == null || entry.from.length > best.from.length)
            ) {
                best = entry
            }
        }
        return Optional.ofNullable(best)
    }

    @JvmStatic
    fun topLevelRoots(): List<String> {
        val prefixes: List<String> = ROOT_TARGETS.map { it.from }
        val out = ArrayList<String>()
        for (prefix in prefixes) {
            val nested = prefixes.any { other -> other != prefix && isPrefixMatch(other, prefix) }
            if (!nested) out.add(prefix)
        }
        return out
    }

    @JvmStatic
    fun scanTree(relpath: String, root: Path): List<String> {
        val out = ArrayList<String>()
        val dir = root.resolve(relpath)
        if (!Files.isDirectory(dir)) return out
        val entries: List<Path> = try {
            Files.list(dir).use { stream ->
                stream.sorted().toList()
            }
        } catch (_: IOException) {
            return out
        }
        for (entry in entries) {
            val childRel = relpath + "/" + entry.fileName
            if (isExcluded(childRel)) continue
            if (Files.isDirectory(entry)) {
                out.addAll(scanTree(childRel, root))
            } else {
                out.add(childRel)
            }
        }
        return out
    }

    @JvmStatic
    fun collectAllMappings(root: Path): List<Mapping> {
        val out = ArrayList<Mapping>(OVERRIDES)
        for (top in topLevelRoots()) {
            for (fileRel in scanTree(top, root)) {
                rootTargetFor(fileRel).ifPresent { entry ->
                    out.add(
                        Mapping(
                            fileRel,
                            entry.to + fileRel.substring(entry.from.length)
                        )
                    )
                }
            }
        }
        return out
    }

    @Throws(IOException::class)
    private fun deleteRecursively(path: Path) {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    @JvmStatic
    @Throws(IOException::class)
    fun ensureRealDirectory(dir: Path?, home: Path) {
        if (dir == null) return
        val rel: Path = try {
            home.relativize(dir)
        } catch (e: IllegalArgumentException) {
            Files.createDirectories(dir)
            return
        }
        var current = home
        for (part in rel) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) {
                Files.delete(current)
            }
        }
        Files.createDirectories(dir)
    }

    @Throws(IOException::class)
    private fun atomicSymlink(dest: Path, target: Path) {
        val parent = dest.parent ?: Paths.get(".")
        val tmp = Files.createTempFile(parent, dest.fileName.toString(), ".tmp-symlink")
        Files.delete(tmp)
        Files.createSymbolicLink(tmp, target)
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @JvmStatic
    fun linkFile(
        sourceRel: String, targetRel: String, root: Path, home: Path,
        dryRun: Boolean, verbose: Boolean
    ): Result<Void?> {
        val src = root.resolve(sourceRel)
        val dest = home.resolve(targetRel)
        if (!Files.exists(src)) {
            if (verbose) System.err.println("[SKIP] Source missing: " + src)
            return Result.err(FileNotFound(src))
        }
        if (dryRun) {
            println("[DRY-RUN] Would link: " + dest + " -> " + src)
            return Result.ok(null)
        }
        try {
            ensureRealDirectory(dest.parent, home)
            if (Files.isDirectory(src) && Files.isDirectory(dest) && !Files.isSymbolicLink(dest)) {
                deleteRecursively(dest)
            }
            atomicSymlink(dest, src)
            if (verbose) println("[OK] Linked: " + dest + " -> " + src)
            return Result.ok(null)
        } catch (e: IOException) {
            System.err.println("[FAIL] Failed to link " + dest + " -> " + src)
            return Result.err(IoError(e.message ?: ""))
        }
    }

    @JvmStatic
    fun unlinkFile(targetRel: String, home: Path, dryRun: Boolean, verbose: Boolean): UnlinkOutcome {
        val dest = home.resolve(targetRel)
        if (!Files.isSymbolicLink(dest)) {
            if (verbose) {
                if (Files.exists(dest)) {
                    System.err.println("[SKIP] Not a symlink: " + dest + " (refusing to delete)")
                } else {
                    println("[SKIP] Symlink does not exist: " + dest)
                }
            }
            return UnlinkOutcome.SKIPPED
        }
        if (dryRun) {
            println("[DRY-RUN] Would remove symlink: " + dest)
            return UnlinkOutcome.REMOVED
        }
        try {
            Files.delete(dest)
            if (verbose) println("[OK] Removed symlink: " + dest)
            return UnlinkOutcome.REMOVED
        } catch (e: IOException) {
            System.err.println("[FAIL] Failed to remove symlink " + dest)
            return UnlinkOutcome.FAILED
        }
    }

    /** A (from -> to) path pair: used both for root-target prefixes and resolved mappings.  */
    @JvmRecord
    data class Mapping(val from: String, val to: String)

    enum class UnlinkOutcome {
        SKIPPED, REMOVED, FAILED
    }
}
