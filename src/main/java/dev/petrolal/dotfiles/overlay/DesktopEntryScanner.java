package dev.petrolal.dotfiles.overlay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Crawls the standard XDG application directories and parses each
 * {@code .desktop} file into an {@link AppEntry}, one virtual thread per file.
 * Decoupled from rendering/launching on purpose: this only ever produces data.
 */
public final class DesktopEntryScanner {

    private static final List<Path> SEARCH_DIRS = List.of(
        Path.of("/usr/share/applications"),
        Path.of("/usr/local/share/applications"),
        Path.of(System.getProperty("user.home"), ".local/share/applications")
    );

    public List<AppEntry> scan() {
        List<Path> desktopFiles = SEARCH_DIRS.stream()
            .filter(Files::isDirectory)
            .flatMap(this::listDesktopFiles)
            .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AppEntry>> futures = desktopFiles.stream()
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
            String id = path.getFileName().toString().replace(".desktop", "");
            return new AppEntry(id, name, exec, icon, categories);
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
