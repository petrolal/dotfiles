package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.OverlayCamera.Ray
import org.joml.Vector3f

/**
 * Owns the Astroland scene's planets, central star, glowing orbit ribbons,
 * and HUD label data; handles ray-sphere hover/click against the planets.
 * Mapped to apps actually installed on this host (confirmed against
 * `nixos/modules/packages.nix`, not guessed): `kitty`,
 * `google-chrome-stable` (Firefox isn't installed here), `idea`
 * (a real JetBrains Toolbox shim, confirmed present), `telegram-desktop` (Discord isn't installed here), `thunar`.
 * 
 * Planet positions are fixed, not continuously orbiting -- see
 * [PlanetNode]'s javadoc for why: this is meant to be used as a real,
 * fast, daily-use launcher, and a moving click target you have to re-aim a
 * manually-orbited camera at works against that.
 */
class CelestialSystem(private val processLauncher: ProcessLauncher) {
    private val star = StarRenderer()
    private val planetRenderer = PlanetRenderer()
    private val ringRenderer: OrbitRingRenderer
    private val beamRenderer: VectorBeamRenderer
    private var hoveredIndex = -1
    private val lastClickSeconds = DoubleArray(PLANETS.size) { Double.NEGATIVE_INFINITY }

    init {
        val radii = FloatArray(PLANETS.size) { PLANETS[it].orbitalRadius }
        val positions = Array(PLANETS.size) { PLANETS[it].position() }
        ringRenderer = OrbitRingRenderer(radii)
        beamRenderer = VectorBeamRenderer(Vector3f(0f, 0f, 0f), positions)
    }

    /** Re-tests the hover ray against every planet; called every frame
     * (not just on mouse-move) from `OverlayWindow`'s loop, since
     * positions are fixed so this is cheap and needs no dedicated
     * cursor-moved callback.  */
    fun updateHover(ray: Ray) {
        hoveredIndex = hitTest(ray)
    }

    private fun hitTest(ray: Ray): Int {
        var best = -1
        var bestT = Float.POSITIVE_INFINITY
        for (i in PLANETS.indices) {
            val t = Raycaster.intersectSphere(ray, PLANETS[i].position(), PLANETS[i].sphereRadius)
            if (!t.isNaN() && t < bestT) {
                bestT = t
                best = i
            }
        }
        return best
    }

    /** Launches whatever's currently hovered, if anything, and records the
     * click time to drive the ring-ripple/pill-flash decay. Call from the
     * mouse-click handler once a drag has been ruled out.  */
    fun clickHovered(elapsedSeconds: Double) {
        if (hoveredIndex >= 0) {
            val planet = PLANETS[hoveredIndex]
            lastClickSeconds[hoveredIndex] = elapsedSeconds
            processLauncher.launch(planet.label, planet.exec)
        }
    }

    /** Fraction of the ripple animation remaining, in [0, 1].  */
    private fun rippleAmount(index: Int, elapsedSeconds: Double): Float {
        val sinceClick = elapsedSeconds - lastClickSeconds[index]
        if (sinceClick < 0.0 || sinceClick > RIPPLE_DURATION_SECONDS) {
            return 0f
        }
        return (1.0 - sinceClick / RIPPLE_DURATION_SECONDS).toFloat()
    }

    fun draw(camera: OverlayCamera, elapsedSeconds: Double, cpuLoad: Float) {
        ringRenderer.beginBatch(camera, elapsedSeconds)
        for (i in PLANETS.indices) {
            ringRenderer.draw(i, rippleAmount(i, elapsedSeconds))
        }

        beamRenderer.beginBatch(camera, elapsedSeconds)
        for (i in PLANETS.indices) {
            beamRenderer.draw(i, PLANETS[i].emissiveGlow, i == hoveredIndex)
        }
        beamRenderer.endBatch()

        star.draw(camera, STAR_RADIUS, elapsedSeconds, cpuLoad)

        planetRenderer.beginBatch(camera)
        for (i in PLANETS.indices) {
            planetRenderer.draw(PLANETS[i], PLANETS[i].position(), elapsedSeconds, i == hoveredIndex)
        }
        planetRenderer.endBatch()
    }

