package dev.petrolal.dotfiles.overlay;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launches a shell command detached from the JVM, off a background
 * executor. Never call this from the GL render thread: process creation can
 * block on fork/exec, and launched apps must outlive the overlay (closing
 * the overlay must not kill what it launched). Used by every clickable
 * biopunk hitbox -- the heart (system monitor), the syringe rack (mapped
 * apps) -- rather than each hitbox spawning its own executor.
 */
public final class ProcessLauncher {

    private final ExecutorService launchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void launch(String label, String exec) {
        launchExecutor.submit(() -> {
            try {
                // Redirect.DISCARD is a write-only /dev/null handle; it's only
                // valid for stdout/stderr. Stdin needs a *readable* /dev/null,
                // or ProcessBuilder throws IllegalArgumentException.
                new ProcessBuilder(toCommand(exec))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .start();
            } catch (Exception e) {
                System.err.println("Failed to launch " + label + ": " + e.getMessage());
            }
        });
    }

    private List<String> toCommand(String exec) {
        // Desktop-entry-style Exec values are a shell-quoted single string;
        // delegate to sh -c rather than hand-rolling a quoting-aware splitter.
        return List.of("sh", "-c", exec);
    }
}
