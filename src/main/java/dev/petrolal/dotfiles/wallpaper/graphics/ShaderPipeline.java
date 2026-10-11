package dev.petrolal.dotfiles.wallpaper.graphics;

import java.lang.foreign.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import dev.petrolal.dotfiles.wallpaper.nativebidings.GLContext;

public class ShaderPipeline {
	private final GLContext gl;
	private final Path vertPath;
	private final Path fragPath;

	private int programId;
	private FileTime lastMtime;

	private int locRes;
	private int locTime;
	private int locCpu;
	private int locRam;

	public ShaderPipeline(GLContext gl, Path vertPath, Path fragPath) throws Throwable {
		this.gl = gl;
		this.vertPath = vertPath;
		this.fragPath = fragPath;

		this.programId = compileProgram();
		this.lastMtime = Files.getLastModifiedTime(fragPath);
		cacheUniformLocations();
	}

	public void bind() throws Throwable {
		gl.glUseProgram.invokeExact(programId);
	}

	public void updateUniforms(float width, float height, float elapsed, float cpu, float ram) throws Throwable {
		gl.glUniform2f.invokeExact(locRes, width, height);
		gl.glUniform1f.invokeExact(locTime, elapsed);
		gl.glUniform1f.invokeExact(locCpu, cpu);
		gl.glUniform1f.invokeExact(locRam, ram);
	}

	public void reloadIfModified() {
		try {
			FileTime currentMtime = Files.getLastModifiedTime(fragPath);
			if (currentMtime.compareTo(lastMtime) > 0) {
				int newProg = compileProgram();
				gl.glDeleteProgram.invokeExact(programId);
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
			int vs = compileShader(vertPath, 0x8B31 /* GL_VERTEX_SHADER */, arena);
			int fs = compileShader(fragPath, 0x8B30 /* GL_FRAGMENT_SHADER */, arena);

			int prog = (int) gl.glCreateProgram.invokeExact();
			gl.glAttachShader.invokeExact(prog, vs);
			gl.glAttachShader.invokeExact(prog, fs);
			gl.glLinkProgram.invokeExact(prog);

			gl.glDeleteShader.invokeExact(vs);
			gl.glDeleteShader.invokeExact(fs);
			return prog;
		}
	}

	private int compileShader(Path path, int type, Arena arena) throws Throwable {
		String code = Files.readString(path);
		int shader = (int) gl.glCreateShader.invokeExact(type);
		MemorySegment ptr = arena.allocateFrom(code);
		MemorySegment ptrArray = arena.allocateFrom(ValueLayout.ADDRESS, ptr);
		gl.glShaderSource.invokeExact(shader, 1, ptrArray, MemorySegment.NULL);
		gl.glCompileShader.invokeExact(shader);
		return shader;
	}

	private void cacheUniformLocations() throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			locRes = (int) gl.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_resolution"));
			locTime = (int) gl.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_time"));
			locCpu = (int) gl.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_cpu"));
			locRam = (int) gl.glGetUniformLocation.invokeExact(programId, arena.allocateFrom("u_ram"));
		}
	}
}