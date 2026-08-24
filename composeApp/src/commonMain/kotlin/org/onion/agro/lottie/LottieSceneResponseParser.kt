package org.onion.agro.lottie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Parses one Gemma [LottieSceneContract] response and compiles it to render-ready Lottie JSON. */
object LottieSceneResponseParser {
    const val CONTENT_TYPE = LottieSceneContract.CONTENT_TYPE

    private const val MAX_RESPONSE_BYTES = 256 * 1024

    fun parse(response: String): ParsedLottieAnimation {
        requireLottie(response.isNotBlank(), "invalid_lottie_json")
        requireLottie(response.utf8SizeAtMost(MAX_RESPONSE_BYTES), "lottie_json_too_large")

        val sceneJson = extractFirstJsonObject(response)
        val sceneRoot = parseObject(sceneJson)
        val declaredType = sceneRoot["type"].stringContentOrNull()
        if (declaredType != CONTENT_TYPE) {
            throw LottieParseException(
                reason = "unexpected_content_type",
                declaredType = declaredType,
            )
        }

        val lottieRoot = LottieSceneCompiler.compile(sceneRoot)
        val fps = lottieRoot["fr"].intOrNull() ?: 30
        val ip = lottieRoot["ip"].intOrNull() ?: 0
        val op = lottieRoot["op"].intOrNull() ?: fps * 2

        return ParsedLottieAnimation(
            title = lottieRoot["nm"].stringContentOrNull() ?: "Lottie Animation",
            width = lottieRoot["w"].intOrNull() ?: 240,
            height = lottieRoot["h"].intOrNull() ?: 240,
            fps = fps,
            durationMs = (((op - ip).coerceAtLeast(1).toFloat() / fps) * 1_000f).toLong(),
            loop = lottieRoot["loop"].booleanOrNull() ?: true,
            json = lottieRoot.toString(),
        )
    }

    /**
     * Fast path returns an already clean object without copying. A single brace-aware scan is used
     * only when Gemma surrounds the object with a Markdown fence or a short prose fragment.
     */
    private fun extractFirstJsonObject(raw: String): String {
        var first = 0
        while (first < raw.length && raw[first].isWhitespace()) first++
        requireLottie(first < raw.length, "invalid_lottie_json")

        var last = raw.lastIndex
        while (last >= first && raw[last].isWhitespace()) last--
        if (raw[first] == '{' && raw[last] == '}') {
            return raw.sliceOrSelf(first, last + 1)
        }

        val start = raw.indexOf('{', startIndex = first)
        requireLottie(start >= 0, "invalid_lottie_json")

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            if (escaped) {
                escaped = false
            } else if (char == '\\' && inString) {
                escaped = true
            } else if (char == '"') {
                inString = !inString
            } else if (!inString) {
                when (char) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return raw.sliceOrSelf(start, index + 1)
                    }
                }
            }
        }
        throw LottieParseException("invalid_lottie_json")
    }

    private fun parseObject(value: String): JsonObject {
        return try {
            Json.parseToJsonElement(value) as? JsonObject
                ?: throw LottieParseException("invalid_lottie_json")
        } catch (error: LottieParseException) {
            throw error
        } catch (error: Exception) {
            throw LottieParseException("invalid_lottie_json", cause = error)
        }
    }

    private fun String.sliceOrSelf(start: Int, endExclusive: Int): String {
        return if (start == 0 && endExclusive == length) this else substring(start, endExclusive)
    }

    /** Counts UTF-8 bytes without allocating the ByteArray created by encodeToByteArray(). */
    private fun String.utf8SizeAtMost(limit: Int): Boolean {
        if (length > limit) return false
        if (length <= limit / 3) return true

        var byteCount = 0
        var index = 0
        while (index < length) {
            val code = this[index].code
            byteCount += when {
                code <= 0x7f -> 1
                code <= 0x7ff -> 2
                code in 0xd800..0xdbff &&
                    index + 1 < length &&
                    this[index + 1].code in 0xdc00..0xdfff -> {
                    index++
                    4
                }
                else -> 3
            }
            if (byteCount > limit) return false
            index++
        }
        return true
    }
}

data class ParsedLottieAnimation(
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val loop: Boolean,
    val json: String,
)

class LottieParseException(
    val reason: String,
    val declaredType: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(reason, cause)

private fun JsonElement?.stringContentOrNull(): String? {
    return (this as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
}

private fun JsonElement?.intOrNull(): Int? {
    return (this as? JsonPrimitive)?.intOrNull
}

private fun JsonElement?.booleanOrNull(): Boolean? {
    return (this as? JsonPrimitive)?.booleanOrNull
}

private fun requireLottie(condition: Boolean, reason: String) {
    if (!condition) throw LottieParseException(reason)
}
