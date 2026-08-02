package org.onion.agro.audio

import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayer
import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayerState
import io.github.kdroidfilter.composemediaplayer.audio.ErrorListener

actual class BgmAudioPlayer actual constructor() {
    private val player = AudioPlayer()

    actual fun play(path: String) {
        player.play(BgmAudioFileStore.playbackUri(path))
    }

    actual fun resume() {
        player.play()
    }

    actual fun pause() {
        player.pause()
    }

    actual fun stop() {
        player.stop()
    }

    actual fun release() {
        player.release()
    }

    actual fun currentPositionMs(): Long = player.currentPosition() ?: 0L

    actual fun currentDurationMs(): Long = player.currentDuration() ?: 0L

    actual fun isPlaying(): Boolean = player.currentPlayerState() == AudioPlayerState.PLAYING

    actual fun setOnErrorListener(listener: (String?) -> Unit) {
        player.setOnErrorListener(
            object : ErrorListener {
                override fun onError(message: String?) {
                    listener(message)
                }
            }
        )
    }
}
