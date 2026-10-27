package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D affine transform (translate * scale * rotate), ported from astroland_dots'
 * `Transform.cpp`. Backed by the raw 2x3 matrix coefficients rather than a
 * 3x3 so [multiply] stays a handful of multiplies instead of a full matmul.
 */
class Transform(pos: Vector2f = Vector2f(0f, 0f), scale: Vector2f = Vector2f(1f, 1f), rot: Float = 0f) {
    // row-major [a b c; d e f] of [[a b][d e]] linear part + [c f] translation,
    // i.e. mat * (x,y,1) = (a*x+b*y+c, d*x+e*y+f)
    var a = 0f; var b = 0f; var c = 0f
    var d = 0f; var e = 0f; var f = 0f

    init {
        val cosR = cos(rot)
        val sinR = sin(rot)
        a = cosR * scale.x; b = -sinR * scale.y; c = pos.x
        d = sinR * scale.x; e = cosR * scale.y; f = pos.y
    }

    private constructor(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) : this() {
        this.a = a; this.b = b; this.c = c
        this.d = d; this.e = e; this.f = f
    }

    fun getPos(): Vector2f = Vector2f(c, f)

    fun getScale(): Vector2f = Vector2f(
        kotlin.math.sqrt(a * a + d * d),
        kotlin.math.sqrt(b * b + e * e),
    )

    fun getRot(): Float = kotlin.math.atan2(d, a)

    fun withPos(pos: Vector2f): Transform = Transform(a, b, pos.x, d, e, pos.y)

    /** mat ⊗ other: apply `other` first, then this. */
    fun multiply(other: Transform): Transform = Transform(
        a * other.a + b * other.d, a * other.b + b * other.e, a * other.c + b * other.f + c,
        d * other.a + e * other.d, d * other.b + e * other.e, d * other.c + e * other.f + f,
    )

    fun multiply(p: Vector2f): Vector2f = Vector2f(a * p.x + b * p.y + c, d * p.x + e * p.y + f)

    fun inv(): Transform {
        val det = a * e - b * d
        val invDet = 1f / det
        val ia = e * invDet
        val ib = -b * invDet
        val id = -d * invDet
        val ie = a * invDet
        // inverse translation: -R^-1 * t
        val ic = -(ia * c + ib * f)
        val iff = -(id * c + ie * f)
        return Transform(ia, ib, ic, id, ie, iff)
    }

    companion object {
        fun identity() = Transform()
    }
}
