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
package dev.petrolal.dotfiles.locker

object LoginPromptTexture {
    const val WIDTH: Int = 440
    const val HEIGHT: Int = 320

    // Packed 0xRRGGBB colors (alpha is always opaque -- see toRgba).
    private const val WIN_GRAY = 0xC0C0C0
    private const val TITLEBAR = 0x800000
    private const val TITLEBAR_TEXT = 0xFFFFFF
    private const val SHADOW = 0x808080
    private const val HIGHLIGHT = 0xFFFFFF
    private const val SELECTION = 0x800000
    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF

    /** Built-in 5-wide x 7-tall bitmap font, uppercase + digits + ':' only
     * (plenty for this dialog's fixed chrome strings and an uppercased
     * username) -- see the class javadoc for why this exists instead of
     * java.awt.Font. Each glyph is 7 rows of a 5-char string, '#' = pixel
     * on, any other char = pixel off.  */
    private val FONT_5X7: Map<Char, Array<String>> = mapOf(
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to arrayOf(".####", "#....", "#....", "#....", "#....", "#....", ".####"),
        'D' to arrayOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
        'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to arrayOf(".####", "#....", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"),
        'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"),
        'N' to arrayOf("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"),
        'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
        'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
        '4' to arrayOf("#...#", "#...#", "#...#", "#####", "....#", "....#", "....#"),
        '5' to arrayOf("#####", "#....", "#....", "####.", "....#", "#...#", ".###."),
        '6' to arrayOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
        ':' to arrayOf(".....", "..#..", ".....", ".....", "..#..", ".....", ".....")
    )

    private const val GLYPH_W = 5
    private const val GLYPH_H = 7

    /** Plots text using [.FONT_5X7] at the given pixel scale onto a
     * top-down WIDTHxHEIGHT ARGB CANVAS; unknown characters (anything not in
     * the table, e.g. lowercase) render as a blank cell rather than
     * throwing.  */
    private fun drawText(canvas: IntArray, text: String, x: Int, y: Int, scale: Int, color: Int) {
        val cellW = (GLYPH_W + 1) * scale
        var cursorX = x
        for (c in text.uppercase()) {
            val glyph = FONT_5X7[c]
            if (glyph != null) {
                for (row in 0..<GLYPH_H) {
                    val bits = glyph[row]
                    for (col in 0..<GLYPH_W) {
                        if (bits[col] == '#') {
                            fillRect(canvas, cursorX + col * scale, y + row * scale, scale, scale, color)
                        }
                    }
                }
            }
            cursorX += cellW
        }
    }

    private fun textWidth(text: String, scale: Int): Int {
        return text.length * (GLYPH_W + 1) * scale - scale
    }

    fun render(username: String): Layout {
        val canvas = IntArray(WIDTH * HEIGHT)

        fillRect(canvas, 0, 0, WIDTH, HEIGHT, WIN_GRAY)

        // Titlebar
        val titleH = 28
        fillRect(canvas, 0, 0, WIDTH, titleH, TITLEBAR)
        drawText(canvas, "Enter Password", 10, 9, 2, TITLEBAR_TEXT)
        drawRaisedBox(canvas, WIDTH - 26, 4, 20, 20)
        drawText(canvas, "X", WIDTH - 19, 8, 2, BLACK)

        // Logo: a small 4-quadrant "flag" + Microsoft/Windows98 wordmark
        val flagX = 16
        val flagY = 42
        val flagSize = 32
        val half = flagSize / 2
        fillRect(canvas, flagX, flagY, half - 1, half - 1, 0xF25522)
        fillRect(canvas, flagX + half, flagY, half - 1, half - 1, 0x7FBA00)
        fillRect(canvas, flagX, flagY + half, half - 1, half - 1, 0x00A4EF)
        fillRect(canvas, flagX + half, flagY + half, half - 1, half - 1, 0xFFB900)

        drawText(canvas, "INFERNAPROJECT", flagX + flagSize + 10, flagY + 2, 2, BLACK)
        drawText(canvas, "IMP98", flagX + flagSize + 8, flagY + 18, 3, BLACK)

        hLine(canvas, 14, WIDTH - 14, 92, SHADOW)
        hLine(canvas, 14, WIDTH - 14, 93, HIGHLIGHT)

        // "Select user name:" label + a single-entry listbox (the real PAM
        // target user, pre-selected -- there's only ever one account to
        // unlock as here, unlike the multi-user reference screenshot).
        drawText(canvas, "Select user name:", 16, 101, 2, BLACK)

        val listY = 122
        val listH = 46
        drawSunkenBox(canvas, 16, listY, WIDTH - 32, listH)
        fillRect(canvas, 18, listY + 2, WIDTH - 36, 20, SELECTION)
        drawText(canvas, username, 24, listY + 6, 2, WHITE)

        // "Password:" label + empty sunken field (live dots drawn by the
        // shader -- see Layout.fieldU0..fieldV1).
        val labelY = listY + listH + 20
        drawText(canvas, "Password:", 16, labelY - 9, 2, BLACK)

        val fieldY = labelY + 8
        val fieldH = 26
        val fieldX = 16
        val fieldW = WIDTH - 32
        drawSunkenBox(canvas, fieldX, fieldY, fieldW, fieldH)

        // OK / Cancel buttons
        val btnY = fieldY + fieldH + 24
        val btnH = 28
        val cancelX = WIDTH - 16 - 74
        val cancelW = 74
        val okX = cancelX - 10 - 74
        val okW = 74
        drawRaisedBox(canvas, okX, btnY, okW, btnH)
        drawRaisedBox(canvas, cancelX, btnY, cancelW, btnH)
        drawCentered(canvas, "OK", okX, btnY, okW, btnH, BLACK)
        drawCentered(canvas, "Cancel", cancelX, btnY, cancelW, btnH, BLACK)

        // Password field rect, pixel -> GL texture UV (v=0 at the image's
        // bottom row, matching the vertical flip toRgba() applies below).
        val u0 = fieldX.toFloat() / WIDTH
        val u1 = (fieldX + fieldW).toFloat() / WIDTH
        val v0 = 1f - (fieldY + fieldH).toFloat() / HEIGHT
        val v1 = 1f - fieldY.toFloat() / HEIGHT

        return Layout(toRgba(canvas), u0, v0, u1, v1)
    }

