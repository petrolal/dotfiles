package dev.petrolal.dotfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XfconfTest {

    @Test
    void expandHomeTildeExpandsLeadingTildeSlash() {
        String home = DotfilePaths.userHomeDirectory().toString();
        String trimmedHome = home.endsWith("/") ? home.substring(0, home.length() - 1) : home;
        assertEquals(trimmedHome + "/Pictures/wallpaper.png", Xfconf.expandHomeTilde("~/Pictures/wallpaper.png"));
    }

    @Test
    void expandHomeTildeLeavesOtherValuesUnchanged() {
        assertEquals("Adwaita-dark", Xfconf.expandHomeTilde("Adwaita-dark"));
        assertEquals("/absolute/path", Xfconf.expandHomeTilde("/absolute/path"));
        assertEquals("~notarealhome", Xfconf.expandHomeTilde("~notarealhome"));
    }
}
