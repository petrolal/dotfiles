// LoginPromptTexture.scala --- Rasterizes a faithful classic Windows 98
// "Enter Password" dialog as a texture for the screen locker's 3D monitor object.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

object LoginPromptTexture:
  val WIDTH: Int = 440
  val HEIGHT: Int = 320

  case class Layout(rgba: Array[Byte], fieldU0: Float, fieldV0: Float, fieldU1: Float, fieldV1: Float)

  private val WIN_GRAY = 0xC0C0C0
  private val TITLEBAR = 0x800000
  private val TITLEBAR_TEXT = 0xFFFFFF
  private val SHADOW = 0x808080
  private val HIGHLIGHT = 0xFFFFFF
  private val SELECTION = 0x800000
  private val BLACK = 0x000000
  private val WHITE = 0xFFFFFF

  private val FONT_5X7: Map[Char, Array[String]] = Map(
    'A' -> Array(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'B' -> Array("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
    'C' -> Array(".####", "#....", "#....", "#....", "#....", "#....", ".####"),
    'D' -> Array("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
    'E' -> Array("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
    'F' -> Array("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
    'G' -> Array(".####", "#....", "#....", "#.###", "#...#", "#...#", ".####"),
    'H' -> Array("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'I' -> Array("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"),
    'J' -> Array("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
    'K' -> Array("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
    'L' -> Array("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
    'M' -> Array("#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"),
    'N' -> Array("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"),
    'O' -> Array(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'P' -> Array("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
    'Q' -> Array(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
    'R' -> Array("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
    'S' -> Array(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
    'T' -> Array("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
    'U' -> Array("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'V' -> Array("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
    'W' -> Array("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
    'X' -> Array("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
    'Y' -> Array("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
    'Z' -> Array("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
    '0' -> Array(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
    '1' -> Array("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    '2' -> Array(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    '3' -> Array(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
    '4' -> Array("#...#", "#...#", "#...#", "#####", "....#", "....#", "....#"),
    '5' -> Array("#####", "#....", "#....", "####.", "....#", "#...#", ".###."),
    '6' -> Array(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
    '7' -> Array("#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."),
    '8' -> Array(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    '9' -> Array(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
    ':' -> Array(".....", "..#..", ".....", ".....", "..#..", ".....", ".....")
  )

  private val GLYPH_W = 5
  private val GLYPH_H = 7

  private def drawText(canvas: Array[Int], text: String, x: Int, y: Int, scale: Int, color: Int): Unit =
    val cellW = (GLYPH_W + 1) * scale
    var cursorX = x
    for c <- text.toUpperCase do
      FONT_5X7.get(c) match
        case Some(glyph) =>
          for row <- 0 until GLYPH_H do
            val bits = glyph(row)
            for col <- 0 until GLYPH_W do
              if bits.charAt(col) == '#' then
                fillRect(canvas, cursorX + col * scale, y + row * scale, scale, scale, color)
        case None => ()
      cursorX += cellW

  private def textWidth(text: String, scale: Int): Int =
    text.length * (GLYPH_W + 1) * scale - scale

  def render(username: String): Layout =
    val canvas = new Array[Int](WIDTH * HEIGHT)

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

    drawText(canvas, "Select user name:", 16, 101, 2, BLACK)

    val listY = 122
    val listH = 46
    drawSunkenBox(canvas, 16, listY, WIDTH - 32, listH)
    fillRect(canvas, 18, listY + 2, WIDTH - 36, 20, SELECTION)
    drawText(canvas, username, 24, listY + 6, 2, WHITE)

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

    val u0 = fieldX.toFloat / WIDTH
    val u1 = (fieldX + fieldW).toFloat / WIDTH
    val v0 = 1.0f - (fieldY + fieldH).toFloat / HEIGHT
    val v1 = 1.0f - fieldY.toFloat / HEIGHT

    Layout(toRgba(canvas), u0, v0, u1, v1)

  private def drawCentered(canvas: Array[Int], text: String, x: Int, y: Int, w: Int, h: Int, color: Int): Unit =
    val scale = 2
    val tw = textWidth(text, scale)
    val th = GLYPH_H * scale
    val tx = x + (w - tw) / 2
    val ty = y + (h - th) / 2
    drawText(canvas, text, tx, ty, scale, color)

  private def drawRaisedBox(canvas: Array[Int], x: Int, y: Int, w: Int, h: Int): Unit =
    fillRect(canvas, x, y, w, h, WIN_GRAY)
    hLine(canvas, x, x + w - 1, y, HIGHLIGHT)
    vLine(canvas, x, y, y + h - 1, HIGHLIGHT)
    hLine(canvas, x, x + w - 1, y + h - 1, SHADOW)
    vLine(canvas, x + w - 1, y, y + h - 1, SHADOW)

  private def drawSunkenBox(canvas: Array[Int], x: Int, y: Int, w: Int, h: Int): Unit =
    fillRect(canvas, x, y, w, h, WHITE)
    hLine(canvas, x, x + w - 1, y, SHADOW)
    vLine(canvas, x, y, y + h - 1, SHADOW)
    hLine(canvas, x, x + w - 1, y + h - 1, HIGHLIGHT)
    vLine(canvas, x + w - 1, y, y + h - 1, HIGHLIGHT)

  private def fillRect(canvas: Array[Int], x: Int, y: Int, w: Int, h: Int, color: Int): Unit =
    val x0 = math.max(0, x)
    val y0 = math.max(0, y)
    val x1 = math.min(WIDTH, x + w)
    val y1 = math.min(HEIGHT, y + h)
    var py = y0
    while py < y1 do
      val row = py * WIDTH
      var px = x0
      while px < x1 do
        canvas(row + px) = color
        px += 1
      py += 1

  private def hLine(canvas: Array[Int], x0: Int, x1: Int, y: Int, color: Int): Unit =
    fillRect(canvas, math.min(x0, x1), y, math.abs(x1 - x0) + 1, 1, color)

  private def vLine(canvas: Array[Int], x: Int, y0: Int, y1: Int, color: Int): Unit =
    fillRect(canvas, x, math.min(y0, y1), 1, math.abs(y1 - y0) + 1, color)

  private def toRgba(canvas: Array[Int]): Array[Byte] =
    val out = new Array[Byte](WIDTH * HEIGHT * 4)
    var y = 0
    while y < HEIGHT do
      val dstRow = (HEIGHT - 1 - y) * WIDTH * 4
      val srcRow = y * WIDTH
      var x = 0
      while x < WIDTH do
        val p = canvas(srcRow + x)
        val o = dstRow + x * 4
        out(o) = (p >> 16).toByte
        out(o + 1) = (p >> 8).toByte
        out(o + 2) = p.toByte
        out(o + 3) = 0xFF.toByte
        x += 1
      y += 1
    out
