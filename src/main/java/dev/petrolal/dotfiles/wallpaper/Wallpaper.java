package dev.petrolal.dotfiles.wallpaper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.petrolal.dotfiles.core.DotfilePaths;

/**
 * Main wallpaper controller:
 * - Detects multi-monitor layout (Left and Right screens)
 * - Renders wallpaper_left.frag on the left monitor
 * - Renders wallpaper_right.frag on the right monitor
 * - Polls user events & hardware telemetry (WallpaperEvents)
 * - Hot-reloads and dispatches uniforms to both 3D shader pipelines
 */
public final class Wallpaper {

    public record Monitor(int x, int y, int width, int height) {}

    private Path resolveShader(String name, String fallbackName) {
        Path p = resolveShader(name);
        if (Files.exists(p)) return p;
        if (fallbackName != null) {
            Path fb = resolveShader(fallbackName);
            if (Files.exists(fb)) return fb;
        }
        return p;
    }

    private Path resolveShader(String name) {
        Path direct = Path.of(name);
        if (Files.exists(direct)) return direct;
        Path inStaticDir = Path.of("static", name);
        if (Files.exists(inStaticDir)) return inStaticDir;
        Path root = DotfilePaths.findDotfilesRoot();
        Path inStatic = root.resolve("src/main/resources/static").resolve(name);
        if (Files.exists(inStatic)) return inStatic;
        Path inRoot = root.resolve(name);
        if (Files.exists(inRoot)) return inRoot;
        Path homeDotfiles = DotfilePaths.userHomeDirectory().resolve("dotfiles/src/main/resources/static").resolve(name);
        if (Files.exists(homeDotfiles)) return homeDotfiles;
        return direct;
    }

    private void configureXfdesktopTransparency() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "sh", "-c",
                "xfconf-query -c xfce4-desktop -l 2>/dev/null | grep -E 'image-style|color-style' | while read p; do " +
                "  if echo \"$p\" | grep -q 'image-style'; then xfconf-query -c xfce4-desktop -p \"$p\" -s 0 2>/dev/null; fi; " +
                "  if echo \"$p\" | grep -q 'color-style'; then xfconf-query -c xfce4-desktop -p \"$p\" -s 3 2>/dev/null; fi; " +
                "done; xfdesktop --reload 2>/dev/null || true"
            );
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    /**
     * Detects connected monitors sorted horizontally from left to right.
     */
    public List<Monitor> detectMonitors(int totalWidth, int totalHeight) {
        List<Monitor> list = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("xrandr", "--listmonitors").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern pat = Pattern.compile("(\\d+)/\\d+x(\\d+)/\\d+\\+(\\d+)\\+(\\d+)");
                while ((line = reader.readLine()) != null) {
                    Matcher m = pat.matcher(line);
                    if (m.find()) {
                        int w = Integer.parseInt(m.group(1));
                        int h = Integer.parseInt(m.group(2));
                        int x = Integer.parseInt(m.group(3));
                        int y = Integer.parseInt(m.group(4));
                        list.add(new Monitor(x, y, w, h));
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}

        if (list.size() >= 2) {
            list.sort(Comparator.comparingInt(Monitor::x));
            return list;
        }

        // Fallback for dual monitor setups if xrandr parsing was unavailable but totalWidth is wide
        if (totalWidth >= 2500) {
            int half = totalWidth / 2;
            return List.of(new Monitor(0, 0, half, totalHeight), new Monitor(half, 0, half, totalHeight));
        }

        return List.of(new Monitor(0, 0, totalWidth, totalHeight));
    }

    private WallpaperEvents.WallpaperState adjustState(WallpaperEvents.WallpaperState raw, Monitor mon) {
        float localX = -1000.0f;
        float localY = -1000.0f;
        if (raw.mouseX() >= mon.x() && raw.mouseX() < mon.x() + mon.width()
                && raw.mouseY() >= mon.y() && raw.mouseY() < mon.y() + mon.height()) {
            localX = raw.mouseX() - mon.x();
            localY = raw.mouseY() - mon.y();
        }

        return new WallpaperEvents.WallpaperState(
            mon.width(),
            mon.height(),
            raw.time(),
            localX,
            localY,
            raw.mouseLeftDown(),
            raw.mouseRightDown(),
            raw.workspace(),
            raw.audioActivity(),
            raw.cpu(),
            raw.ram()
        );
    }

    public void start() throws Throwable {
        Path vertPath = resolveShader("wallpaper.vert");
        Path leftFrag = resolveShader("wallpaper_left.frag", "wallpaper.frag");
        Path rightFrag = resolveShader("wallpaper_right.frag", "wallpaper_left.frag");

        // Ensure XFCE desktop background is transparent so desktop icons remain on top
        configureXfdesktopTransparency();

        try (X11Canvas canvas = new X11Canvas()) {
            List<Monitor> monitors = detectMonitors(canvas.getWidth(), canvas.getHeight());

            ShaderPipeline pipelineLeft = new ShaderPipeline(canvas, vertPath, leftFrag);
            ShaderPipeline pipelineRight = (monitors.size() >= 2)
                    ? new ShaderPipeline(canvas, vertPath, rightFrag)
                    : pipelineLeft;

            WallpaperEvents events = new WallpaperEvents(canvas);

            pipelineLeft.bind();

            long startTime = System.currentTimeMillis();

            while (true) {
                // Live hot-reloading: instantly recompiles either shader if modified on disk
                pipelineLeft.reloadIfModified();
                if (pipelineRight != pipelineLeft) {
                    pipelineRight.reloadIfModified();
                }

                float elapsed = (System.currentTimeMillis() - startTime) / 1000.0f;
                WallpaperEvents.WallpaperState rawState = events.pollState(canvas.getWidth(), canvas.getHeight(), elapsed);

                if (monitors.size() >= 2) {
                    Monitor leftMon = monitors.get(0);
                    Monitor rightMon = monitors.get(1);

                    // 1. Render Left Monitor
                    canvas.setViewport(leftMon.x(), 0, leftMon.width(), leftMon.height());
                    pipelineLeft.bind();
                    pipelineLeft.updateUniforms(adjustState(rawState, leftMon));
                    canvas.drawQuad();

                    // 2. Render Right Monitor
                    canvas.setViewport(rightMon.x(), 0, rightMon.width(), rightMon.height());
                    pipelineRight.bind();
                    pipelineRight.updateUniforms(adjustState(rawState, rightMon));
                    canvas.drawQuad();
                } else {
                    // Single monitor fullscreen
                    Monitor mon = monitors.get(0);
                    canvas.setViewport(0, 0, mon.width(), mon.height());
                    pipelineLeft.bind();
                    pipelineLeft.updateUniforms(rawState);
                    canvas.drawQuad();
                }

                canvas.swapBuffers();

                Thread.sleep(16); // ~60 FPS
            }
        }
    }
}
