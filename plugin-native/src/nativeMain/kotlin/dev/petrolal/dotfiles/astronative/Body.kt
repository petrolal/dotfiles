package dev.petrolal.dotfiles.astronative

import kotlin.math.cos
import kotlin.math.sin

/** Scene-graph node, ported from astroland_dots' `Body.cpp`. See the JVM port's `Body.kt` for context. */
open class Body(transform: Transform = Transform.identity()) {
    var name: String = ""
    var parent: Body? = null
    val children: MutableList<Body> = mutableListOf()
    var isDisposed: Boolean = false
    var isVisible: Boolean = true

    var pos: Vec2 = transform.getPos()
    var scale: Vec2 = transform.getScale()
    var rot: Float = transform.getRot()

    open fun onInitialized() {}

    open fun tick(dt: Float) {
        val childrenCopy = ArrayList(children)
        for (node in childrenCopy) {
            node.tick(dt)
        }
        children.removeAll { it.isDisposed }
    }

    fun <T : Body> instantiate(child: T): T {
        child.parent = this
        child.onInitialized()
        children.add(child)
        return child
    }

    open fun dispose() {
        isDisposed = true
    }

    fun getTransform(): Transform = Transform(pos, scale, rot)

    fun getWorldTransform(): Transform {
        val p = parent
        return if (p != null) p.getWorldTransform().multiply(getTransform()) else getTransform()
    }

    fun getWorldPos(): Vec2 = getWorldTransform().getPos()

    fun forEachChildRecursive(action: (Body) -> Unit) {
        for (node in children) {
            node.forEachChildRecursive(action)
            action(node)
        }
    }

    fun getLookDir(): Vec2 = Vec2(-sin(-rot), -cos(-rot))
}
