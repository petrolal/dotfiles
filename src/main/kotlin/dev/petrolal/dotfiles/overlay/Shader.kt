package dev.petrolal.dotfiles.overlay

import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.system.MemoryStack

/** Compiles/links one vertex+fragment GLSL program and caches its uniform locations.  */
class Shader(vertexSource: String, fragmentSource: String) {
    private val programId: Int
    private val uniformLocations = HashMap<String, Int>()

    init {
        val vertexShader = compile(GL20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource)

        programId = GL20.glCreateProgram()
        GL20.glAttachShader(programId, vertexShader)
        GL20.glAttachShader(programId, fragmentShader)
        GL20.glLinkProgram(programId)

        check(GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != GL11.GL_FALSE) {
            "Shader link failed: ${GL20.glGetProgramInfoLog(programId)}"
        }

        GL20.glDeleteShader(vertexShader)
        GL20.glDeleteShader(fragmentShader)
    }

    fun use() {
        GL20.glUseProgram(programId)
    }

    fun destroy() {
        GL20.glDeleteProgram(programId)
    }

    fun setMat4(name: String, matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val buffer = stack.mallocFloat(16)
            matrix.get(buffer)
            GL20.glUniformMatrix4fv(location(name), false, buffer)
        }
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        GL20.glUniform4f(location(name), x, y, z, w)
    }

    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        GL20.glUniform3f(location(name), x, y, z)
    }

    fun setVec2(name: String, x: Float, y: Float) {
        GL20.glUniform2f(location(name), x, y)
    }

    fun setInt(name: String, value: Int) {
        GL20.glUniform1i(location(name), value)
    }

    fun setFloat(name: String, value: Float) {
        GL20.glUniform1f(location(name), value)
    }

    private fun location(name: String): Int =
        uniformLocations.getOrPut(name) { GL20.glGetUniformLocation(programId, name) }

    companion object {
        private fun compile(type: Int, source: String): Int {
            val shader = GL20.glCreateShader(type)
            GL20.glShaderSource(shader, source)
            GL20.glCompileShader(shader)

            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                val kind = if (type == GL20.GL_VERTEX_SHADER) "vertex" else "fragment"
                throw IllegalStateException("Failed to compile $kind shader: ${GL20.glGetShaderInfoLog(shader)}")
            }
            return shader
        }
    }
}
