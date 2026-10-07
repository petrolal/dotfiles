// Generators.java --- Templated config file generation
// License: GPL-3.0-or-later

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Generators {
    private Generators() {}

    /** Ensures TARGET exists with CONTENT, skipping the rewrite if unchanged. */
    static Result<Void> ensureFileContent(Path target, String content, boolean dryRun, boolean verbose) {
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

    /** Currently a no-op: every config file under config/ is a plain tracked dotfile
     * handled by the symlink mechanism (Linker) rather than generated content. This
     * hook exists for a future config that genuinely needs to be derived (e.g. from
     * theme colors) rather than duplicated verbatim. */
    static boolean generateAllConfigs(boolean verbose) {
        if (verbose) System.out.println("Ensuring templated configuration assets... (none defined)");
        return true;
    }
}
