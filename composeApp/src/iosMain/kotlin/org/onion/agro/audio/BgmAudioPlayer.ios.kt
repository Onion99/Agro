@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.onion.agro.audio

import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSURL

actual class BgmAudioPlayer actual constructor() {
    private var player: AVAudioPlayer? = null
    private var errorListener: ((String?) -> Unit)? = null

    actual fun play(path: String) {
        stop()
        if (path.isBlank()) {
            reportError("bgm_audio_path_unavailable")
            return
        }

        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        val newPlayer = runCatching {
            AVAudioPlayer(
                contentsOfURL = NSURL.fileURLWithPath(path),
                error = null
            )
        }.getOrElse {
            reportError("bgm_audio_file_unreadable")
            return
        }
        player = newPlayer

        if (!newPlayer.prepareToPlay() || !newPlayer.play()) {
            reportError("bgm_audio_playback_failed")
        }
    }

    actual fun resume() {
        player?.play()
    }

    actual fun pause() {
        player?.pause()
    }

    actual fun stop() {
        player?.stop()
    }

    actual fun release() {
        stop()
        player = null
    }

    actual fun currentPositionMs(): Long = ((player?.currentTime ?: 0.0) * 1_000.0).toLong()

    actual fun currentDurationMs(): Long = ((player?.duration ?: 0.0) * 1_000.0).toLong()

    actual fun isPlaying(): Boolean = player?.playing == true

    actual fun setOnErrorListener(listener: (String?) -> Unit) {
        errorListener = listener
    }

    private fun reportError(message: String) {
        errorListener?.invoke(message)
    }
}
