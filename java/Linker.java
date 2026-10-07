// Linker.java --- Symlink discovery/creation/removal
// License: GPL-3.0-or-later

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

final class Linker {
    private Linker() {}

    /** A (from -> to) path pair: used both for root-target prefixes and resolved mappings. */
    record Mapping(String from, String to) {}

    enum UnlinkOutcome { SKIPPED, REMOVED, FAILED }

    /** Source prefixes (relative to the repo root) auto-discovered and mirrored
     * file-by-file under the given target base (relative to $HOME). Longest
     * matching prefix wins, so a more specific entry can override a general one. */
    static final List<Mapping> ROOT_TARGETS = List.of(
            // Open Display standalone: embedded in the Settings Manager it renders blank,
            // so it lives under applications/ and needs its own target root.
            new Mapping("config/applications", ".local/share/applications"),
            new Mapping("config", ".config"),
            new Mapping("fonts", ".local/share/fonts")
    );

    /** Source paths skipped entirely during auto-discovery; a listed directory
     * is not descended into. */
    static final List<String> EXCLUDES = List.of(
            "config/gtk-2.0/gtkrc",
            "config/gtk-3.0/libwrapper-menu-fix.so",
            "config/gtk-3.0/wrapper-menu-fix.c"
    );

    /** Explicit (source -> target) pairs for paths that don't fit the generic
     * root-mirrored layout: renames, or a source linked to more than one target. */
    static final List<Mapping> OVERRIDES = List.of(
            new Mapping("config/gtk-2.0/gtkrc", ".gtkrc-2.0")
    );

    /** Single-symlink target paths (relative to $HOME) from ROOT_TARGETS/OVERRIDES
     * entries removed when the imp98/quickshell sources were deleted. Includes both
     * the current and a prior (pre-rename) name actually found deployed on disk --
     * collectAllMappings can no longer discover either (their sources are gone), so
     * deploy/uninstall clean them up explicitly to avoid leaving dangling symlinks
     * into deleted repo paths. */
    static final List<String> LEGACY_TARGETS = List.of(
            ".icons/imp98",
            ".icons/RetroismIcons",
            ".local/share/icons/imp98",
            ".local/share/icons/RetroismIcons",
            ".local/share/fonts/w95fa.otf",
            ".config/gtk-3.0/gtk.css",
            ".config/gtk-4.0/gtk.css",
            ".config/gtk-3.0/assets",
            ".config/autostart/imp98.desktop"
    );

    /** Directories that were mirrored file-by-file (ROOT_TARGETS style) from now
     * -deleted repo sources, so individual leaf symlinks need walking rather than
     * a single unlink. */
    static final List<String> LEGACY_TARGET_DIRS = List.of(
            ".local/share/themes/imp98",
            ".local/share/imp98",
            ".config/quickshell"
    );

