// LoginPromptTexture.java --- Rasterizes a faithful classic Windows 98
// "Enter Password" dialog as a texture for the screen locker's 3D monitor
// object. The raymarch shader has no font/text rendering of its own, so the
// static chrome (titlebar, logo, labels, listbox, buttons) is baked here
// with java.awt.Graphics2D -- the only part of this feature doing 2D
// rasterization instead of raymarching, and deliberately so: there's no SDF
// font substitute for crisp, readable dialog text. The live password field
// is left blank here; ScreenLocker overlays the actual typed-character
// dots/caret procedurally in the shader, aligned via the UV rect returned
// alongside the image.
//
// Text is NOT drawn via Graphics2D.drawString/java.awt.Font: this JDK build
// ships with no lib/fontconfig.properties at all, so the very first
// drawString call anywhere in the process throws
// "Fontconfig head is null, check your fonts or fonts configuration" from
// sun.awt.FontConfiguration, regardless of which Font is used -- confirmed
// via a minimal repro, not fixable by picking a different/physical font.
// Text is instead plotted with a small built-in 5x7 bitmap font (see
// FONT_5X7 below), which only touches Graphics2D.fillRect and never enters
// the native font subsystem.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker;

import java.awt.*;
import java.awt.image.BufferedImage;
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

    private static final Color WIN_GRAY = new Color(192, 192, 192);
    private static final Color TITLEBAR = new Color(128, 0, 0);
    private static final Color TITLEBAR_TEXT = Color.WHITE;
    private static final Color SHADOW = new Color(128, 128, 128);
    private static final Color HIGHLIGHT = Color.WHITE;
    private static final Color SELECTION = new Color(128, 0, 0);

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

    /** Plots text using {@link #FONT_5X7} at the given pixel scale; unknown
     * characters (anything not in the table, e.g. lowercase) render as a
     * blank cell rather than throwing. Returns the text's pixel width. */
    private static int drawText(Graphics2D g, String text, int x, int y, int scale, Color color) {
        g.setColor(color);
        int cellW = (GLYPH_W + 1) * scale;
        int cursorX = x;
        for (char c : text.toUpperCase().toCharArray()) {
            String[] glyph = FONT_5X7.get(c);
            if (glyph != null) {
                for (int row = 0; row < GLYPH_H; row++) {
                    String bits = glyph[row];
                    for (int col = 0; col < GLYPH_W; col++) {
                        if (bits.charAt(col) == '#') {
                            g.fillRect(cursorX + col * scale, y + row * scale, scale, scale);
                        }
                    }
                }
            }
            cursorX += cellW;
        }
        return cursorX - x - scale;
    }

    private static int textWidth(String text, int scale) {
        return text.length() * (GLYPH_W + 1) * scale - scale;
    }

    public static Layout render(String username) {
        System.setProperty("java.awt.headless", "true");
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(WIN_GRAY);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Titlebar
        int titleH = 28;
        g.setColor(TITLEBAR);
        g.fillRect(0, 0, WIDTH, titleH);
        drawText(g, "Enter Password", 10, 9, 2, TITLEBAR_TEXT);
        drawRaisedBox(g, WIDTH - 26, 4, 20, 20);
        drawText(g, "X", WIDTH - 19, 8, 2, Color.BLACK);

        // Logo: a small 4-quadrant "flag" + Microsoft/Windows98 wordmark
        int flagX = 16, flagY = 42, flagSize = 32, half = flagSize / 2;
        g.setColor(new Color(0xF2, 0x55, 0x22));
        g.fillRect(flagX, flagY, half - 1, half - 1);
        g.setColor(new Color(0x7F, 0xBA, 0x00));
        g.fillRect(flagX + half, flagY, half - 1, half - 1);
        g.setColor(new Color(0x00, 0xA4, 0xEF));
        g.fillRect(flagX, flagY + half, half - 1, half - 1);
        g.setColor(new Color(0xFF, 0xB9, 0x00));
        g.fillRect(flagX + half, flagY + half, half - 1, half - 1);

        drawText(g, "Microsoft", flagX + flagSize + 10, flagY + 2, 2, Color.BLACK);
        drawText(g, "Windows98", flagX + flagSize + 8, flagY + 18, 3, Color.BLACK);

        g.setColor(SHADOW);
        g.drawLine(14, 92, WIDTH - 14, 92);
        g.setColor(HIGHLIGHT);
        g.drawLine(14, 93, WIDTH - 14, 93);

        // "Select user name:" label + a single-entry listbox (the real PAM
        // target user, pre-selected -- there's only ever one account to
        // unlock as here, unlike the multi-user reference screenshot).
        drawText(g, "Select user name:", 16, 101, 2, Color.BLACK);

        int listY = 122, listH = 46;
        drawSunkenBox(g, 16, listY, WIDTH - 32, listH);
        g.setColor(SELECTION);
        g.fillRect(18, listY + 2, WIDTH - 36, 20);
        drawText(g, username, 24, listY + 6, 2, Color.WHITE);

        // "Password:" label + empty sunken field (live dots drawn by the
        // shader -- see Layout.fieldU0..fieldV1).
        int labelY = listY + listH + 20;
        drawText(g, "Password:", 16, labelY - 9, 2, Color.BLACK);

        int fieldY = labelY + 8, fieldH = 26;
        int fieldX = 16, fieldW = WIDTH - 32;
        drawSunkenBox(g, fieldX, fieldY, fieldW, fieldH);

        // OK / Cancel buttons
        int btnY = fieldY + fieldH + 24, btnH = 28;
        int cancelX = WIDTH - 16 - 74, cancelW = 74;
        int okX = cancelX - 10 - 74, okW = 74;
        drawRaisedBox(g, okX, btnY, okW, btnH);
        drawRaisedBox(g, cancelX, btnY, cancelW, btnH);
        drawCentered(g, "OK", okX, btnY, okW, btnH, Color.BLACK);
        drawCentered(g, "Cancel", cancelX, btnY, cancelW, btnH, Color.BLACK);

        g.dispose();

        // Password field rect, pixel -> GL texture UV (v=0 at the image's
        // bottom row, matching the vertical flip toRgba() applies below).
        float u0 = (float) fieldX / WIDTH;
        float u1 = (float) (fieldX + fieldW) / WIDTH;
        float v0 = 1f - (float) (fieldY + fieldH) / HEIGHT;
        float v1 = 1f - (float) fieldY / HEIGHT;

        return new Layout(toRgba(img), u0, v0, u1, v1);
    }

    private static void drawCentered(Graphics2D g, String text, int x, int y, int w, int h, Color color) {
        int scale = 2;
        int tw = textWidth(text, scale);
        int th = GLYPH_H * scale;
        int tx = x + (w - tw) / 2;
        int ty = y + (h - th) / 2;
        drawText(g, text, tx, ty, scale, color);
    }

    /** Classic Win98 "outset" bevel: light top/left, dark bottom/right. */
    private static void drawRaisedBox(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(WIN_GRAY);
        g.fillRect(x, y, w, h);
        g.setColor(HIGHLIGHT);
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(SHADOW);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
    }

    /** Classic Win98 "inset" bevel (text fields, listboxes): dark top/left,
     * light bottom/right, white fill. */
    private static void drawSunkenBox(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, w, h);
        g.setColor(SHADOW);
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(HIGHLIGHT);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
    }

    /** Same tightly-packed, vertically-flipped RGBA convention as
     * {@link dev.petrolal.dotfiles.wallpaper.ImageLoader} -- row 0 is the
     * bottom of the image, matching GL's texture coordinate convention. */
    private static byte[] toRgba(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        byte[] out = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            int dstRow = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
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