    private fun drawCentered(canvas: IntArray, text: String, x: Int, y: Int, w: Int, h: Int, color: Int) {
        val scale = 2
        val tw = textWidth(text, scale)
        val th = GLYPH_H * scale
        val tx = x + (w - tw) / 2
        val ty = y + (h - th) / 2
        drawText(canvas, text, tx, ty, scale, color)
    }

    /** Classic Win98 "outset" bevel: light top/left, dark bottom/right.  */
    private fun drawRaisedBox(canvas: IntArray, x: Int, y: Int, w: Int, h: Int) {
        fillRect(canvas, x, y, w, h, WIN_GRAY)
        hLine(canvas, x, x + w - 1, y, HIGHLIGHT)
        vLine(canvas, x, y, y + h - 1, HIGHLIGHT)
        hLine(canvas, x, x + w - 1, y + h - 1, SHADOW)
        vLine(canvas, x + w - 1, y, y + h - 1, SHADOW)
    }

    /** Classic Win98 "inset" bevel (text fields, listboxes): dark top/left,
     * light bottom/right, white fill.  */
    private fun drawSunkenBox(canvas: IntArray, x: Int, y: Int, w: Int, h: Int) {
        fillRect(canvas, x, y, w, h, WHITE)
        hLine(canvas, x, x + w - 1, y, SHADOW)
        vLine(canvas, x, y, y + h - 1, SHADOW)
        hLine(canvas, x, x + w - 1, y + h - 1, HIGHLIGHT)
        vLine(canvas, x + w - 1, y, y + h - 1, HIGHLIGHT)
    }

    private fun fillRect(canvas: IntArray, x: Int, y: Int, w: Int, h: Int, color: Int) {
        val x0 = maxOf(0, x)
        val y0 = maxOf(0, y)
        val x1 = minOf(WIDTH, x + w)
        val y1 = minOf(HEIGHT, y + h)
        for (py in y0..<y1) {
            val row = py * WIDTH
            for (px in x0..<x1) {
                canvas[row + px] = color
            }
        }
    }

    private fun hLine(canvas: IntArray, x0: Int, x1: Int, y: Int, color: Int) {
        fillRect(canvas, minOf(x0, x1), y, kotlin.math.abs(x1 - x0) + 1, 1, color)
    }

    private fun vLine(canvas: IntArray, x: Int, y0: Int, y1: Int, color: Int) {
        fillRect(canvas, x, minOf(y0, y1), 1, kotlin.math.abs(y1 - y0) + 1, color)
    }

    /** Same tightly-packed, vertically-flipped RGBA convention as
     * [ImageLoader] -- row 0 is the
     * bottom of the image, matching GL's texture coordinate convention.
     * CANVAS is top-down 0xRRGGBB (opaque), as filled by render() above.  */
    private fun toRgba(canvas: IntArray): ByteArray {
        val out = ByteArray(WIDTH * HEIGHT * 4)
        for (y in 0..<HEIGHT) {
            val dstRow = (HEIGHT - 1 - y) * WIDTH * 4
            val srcRow = y * WIDTH
            for (x in 0..<WIDTH) {
                val p = canvas[srcRow + x]
                val o = dstRow + x * 4
                out[o] = (p shr 16).toByte()
                out[o + 1] = (p shr 8).toByte()
                out[o + 2] = p.toByte()
                out[o + 3] = 0xFF.toByte()
            }
        }
        return out
    }

    /** The password input field's rectangle in GL texture UV space (v=0 at
     * the bottom, matching the same row-flip [.toRgba] applies) --
     * ScreenLocker/wallpaper.frag use this to draw the live password dots
     * in exactly the right place on the dialog, regardless of layout tweaks
     * made here.  */
    @JvmRecord
    data class Layout(
        val rgba: ByteArray,
        val fieldU0: Float,
        val fieldV0: Float,
        val fieldU1: Float,
        val fieldV1: Float
    )
}
