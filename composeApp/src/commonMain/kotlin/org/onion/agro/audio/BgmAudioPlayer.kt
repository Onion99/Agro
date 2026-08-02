package org.onion.agro.audio

expect class BgmAudioPlayer() {
    fun play(path: String)

    fun resume()

    fun pause()

    fun stop()

    fun release()

    fun currentPositionMs(): Long

    fun currentDurationMs(): Long

    fun isPlaying(): Boolean

    fun setOnErrorListener(listener: (String?) -> Unit)
}
