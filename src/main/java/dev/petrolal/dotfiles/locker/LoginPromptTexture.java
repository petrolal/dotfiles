// LoginPromptTexture.java --- Rasterizes a faithful classic Windows 98
// "Enter Password" dialog as a texture for the screen locker's 3D monitor
// object. The raymarch shader has no font/text rendering of its own, so the
// static chrome (titlebar, logo, labels, listbox, buttons) is baked here --
// the only part of this feature doing 2D rasterization instead of
// raymarching, and deliberately so: there's no SDF font substitute for
// crisp, readable dialog text. The live password field is left blank here;
// ScreenLocker overlays the actual typed-character dots/caret procedurally
// in the shader, aligned via the UV rect returned alongside the image.
//
// This rasterizes onto a plain int[] ARGB buffer with hand-rolled
// fillRect/hLine/vLine/text helpers -- NOT java.awt.Graphics2D/BufferedImage
// at all. Two independent reasons converged on that:
//  1. Text: this JDK build ships with no lib/fontconfig.properties, so the
//     very first Graphics2D.drawString call anywhere in the process throws
//     "Fontconfig head is null, check your fonts or fonts configuration"
//     from sun.awt.FontConfiguration, regardless of which Font is used.
//     Fixed by plotting text with a small built-in 5x7 bitmap font instead
//     (see FONT_5X7 below).
//  2. Everything else: under this project's GraalVM native-image build,
//     merely touching java.awt.image.BufferedImage (even with no text
//     involved) throws "NoClassDefFoundError: java.awt.GraphicsEnvironment"
//     at runtime -- BufferedImage's static init loads the bundled native
//     libawt.so, whose JNI_OnLoad does a JNI FindClass the image's
//     reflection metadata doesn't cover, and which the native-image tracing
//     agent cannot pick up (it happens inside native code, not through an
//     instrumentable Java reflection call). Confirmed by reproducing with a
//     BufferedImage-only (no drawString) native-image test. Avoiding
//     java.awt entirely sidesteps both problems at once.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker;

import java.util.Map;

public final class LoginPromptTexture {
    private LoginPromptTexture() {}

    public static final int WIDTH = 440;
    public static final int HEIGHT = 320;

    /** The password input field's rectangle in GL texture UV space (v=0 at
     * the bottom, matching the same row-flip {@link #toRgba} applies) --
     * ScreenLocker/wallpaper.frag use this to draw the live password dots
     * in exactly the right place on the dialog, regardless of layout tweaks
     * made here. */
    public record Layout(byte[] rgba, float fieldU0, float fieldV0, float fieldU1, float fieldV1) {}

