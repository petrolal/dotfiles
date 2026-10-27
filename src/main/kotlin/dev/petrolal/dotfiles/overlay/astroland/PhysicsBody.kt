package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Axis-aligned box: top-left [pos] + [size]. Mirrors the subset of Hyprland's `CBox` this port needs. */
data class AABB(val pos: Vector2f, val size: Vector2f) {
    fun middle(): Vector2f = Vector2f(pos.x + size.x / 2f, pos.y + size.y / 2f)
    fun overlaps(other: AABB): Boolean =
        pos.x < other.pos.x + other.size.x && pos.x + size.x > other.pos.x &&
            pos.y < other.pos.y + other.size.y && pos.y + size.y > other.pos.y
}

/**
 * Ported from astroland_dots' `PhysicsBody.cpp`: AABB bodies with velocity,
 * deceleration, and swept-AABB collision against every [Hittable]-or-not
 * sibling under [AstroGlobals.scene]. The ray-box sweep and corner-vs-edge
 * normal pick are copied verbatim from the original's comments/links.
 */
open class PhysicsBody(var size: Vector2f, transform: Transform = Transform.identity()) : Body(transform) {
    var isSolid: Boolean = true
    var decel: Float = 500f
    var velocity: Vector2f = Vector2f(0f, 0f)

    data class Collision(val body: PhysicsBody, val pos: Vector2f, val normal: Vector2f)

    fun moveAndCollide(dt: Float): List<Collision> {
        val collisions = mutableListOf<Collision>()

        val decelThisTick = decel * dt
        val speed = velocity.length()
        velocity = if (speed > 0 && decelThisTick < speed) {
            val dir = Vector2f(velocity).div(speed)
            Vector2f(velocity).sub(Vector2f(dir).mul(decelThisTick))
        } else {
            Vector2f(0f, 0f)
        }

        val pos = getWorldPos()
        var newPos = Vector2f(pos).add(Vector2f(velocity).mul(dt))

        AstroGlobals.scene.forEachChildRecursive { node ->
            if (!node.isVisible) return@forEachChildRecursive
            val other = node as? PhysicsBody ?: return@forEachChildRecursive
            if (other === this || !other.isSolid) return@forEachChildRecursive

            val box = getWorldBoundingBox()
            val otherBox = other.getWorldBoundingBox()

            if (velocity.length() == 0f && box.overlaps(otherBox)) {
                var normal = Vector2f(newPos).sub(otherBox.middle())
                normal = if (normal.length() == 0f) Vector2f(1f, 0f) else normal.div(normal.length())

                if (isSolid) {
                    newPos = Vector2f(newPos).add(Vector2f(normal).mul(500f * dt))
                }

                collisions.add(Collision(other, Vector2f(newPos), normal))
            } else {
                val expanded = boxExpand(otherBox, size)
                val isect = rayIsectBox(pos, newPos, expanded)
                if (isect != -1f) {
                    val rayDir = Vector2f(newPos).sub(pos)
                    rayDir.div(rayDir.length())
                    val hitPos = Vector2f(pos).add(Vector2f(rayDir).mul(isect))

                    var normal = Vector2f(0f, 0f)
                    val dVec = Vector2f(hitPos).sub(otherBox.middle())
                    val p = Vector2f(otherBox.size).div(2f).sub(Vector2f(abs(dVec.x), abs(dVec.y)))
                    if (p.x < p.y) {
                        normal.x = sign(dVec.x)
                    } else {
                        normal.y = sign(dVec.y)
                    }

                    if (isSolid) {
                        val reaction = Vector2f(normal).mul(velocity.length() / 2f)
                        other.velocity = Vector2f(other.velocity).sub(reaction)
                        velocity = Vector2f(velocity).add(reaction)
                    }
                    newPos = Vector2f(pos).add(Vector2f(velocity).mul(dt))

                    collisions.add(Collision(other, hitPos, normal))
                }
            }
        }

        this.pos = newPos
        return collisions
    }

    fun getWorldSize(): Vector2f = Vector2f(size).mul(getWorldTransform().getScale())

    fun getWorldBoundingBox(): AABB {
        val worldTransform = getWorldTransform()
        val worldSize = Vector2f(size).mul(worldTransform.getScale())
        return AABB(Vector2f(worldTransform.getPos()).sub(Vector2f(worldSize).div(2f)), worldSize)
    }

    companion object {
        private fun sign(x: Float): Float = if (x > 0) 1f else if (x < 0) -1f else 0f

        private fun boxMin(box: AABB): Vector2f = box.pos
        private fun boxMax(box: AABB): Vector2f = Vector2f(box.pos).add(box.size)

        private fun boxExpand(box: AABB, size: Vector2f): AABB = AABB(
            Vector2f(box.pos.x - size.x / 2f, box.pos.y - size.y / 2f),
            Vector2f(box.size.x + size.x, box.size.y + size.y),
        )

        /** https://tavianator.com/2015/ray_box_nan.html -- returns -1 if no hit. */
        private fun rayIsectBox(p1: Vector2f, p2: Vector2f, box: AABB): Float {
            val rayPos = p1
            val rayDir = Vector2f(p2).sub(p1)
            val rayLen = rayDir.length()
            if (rayLen == 0f) return -1f
            rayDir.div(rayLen)

            var t1 = (boxMin(box).x - rayPos.x) / rayDir.x
            var t2 = (boxMax(box).x - rayPos.x) / rayDir.x
            var tmin = min(t1, t2)
            var tmax = max(t1, t2)

            t1 = (boxMin(box).y - rayPos.y) / rayDir.y
            t2 = (boxMax(box).y - rayPos.y) / rayDir.y
            tmin = max(tmin, min(t1, t2))
            tmax = min(tmax, max(t1, t2))

            if (tmin > rayLen) return -1f
            return if (tmax > max(tmin, 0f)) max(tmin, 0f) else -1f
        }
    }
}