    /** Removes every symlink found under DIR (bottom-up), then DIR itself and any
     * subdirectory left empty by that. Never touches a non-symlink file, so a
     * directory holding anything unexpected is simply left in place. */
    private static void cleanupLegacyDir(Path dir, boolean dryRun, boolean verbose) {
        if (!Files.isDirectory(dir) || Files.isSymbolicLink(dir)) return;
        List<Path> entries;
        try (var stream = Files.walk(dir)) {
            entries = stream.sorted(java.util.Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            return;
        }
        for (Path entry : entries) {
            if (Files.isSymbolicLink(entry)) {
                if (dryRun) {
                    System.out.println("[DRY-RUN] Would remove symlink: " + entry);
                    continue;
                }
                try {
                    Files.delete(entry);
                    if (verbose) System.out.println("[OK] Removed symlink: " + entry);
                } catch (IOException e) {
                    System.err.println("[FAIL] Failed to remove symlink " + entry);
                }
            } else if (Files.isDirectory(entry) && !dryRun) {
                try {
                    Files.deleteIfExists(entry);
                    if (verbose && !Files.exists(entry)) System.out.println("[OK] Removed empty directory: " + entry);
                } catch (IOException e) {
                    // Not empty (holds something unexpected) -- leave it alone.
                }
            }
        }
    }

    /** Removes any still-present LEGACY_TARGETS/LEGACY_TARGET_DIRS remnants. Safe to
     * call unconditionally: unlinkFile and cleanupLegacyDir no-op on anything that
     * isn't actually a symlink (or, for directories, isn't left empty afterward). */
    static int cleanupLegacyTargets(Path home, boolean dryRun, boolean verbose) {
        int removed = 0;
        for (String legacyTarget : LEGACY_TARGETS) {
            if (unlinkFile(legacyTarget, home, dryRun, verbose) == UnlinkOutcome.REMOVED) removed++;
        }
        for (String legacyDir : LEGACY_TARGET_DIRS) {
            cleanupLegacyDir(home.resolve(legacyDir), dryRun, verbose);
        }
        return removed;
    }

    private static final String XFCONF_EXPORT_MARKER = "xfconf/xfce-perchannel-xml";

    /** True if relpath is (or is under) an xfce-perchannel-xml export directory.
     * Those hold live settings exports: never symlinked (the running session
     * clobbers them), instead auto-applied via xfconf-query. */
    static boolean xfconfExportDirP(String relpath) {
        int pos = relpath.indexOf(XFCONF_EXPORT_MARKER);
        return pos >= 0 && (pos == 0 || relpath.charAt(pos - 1) == '/');
    }

    static boolean excludedP(String relpath) {
        if (xfconfExportDirP(relpath)) return true;
        for (String prefix : EXCLUDES) {
            if (relpath.equals(prefix)) return true;
            if (relpath.length() > prefix.length()
                    && relpath.startsWith(prefix)
                    && relpath.charAt(prefix.length()) == '/') {
                return true;
            }
        }
        return false;
    }

    static boolean prefixMatchP(String prefix, String relpath) {
        return relpath.length() >= prefix.length()
                && relpath.startsWith(prefix)
                && (relpath.length() == prefix.length() || relpath.charAt(prefix.length()) == '/');
    }

    static Optional<Mapping> rootTargetFor(String relpath) {
        Mapping best = null;
        for (Mapping entry : ROOT_TARGETS) {
            if (prefixMatchP(entry.from(), relpath) && (best == null || entry.from().length() > best.from().length())) {
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    /** ROOT_TARGETS prefixes not nested inside another prefix — the minimal
     * set of directories that need walking once. */
    static List<String> topLevelRoots() {
        List<String> prefixes = ROOT_TARGETS.stream().map(Mapping::from).toList();
        List<String> out = new ArrayList<>();
        for (String prefix : prefixes) {
            boolean nested = prefixes.stream().anyMatch(other -> !other.equals(prefix) && prefixMatchP(other, prefix));
            if (!nested) out.add(prefix);
        }
        return out;
    }

    /** Recursively lists every non-excluded file under relpath (relative to root). */
    static List<String> scanTree(String relpath, Path root) {
        List<String> out = new ArrayList<>();
        Path dir = root.resolve(relpath);
        if (!Files.isDirectory(dir)) return out;
        List<Path> entries;
        try (var stream = Files.list(dir)) {
            entries = stream.sorted().toList();
        } catch (IOException e) {
            return out;
        }
        for (Path entry : entries) {
            String childRel = relpath + "/" + entry.getFileName();
            if (excludedP(childRel)) continue;
            if (Files.isDirectory(entry)) {
                out.addAll(scanTree(childRel, root));
            } else {
                out.add(childRel);
            }
        }
        return out;
    }

    /** Builds the full (source -> target) list: OVERRIDES plus every file
     * auto-discovered under the ROOT_TARGETS prefixes. */
    static List<Mapping> collectAllMappings(Path root) {
        List<Mapping> out = new ArrayList<>(OVERRIDES);
        for (String top : topLevelRoots()) {
            for (String fileRel : scanTree(top, root)) {
                rootTargetFor(fileRel).ifPresent(entry ->
                        out.add(new Mapping(fileRel, entry.to() + fileRel.substring(entry.from().length()))));
            }
        }
        return out;
    }

    static boolean symlinkP(Path path) {
        return Files.isSymbolicLink(path);
    }

    /** True if PATH exists on disk and is a directory (symlink or not), mirroring `test -d`. */
    static boolean directoryExistsP(Path path) {
        return Files.isDirectory(path);
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

    /** Ensures DIR (under HOME) exists as a real directory, replacing any symlinked
     * ancestor with a real one first. Needed because an earlier whole-directory link
     * scheme left some of these as symlinks straight into the repo; leaving such an
     * ancestor in place would make a new leaf symlink resolve back onto its own source. */
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

    /** Atomically points DEST at TARGET: create a temp symlink beside DEST, then rename
     * it over DEST. Avoids the window where DEST is briefly missing that a plain
     * delete-then-create would have. */
    private static void atomicSymlink(Path dest, Path target) throws IOException {
        Path parent = dest.getParent();
        Path tmp = Files.createTempFile(parent, dest.getFileName().toString(), ".tmp-symlink");
        Files.delete(tmp);
        Files.createSymbolicLink(tmp, target);
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static Result<Void> linkFile(String sourceRel, String targetRel, Path root, Path home,
                                  boolean dryRun, boolean verbose) {
        Path src = root.resolve(sourceRel);
        Path dest = home.resolve(targetRel);
        if (!Files.exists(src)) {
            if (verbose) System.err.println("[SKIP] Source missing: " + src);
            return new Result.Err<>(new DotfileError.FileNotFound(src));
        }
        if (dryRun) {
            System.out.println("[DRY-RUN] Would link: " + dest + " -> " + src);
            return new Result.Ok<>(null);
        }
        try {
            ensureRealDirectory(dest.getParent(), home);
            // If SRC is a directory and DEST already exists as a real (non-symlink) directory
            // — e.g. left over from before this path became a directory-level link — a plain
            // symlink rename can't replace it; clear it out first so the symlink lands at DEST.
            if (directoryExistsP(src) && directoryExistsP(dest) && !symlinkP(dest)) {
                deleteRecursively(dest);
            }
            atomicSymlink(dest, src);
            if (verbose) System.out.println("[OK] Linked: " + dest + " -> " + src);
            return new Result.Ok<>(null);
        } catch (IOException e) {
            System.err.println("[FAIL] Failed to link " + dest + " -> " + src);
            return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
        }
    }

    static UnlinkOutcome unlinkFile(String targetRel, Path home, boolean dryRun, boolean verbose) {
        Path dest = home.resolve(targetRel);
        if (!symlinkP(dest)) {
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
