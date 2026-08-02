package org.onion.agro.audio

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import okio.Path.Companion.toPath
import org.onion.agro.io.systemFileSystem

object BgmAudioFileStore {
    fun write(title: String, sourceSpecJson: String, wavBytes: ByteArray): String {
        val cacheRoot = FileKit.cacheDir.path
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("bgm_cache_directory_unavailable")
        val directory = cacheRoot.toPath() / "generated-bgm"
        systemFileSystem.createDirectories(directory)
        val safeTitle = title
            .trim()
            .replace(INVALID_FILE_NAME_CHARS, "_")
            .trim('_')
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { "chiptune" }
        val fingerprint = sourceSpecJson.hashCode().toUInt().toString(16)
        val path = directory / "$safeTitle-$fingerprint.wav"
        systemFileSystem.write(path) {
            write(wavBytes)
        }
        return path.toString()
    }

    fun read(path: String): ByteArray {
        return systemFileSystem.read(path.toPath()) {
            readByteArray()
        }
    }

    private const val MAX_FILE_NAME_LENGTH = 32
    private val INVALID_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
}
