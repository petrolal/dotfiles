// Theme.java --- Single source of truth for the active desktop theme
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core;

import java.util.Map;

public final class Theme {
    private Theme() {}

    public static final String NAME = "Imp98";
    public static final String ICONS = "Chicago95";

    /** Toolkit environment every spawned desktop process must inherit. */
    public static final Map<String, String> ENV = Map.of(
            "GTK_THEME", NAME,
            "GTK_OVERLAY_SCROLLING", "0",
            "QT_STYLE_OVERRIDE", "Windows");
}
