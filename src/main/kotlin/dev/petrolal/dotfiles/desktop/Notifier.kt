// Notifier.kt --- Shared desktop notification helpers
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.system.ProcessRunner
import java.io.File

/** Single place both one-shot toasts (VhsShader) and in-place-updating OSDs
 * (VolumeControl) send notifications through, instead of each feature
 * re-deriving its own gdbus/notify-send call. */
object Notifier {
    /** One-shot, fire-and-forget desktop notification. */
    @JvmStatic
    fun toast(summary: String, body: String, icon: String? = null) {
        val args = mutableListOf("notify-send", summary, body)
        if (icon != null) args += listOf("-i", icon)
        ProcessRunner.spawnDetached(args)
    }

    /**
     * In-place-updating OSD notification (e.g. the volume bubble): `id` both
     * names the notification bus app and tags a replaces-id file under
     * $XDG_RUNTIME_DIR, so repeated calls update the same bubble instead of
     * stacking new ones. `hint` is a raw GVariant dict literal merged into the
     * Notify call (e.g. a progress value).
     */
    @JvmStatic
    fun updateOsd(id: String, icon: String, summary: String, body: String, hint: String, timeoutMs: Int = 1000) {
        val runtimeDir = System.getenv("XDG_RUNTIME_DIR") ?: "/tmp"
        val idFile = File(runtimeDir, "${id.lowercase()}_notification_id")
        val replacesId = if (idFile.exists()) idFile.readText().trim().ifBlank { "0" } else "0"

        val cmd = listOf(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.Notifications",
            "--object-path", "/org/freedesktop/Notifications",
            "--method", "org.freedesktop.Notifications.Notify",
            id, replacesId, icon, summary, body, "[]",
            hint, timeoutMs.toString()
        )

        val res = ProcessRunner.captureOutput(cmd).getOrNull() ?: ""
        val match = "\\(uint32\\s+(\\d+),?\\)".toRegex().find(res)
            ?: "\\b(\\d+)\\b".toRegex().find(res)
        val newId = match?.groupValues?.get(1)
        if (!newId.isNullOrEmpty() && newId != "0") {
            try {
                idFile.writeText(newId)
            } catch (_: Exception) {}
        }
    }
}
