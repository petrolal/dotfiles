package dev.petrolal.dotfiles.overlay;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Milestone 6 of the render pipeline: GLFW window + GL context + render loop
 * (milestone 1) driving a perspective camera + quad renderer (milestone 2),
 * text labels (milestone 3), icon textures (milestone 4), and real
 * {@link DesktopEntryScanner} data via {@link NodeLayout} (milestone 5), now
 * with mouse/keyboard node selection wired to {@link ProcessLauncher}. A
 * `Cli` subcommand to launch this is still a separate Integration item (see
 * docs/checklist.md).
 *
 * <p>Mouse: click a node to launch it immediately. Keyboard: arrow keys move
 * a highlighted selection (rendered as a yellow border quad behind the node),
 * Enter launches whatever's selected.
 *
 * Run via the {@code quick-overlay} Gradle task (plain JVM, no native-image
 * build) during development; press Escape to close.
 */
public final class OverlayWindow {

    private static final String LABEL_FONT_FAMILY = "JetBrainsMono Nerd Font Mono";
    private static final float LABEL_PIXEL_HEIGHT = 24f;
    private static final int ICON_SIZE_HINT = 48;
    private static final float LABEL_OFFSET_Y = 0.6f; // world units below each node's icon
    private static final float SELECTION_HIGHLIGHT_SIZE = NodeLayout.NODE_SIZE * 1.15f;

    private long windowHandle;
    private OverlayCamera camera;
    private QuadRenderer quadRenderer;
    private FontAtlas fontAtlas;
    private TextRenderer textRenderer;
    private IconLoader iconLoader;
    private IconRenderer iconRenderer;
    private ProcessLauncher processLauncher;
    private List<NodeLayout.PositionedEntry> nodes;
    private int nodeColumns;
    private int selectedIndex = -1;
    private int framebufferWidth;
    private int framebufferHeight;

    public static void main(String[] args) {
        NativeLibraryPaths.install();
        new OverlayWindow().run();
    }

