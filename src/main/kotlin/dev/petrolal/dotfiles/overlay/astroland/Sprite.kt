package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f

/**
 * Ported from `Sprite.cpp`, minus the Hyprland-specific render-damage
 * bookkeeping (irrelevant to a standalone GLFW window that just redraws
 * every frame). [texture] carries its own pixel size, which stands in for
 * the original's `tex->m_size`.
 */
open class Sprite(resourcePath: String?, transform: Transform = Transform.identity()) : Body(transform) {
    lateinit var texture: Texture
        protected set

    init {
        if (resourcePath != null) {
            texture = TextureLoader.loadPng(resourcePath)
        }
    }

    fun worldSize(): Vector2f = Vector2f(texture.width.toFloat(), texture.height.toFloat()).mul(getWorldTransform().getScale())
}
