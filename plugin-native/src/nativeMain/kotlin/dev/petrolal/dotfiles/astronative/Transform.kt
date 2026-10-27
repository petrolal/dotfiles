package dev.petrolal.dotfiles.astronative

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 2D affine transform (translate * scale * rotate), ported from astroland_dots' `Transform.cpp`. */
class Transform(pos: Vec2 = Vec2.ZERO, scale: Vec2 = Vec2.ONE, rot: Float = 0f) {
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

    fun getPos(): Vec2 = Vec2(c, f)
    fun getScale(): Vec2 = Vec2(sqrt(a * a + d * d), sqrt(b * b + e * e))
    fun getRot(): Float = atan2(d, a)

    fun multiply(other: Transform): Transform = Transform(
        a * other.a + b * other.d, a * other.b + b * other.e, a * other.c + b * other.f + c,
        d * other.a + e * other.d, d * other.b + e * other.e, d * other.c + e * other.f + f,
    )

    fun inv(): Transform {
        val det = a * e - b * d
        val invDet = 1f / det
        val ia = e * invDet
        val ib = -b * invDet
        val id = -d * invDet
        val ie = a * invDet
        val ic = -(ia * c + ib * f)
        val iff = -(id * c + ie * f)
        return Transform(ia, ib, ic, id, ie, iff)
    }

    fun multiply(p: Vec2): Vec2 = Vec2(a * p.x + b * p.y + c, d * p.x + e * p.y + f)

    companion object {
        fun identity() = Transform()
    }
}
