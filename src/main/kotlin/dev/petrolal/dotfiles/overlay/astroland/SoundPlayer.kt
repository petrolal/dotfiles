package dev.petrolal.dotfiles.overlay.astroland

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem

/**
 * Stands in for the original's `miniaudio`-backed `ma_engine_play_sound`
 * calls. `javax.sound.sampled` is good enough for astroland's short,
 * fire-and-forget sound effects (shots, explosions) -- each call opens a
 * fresh [javax.sound.sampled.Clip] on a daemon thread so overlapping shots
 * don't cut each other off, and the clip closes itself once playback ends.
 */
object SoundPlayer {
    private val cache = HashMap<String, ByteArray>()

    fun play(resourcePath: String) {
        val bytes = cache.getOrPut(resourcePath) {
            TextureLoader.readResourceBytes(resourcePath)
        }
        Thread.ofVirtual().start {
            try {
                val clip = AudioSystem.getClip()
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { audioIn ->
                    clip.open(audioIn)
                    clip.addLineListener { event ->
                        if (event.type == javax.sound.sampled.LineEvent.Type.STOP) {
                            clip.close()
                        }
                    }
                    clip.start()
                }
            } catch (_: Exception) {
                // Best-effort sound effects -- a missing/unsupported codec shouldn't crash the overlay.
            }
        }
    }
}