    /** Everything `OverlayWindow`'s HUD pass needs for one planet's
     * label: text, exec command (shown on hover), the planet's own
     * projected screen position (for the connecting line), the label
     * anchor's screen position, hover state, and the glow tint to use.
     * `visible` is false when [OverlayCamera.worldToScreenOrNull]
     * determined the planet is behind the camera plane -- the caller must
     * skip drawing this entry entirely in that case, not just hide the text.  */
    @JvmRecord
    data class HudEntry(
        val label: String,
        val exec: String,
        val planetScreenX: Float,
        val planetScreenY: Float,
        val anchorScreenX: Float,
        val anchorScreenY: Float,
        val hovered: Boolean,
        val glowColor: Vector3f,
        val visible: Boolean
    )

    fun hudEntries(camera: OverlayCamera, framebufferWidth: Int, framebufferHeight: Int): List<HudEntry> {
        val entries = ArrayList<HudEntry>(PLANETS.size)
        for (i in PLANETS.indices) {
            val planet = PLANETS[i]
            val position = planet.position()
            val screen = camera.worldToScreenOrNull(
                position.x, position.y, position.z,
                framebufferWidth, framebufferHeight
            )
            if (screen == null) {
                entries.add(
                    HudEntry(
                        planet.label, planet.exec, 0f, 0f, 0f, 0f,
                        false, planet.emissiveGlow, false
                    )
                )
                continue
            }
            val anchorX = screen.x + LABEL_ANCHOR_OFFSET_X_PX
            val anchorY = screen.y + LABEL_ANCHOR_OFFSET_Y_PX
            entries.add(
                HudEntry(
                    planet.label, planet.exec, screen.x, screen.y,
                    anchorX, anchorY, i == hoveredIndex, planet.emissiveGlow, true
                )
            )
        }
        return entries
    }

    fun destroy() {
        star.destroy()
        planetRenderer.destroy()
        ringRenderer.destroy()
        beamRenderer.destroy()
    }

    companion object {
        const val STAR_RADIUS = 0.35f

        /** Pixel offset from a planet's projected screen position to its HUD
         * label's anchor -- the "angled guide vector" the connecting line
         * follows.  */
        private const val LABEL_ANCHOR_OFFSET_X_PX = 55f
        private const val LABEL_ANCHOR_OFFSET_Y_PX = -35f

        /** How long a click's ring-ripple/pill-flash stays visible.  */
        private const val RIPPLE_DURATION_SECONDS = 0.6
        private const val PLANET_COUNT = 5

        private val PLANETS = arrayOf(
            PlanetNode(
                "Terminal", "kitty", 0.8f, 0.6f, angle(0),
                Vector3f(0.2f, 0.9f, 0.4f), Vector3f(0.3f, 1.0f, 0.5f), 0.10f,
                PolyhedronMesh.Shape.TETRAHEDRON
            ),
            PlanetNode(
                "Browser", "google-chrome-stable", 1.15f, 0.4f, angle(1),
                Vector3f(1.0f, 0.6f, 0.1f), Vector3f(1.0f, 0.75f, 0.2f), 0.12f,
                PolyhedronMesh.Shape.ICOSAHEDRON
            ),
            PlanetNode(
                "IDE", "idea", 1.5f, 0.3f, angle(2),
                Vector3f(1.0f, 0.85f, 0.2f), Vector3f(1.0f, 0.9f, 0.4f), 0.14f,
                PolyhedronMesh.Shape.OCTAHEDRON
            ),
            PlanetNode(
                "Chat", "telegram-desktop", 1.85f, 0.5f, angle(3),
                Vector3f(0.2f, 0.4f, 1.0f), Vector3f(0.3f, 0.5f, 1.0f), 0.11f,
                PolyhedronMesh.Shape.ICOSAHEDRON
            ),
            PlanetNode(
                "Files", "thunar", 2.2f, 0.35f, angle(4),
                Vector3f(0.2f, 0.9f, 1.0f), Vector3f(0.3f, 0.95f, 1.0f), 0.13f,
                PolyhedronMesh.Shape.OCTAHEDRON
            ),
        )

        private fun angle(index: Int): Float =
            (2 * kotlin.math.PI * index / PLANET_COUNT).toFloat()
    }
}
