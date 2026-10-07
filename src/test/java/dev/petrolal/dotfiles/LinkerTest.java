package dev.petrolal.dotfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkerTest {

    @Test
    void prefixMatchPMatchesExactAndNestedPaths() {
        assertTrue(Linker.prefixMatchP("config", "config"));
        assertTrue(Linker.prefixMatchP("config", "config/gtk-3.0/settings.ini"));
        assertFalse(Linker.prefixMatchP("config", "configure.sh"));
        assertFalse(Linker.prefixMatchP("config/gtk-3.0", "config/gtk-2.0/gtkrc"));
    }

    @Test
    void excludedPMatchesListedFileAndSkipsUnrelatedOnes() {
        assertTrue(Linker.excludedP("config/gtk-2.0/gtkrc"));
        assertTrue(Linker.excludedP("config/gtk-3.0/wrapper-menu-fix.c"));
        assertFalse(Linker.excludedP("config/gtk-3.0/settings.ini"));
    }

    @Test
    void xfconfExportDirPOnlyMatchesAtPathBoundary() {
        assertTrue(Linker.xfconfExportDirP("config/xfce4-panel/xfconf/xfce-perchannel-xml/xfce4-panel.xml"));
        assertFalse(Linker.xfconfExportDirP("config/not-xfconf/xfce-perchannel-xml-ish/file"));
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
