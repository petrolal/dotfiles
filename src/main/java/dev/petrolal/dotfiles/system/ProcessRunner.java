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

    public static Result<String> captureOutput(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.getErrorStream().readAllBytes();
            p.waitFor();
            return new Result.Ok<>(out);
        } catch (IOException e) {
            return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result.Err<>(new DotfileError.IoError("interrupted"));
        }
    }

    /** Runs CMD to completion, discarding output. Exit status is not treated as
     * failure — callers that care check it themselves. */
    public static Result<Integer> run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return new Result.Ok<>(p.waitFor());
        } catch (IOException e) {
            return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result.Err<>(new DotfileError.IoError("interrupted"));
        }
    }

    /** Fire-and-forget launch: start() without waitFor() genuinely detaches the child
     * process instead of blocking for its lifetime. */
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
