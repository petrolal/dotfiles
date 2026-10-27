package dev.petrolal.dotfiles.overlay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Crawls the XDG application directories and parses each {@code .desktop}
 * file into an {@link AppEntry}, one virtual thread per file. Decoupled from
 * rendering/launching on purpose: this only ever produces data.
 */
public final class DesktopEntryScanner {

    /**
     * Resolved from {@code $XDG_DATA_HOME}/{@code $XDG_DATA_DIRS} per the
     * Base Directory spec, highest-priority dir first -- NOT a hardcoded
     * FHS list like {@code /usr/share/applications}, which doesn't exist on
     * NixOS (there is no {@code /usr}). On this host the real entries (Thunar,
     * Chrome, the terminal, ...) live under
     * {@code /run/current-system/sw/share/applications}, which only
     * {@code $XDG_DATA_DIRS} actually points at.
     */
    private static final List<Path> SEARCH_DIRS = resolveSearchDirs();

    private static List<Path> resolveSearchDirs() {
        List<Path> dirs = new ArrayList<>();

        String dataHome = System.getenv("XDG_DATA_HOME");
        dirs.add(Path.of(dataHome != null && !dataHome.isBlank()
                ? dataHome
                : System.getProperty("user.home") + "/.local/share", "applications"));

        String dataDirs = System.getenv("XDG_DATA_DIRS");
        if (dataDirs != null && !dataDirs.isBlank()) {
            for (String dir : dataDirs.split(":")) {
                if (!dir.isBlank()) {
                    dirs.add(Path.of(dir, "applications"));
                }
            }
        } else {
            // Spec-defined default when $XDG_DATA_DIRS is unset.
            dirs.add(Path.of("/usr/local/share/applications"));
            dirs.add(Path.of("/usr/share/applications"));
        }
        return List.copyOf(dirs);
    }

    public List<AppEntry> scan() {
        // Preserves SEARCH_DIRS priority order so a higher-priority dir's
        // entry wins over a same-named one further down (e.g. a user
        // override in ~/.local/share beating the system one).
        Map<String, Path> pathsById = new LinkedHashMap<>();
        for (Path dir : SEARCH_DIRS) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path file : listDesktopFiles(dir).toList()) {
                pathsById.putIfAbsent(idOf(file), file);
            }
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AppEntry>> futures = pathsById.values().stream()
                .map(path -> executor.submit(() -> parse(path)))
                .toList();

            List<AppEntry> entries = new ArrayList<>(futures.size());
            for (Future<AppEntry> future : futures) {
                AppEntry entry = awaitQuietly(future);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        }
    }

    private static String idOf(Path desktopFile) {
        return desktopFile.getFileName().toString().replace(".desktop", "");
    }

    private Stream<Path> listDesktopFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".desktop")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AppEntry parse(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            String name = null;
            String exec = null;
            String icon = null;
            List<String> categories = List.of();
            boolean visible = true;
            boolean inDesktopEntrySection = false;

            for (String line : lines) {
                if (line.startsWith("[")) {
                    inDesktopEntrySection = line.equals("[Desktop Entry]");
                    continue;
                }
                if (!inDesktopEntrySection) {
                    continue;
                }
                if (line.startsWith("Name=") && name == null) {
                    name = line.substring("Name=".length());
                } else if (line.startsWith("Exec=")) {
                    exec = stripFieldCodes(line.substring("Exec=".length()));
                } else if (line.startsWith("Icon=")) {
                    icon = line.substring("Icon=".length());
                } else if (line.startsWith("Categories=")) {
                    categories = List.of(line.substring("Categories=".length()).split(";"));
                } else if (line.equals("NoDisplay=true") || line.equals("Hidden=true")) {
                    visible = false;
                }
            }

            if (!visible || name == null || exec == null) {
                return null;
            }
            return new AppEntry(idOf(path), name, exec, icon, categories);
        } catch (IOException e) {
            return null;
        }
    }

    /** Strips desktop-entry-spec field codes (%f, %U, etc.) we have no file/URL context for. */
    private String stripFieldCodes(String exec) {
        return exec.replaceAll("%[fFuUdDnNickvm]", "").trim();
    }

    private AppEntry awaitQuietly(Future<AppEntry> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }
}
