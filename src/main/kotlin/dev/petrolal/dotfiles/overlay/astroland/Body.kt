package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scene-graph node ported from astroland_dots' `Body.cpp`: every gameplay
 * entity (ship, moon, bullet, sprite) is one of these, parented into a tree
 * rooted at [AstroScene.root]. Unlike the C++ original there's no shared_ptr
 * self-reference dance -- the JVM GC handles that for us -- so [instantiate]
 * just appends and [dispose] just marks for removal on the next [tick].
 */
open class Body(transform: Transform = Transform.identity()) {
    var name: String = ""
    var parent: Body? = null
    val children: MutableList<Body> = mutableListOf()
    var isDisposed: Boolean = false
    var isVisible: Boolean = true

    var pos: Vector2f = transform.getPos()
    var scale: Vector2f = transform.getScale()
    var rot: Float = transform.getRot() // counter-clockwise, radians

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

    fun getWorldPos(): Vector2f = getWorldTransform().getPos()

    fun forEachChildRecursive(action: (Body) -> Unit) {
        for (node in children) {
            node.forEachChildRecursive(action)
            action(node)
        }
    }

    /** -sin(-rot), cos(-rot) -- matches the original's "idk why the negatives" comment verbatim. */
    fun getLookDir(): Vector2f = Vector2f(-sin(-rot), -cos(-rot))
}
