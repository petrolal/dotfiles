package dev.petrolal.dotfiles.astronative

import kotlin.math.sqrt

/** Plain 2D float vector -- the JVM port used JOML, which doesn't exist on Kotlin/Native. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)
    fun length(): Float = sqrt(x * x + y * y)
    fun distance(o: Vec2): Float = (this - o).length()
    fun normalized(): Vec2 = if (length() == 0f) this else this / length()

    companion object {
        val ZERO = Vec2(0f, 0f)
        val ONE = Vec2(1f, 1f)
    }
}
