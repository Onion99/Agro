package org.onion.agro.audio

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import okio.FileSystem
import okio.Path.Companion.toPath

object BgmAudioFileStore {
    fun write(title: String, sourceSpecJson: String, wavBytes: ByteArray): String {
        val cacheRoot = FileKit.cacheDir.path
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("bgm_cache_directory_unavailable")
        val directory = cacheRoot.toPath() / "generated-bgm"
        FileSystem.SYSTEM.createDirectories(directory)
        val safeTitle = title
            .trim()
            .replace(INVALID_FILE_NAME_CHARS, "_")
            .trim('_')
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { "chiptune" }
        val fingerprint = sourceSpecJson.hashCode().toUInt().toString(16)
        val path = directory / "$safeTitle-$fingerprint.wav"
        FileSystem.SYSTEM.write(path) {
            write(wavBytes)
        }
        return path.toString()
    }

    fun read(path: String): ByteArray {
        return FileSystem.SYSTEM.read(path.toPath()) {
            readByteArray()
        }
    }

    internal fun playbackUri(path: String): String {
        if (path.startsWith(FILE_URI_PREFIX, ignoreCase = true)) return path

        val normalizedPath = path.replace('\\', '/')
        val prefix = if (normalizedPath.startsWith('/')) FILE_URI_PREFIX else "file:///"
        return buildString(prefix.length + normalizedPath.length) {
            append(prefix)
            normalizedPath.encodeToByteArray().forEach { byte ->
                val value = byte.toInt() and 0xFF
                if (value.isUnreservedFileUriByte()) {
                    append(value.toChar())
                } else {
                    append('%')
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0F])
                }
            }
        }
    }

    private const val MAX_FILE_NAME_LENGTH = 32
    private const val FILE_URI_PREFIX = "file://"
    private const val HEX = "0123456789ABCDEF"
    private val INVALID_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

    private fun Int.isUnreservedFileUriByte(): Boolean {
        return this in 'a'.code..'z'.code ||
            this in 'A'.code..'Z'.code ||
            this in '0'.code..'9'.code ||
            this == '-'.code ||
            this == '.'.code ||
            this == '_'.code ||
            this == '~'.code ||
            this == '/'.code ||
            this == ':'.code
    }
}
