package dev.petrolal.dotfiles.astronative

/** Ported from `Hittable.cpp`. */
interface Hittable {
    var health: Int
    fun hit(collision: PhysicsBody.Collision, damage: Int)
}
