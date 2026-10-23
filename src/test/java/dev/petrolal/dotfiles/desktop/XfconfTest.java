package dev.petrolal.dotfiles.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.petrolal.dotfiles.core.DotfilePaths;

class XfconfTest {

    @Test
    void expandHomeTildeExpandsLeadingTildeSlash() {
        String home = DotfilePaths.userHomeDirectory().toString();
        String trimmedHome = home.endsWith("/") ? home.substring(0, home.length() - 1) : home;
        assertEquals(trimmedHome + "/Pictures/wallpaper.png", Xfconf.expandHomeTilde("~/Pictures/wallpaper.png"));
        assertEquals(trimmedHome + "/.config/xfce4/scripts/volume.sh up", Xfconf.expandHomeTilde("~/.config/xfce4/scripts/volume.sh up"));
    }

    @Test
    void expandHomeTildeLeavesOtherValuesUnchanged() {
        assertEquals("Imp98", Xfconf.expandHomeTilde("Imp98"));
        assertEquals("/absolute/path", Xfconf.expandHomeTilde("/absolute/path"));
        assertEquals("~notarealhome", Xfconf.expandHomeTilde("~notarealhome"));
    }
}
