// Theme.scala --- Single source of truth for the active desktop theme
// License: GPL-3.0-or-later
// Ported from Theme.java (docs/scala-migration-checklist.md step 2).
//
// Field names (NAME/ICONS/ENV) are kept upper-case/Java-style rather than
// recased to Scala convention: build.gradle's resolveThemeName() still
// greps the *Java* Theme.java for `NAME = "..."` (unaffected, that file is
// untouched), but once Orchestrator and its other not-yet-ported callers
// move to Scala they'll reference this object by the same names -- no
// reason to introduce a rename for its own sake per rule 13.
package dev.petrolal.dotfiles.core

object Theme:
  val NAME = "Imp98"
  val ICONS = "Chicago95"

  val ENV: Map[String, String] = Map(
    "GTK_THEME" -> NAME,
    "GTK_OVERLAY_SCROLLING" -> "0",
    "QT_STYLE_OVERRIDE" -> "Windows"
  )
