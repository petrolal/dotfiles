package dev.petrolal.dotfiles.wallpaper.nativebidings;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class GLContext {

	private final X11DesktopWindow window;
	private final MemorySegment glContext;

	private static final MethodHandle glXChooseVisual;
	private static final MethodHandle glXCreateContext;
	private static final MethodHandle glXMakeCurrent;
	private static final MethodHandle glXSwapBuffers;
	private static final MethodHandle glXGetProcAddress;

	// Direct OpenGL Bindings
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
	public final MethodHandle glGenBuffers;
	public final MethodHandle glBindBuffer;
	public final MethodHandle glBufferData;
	public final MethodHandle glEnableVertexAttribArray;
	public final MethodHandle glVertexAttribPointer;
	public final MethodHandle glDrawArrays;

	private static SymbolLookup loadLibrary(String libName, Arena arena) {
		try {
			return SymbolLookup.libraryLookup(libName, arena);
		} catch (IllegalArgumentException e) {
			String[] fallbackDirs = {
				"/run/current-system/sw/lib",
				"/usr/lib",
				"/usr/local/lib"
			};
			for (String dir : fallbackDirs) {
				java.nio.file.Path path = java.nio.file.Path.of(dir, libName);
				if (java.nio.file.Files.exists(path)) {
					return SymbolLookup.libraryLookup(path, arena);
				}
			}
			throw e;
		}
	}

	static {
		Linker linker = Linker.nativeLinker();
		SymbolLookup gl = loadLibrary("libGL.so", Arena.global());

		glXChooseVisual = linker.downcallHandle(gl.find("glXChooseVisual").get(), FunctionDescriptor
				.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		glXCreateContext = linker.downcallHandle(gl.find("glXCreateContext").get(),
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
		glXMakeCurrent = linker.downcallHandle(gl.find("glXMakeCurrent").get(), FunctionDescriptor
				.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		glXSwapBuffers = linker.downcallHandle(gl.find("glXSwapBuffers").get(),
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
		glXGetProcAddress = linker.downcallHandle(gl.find("glXGetProcAddress").get(),
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	public GLContext(X11DesktopWindow window) throws Throwable {
		this.window = window;
		Linker linker = Linker.nativeLinker();

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment glAttrs = arena.allocateFrom(ValueLayout.JAVA_INT, 4 /* GLX_RGBA */,
					5 /* GLX_DOUBLEBUFFER */, 0);
			MemorySegment visInfo = (MemorySegment) glXChooseVisual.invokeExact(window.getDisplay(), window.getScreen(),
					glAttrs);
			glContext = (MemorySegment) glXCreateContext.invokeExact(window.getDisplay(), visInfo, MemorySegment.NULL,
					1);
			int ignored = (int) glXMakeCurrent.invokeExact(window.getDisplay(), window.getWindow(), glContext);
		}

		// Initialize GL function pointers
		glCreateShader = getProc(linker, "glCreateShader",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		glShaderSource = getProc(linker, "glShaderSource", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT,
				ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		glCompileShader = getProc(linker, "glCompileShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
		glCreateProgram = getProc(linker, "glCreateProgram", FunctionDescriptor.of(ValueLayout.JAVA_INT));
		glAttachShader = getProc(linker, "glAttachShader",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		glLinkProgram = getProc(linker, "glLinkProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
		glUseProgram = getProc(linker, "glUseProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
		glDeleteShader = getProc(linker, "glDeleteShader", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
		glDeleteProgram = getProc(linker, "glDeleteProgram", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
		glGetUniformLocation = getProc(linker, "glGetUniformLocation",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		glUniform1f = getProc(linker, "glUniform1f",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
		glUniform2f = getProc(linker, "glUniform2f",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
		glGenBuffers = getProc(linker, "glGenBuffers",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		glBindBuffer = getProc(linker, "glBindBuffer",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		glBufferData = getProc(linker, "glBufferData", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT,
				ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
		glEnableVertexAttribArray = getProc(linker, "glEnableVertexAttribArray",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
		glVertexAttribPointer = getProc(linker, "glVertexAttribPointer",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
						ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		glDrawArrays = getProc(linker, "glDrawArrays",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
	}

	private MethodHandle getProc(Linker linker, String name, FunctionDescriptor desc) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment ptr = (MemorySegment) glXGetProcAddress.invokeExact(arena.allocateFrom(name));
			return linker.downcallHandle(ptr, desc);
		}
	}

	public void swapBuffers() throws Throwable {
		glXSwapBuffers.invokeExact(window.getDisplay(), window.getWindow());
	}
}