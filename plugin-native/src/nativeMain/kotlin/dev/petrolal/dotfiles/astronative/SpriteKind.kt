package dev.petrolal.dotfiles.astronative

/**
 * Every texture astroland_dots draws, as a plain ordinal the C++ shim maps
 * to its own already-loaded `Render::ITexture`/GIF-frame-array -- Kotlin/
 * Native has no PNG/GIF decoder of its own (and shouldn't: the shim already
 * needs cairo/gifdec for Hyprland's renderer), so this engine only ever
 * says *which* texture and *which frame*, never touches pixels.
 */
enum class SpriteKind {
    SHIP, MOON_0, MOON_1, MOON_2, BULLET, CHAR, EXPLODE, VAX, BACKGROUND
}
