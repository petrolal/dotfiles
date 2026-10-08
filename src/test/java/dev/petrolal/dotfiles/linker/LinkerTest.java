package dev.petrolal.dotfiles.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
        assertFalse(Linker.isExcluded("config/gtk-3.0/settings.ini"));
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
}
