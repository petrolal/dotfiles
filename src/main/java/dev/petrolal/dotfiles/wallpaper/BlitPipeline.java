package dev.petrolal.dotfiles.wallpaper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal second shader pass: samples the low-resolution offscreen
 * {@link X11Canvas.RenderTarget} the expensive raymarch shader drew into and
 * writes it to the real window-sized backbuffer. Deliberately separate from
 * {@link ShaderPipeline} -- no hot-reload, no per-frame uniform set beyond
 * the one texture sampler, and conflating the two would make the expensive
 * shader's uniform cache responsible for a texture binding it doesn't use.
 */
public class BlitPipeline {

    private final X11Canvas canvas;
    private final int programId;
    private final int locTexture;

    public BlitPipeline(X11Canvas canvas, Path vertPath, Path fragPath) throws Throwable {
        this.canvas = canvas;

        int vs = canvas.createShader(X11Canvas.GL_VERTEX_SHADER);
        canvas.shaderSource(vs, Files.readString(vertPath));
        canvas.compileShader(vs);
        if (canvas.getShaderCompileStatus(vs)) {
            String log = canvas.getShaderInfoLog(vs);
            canvas.deleteShader(vs);
            throw new IllegalStateException("Blit vertex shader compile failed (" + vertPath.getFileName() + "): " + log);
        }

        int fs = canvas.createShader(X11Canvas.GL_FRAGMENT_SHADER);
        canvas.shaderSource(fs, Files.readString(fragPath));
        canvas.compileShader(fs);
        if (canvas.getShaderCompileStatus(fs)) {
            String log = canvas.getShaderInfoLog(fs);
            canvas.deleteShader(fs);
            canvas.deleteShader(vs);
            throw new IllegalStateException("Blit fragment shader compile failed (" + fragPath.getFileName() + "): " + log);
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
            throw new IllegalStateException("Blit program link failed (" + fragPath.getFileName() + "): " + log);
        }

        this.programId = prog;
        this.locTexture = canvas.getUniformLocation(programId, "u_texture");
    }

    /** Binds the program and TEXTURE (texture unit 0) in one call, ready to draw a quad. */
    public void bind(int texture) throws Throwable {
        canvas.useProgram(programId);
        canvas.bindTexture(texture);
        canvas.uniform1i(locTexture, 0);
    }
}
