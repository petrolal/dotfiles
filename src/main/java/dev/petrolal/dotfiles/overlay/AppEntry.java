package dev.petrolal.dotfiles.overlay;

import java.util.List;

/** Lightweight node vector for one launchable application, ready for the GL layer to place/render. */
record AppEntry(String id, String name, String exec, String icon, List<String> categories) {
}
