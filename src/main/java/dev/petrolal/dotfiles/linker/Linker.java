// Linker.java --- Symlink discovery, creation, and removal
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.linker;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.petrolal.dotfiles.core.Theme;
import dev.petrolal.dotfiles.domain.DotfileError;
import dev.petrolal.dotfiles.domain.Result;

public final class Linker {
    private Linker() {}

    /** A (from -> to) path pair: used both for root-target prefixes and resolved mappings. */
    public record Mapping(String from, String to) {}

    public enum UnlinkOutcome { SKIPPED, REMOVED, FAILED }

    /** Source prefixes (relative to the repo root) auto-discovered and mirrored
     * file-by-file under the given target base (relative to $HOME). Longest
     * matching prefix wins, so a more specific entry can override a general one. */
    static final List<Mapping> ROOT_TARGETS = List.of(
            new Mapping("config/applications", ".local/share/applications"),
            new Mapping("config/icons", ".local/share/icons"),
            new Mapping("config", ".config")
    );

    /** Source paths skipped entirely during auto-discovery; a listed directory
     * is not descended into. */
    static final List<String> EXCLUDES = List.of(
            "config/gtk-2.0/gtkrc"
    );

    /** Explicit (source -> target) pairs for paths that don't fit the generic
     * root-mirrored layout: renames, or a source linked to more than one target. */
    static final List<Mapping> OVERRIDES = List.of(
            new Mapping("config/gtk-2.0/gtkrc", ".gtkrc-2.0"),
            new Mapping("themes/" + Theme.NAME, ".themes/" + Theme.NAME),
            new Mapping("themes/" + Theme.NAME, ".local/share/themes/" + Theme.NAME)
    );

    static boolean isExcluded(String relpath) {
        for (String prefix : EXCLUDES) {
            if (isPrefixMatch(prefix, relpath)) return true;
        }
        return false;
    }

    static boolean isPrefixMatch(String prefix, String relpath) {
        return relpath.length() >= prefix.length()
                && relpath.startsWith(prefix)
                && (relpath.length() == prefix.length() || relpath.charAt(prefix.length()) == '/');
    }

    static Optional<Mapping> rootTargetFor(String relpath) {
        Mapping best = null;
        for (Mapping entry : ROOT_TARGETS) {
            if (isPrefixMatch(entry.from(), relpath) && (best == null || entry.from().length() > best.from().length())) {
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    static List<String> topLevelRoots() {
        List<String> prefixes = ROOT_TARGETS.stream().map(Mapping::from).toList();
        List<String> out = new ArrayList<>();
        for (String prefix : prefixes) {
            boolean nested = prefixes.stream().anyMatch(other -> !other.equals(prefix) && isPrefixMatch(other, prefix));
            if (!nested) out.add(prefix);
        }
        return out;
    }

    static List<String> scanTree(String relpath, Path root) {
        List<String> out = new ArrayList<>();
        Path dir = root.resolve(relpath);
        if (!Files.isDirectory(dir)) return out;
        List<Path> entries;
        try (var stream = Files.list(dir)) {
            entries = stream.sorted().toList();
        } catch (IOException _) {
            return out;
        }
        for (Path entry : entries) {
            String childRel = relpath + "/" + entry.getFileName();
            if (isExcluded(childRel)) continue;
            if (Files.isDirectory(entry)) {
                out.addAll(scanTree(childRel, root));
            } else {
                out.add(childRel);
            }
        }
        return out;
    }

    public static List<Mapping> collectAllMappings(Path root) {
        List<Mapping> out = new ArrayList<>(OVERRIDES);
        for (String top : topLevelRoots()) {
            for (String fileRel : scanTree(top, root)) {
                rootTargetFor(fileRel).ifPresent(entry ->
                        out.add(new Mapping(fileRel, entry.to() + fileRel.substring(entry.from().length()))));
            }
        }
        return out;
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void ensureRealDirectory(Path dir, Path home) throws IOException {
        Path rel;
        try {
            rel = home.relativize(dir);
        } catch (IllegalArgumentException e) {
            Files.createDirectories(dir);
            return;
        }
        Path current = home;
        for (Path part : rel) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                Files.delete(current);
            }
        }
        Files.createDirectories(dir);
    }

    private static void atomicSymlink(Path dest, Path target) throws IOException {
        Path parent = dest.getParent();
        Path tmp = Files.createTempFile(parent, dest.getFileName().toString(), ".tmp-symlink");
        Files.delete(tmp);
        Files.createSymbolicLink(tmp, target);
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static Result<Void> linkFile(String sourceRel, String targetRel, Path root, Path home,
                                         boolean dryRun, boolean verbose) {
        Path src = root.resolve(sourceRel);
        Path dest = home.resolve(targetRel);
        if (!Files.exists(src)) {
            if (verbose) System.err.println("[SKIP] Source missing: " + src);
            return Result.err(new DotfileError.FileNotFound(src));
        }
        if (dryRun) {
            System.out.println("[DRY-RUN] Would link: " + dest + " -> " + src);
            return Result.ok(null);
        }
        try {
            ensureRealDirectory(dest.getParent(), home);
            if (Files.isDirectory(src) && Files.isDirectory(dest) && !Files.isSymbolicLink(dest)) {
                deleteRecursively(dest);
            }
            atomicSymlink(dest, src);
            if (verbose) System.out.println("[OK] Linked: " + dest + " -> " + src);
            return Result.ok(null);
        } catch (IOException e) {
            System.err.println("[FAIL] Failed to link " + dest + " -> " + src);
            return Result.err(new DotfileError.IoError(e.getMessage()));
        }
    }

    public static UnlinkOutcome unlinkFile(String targetRel, Path home, boolean dryRun, boolean verbose) {
        Path dest = home.resolve(targetRel);
        if (!Files.isSymbolicLink(dest)) {
            if (verbose) {
                if (Files.exists(dest)) {
                    System.err.println("[SKIP] Not a symlink: " + dest + " (refusing to delete)");
                } else {
                    System.out.println("[SKIP] Symlink does not exist: " + dest);
                }
            }
            return UnlinkOutcome.SKIPPED;
        }
        if (dryRun) {
            System.out.println("[DRY-RUN] Would remove symlink: " + dest);
            return UnlinkOutcome.REMOVED;
        }
        try {
            Files.delete(dest);
            if (verbose) System.out.println("[OK] Removed symlink: " + dest);
            return UnlinkOutcome.REMOVED;
        } catch (IOException e) {
            System.err.println("[FAIL] Failed to remove symlink " + dest);
            return UnlinkOutcome.FAILED;
        }
    }
}
