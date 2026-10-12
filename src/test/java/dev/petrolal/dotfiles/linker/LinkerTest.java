package dev.petrolal.dotfiles.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkerTest {

    @Test
    void isPrefixMatchMatchesExactAndNestedPaths() {
        assertTrue(Linker.isPrefixMatch("config", "config"));
        assertTrue(Linker.isPrefixMatch("config", "config/gtk-3.0/settings.ini"));
        assertFalse(Linker.isPrefixMatch("config", "configure.sh"));
        assertFalse(Linker.isPrefixMatch("config/gtk-3.0", "config/gtk-2.0/gtkrc"));
    }

    @Test
    void isExcludedMatchesListedFileAndSkipsUnrelatedOnes() {
        assertTrue(Linker.isExcluded("config/gtk-2.0/gtkrc"));
        assertTrue(Linker.isExcluded("config/gtk-3.0/wrapper-menu-fix.c"));
        assertTrue(Linker.isExcluded("config/eclipse/org.eclipse.ui.workbench.prefs"));
        assertFalse(Linker.isExcluded("config/gtk-3.0/settings.ini"));
        assertFalse(Linker.isExcluded("config/eclipse/plugin_customization.ini"));
    }

    @Test
    void isXfconfExportDirOnlyMatchesAtPathBoundary() {
        assertTrue(Linker.isXfconfExportDir("config/xfce4-panel/xfconf/xfce-perchannel-xml/xfce4-panel.xml"));
        assertFalse(Linker.isXfconfExportDir("config/not-xfconf/xfce-perchannel-xml-ish/file"));
    }

    @Test
    void rootTargetForPicksLongestMatchingPrefix() {
        var target = Linker.rootTargetFor("config/applications/foo.desktop");
        assertTrue(target.isPresent());
        assertEquals(".local/share/applications", target.get().to());

        var generic = Linker.rootTargetFor("config/gtk-3.0/settings.ini");
        assertTrue(generic.isPresent());
        assertEquals(".config", generic.get().to());

        assertTrue(Linker.rootTargetFor("not-a-root/file").isEmpty());
    }

    @Test
    void linkFileCreatesValidSymlinkAndUnlinkRemovesIt(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("repo");
        Path home = tempDir.resolve("home");
        Files.createDirectories(root);
        Files.createDirectories(home);

        Path srcFile = root.resolve("config/test.ini");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, "hello=world\n");

        // Dry-run should not create link
        var dryResult = Linker.linkFile("config/test.ini", ".config/test.ini", root, home, true, false);
        assertTrue(dryResult.isOk());
        assertFalse(Files.exists(home.resolve(".config/test.ini")));

        // Real link should create valid symlink
        var realResult = Linker.linkFile("config/test.ini", ".config/test.ini", root, home, false, false);
        assertTrue(realResult.isOk());
        Path dest = home.resolve(".config/test.ini");
        assertTrue(Files.isSymbolicLink(dest));
        assertEquals("hello=world\n", Files.readString(dest));

        // Unlink should remove symlink
        var unlinkOutcome = Linker.unlinkFile(".config/test.ini", home, false, false);
        assertEquals(Linker.UnlinkOutcome.REMOVED, unlinkOutcome);
        assertFalse(Files.exists(dest));
    }
}
