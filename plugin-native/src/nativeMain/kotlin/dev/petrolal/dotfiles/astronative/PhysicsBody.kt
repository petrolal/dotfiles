package dev.petrolal.dotfiles.astronative

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AABB(val pos: Vec2, val size: Vec2) {
    fun middle(): Vec2 = pos + size / 2f
    fun overlaps(other: AABB): Boolean =
        pos.x < other.pos.x + other.size.x && pos.x + size.x > other.pos.x &&
            pos.y < other.pos.y + other.size.y && pos.y + size.y > other.pos.y
}

/** Ported from astroland_dots' `PhysicsBody.cpp` -- see the JVM port's `PhysicsBody.kt` for the line-by-line origin. */
open class PhysicsBody(var size: Vec2, transform: Transform = Transform.identity()) : Body(transform) {
    var isSolid: Boolean = true
    var decel: Float = 500f
    var velocity: Vec2 = Vec2.ZERO

    data class Collision(val body: PhysicsBody, val pos: Vec2, val normal: Vec2)

    fun moveAndCollide(dt: Float): List<Collision> {
        val collisions = mutableListOf<Collision>()

        val decelThisTick = decel * dt
        val speed = velocity.length()
        velocity = if (speed > 0 && decelThisTick < speed) {
            velocity - velocity.normalized() * decelThisTick
        } else {
            Vec2.ZERO
        }

        val pos = getWorldPos()
        var newPos = pos + velocity * dt

        AstroGlobals.scene.forEachChildRecursive { node ->
            if (!node.isVisible) return@forEachChildRecursive
            val other = node as? PhysicsBody ?: return@forEachChildRecursive
            if (other === this || !other.isSolid) return@forEachChildRecursive

            val box = getWorldBoundingBox()
            val otherBox = other.getWorldBoundingBox()

            if (velocity.length() == 0f && box.overlaps(otherBox)) {
                var normal = newPos - otherBox.middle()
                normal = if (normal.length() == 0f) Vec2(1f, 0f) else normal.normalized()

                if (isSolid) {
                    newPos += normal * 500f * dt
                }

                collisions.add(Collision(other, newPos, normal))
            } else {
                val expanded = boxExpand(otherBox, size)
                val isect = rayIsectBox(pos, newPos, expanded)
                if (isect != -1f) {
                    val rayDir = (newPos - pos).normalized()
                    val hitPos = pos + rayDir * isect

                    var normal = Vec2.ZERO
                    val dVec = hitPos - otherBox.middle()
                    val p = otherBox.size / 2f - Vec2(abs(dVec.x), abs(dVec.y))
                    normal = if (p.x < p.y) Vec2(sign(dVec.x), 0f) else Vec2(0f, sign(dVec.y))

                    if (isSolid) {
                        val reaction = normal * (velocity.length() / 2f)
                        other.velocity = other.velocity - reaction
                        velocity += reaction
                    }
                    newPos = pos + velocity * dt

                    collisions.add(Collision(other, hitPos, normal))
                }
            }
        }

        this.pos = newPos
        return collisions
    }

    fun getWorldBoundingBox(): AABB {
        val worldTransform = getWorldTransform()
        val worldSize = Vec2(size.x * worldTransform.getScale().x, size.y * worldTransform.getScale().y)
        return AABB(worldTransform.getPos() - worldSize / 2f, worldSize)
    }

    companion object {
        private fun sign(x: Float): Float = if (x > 0) 1f else if (x < 0) -1f else 0f

        private fun boxExpand(box: AABB, size: Vec2): AABB = AABB(
            Vec2(box.pos.x - size.x / 2f, box.pos.y - size.y / 2f),
            Vec2(box.size.x + size.x, box.size.y + size.y),
        )

        /** https://tavianator.com/2015/ray_box_nan.html -- returns -1 if no hit. */
        private fun rayIsectBox(p1: Vec2, p2: Vec2, box: AABB): Float {
            val rayDirRaw = p2 - p1
            val rayLen = rayDirRaw.length()
            if (rayLen == 0f) return -1f
            val rayDir = rayDirRaw / rayLen

            var t1 = (box.pos.x - p1.x) / rayDir.x
            var t2 = (box.pos.x + box.size.x - p1.x) / rayDir.x
            var tmin = min(t1, t2)
            var tmax = max(t1, t2)

            t1 = (box.pos.y - p1.y) / rayDir.y
            t2 = (box.pos.y + box.size.y - p1.y) / rayDir.y
            tmin = max(tmin, min(t1, t2))
            tmax = min(tmax, max(t1, t2))

            if (tmin > rayLen) return -1f
            return if (tmax > max(tmin, 0f)) max(tmin, 0f) else -1f
        }
    }
}
