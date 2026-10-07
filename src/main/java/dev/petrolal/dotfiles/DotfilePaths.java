// DotfilePaths.java --- Path/environment utilities
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

final class DotfilePaths {
    private DotfilePaths() {}

    static Path userHomeDirectory() {
        String home = System.getenv("HOME");
        return Paths.get(home != null ? home : System.getProperty("user.home"));
    }

    private static Path normalizeDirParent(Path dir) {
        Path last = dir.getFileName();
        if (last != null && (last.toString().equals("src") || last.toString().equals("bin"))) {
            Path parent = dir.getParent();
            return parent != null ? parent : dir;
        }
        return dir;
    }

    static Path findDotfilesRoot() {
        String envDir = System.getenv("DOTFILES_DIR");
        if (envDir != null) {
            Path p = Paths.get(envDir);
            if (Files.exists(p)) {
                try {
                    return p.toRealPath();
                } catch (IOException ignored) {
                    return p.toAbsolutePath().normalize();
                }
            }
        }
        Optional<String> exePath = ProcessHandle.current().info().command();
        if (exePath.isPresent()) {
            Path exe = Paths.get(exePath.get());
            if (Files.exists(exe)) {
                try {
                    Path real = exe.toRealPath();
                    Path dir = real.getParent();
                    if (dir != null) {
                        return normalizeDirParent(dir);
                    }
                } catch (IOException ignored) {
                    // fall through to cwd
                }
            }
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    static boolean commandExists(String cmd) {
        if (cmd == null || cmd.isEmpty()) return false;
        if (cmd.indexOf('/') >= 0) {
            Path p = Paths.get(cmd);
            return Files.isRegularFile(p) && Files.isExecutable(p);
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return false;
        for (String dir : pathEnv.split(":")) {
            if (dir.isEmpty()) continue;
            Path candidate = Paths.get(dir).resolve(cmd);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }
}
