package dev.petrolal.dotfiles.wallpaper.graphics;

import java.lang.foreign.*;

import dev.petrolal.dotfiles.wallpaper.nativebidings.GLContext;

public class Mesh {
	private final GLContext gl;
	private final int vertexCount;

	Mesh(GLContext gl, float[] vertices) throws Throwable {
		this.gl = gl;
		this.vertexCount = vertices.length / 2;

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment vboBuf = arena.allocate(ValueLayout.JAVA_INT);
			gl.glGenBuffers.invokeExact(1, vboBuf);
			int vbo = vboBuf.get(ValueLayout.JAVA_INT, 0);

			gl.glBindBuffer.invokeExact(0x8892 /* GL_ARRAY_BUFFER */, vbo);
			MemorySegment quadData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, vertices);
			gl.glBufferData.invokeExact(0x8892, vertices.length * 4L, quadData, 0x88E4 /* GL_STATIC_DRAW */);

			gl.glEnableVertexAttribArray.invokeExact(0);
			gl.glVertexAttribPointer.invokeExact(0, 2, 0x1406 /* GL_FLOAT */, (byte) 0, 0, MemorySegment.NULL);
		}
	}

	public static Mesh createFullscreenQuad(GLContext gl) throws Throwable {
		return new Mesh(gl, new float[] { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f });
	}

	public void render() throws Throwable {
		gl.glDrawArrays.invokeExact(0x0005 /* GL_TRIANGLE_STRIP */, 0, vertexCount);
	}
}