    private void run() {
        init();
        loop();
        destroy();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        // Must match the `class:^(imp-overlay)$` windowrulev2 rules in
        // docs/migration_plan.md for Hyprland to apply them.
        glfwWindowHintString(GLFW_X11_CLASS_NAME, "imp-overlay");
        glfwWindowHintString(GLFW_X11_INSTANCE_NAME, "imp-overlay");

        windowHandle = glfwCreateWindow(1920, 1080, "imp overlay", NULL, NULL);
        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();

        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(windowHandle, width, height);
        framebufferWidth = width[0];
        framebufferHeight = height[0];
        camera = new OverlayCamera(framebufferWidth, framebufferHeight);
        quadRenderer = new QuadRenderer();
        fontAtlas = new FontAtlas(LABEL_FONT_FAMILY, LABEL_PIXEL_HEIGHT);
        textRenderer = new TextRenderer(fontAtlas);
        textRenderer.resize(framebufferWidth, framebufferHeight);
        iconLoader = new IconLoader();
        iconRenderer = new IconRenderer();
        processLauncher = new ProcessLauncher();

        NodeLayout.Grid grid = NodeLayout.grid(new DesktopEntryScanner().scan());
        nodes = grid.nodes();
        nodeColumns = grid.columns();
        selectedIndex = nodes.isEmpty() ? -1 : 0;

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true);
            } else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
                // Deliberately GLFW_PRESS only, not GLFW_REPEAT: holding
                // Enter down must not re-launch the same app repeatedly.
                if (action == GLFW_PRESS) {
                    launchSelected();
                }
            } else if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                // Arrow-key navigation is fine to repeat while held.
                if (key == GLFW_KEY_LEFT) {
                    moveSelection(-1);
                } else if (key == GLFW_KEY_RIGHT) {
                    moveSelection(1);
                } else if (key == GLFW_KEY_UP) {
                    moveSelection(-nodeColumns);
                } else if (key == GLFW_KEY_DOWN) {
                    moveSelection(nodeColumns);
                }
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                double[] cursorX = new double[1];
                double[] cursorY = new double[1];
                glfwGetCursorPos(window, cursorX, cursorY);
                int hit = nodeAt((float) cursorX[0], (float) cursorY[0]);
                if (hit >= 0) {
                    selectedIndex = hit;
                    launchSelected();
                }
            }
        });

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // text alpha is the glyph coverage

        // Registered only after GL.createCapabilities()/camera/quadRenderer
        // exist: on Wayland, glfwShowWindow below fires this callback
        // synchronously (an immediate compositor configure event), so
        // anything it touches must already be ready.
        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
            if (w > 0 && h > 0) {
                framebufferWidth = w;
                framebufferHeight = h;
                glViewport(0, 0, w, h);
                camera.resize(w, h);
                textRenderer.resize(w, h);
            }
        });

        glfwSwapInterval(1);
        glfwShowWindow(windowHandle);
    }

    private void moveSelection(int delta) {
        if (nodes.isEmpty()) {
            return;
        }
        int next = selectedIndex + delta;
        if (next >= 0 && next < nodes.size()) {
            selectedIndex = next;
        }
    }

    private void launchSelected() {
        if (selectedIndex >= 0 && selectedIndex < nodes.size()) {
            processLauncher.launch(nodes.get(selectedIndex).entry());
        }
    }

    /** Screen-space hit test: returns the clicked node's index, or -1 if the click missed every node. */
    private int nodeAt(float screenX, float screenY) {
        for (int i = 0; i < nodes.size(); i++) {
            NodeLayout.PositionedEntry node = nodes.get(i);
            var center = camera.worldToScreen(node.x(), node.y(), 0f, framebufferWidth, framebufferHeight);
            var edge = camera.worldToScreen(node.x() + NodeLayout.NODE_SIZE / 2f, node.y(), 0f,
                    framebufferWidth, framebufferHeight);
            float halfSize = Math.abs(edge.x - center.x);
            if (Math.abs(screenX - center.x) <= halfSize && Math.abs(screenY - center.y) <= halfSize) {
                return i;
            }
        }
        return -1;
    }

    private void loop() {
        glClearColor(0.07f, 0.07f, 0.09f, 1.0f);

        while (!glfwWindowShouldClose(windowHandle)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (selectedIndex >= 0) {
                NodeLayout.PositionedEntry selected = nodes.get(selectedIndex);
                quadRenderer.draw(camera, selected.x(), selected.y(), -0.01f,
                        SELECTION_HIGHLIGHT_SIZE, SELECTION_HIGHLIGHT_SIZE, 0.9f, 0.85f, 0.1f, 1f);
            }

            for (NodeLayout.PositionedEntry node : nodes) {
                quadRenderer.draw(camera, node.x(), node.y(), 0f, 1f, 1f, 0.3f, 0.6f, 0.9f, 1f);

                IconLoader.IconTexture icon = iconLoader.load(node.entry().icon(), ICON_SIZE_HINT);
                iconRenderer.draw(camera, icon, node.x(), node.y(), 0.01f, 0.7f, 0.7f);
            }

            // Label projected from each node's world position, not a
            // separate decorative row -- see OverlayCamera.worldToScreen.
            for (NodeLayout.PositionedEntry node : nodes) {
                var screen = camera.worldToScreen(node.x(), node.y() - LABEL_OFFSET_Y, 0f,
                        framebufferWidth, framebufferHeight);
                textRenderer.draw(node.entry().name(), screen.x, screen.y, 1f, 1f, 1f, 1f);
            }

            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
    }

    private void destroy() {
        iconRenderer.destroy();
        iconLoader.destroy();
        textRenderer.destroy();
        fontAtlas.destroy();
        quadRenderer.destroy();
        Callbacks.glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback previous = glfwSetErrorCallback(null);
        if (previous != null) {
            previous.free();
        }
    }
}
