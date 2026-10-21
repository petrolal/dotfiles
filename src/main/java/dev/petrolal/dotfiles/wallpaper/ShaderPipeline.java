package dev.petrolal.dotfiles.wallpaper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * ShaderPipeline manages compilation, linking, uniform binding, and live hot-reloading
 * of OpenGL shaders. All GL access goes through X11Canvas's typed wrapper
 * methods -- this class never touches a MethodHandle, Arena, or MemorySegment.
 */
public class ShaderPipeline {

    private final X11Canvas canvas;
    private final Path vertPath;
    private final Path fragPath;

    private int programId;
    private FileTime lastMtime;

    // Uniform locations
    private int locTexture = -1;
    private int locRes = -1;
    private int locTimeOfDay = -1;
    private int locHasTexture = -1;
    private boolean hasTexture = true;
    private int locTime = -1;
    private int locMouse = -1;
    private int locWorkspace = -1;
    private int locAudio = -1;
    private int locCpu = -1;
    private int locRam = -1;
    private int locViewportOffset = -1;
    private int locRenderScale = -1;

    public ShaderPipeline(X11Canvas canvas, Path vertPath, Path fragPath) throws Throwable {
        this.canvas = canvas;
        this.vertPath = vertPath;
        this.fragPath = fragPath;

        this.programId = compileProgram();
        this.lastMtime = Files.getLastModifiedTime(fragPath);
        cacheUniformLocations();
    }

    /** Overrides u_resolution with the real size of the target being drawn into (the low-res FBO). */
    public void setResolution(float w, float h) throws Throwable {
        canvas.uniform2f(locRes, w, h);
    }

    /** False switches wallpaper.frag to its black + old-TV-static fallback. */
    public void setHasTexture(boolean value) {
        this.hasTexture = value;
    }

    public void bind() throws Throwable {
        canvas.useProgram(programId);
    }

    public void updateUniforms(WallpaperEvents.WallpaperState state, float viewportOffsetX, float viewportOffsetY,
                                float renderScale) throws Throwable {
        canvas.uniform2f(locRes, state.width(), state.height());
        canvas.uniform1f(locTimeOfDay, localTimeOfDay());
        canvas.uniform1f(locHasTexture, hasTexture ? 1.0f : 0.0f);
        canvas.uniform1f(locTime, state.time());
        canvas.uniform4f(locMouse, state.mouseX(), state.mouseY(),
                state.mouseLeftDown() ? 1.0f : 0.0f, state.mouseRightDown() ? 1.0f : 0.0f);
        canvas.uniform1f(locWorkspace, (float) state.workspace());
        canvas.uniform1f(locAudio, state.audioActivity());
        canvas.uniform1f(locCpu, state.cpu());
        canvas.uniform1f(locRam, state.ram());
        canvas.uniform2f(locViewportOffset, viewportOffsetX, viewportOffsetY);
        canvas.uniform1f(locRenderScale, renderScale);
    }

    /**
     * Checks if the fragment shader file was modified on disk and recompiles it live.
     */
    public void reloadIfModified() {
        try {
            FileTime currentMtime = Files.getLastModifiedTime(fragPath);
            if (currentMtime.compareTo(lastMtime) > 0) {
                int newProg = compileProgram();
                canvas.deleteProgram(programId);
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
        int vs = compileShader(vertPath, X11Canvas.GL_VERTEX_SHADER);
        int fs;
        try {
            fs = compileShader(fragPath, X11Canvas.GL_FRAGMENT_SHADER);
        } catch (Throwable ex) {
            // vs compiled fine but fs didn't -- without this, every failed
            // hot-reload attempt on a broken fragment shader leaks one more
            // vertex shader object in the driver for the life of the daemon.
            canvas.deleteShader(vs);
            throw ex;
        }

        int prog = canvas.createProgram();
        canvas.attachShader(prog, vs);
        canvas.attachShader(prog, fs);
        canvas.linkProgram(prog);
        boolean linked = canvas.getProgramLinkStatus(prog);

        canvas.deleteShader(vs);
        canvas.deleteShader(fs);

        if (!linked) {
            String log = canvas.getProgramInfoLog(prog);
            canvas.deleteProgram(prog);
            throw new IllegalStateException(
                    "Shader program link failed (" + fragPath.getFileName() + "): " + log);
        }
        return prog;
    }

    private int compileShader(Path path, int type) throws Throwable {
        int shader = canvas.createShader(type);
        canvas.shaderSource(shader, Files.readString(path));
        canvas.compileShader(shader);

        if (canvas.getShaderCompileStatus(shader)) {
            String log = canvas.getShaderInfoLog(shader);
            canvas.deleteShader(shader);
            throw new IllegalStateException("Shader compile failed (" + path.getFileName() + "): " + log);
        }
        return shader;
    }

    /** Local wall-clock time as a fractional hour in [0, 24): hour + minute/60 + second/3600. */
    static float localTimeOfDay() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.getHour() + now.getMinute() / 60.0f + now.getSecond() / 3600.0f;
    }

    /** Location of the {@code u_texture} sampler (-1 if the shader doesn't declare it). */
    public int textureLocation() { return locTexture; }

    private void cacheUniformLocations() throws Throwable {
        locTexture = canvas.getUniformLocation(programId, "u_texture");
        locRes = canvas.getUniformLocation(programId, "u_resolution");
        locTimeOfDay = canvas.getUniformLocation(programId, "u_timeOfDay");
        locHasTexture = canvas.getUniformLocation(programId, "u_hasTexture");
        locTime = canvas.getUniformLocation(programId, "u_time");
        locMouse = canvas.getUniformLocation(programId, "u_mouse");
        locWorkspace = canvas.getUniformLocation(programId, "u_workspace");
        locAudio = canvas.getUniformLocation(programId, "u_audio");
        locCpu = canvas.getUniformLocation(programId, "u_cpu");
        locRam = canvas.getUniformLocation(programId, "u_ram");
        locViewportOffset = canvas.getUniformLocation(programId, "u_viewportOffset");
        locRenderScale = canvas.getUniformLocation(programId, "u_renderScale");
    }
}