    // Packed 0xRRGGBB colors (alpha is always opaque -- see toRgba).
    private static final int WIN_GRAY = 0xC0C0C0;
    private static final int TITLEBAR = 0x800000;
    private static final int TITLEBAR_TEXT = 0xFFFFFF;
    private static final int SHADOW = 0x808080;
    private static final int HIGHLIGHT = 0xFFFFFF;
    private static final int SELECTION = 0x800000;
    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    /** Built-in 5-wide x 7-tall bitmap font, uppercase + digits + ':' only
     * (plenty for this dialog's fixed chrome strings and an uppercased
     * username) -- see the class javadoc for why this exists instead of
     * java.awt.Font. Each glyph is 7 rows of a 5-char string, '#' = pixel
     * on, any other char = pixel off. */
    private static final Map<Character, String[]> FONT_5X7 = Map.ofEntries(
            Map.entry('A', new String[]{".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('B', new String[]{"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."}),
            Map.entry('C', new String[]{".####", "#....", "#....", "#....", "#....", "#....", ".####"}),
            Map.entry('D', new String[]{"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."}),
            Map.entry('E', new String[]{"#####", "#....", "#....", "####.", "#....", "#....", "#####"}),
            Map.entry('F', new String[]{"#####", "#....", "#....", "####.", "#....", "#....", "#...."}),
            Map.entry('G', new String[]{".####", "#....", "#....", "#.###", "#...#", "#...#", ".####"}),
            Map.entry('H', new String[]{"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('I', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}),
            Map.entry('J', new String[]{"..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."}),
            Map.entry('K', new String[]{"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"}),
            Map.entry('L', new String[]{"#....", "#....", "#....", "#....", "#....", "#....", "#####"}),
            Map.entry('M', new String[]{"#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"}),
            Map.entry('N', new String[]{"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}),
            Map.entry('O', new String[]{".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('P', new String[]{"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}),
            Map.entry('Q', new String[]{".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"}),
            Map.entry('R', new String[]{"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}),
            Map.entry('S', new String[]{".####", "#....", "#....", ".###.", "....#", "....#", "####."}),
            Map.entry('T', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('U', new String[]{"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('V', new String[]{"#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."}),
            Map.entry('W', new String[]{"#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"}),
            Map.entry('X', new String[]{"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"}),
            Map.entry('Y', new String[]{"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('Z', new String[]{"#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"}),
            Map.entry('0', new String[]{".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."}),
            Map.entry('1', new String[]{"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."}),
            Map.entry('2', new String[]{".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"}),
            Map.entry('3', new String[]{".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."}),
            Map.entry('4', new String[]{"#...#", "#...#", "#...#", "#####", "....#", "....#", "....#"}),
            Map.entry('5', new String[]{"#####", "#....", "#....", "####.", "....#", "#...#", ".###."}),
            Map.entry('6', new String[]{".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."}),
            Map.entry('7', new String[]{"#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('8', new String[]{".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."}),
            Map.entry('9', new String[]{".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."}),
            Map.entry(':', new String[]{".....", "..#..", ".....", ".....", "..#..", ".....", "....."})
    );

    private static final int GLYPH_W = 5;
    private static final int GLYPH_H = 7;

    /** Plots text using {@link #FONT_5X7} at the given pixel scale onto a
     * top-down WIDTHxHEIGHT ARGB CANVAS; unknown characters (anything not in
     * the table, e.g. lowercase) render as a blank cell rather than
     * throwing. */
    private static void drawText(int[] canvas, String text, int x, int y, int scale, int color) {
        int cellW = (GLYPH_W + 1) * scale;
        int cursorX = x;
        for (char c : text.toUpperCase().toCharArray()) {
            String[] glyph = FONT_5X7.get(c);
            if (glyph != null) {
                for (int row = 0; row < GLYPH_H; row++) {
                    String bits = glyph[row];
                    for (int col = 0; col < GLYPH_W; col++) {
                        if (bits.charAt(col) == '#') {
                            fillRect(canvas, cursorX + col * scale, y + row * scale, scale, scale, color);
                        }
                    }
                }
            }
            cursorX += cellW;
        }
    }

    private static int textWidth(String text, int scale) {
        return text.length() * (GLYPH_W + 1) * scale - scale;
    }

    public static Layout render(String username) {
        int[] canvas = new int[WIDTH * HEIGHT];

        fillRect(canvas, 0, 0, WIDTH, HEIGHT, WIN_GRAY);

        // Titlebar
        int titleH = 28;
        fillRect(canvas, 0, 0, WIDTH, titleH, TITLEBAR);
        drawText(canvas, "Enter Password", 10, 9, 2, TITLEBAR_TEXT);
        drawRaisedBox(canvas, WIDTH - 26, 4, 20, 20);
        drawText(canvas, "X", WIDTH - 19, 8, 2, BLACK);

        // Logo: a small 4-quadrant "flag" + Microsoft/Windows98 wordmark
        int flagX = 16, flagY = 42, flagSize = 32, half = flagSize / 2;
        fillRect(canvas, flagX, flagY, half - 1, half - 1, 0xF25522);
        fillRect(canvas, flagX + half, flagY, half - 1, half - 1, 0x7FBA00);
        fillRect(canvas, flagX, flagY + half, half - 1, half - 1, 0x00A4EF);
        fillRect(canvas, flagX + half, flagY + half, half - 1, half - 1, 0xFFB900);

        drawText(canvas, "INFERNAPROJECT", flagX + flagSize + 10, flagY + 2, 2, BLACK);
        drawText(canvas, "IMP98", flagX + flagSize + 8, flagY + 18, 3, BLACK);

        hLine(canvas, 14, WIDTH - 14, 92, SHADOW);
        hLine(canvas, 14, WIDTH - 14, 93, HIGHLIGHT);

        // "Select user name:" label + a single-entry listbox (the real PAM
        // target user, pre-selected -- there's only ever one account to
        // unlock as here, unlike the multi-user reference screenshot).
        drawText(canvas, "Select user name:", 16, 101, 2, BLACK);

        int listY = 122, listH = 46;
        drawSunkenBox(canvas, 16, listY, WIDTH - 32, listH);
        fillRect(canvas, 18, listY + 2, WIDTH - 36, 20, SELECTION);
        drawText(canvas, username, 24, listY + 6, 2, WHITE);

        // "Password:" label + empty sunken field (live dots drawn by the
        // shader -- see Layout.fieldU0..fieldV1).
        int labelY = listY + listH + 20;
        drawText(canvas, "Password:", 16, labelY - 9, 2, BLACK);

        int fieldY = labelY + 8, fieldH = 26;
        int fieldX = 16, fieldW = WIDTH - 32;
        drawSunkenBox(canvas, fieldX, fieldY, fieldW, fieldH);

        // OK / Cancel buttons
        int btnY = fieldY + fieldH + 24, btnH = 28;
        int cancelX = WIDTH - 16 - 74, cancelW = 74;
        int okX = cancelX - 10 - 74, okW = 74;
        drawRaisedBox(canvas, okX, btnY, okW, btnH);
        drawRaisedBox(canvas, cancelX, btnY, cancelW, btnH);
        drawCentered(canvas, "OK", okX, btnY, okW, btnH, BLACK);
        drawCentered(canvas, "Cancel", cancelX, btnY, cancelW, btnH, BLACK);

        // Password field rect, pixel -> GL texture UV (v=0 at the image's
        // bottom row, matching the vertical flip toRgba() applies below).
        float u0 = (float) fieldX / WIDTH;
        float u1 = (float) (fieldX + fieldW) / WIDTH;
        float v0 = 1f - (float) (fieldY + fieldH) / HEIGHT;
        float v1 = 1f - (float) fieldY / HEIGHT;

        return new Layout(toRgba(canvas), u0, v0, u1, v1);
    }

    private static void drawCentered(int[] canvas, String text, int x, int y, int w, int h, int color) {
        int scale = 2;
        int tw = textWidth(text, scale);
        int th = GLYPH_H * scale;
        int tx = x + (w - tw) / 2;
        int ty = y + (h - th) / 2;
        drawText(canvas, text, tx, ty, scale, color);
    }

    /** Classic Win98 "outset" bevel: light top/left, dark bottom/right. */
    private static void drawRaisedBox(int[] canvas, int x, int y, int w, int h) {
        fillRect(canvas, x, y, w, h, WIN_GRAY);
        hLine(canvas, x, x + w - 1, y, HIGHLIGHT);
        vLine(canvas, x, y, y + h - 1, HIGHLIGHT);
        hLine(canvas, x, x + w - 1, y + h - 1, SHADOW);
        vLine(canvas, x + w - 1, y, y + h - 1, SHADOW);
    }

    /** Classic Win98 "inset" bevel (text fields, listboxes): dark top/left,
     * light bottom/right, white fill. */
    private static void drawSunkenBox(int[] canvas, int x, int y, int w, int h) {
        fillRect(canvas, x, y, w, h, WHITE);
        hLine(canvas, x, x + w - 1, y, SHADOW);
        vLine(canvas, x, y, y + h - 1, SHADOW);
        hLine(canvas, x, x + w - 1, y + h - 1, HIGHLIGHT);
        vLine(canvas, x + w - 1, y, y + h - 1, HIGHLIGHT);
    }

    private static void fillRect(int[] canvas, int x, int y, int w, int h, int color) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(WIDTH, x + w), y1 = Math.min(HEIGHT, y + h);
        for (int py = y0; py < y1; py++) {
            int row = py * WIDTH;
            for (int px = x0; px < x1; px++) {
                canvas[row + px] = color;
            }
        }
    }

    private static void hLine(int[] canvas, int x0, int x1, int y, int color) {
        fillRect(canvas, Math.min(x0, x1), y, Math.abs(x1 - x0) + 1, 1, color);
    }

    private static void vLine(int[] canvas, int x, int y0, int y1, int color) {
        fillRect(canvas, x, Math.min(y0, y1), 1, Math.abs(y1 - y0) + 1, color);
    }

    /** Same tightly-packed, vertically-flipped RGBA convention as
     * {@link dev.petrolal.dotfiles.wallpaper.ImageLoader} -- row 0 is the
     * bottom of the image, matching GL's texture coordinate convention.
     * CANVAS is top-down 0xRRGGBB (opaque), as filled by render() above. */
    private static byte[] toRgba(int[] canvas) {
        byte[] out = new byte[WIDTH * HEIGHT * 4];
        for (int y = 0; y < HEIGHT; y++) {
            int dstRow = (HEIGHT - 1 - y) * WIDTH * 4;
            int srcRow = y * WIDTH;
            for (int x = 0; x < WIDTH; x++) {
                int p = canvas[srcRow + x];
                int o = dstRow + x * 4;
                out[o] = (byte) (p >> 16);
                out[o + 1] = (byte) (p >> 8);
                out[o + 2] = (byte) p;
                out[o + 3] = (byte) 0xFF;
            }
        }
        return out;
    }
}
