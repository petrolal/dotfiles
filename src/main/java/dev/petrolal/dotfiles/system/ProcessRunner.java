// ProcessRunner.java --- Process execution and subprocess management
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.system;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.petrolal.dotfiles.domain.DotfileError;
import dev.petrolal.dotfiles.domain.Result;

public final class ProcessRunner {
    private ProcessRunner() {}

    /**
     * Executes CMD, utilizing Virtual Threads to concurrently drain standard output
     * and standard error without blocking or pipe deadlocks, returning the captured
     * stdout on exit code 0.
     */
    public static Result<String> captureOutput(List<String> cmd) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Process p = new ProcessBuilder(cmd).start();
            Future<String> stdoutFuture = executor.submit(() ->
                new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> stderrFuture = executor.submit(() ->
                new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));

            int exitCode = p.waitFor();
            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();

            if (exitCode != 0) {
                String binary = cmd.isEmpty() ? "" : cmd.get(0);
                String detail = stderr.isBlank() ? binary : binary + ": " + stderr.trim();
                return Result.err(new DotfileError.CommandFailed(detail, exitCode));
            }
            return Result.ok(stdout);
        } catch (IOException e) {
            return Result.err(new DotfileError.IoError(e.getMessage()));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Result.err(new DotfileError.IoError("interrupted"));
        } catch (ExecutionException e) {
            return Result.err(new DotfileError.IoError(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
    }

    /**
     * Runs CMD to completion, discarding output. Returns Result.Ok(0) on success,
     * or Result.Err with CommandFailed if the exit code is non-zero.
     */
    public static Result<Integer> run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                String binary = cmd.isEmpty() ? "" : cmd.get(0);
                return Result.err(new DotfileError.CommandFailed(binary, exitCode));
            }
            return Result.ok(exitCode);
        } catch (IOException e) {
            return Result.err(new DotfileError.IoError(e.getMessage()));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Result.err(new DotfileError.IoError("interrupted"));
        }
    }

    /**
     * Fire-and-forget launch: start() without waitFor() genuinely detaches the child
     * process instead of blocking for its lifetime.
     */
    public static void spawnDetached(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            pb.environment().put("GTK_THEME", "Imp98");
            pb.environment().put("GTK_OVERLAY_SCROLLING", "0");
            pb.environment().put("QT_STYLE_OVERRIDE", "Windows");
            pb.start();
        } catch (IOException _) {
            // best-effort: service reload is not fatal to deployment
        }
    }
}
