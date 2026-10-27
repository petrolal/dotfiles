package dev.petrolal.dotfiles.overlay;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launches an {@link AppEntry}'s command detached from the JVM, off a
 * background executor. Never call this from the GL render thread: process
 * creation can block on fork/exec, and launched apps must outlive the
 * overlay (closing the overlay must not kill what it launched).
 */
public final class ProcessLauncher {

    private final ExecutorService launchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void launch(AppEntry entry) {
        launchExecutor.submit(() -> {
            try {
                // Redirect.DISCARD is a write-only /dev/null handle; it's only
                // valid for stdout/stderr. Stdin needs a *readable* /dev/null,
                // or ProcessBuilder throws IllegalArgumentException.
                new ProcessBuilder(toCommand(entry.exec()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .start();
            } catch (Exception e) {
                System.err.println("Failed to launch " + entry.name() + ": " + e.getMessage());
            }
        });
    }

    private List<String> toCommand(String exec) {
        // Desktop-entry Exec values are a shell-quoted single string by spec;
        // delegate to sh -c rather than hand-rolling a quoting-aware splitter.
        return List.of("sh", "-c", exec);
    }
}
