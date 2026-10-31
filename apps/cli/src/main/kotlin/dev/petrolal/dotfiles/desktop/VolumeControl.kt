// VolumeControl.kt --- Volume control with in-place updating OSD notifications
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.domain.DotfileError.CommandFailed
import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner
import kotlin.math.roundToInt

object VolumeControl {

    @JvmStatic
    fun run(action: String): Result<String> {
        val wpCmd = when (action.lowercase()) {
            "up" -> listOf("wpctl", "set-volume", "-l", "1.0", "@DEFAULT_AUDIO_SINK@", "5%+")
            "down" -> listOf("wpctl", "set-volume", "-l", "1.0", "@DEFAULT_AUDIO_SINK@", "5%-")
            "mute" -> listOf("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "toggle")
            "mic-mute" -> listOf("wpctl", "set-mute", "@DEFAULT_AUDIO_SOURCE@", "toggle")
            else -> return Result.err(CommandFailed("Unknown volume action: $action", 1))
        }

        when (val wpResult = ProcessRunner.run(wpCmd)) {
            is Result.Err -> return Result.err(wpResult.error)
            is Result.Ok -> {}
        }

        val volInfo = ProcessRunner.captureOutput(listOf("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@"))
            .getOrNull() ?: ""

        val isMuted = volInfo.contains("MUTED")
        val volParts = volInfo.trim().split("\\s+".toRegex())
        val volRaw = volParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        var volPct = (volRaw * 100.0).roundToInt()
        if (volPct > 100) volPct = 100

        val icon: String
        val summary: String
        val body: String

        if (isMuted) {
            icon = "audio-volume-muted"
            summary = "Audio Muted"
            body = "Muted"
            volPct = 0
        } else {
            icon = when {
                volPct >= 70 -> "audio-volume-high"
                volPct >= 30 -> "audio-volume-medium"
                volPct > 0 -> "audio-volume-low"
                else -> "audio-volume-muted"
            }
            summary = "Volume"
            body = "$volPct%"
        }

        val hint = "{\"value\": <int32 $volPct>, \"synchronous\": <\"volume\">, \"x-canonical-private-synchronous\": <\"volume\">}"
        Notifier.updateOsd("Volume", icon, summary, body, hint)
        return Result.ok("$summary: $body")
    }
}
