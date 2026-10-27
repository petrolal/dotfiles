// Theme.java --- Single source of truth for the active desktop theme
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core

object Theme {
    const val NAME: String = "Imp98"
    const val ICONS: String = "Chicago95"

    /** Toolkit environment every spawned desktop process must inherit.  */
    val ENV: Map<String, String> = mapOf(
        "GTK_THEME" to NAME,
        "GTK_OVERLAY_SCROLLING" to "0",
        "QT_STYLE_OVERRIDE" to "Windows"
    )
}
