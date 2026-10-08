// Generators.java --- Templated config file generation
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.linker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.petrolal.dotfiles.domain.DotfileError;
import dev.petrolal.dotfiles.domain.Result;

public final class Generators {
    private Generators() {}

    /** Ensures TARGET exists with CONTENT, skipping the rewrite if unchanged. */
    public static Result<Void> ensureFileContent(Path target, String content, boolean dryRun, boolean verbose) {
        if (dryRun) {
            System.out.println("[DRY-RUN] Would generate " + target);
            return new Result.Ok<>(null);
        }
        try {
            if (Files.exists(target)) {
                String existing = Files.readString(target);
                if (existing.equals(content)) {
                    if (verbose) System.out.println("[UP-TO-DATE] Generated config unchanged: " + target);
                    return new Result.Ok<>(null);
                }
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            if (verbose) System.out.println("[GEN] Generated: " + target);
            return new Result.Ok<>(null);
        } catch (IOException e) {
            System.err.println("[FAIL] Failed generating " + target + ": " + e.getMessage());
            return new Result.Err<>(new DotfileError.IoError(e.getMessage()));
        }
    }

    /** Hook for future templated configuration assets. */
    public static boolean generateAllConfigs(boolean verbose) {
        if (verbose) System.out.println("Ensuring templated configuration assets... (none defined)");
        return true;
    }
}
