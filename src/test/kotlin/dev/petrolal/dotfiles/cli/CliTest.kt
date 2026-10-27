package dev.petrolal.dotfiles.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CliTest {

    @Test
    void helpReturnsZero() {
        assertEquals(0, Cli.run(new String[]{"--help"}));
        assertEquals(0, Cli.run(new String[]{"-h"}));
    }

    @Test
    void versionReturnsZero() {
        assertEquals(0, Cli.run(new String[]{"--version"}));
        assertEquals(0, Cli.run(new String[]{"-v"}));
    }

    @Test
    void unrecognizedOptionReturnsOne() {
        assertEquals(1, Cli.run(new String[]{"--bogus-nonexistent-flag"}));
    }

    @Test
    void wallpaperDryRunReturnsZeroWithoutStartingLoop() {
        assertEquals(0, Cli.run(new String[]{"wallpaper", "--dry-run", "-q"}));
    }

    @Test
    void overlayDryRunReturnsZeroWithoutOpeningWindow() {
        assertEquals(0, Cli.run(new String[]{"overlay", "--dry-run", "-q"}));
    }
}
