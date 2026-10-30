// KeybindHelper.kt --- Tofi-based keybinding cheat sheet for Hyprland
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object KeybindHelper {
    data class Bind(val keys: String, val action: String) {
        fun toMenuLabel(): String {
            val label = "$keys  →  $action"
            return if (label.length > 70) label.take(67) + "..." else label
        }
    }

    // Kept in sync with config/hypr/hyprland.lua's hl.bind(...) calls and
    // docs/keybindings.md -- this is a cheat sheet, not the source of truth,
    // so it does not execute anything: selecting an entry just closes it.
    private val binds = listOf(
        Bind("SUPER + Return", "Open the terminal (kitty)"),
        Bind("SUPER + B", "Open the browser"),
        Bind("SUPER + E", "Open the file manager (thunar)"),
        Bind("SUPER + Space / SUPER + D", "Open application launcher (tofi)"),

        Bind("SUPER + 0-9", "Focus workspace 1-10 (0 = workspace 10)"),
        Bind("SUPER + SHIFT + 0-9", "Move focused window to workspace 1-10"),
        Bind("SUPER + mouse wheel down/up", "Switch to next/previous workspace"),
        Bind("SUPER + S", "Toggle special workspace \"scratchpad\""),
        Bind("SUPER + SHIFT + S", "Float window + move it to the scratchpad"),

        Bind("SUPER + Q", "Close active window"),
        Bind("SUPER + F", "Toggle fullscreen"),
        Bind("SUPER + M", "Toggle maximized"),
        Bind("SUPER + T", "Toggle floating"),
        Bind("SUPER + J", "Toggle split"),
        Bind("SUPER + K", "Swap split"),
        Bind("SUPER + G", "Toggle window group"),
        Bind("SUPER + arrows", "Move focus left/right/up/down"),
        Bind("SUPER + ALT + arrows", "Swap tiled window left/right/up/down"),
        Bind("SUPER + SHIFT + arrows", "Resize window (repeats while held)"),
        Bind("SUPER + left-click drag", "Move window with the mouse"),
        Bind("SUPER + right-click drag", "Resize window with the mouse"),
        Bind("ALT + Tab / ALT + SHIFT + Tab", "Switch applications (cyclenext)"),

        Bind("SUPER + Print", "Area screenshot to ~/Pictures/"),
        Bind("SUPER + ALT + F", "Instant full-screen screenshot"),
        Bind("SUPER + ALT + S", "Instant area screenshot"),

        Bind("SUPER + SHIFT + R", "Reload Hyprland config"),
        Bind("SUPER + SHIFT + B", "Restart Quickshell HUD"),
        Bind("SUPER + N", "Open Tofi notification center"),
        Bind("SUPER + L / SUPER + CTRL + L", "Lock screen"),
        Bind("SUPER + CTRL + R", "Open screen recorder"),
        Bind("SUPER + V / SUPER + SHIFT + V", "Toggle VHS / CRT screen shader"),

        Bind("XF86AudioRaiseVolume / LowerVolume", "Adjust volume"),
        Bind("XF86AudioMute / MicMute", "Mute audio / microphone"),
        Bind("XF86AudioNext/Pause/Play/Prev", "Media playback (playerctl)"),
    )

    private fun runTofi(input: String): String? {
        val pb = ProcessBuilder(
            "tofi",
            "--prompt-text=> ",
            "--placeholder-text=keybinds:"
        )
        return try {
            val process = pb.start()
            OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(input)
                writer.flush()
            }
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotEmpty()) output else null
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun show(): Int {
        val menu = binds.joinToString("\n") { it.toMenuLabel() }
        runTofi(menu)
        return 0
    }
}
