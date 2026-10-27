package dev.petrolal.dotfiles.overlay;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the Astroland scene's planets, central star, glowing orbit ribbons,
 * and HUD label data; handles ray-sphere hover/click against the planets.
 * Mapped to apps actually installed on this host (confirmed against
 * {@code nixos/modules/packages.nix}, not guessed): {@code kitty},
 * {@code google-chrome-stable} (Firefox isn't installed here), {@code idea}
 * (a real JetBrains Toolbox shim, confirmed present), {@code
 * telegram-desktop} (Discord isn't installed here), {@code thunar}.
 *
 * <p>Planet positions are fixed, not continuously orbiting -- see
 * {@link PlanetNode}'s javadoc for why: this is meant to be used as a real,
 * fast, daily-use launcher, and a moving click target you have to re-aim a
 * manually-orbited camera at works against that.
 */
final class CelestialSystem {

    static final float STAR_RADIUS = 0.35f;
    /** Pixel offset from a planet's projected screen position to its HUD
     * label's anchor -- the "angled guide vector" the connecting line
     * follows. */
    private static final float LABEL_ANCHOR_OFFSET_X_PX = 55f;
    private static final float LABEL_ANCHOR_OFFSET_Y_PX = -35f;
    /** How long a click's ring-ripple/pill-flash stays visible. */
    private static final double RIPPLE_DURATION_SECONDS = 0.6d;
    private static final int PLANET_COUNT = 5;

    private static final PlanetNode[] PLANETS = {
            new PlanetNode("Terminal", "kitty", 0.8f, 0.6f, angle(0),
                    new Vector3f(0.2f, 0.9f, 0.4f), new Vector3f(0.3f, 1.0f, 0.5f), 0.10f,
                    PolyhedronMesh.Shape.TETRAHEDRON),
            new PlanetNode("Browser", "google-chrome-stable", 1.15f, 0.4f, angle(1),
                    new Vector3f(1.0f, 0.6f, 0.1f), new Vector3f(1.0f, 0.75f, 0.2f), 0.12f,
                    PolyhedronMesh.Shape.ICOSAHEDRON),
            new PlanetNode("IDE", "idea", 1.5f, 0.3f, angle(2),
                    new Vector3f(1.0f, 0.85f, 0.2f), new Vector3f(1.0f, 0.9f, 0.4f), 0.14f,
                    PolyhedronMesh.Shape.OCTAHEDRON),
            new PlanetNode("Chat", "telegram-desktop", 1.85f, 0.5f, angle(3),
                    new Vector3f(0.2f, 0.4f, 1.0f), new Vector3f(0.3f, 0.5f, 1.0f), 0.11f,
                    PolyhedronMesh.Shape.ICOSAHEDRON),
            new PlanetNode("Files", "thunar", 2.2f, 0.35f, angle(4),
                    new Vector3f(0.2f, 0.9f, 1.0f), new Vector3f(0.3f, 0.95f, 1.0f), 0.13f,
                    PolyhedronMesh.Shape.OCTAHEDRON),
    };

    // Takes PLANET_COUNT, not PLANETS.length -- referencing PLANETS here
    // (even just its length) during its own static initializer throws
    // NullPointerException: the array literal above calls this for each
    // element while PLANETS itself is still being assigned, so the field
    // reads as null until the whole initializer completes.
    private static float angle(int index) {
        return (float) (2 * Math.PI * index / PLANET_COUNT);
    }

    private final StarRenderer star = new StarRenderer();
    private final PlanetRenderer planetRenderer = new PlanetRenderer();
    private final OrbitRingRenderer ringRenderer;
    private final VectorBeamRenderer beamRenderer;
    private final ProcessLauncher processLauncher;
    private int hoveredIndex = -1;
    private final double[] lastClickSeconds = new double[PLANETS.length];

    CelestialSystem(ProcessLauncher processLauncher) {
        this.processLauncher = processLauncher;
        java.util.Arrays.fill(lastClickSeconds, Double.NEGATIVE_INFINITY);

        float[] radii = new float[PLANETS.length];
        Vector3f[] positions = new Vector3f[PLANETS.length];
        for (int i = 0; i < PLANETS.length; i++) {
            radii[i] = PLANETS[i].orbitalRadius();
            positions[i] = PLANETS[i].position();
        }
        ringRenderer = new OrbitRingRenderer(radii);
        beamRenderer = new VectorBeamRenderer(new Vector3f(0f, 0f, 0f), positions);
    }

    /** Re-tests the hover ray against every planet; called every frame
     * (not just on mouse-move) from {@code OverlayWindow}'s loop, since
     * positions are fixed so this is cheap and needs no dedicated
     * cursor-moved callback. */
    void updateHover(OverlayCamera.Ray ray) {
        hoveredIndex = hitTest(ray);
    }

    private int hitTest(OverlayCamera.Ray ray) {
        int best = -1;
        float bestT = Float.POSITIVE_INFINITY;
        for (int i = 0; i < PLANETS.length; i++) {
            float t = Raycaster.intersectSphere(ray, PLANETS[i].position(), PLANETS[i].sphereRadius());
            if (!Float.isNaN(t) && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    /** Launches whatever's currently hovered, if anything, and records the
     * click time to drive the ring-ripple/pill-flash decay. Call from the
     * mouse-click handler once a drag has been ruled out. */
    void clickHovered(double elapsedSeconds) {
        if (hoveredIndex >= 0) {
            PlanetNode planet = PLANETS[hoveredIndex];
            lastClickSeconds[hoveredIndex] = elapsedSeconds;
            processLauncher.launch(planet.label(), planet.exec());
        }
    }

    /** Fraction of the ripple animation remaining, in [0, 1]. */
    private float rippleAmount(int index, double elapsedSeconds) {
        double sinceClick = elapsedSeconds - lastClickSeconds[index];
        if (sinceClick < 0d || sinceClick > RIPPLE_DURATION_SECONDS) {
            return 0f;
        }
        return (float) (1d - sinceClick / RIPPLE_DURATION_SECONDS);
    }

    void draw(OverlayCamera camera, double elapsedSeconds, float cpuLoad) {
        ringRenderer.beginBatch(camera, elapsedSeconds);
        for (int i = 0; i < PLANETS.length; i++) {
            ringRenderer.draw(i, rippleAmount(i, elapsedSeconds));
        }

        beamRenderer.beginBatch(camera, elapsedSeconds);
        for (int i = 0; i < PLANETS.length; i++) {
            beamRenderer.draw(i, PLANETS[i].emissiveGlow(), i == hoveredIndex);
        }
        beamRenderer.endBatch();

        star.draw(camera, STAR_RADIUS, elapsedSeconds, cpuLoad);

        planetRenderer.beginBatch(camera);
        for (int i = 0; i < PLANETS.length; i++) {
            planetRenderer.draw(PLANETS[i], PLANETS[i].position(), elapsedSeconds, i == hoveredIndex);
        }
        planetRenderer.endBatch();
    }

    /** Everything {@code OverlayWindow}'s HUD pass needs for one planet's
     * label: text, exec command (shown on hover), the planet's own
     * projected screen position (for the connecting line), the label
     * anchor's screen position, hover state, and the glow tint to use.
     * {@code visible} is false when {@link OverlayCamera#worldToScreenOrNull}
     * determined the planet is behind the camera plane -- the caller must
     * skip drawing this entry entirely in that case, not just hide the text. */
    record HudEntry(String label, String exec, float planetScreenX, float planetScreenY,
                     float anchorScreenX, float anchorScreenY, boolean hovered,
                     Vector3f glowColor, boolean visible) {
    }

    List<HudEntry> hudEntries(OverlayCamera camera, int framebufferWidth, int framebufferHeight) {
        List<HudEntry> entries = new ArrayList<>(PLANETS.length);
        for (int i = 0; i < PLANETS.length; i++) {
            PlanetNode planet = PLANETS[i];
            Vector3f position = planet.position();
            Vector2f screen = camera.worldToScreenOrNull(position.x, position.y, position.z,
                    framebufferWidth, framebufferHeight);
            if (screen == null) {
                entries.add(new HudEntry(planet.label(), planet.exec(), 0f, 0f, 0f, 0f,
                        false, planet.emissiveGlow(), false));
                continue;
            }
            float anchorX = screen.x + LABEL_ANCHOR_OFFSET_X_PX;
            float anchorY = screen.y + LABEL_ANCHOR_OFFSET_Y_PX;
            entries.add(new HudEntry(planet.label(), planet.exec(), screen.x, screen.y,
                    anchorX, anchorY, i == hoveredIndex, planet.emissiveGlow(), true));
        }
        return entries;
    }

    void destroy() {
        star.destroy();
        planetRenderer.destroy();
        ringRenderer.destroy();
        beamRenderer.destroy();
    }
}
