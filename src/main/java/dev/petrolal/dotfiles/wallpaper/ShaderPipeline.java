package dev.petrolal.dotfiles.wallpaper;

import java.lang.foreign.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * ShaderPipeline manages compilation, linking, uniform binding, and live hot-reloading
 * of OpenGL shaders.
 */
public class ShaderPipeline {

    private final X11Canvas canvas;
    private final Path vertPath;
    private final Path fragPath;

    private int programId;
    private FileTime lastMtime;

    // Uniform locations
    private int locRes = -1;
    private int locTime = -1;
    private int locMouse = -1;
    private int locWorkspace = -1;
    private int locAudio = -1;
    private int locCpu = -1;
    private int locRam = -1;
    private int locViewportOffset = -1;

    public ShaderPipeline(X11Canvas canvas, Path vertPath, Path fragPath) throws Throwable {
        this.canvas = canvas;
        this.vertPath = vertPath;
        this.fragPath = fragPath;

        this.programId = compileProgram();
        this.lastMtime = Files.getLastModifiedTime(fragPath);
        cacheUniformLocations();
    }

    public void bind() throws Throwable {
        canvas.glUseProgram.invokeExact(programId);
    }

    public void updateUniforms(WallpaperEvents.WallpaperState state, float viewportOffsetX, float viewportOffsetY) throws Throwable {
        if (locRes >= 0) canvas.glUniform2f.invokeExact(locRes, state.width(), state.height());
        if (locTime >= 0) canvas.glUniform1f.invokeExact(locTime, state.time());
        if (locMouse >= 0) {
            float left = state.mouseLeftDown() ? 1.0f : 0.0f;
            float right = state.mouseRightDown() ? 1.0f : 0.0f;
            canvas.glUniform4f.invokeExact(locMouse, state.mouseX(), state.mouseY(), left, right);
        }
        if (locWorkspace >= 0) canvas.glUniform1f.invokeExact(locWorkspace, (float) state.workspace());
        if (locAudio >= 0) canvas.glUniform1f.invokeExact(locAudio, state.audioActivity());
        if (locCpu >= 0) canvas.glUniform1f.invokeExact(locCpu, state.cpu());
        if (locRam >= 0) canvas.glUniform1f.invokeExact(locRam, state.ram());
        if (locViewportOffset >= 0) canvas.glUniform2f.invokeExact(locViewportOffset, viewportOffsetX, viewportOffsetY);
    }

    /**
     * Checks if the fragment shader file was modified on disk and recompiles it live.
     */
    public void reloadIfModified() {
        try {
            FileTime currentMtime = Files.getLastModifiedTime(fragPath);
            if (currentMtime.compareTo(lastMtime) > 0) {
                int newProg = compileProgram();
                canvas.glDeleteProgram.invokeExact(programId);
                programId = newProg;
                bind();
                cacheUniformLocations();
                lastMtime = currentMtime;
                System.out.println("[Hot-Reload] Recompiled " + fragPath.getFileName());
            }
        } catch (Throwable ex) {
            System.err.println("[Hot-Reload Error] " + ex.getMessage());
        }
    }

    private int compileProgram() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            int vs = compileShader(vertPath, X11Canvas.GL_VERTEX_SHADER, arena);
            int fs = compileShader(fragPath, X11Canvas.GL_FRAGMENT_SHADER, arena);

            int prog = (int) canvas.glCreateProgram.invokeExact();
            canvas.glAttachShader.invokeExact(prog, vs);
            canvas.glAttachShader.invokeExact(prog, fs);
            canvas.glLinkProgram.invokeExact(prog);

            canvas.glDeleteShader.invokeExact(vs);
            canvas.glDeleteShader.invokeExact(fs);
            return prog;
        }
    }

    private int compileShader(Path path, int type, Arena arena) throws Throwable {
        String code = Files.readString(path);
        int shader = (int) canvas.glCreateShader.invokeExact(type);
        MemorySegment ptr = arena.allocateFrom(code);
        MemorySegment ptrArray = arena.allocateFrom(ValueLayout.ADDRESS, ptr);
        canvas.glShaderSource.invokeExact(shader, 1, ptrArray, MemorySegment.NULL);
        canvas.glCompileShader.invokeExact(shader);
        return shader;
    }

    private void cacheUniformLocations() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            locRes = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_resolution"));
            locTime = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_time"));
            locMouse = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_mouse"));
            locWorkspace = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_workspace"));
            locAudio = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_audio"));
            locCpu = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_cpu"));
            locRam = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_ram"));
            locViewportOffset = (int) canvas.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_viewportOffset"));
        }
    }
}
