package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/** Compiles/links one vertex+fragment GLSL program and caches its uniform locations. */
final class Shader {

    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    Shader(String vertexSource, String fragmentSource) {
        int vertexShader = compile(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compile(GL_FRAGMENT_SHADER, fragmentSource);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Shader link failed: " + glGetProgramInfoLog(programId));
        }

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String kind = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new IllegalStateException("Failed to compile " + kind + " shader: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    void use() {
        glUseProgram(programId);
    }

    void destroy() {
        glDeleteProgram(programId);
    }

    void setMat4(String name, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            glUniformMatrix4fv(location(name), false, buffer);
        }
    }

    void setVec4(String name, float x, float y, float z, float w) {
        glUniform4f(location(name), x, y, z, w);
    }

    void setInt(String name, int value) {
        glUniform1i(location(name), value);
    }

    private int location(String name) {
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }
}
