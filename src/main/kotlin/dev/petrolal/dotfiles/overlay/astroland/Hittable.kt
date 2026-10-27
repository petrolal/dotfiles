package dev.petrolal.dotfiles.overlay.astroland

/** Ported from `Hittable.cpp`. Mixed into [PhysicsBody] subclasses that take damage. */
interface Hittable {
    var health: Int
    fun hit(collision: PhysicsBody.Collision, damage: Int)
}
