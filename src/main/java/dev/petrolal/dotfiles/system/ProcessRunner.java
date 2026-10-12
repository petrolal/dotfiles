// ProcessRunner.java --- Process execution and subprocess management
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.system;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.petrolal.dotfiles.domain.DotfileError;
import dev.petrolal.dotfiles.domain.Result;

public final class ProcessRunner {
    private ProcessRunner() {}

    /**
     * Executes CMD, merging standard error into standard output to prevent pipe buffer
     * deadlocks, and returns the captured output if the process exits successfully.
     */
    public static Result<String> captureOutput(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                String binary = cmd.isEmpty() ? "" : cmd.get(0);
                return Result.err(new DotfileError.CommandFailed(binary, exitCode));
            }
            return Result.ok(out);
        } catch (IOException e) {
            return Result.err(new DotfileError.IoError(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.err(new DotfileError.IoError("interrupted"));
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
        } catch (InterruptedException e) {
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
            new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .start();
        } catch (IOException ignored) {
            // best-effort: service reload is not fatal to deployment
        }
    }
}
