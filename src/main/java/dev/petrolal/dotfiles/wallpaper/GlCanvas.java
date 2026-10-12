package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Window-system-independent half of the wallpaper renderer: owns the GL 3.3
 * function pointers and the fullscreen quad, and leaves everything that
 * differs between X11/GLX and Wayland/EGL (surface creation, cursor/workspace
 * queries, buffer swap) to the concrete subclass ({@link X11Canvas},
 * {@link WaylandCanvas}). {@link ShaderPipeline} and {@link WallpaperEvents}
 * only ever see this type, so the same shader pipeline runs unchanged under
 * either windowing backend.
 */
public abstract class GlCanvas implements AutoCloseable {

    public record MouseInfo(float x, float y, boolean leftDown, boolean rightDown) {}

    public final MethodHandle glCreateShader;
    public final MethodHandle glShaderSource;
    public final MethodHandle glCompileShader;
    public final MethodHandle glCreateProgram;
    public final MethodHandle glAttachShader;
    public final MethodHandle glLinkProgram;
    public final MethodHandle glUseProgram;
    public final MethodHandle glDeleteShader;
    public final MethodHandle glDeleteProgram;
    public final MethodHandle glGetUniformLocation;
    public final MethodHandle glUniform1f;
    public final MethodHandle glUniform2f;
    public final MethodHandle glUniform4f;
    public final MethodHandle glViewport;
    public final MethodHandle glGenBuffers;
    public final MethodHandle glBindBuffer;
    public final MethodHandle glBufferData;
    public final MethodHandle glEnableVertexAttribArray;
    public final MethodHandle glVertexAttribPointer;
    public final MethodHandle glDrawArrays;

    /**
     * @param procResolver resolves a GL function name to its address: glXGetProcAddress
     *                      for X11/GLX, eglGetProcAddress for Wayland/EGL.
     */
    protected GlCanvas(java.util.function.Function<String, MemorySegment> procResolver) throws Throwable {
        Linker linker = Linker.nativeLinker();
        glCreateShader = bind(linker, procResolver, "glCreateShader", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glShaderSource = bind(linker, procResolver, "glShaderSource", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        glCompileShader = bind(linker, procResolver, "glCompileShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glCreateProgram = bind(linker, procResolver, "glCreateProgram", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        glAttachShader = bind(linker, procResolver, "glAttachShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glLinkProgram = bind(linker, procResolver, "glLinkProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glUseProgram = bind(linker, procResolver, "glUseProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glDeleteShader = bind(linker, procResolver, "glDeleteShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glDeleteProgram = bind(linker, procResolver, "glDeleteProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glGetUniformLocation = bind(linker, procResolver, "glGetUniformLocation", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glUniform1f = bind(linker, procResolver, "glUniform1f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
        glUniform2f = bind(linker, procResolver, "glUniform2f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        glUniform4f = bind(linker, procResolver, "glUniform4f", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        glViewport = bind(linker, procResolver, "glViewport", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glGenBuffers = bind(linker, procResolver, "glGenBuffers", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glBindBuffer = bind(linker, procResolver, "glBindBuffer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        glBufferData = bind(linker, procResolver, "glBufferData", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        glEnableVertexAttribArray = bind(linker, procResolver, "glEnableVertexAttribArray", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        glVertexAttribPointer = bind(linker, procResolver, "glVertexAttribPointer", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        glDrawArrays = bind(linker, procResolver, "glDrawArrays", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }

    private static MethodHandle bind(Linker linker, java.util.function.Function<String, MemorySegment> procResolver,
                                      String name, FunctionDescriptor desc) {
        MemorySegment ptr = procResolver.apply(name);
        if (ptr == null || ptr.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Could not resolve GL function: " + name);
        }
        return linker.downcallHandle(ptr, desc);
    }

    protected void setupFullscreenQuad() throws Throwable {
        float[] quadVertices = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment vboBuf = arena.allocate(ValueLayout.JAVA_INT);
            glGenBuffers.invokeExact(1, vboBuf);
            int vbo = vboBuf.get(ValueLayout.JAVA_INT, 0);

            glBindBuffer.invokeExact(0x8892 /* GL_ARRAY_BUFFER */, vbo);
            MemorySegment quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, quadVertices);
            glBufferData.invokeExact(0x8892, quadVertices.length * 4L, quadData, 0x88E4 /* GL_STATIC_DRAW */);

            glEnableVertexAttribArray.invokeExact(0);
            glVertexAttribPointer.invokeExact(0, 2, 0x1406 /* GL_FLOAT */, (byte) 0, 0, MemorySegment.NULL);
        }
    }

    public void setViewport(int x, int y, int width, int height) throws Throwable {
        glViewport.invokeExact(x, y, width, height);
    }

    public void drawQuad() throws Throwable {
        glDrawArrays.invokeExact(0x0005 /* GL_TRIANGLE_STRIP */, 0, 4);
    }

    public abstract MouseInfo queryMouse();

    public abstract int queryWorkspace();

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void swapBuffers() throws Throwable;

    @Override
    public abstract void close();
}
