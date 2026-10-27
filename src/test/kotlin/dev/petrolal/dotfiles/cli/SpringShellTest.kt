package dev.petrolal.dotfiles.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpringShellTest {

    @Test
    void springShellVersionCommand() {
        int exitCode = ImpApplication.run(new String[]{"version"});
        assertEquals(0, exitCode);
    }

    @Test
    void springShellHelpCommand() {
        int exitCode = ImpApplication.run(new String[]{"help"});
        assertEquals(0, exitCode);
    }

    @Test
    void springShellWallpaperDryRun() {
        int exitCode = ImpApplication.run(new String[]{"wallpaper", "--dry-run", "--quiet"});
        assertEquals(0, exitCode);
    }
}
