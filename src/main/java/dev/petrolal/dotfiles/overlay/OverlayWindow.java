package dev.petrolal.dotfiles.overlay;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Milestone 1 of the render pipeline: a GLFW window + GL context + render
 * loop, with nothing drawn yet beyond a clear color. Proves windowing and
 * the Hyprland window-rule match (see docs/migration_plan.md) before any
 * camera, texture, or font rendering is added.
 *
 * Run via the {@code quick-overlay} Gradle task (plain JVM, no native-image
 * build) during development; press Escape to close.
 */
public final class OverlayWindow {

    private long windowHandle;

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

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
        });

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(1);
        glfwShowWindow(windowHandle);

        GL.createCapabilities();
    }

    private void loop() {
        glClearColor(0.07f, 0.07f, 0.09f, 1.0f);

        while (!glfwWindowShouldClose(windowHandle)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
    }

    private void destroy() {
        Callbacks.glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();

        GLFWErrorCallback previous = glfwSetErrorCallback(null);
        if (previous != null) {
            previous.free();
        }
    }
}
