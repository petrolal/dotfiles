package dev.petrolal.dotfiles.wallpaper;

import java.nio.file.Path;

import dev.petrolal.dotfiles.wallpaper.graphics.Mesh;
import dev.petrolal.dotfiles.wallpaper.graphics.ShaderPipeline;
import dev.petrolal.dotfiles.wallpaper.nativebidings.GLContext;
import dev.petrolal.dotfiles.wallpaper.nativebidings.X11DesktopWindow;
import dev.petrolal.dotfiles.wallpaper.telemetry.SystemMetrics;

public final class Wallpaper {

	private Path resolveShader(String name) {
		Path direct = Path.of(name);
		if (java.nio.file.Files.exists(direct)) return direct;
		Path inStaticDir = Path.of("static", name);
		if (java.nio.file.Files.exists(inStaticDir)) return inStaticDir;
		Path root = dev.petrolal.dotfiles.core.DotfilePaths.findDotfilesRoot();
		Path inStatic = root.resolve("src/main/resources/static").resolve(name);
		if (java.nio.file.Files.exists(inStatic)) return inStatic;
		Path inRoot = root.resolve(name);
		if (java.nio.file.Files.exists(inRoot)) return inRoot;
		Path homeDotfiles = dev.petrolal.dotfiles.core.DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources/static").resolve(name);
		if (java.nio.file.Files.exists(homeDotfiles)) return homeDotfiles;
		return direct;
	}

	public void start() throws Throwable {
		Path vertPath = resolveShader("wallpaper.vert");
		Path fragPath = resolveShader("wallpaper.frag");

		try (X11DesktopWindow window = new X11DesktopWindow()) {
			GLContext gl = new GLContext(window);
			Mesh quad = Mesh.createFullscreenQuad(gl);
			ShaderPipeline pipeline = new ShaderPipeline(gl, vertPath, fragPath);
			SystemMetrics telemetry = new SystemMetrics();

			pipeline.bind();

			long startTime = System.currentTimeMillis();

			while (true) {
				pipeline.reloadIfModified();

				float elapsed = (System.currentTimeMillis() - startTime) / 1000.0f;
				float cpu = telemetry.getCpuUsage();
				float ram = telemetry.getRamUsage();

				pipeline.updateUniforms(window.getWidth(), window.getHeight(), elapsed, cpu, ram);
				quad.render();
				gl.swapBuffers();

				Thread.sleep(16); // ~60 FPS
			}
		}
	}
}
