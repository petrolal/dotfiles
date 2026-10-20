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
        canvas.useProgram(programId);
    }

    public void updateUniforms(WallpaperEvents.WallpaperState state, float viewportOffsetX, float viewportOffsetY) throws Throwable {
        canvas.uniform2f(locRes, state.width(), state.height());
        canvas.uniform1f(locTime, state.time());
        canvas.uniform4f(locMouse, state.mouseX(), state.mouseY(),
                state.mouseLeftDown() ? 1.0f : 0.0f, state.mouseRightDown() ? 1.0f : 0.0f);
        canvas.uniform1f(locWorkspace, (float) state.workspace());
        canvas.uniform1f(locAudio, state.audioActivity());
        canvas.uniform1f(locCpu, state.cpu());
        canvas.uniform1f(locRam, state.ram());
        canvas.uniform2f(locViewportOffset, viewportOffsetX, viewportOffsetY);
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
        int fs = compileShader(fragPath, X11Canvas.GL_FRAGMENT_SHADER);

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

        if (!canvas.getShaderCompileStatus(shader)) {
            String log = canvas.getShaderInfoLog(shader);
            canvas.deleteShader(shader);
            throw new IllegalStateException("Shader compile failed (" + path.getFileName() + "): " + log);
        }
        return shader;
    }

    private void cacheUniformLocations() throws Throwable {
        locRes = canvas.getUniformLocation(programId, "u_resolution");
        locTime = canvas.getUniformLocation(programId, "u_time");
        locMouse = canvas.getUniformLocation(programId, "u_mouse");
        locWorkspace = canvas.getUniformLocation(programId, "u_workspace");
        locAudio = canvas.getUniformLocation(programId, "u_audio");
        locCpu = canvas.getUniformLocation(programId, "u_cpu");
        locRam = canvas.getUniformLocation(programId, "u_ram");
        locViewportOffset = canvas.getUniformLocation(programId, "u_viewportOffset");
    }
}
