package dev.petrolal.dotfiles.overlay;

import java.io.IOException;
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
                new ProcessBuilder(toCommand(entry.exec()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            } catch (IOException e) {
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
