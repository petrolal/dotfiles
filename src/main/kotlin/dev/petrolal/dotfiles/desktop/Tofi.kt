// Tofi.kt --- Shared tofi dmenu-mode prompt, used by NotificationCenter and KeybindHelper
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object Tofi {
    /** Feeds `input` to tofi's stdin and returns the selected line, or null on
     * cancel/failure. */
    fun prompt(input: String, prompt: String = "> ", placeholder: String = ""): String? {
        val pb = ProcessBuilder(
            "tofi",
            "--prompt-text=$prompt",
            "--placeholder-text=$placeholder"
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
}
