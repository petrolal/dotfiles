package dev.petrolal.dotfiles.overlay

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Launches a shell command detached from the JVM, off a background
 * executor. Never call this from the GL render thread: process creation can
 * block on fork/exec, and launched apps must outlive the overlay (closing
 * the overlay must not kill what it launched).
 */
class ProcessLauncher {
    private val launchExecutor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    fun launch(label: String, exec: String) {
        launchExecutor.submit {
            try {
                // Redirect.DISCARD is a write-only /dev/null handle; it's only
                // valid for stdout/stderr. Stdin needs a *readable* /dev/null,
                // or ProcessBuilder throws IllegalArgumentException.
                ProcessBuilder(toCommand(exec))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .start()
            } catch (e: Exception) {
                System.err.println("Failed to launch $label: ${e.message}")
            }
        }
    }

    private fun toCommand(exec: String): List<String> =
        listOf("sh", "-c", exec)
}
