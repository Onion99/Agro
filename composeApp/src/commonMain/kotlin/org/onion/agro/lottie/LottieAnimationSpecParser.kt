package org.onion.agro.lottie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parses Native Lottie JSON returned by the model.
 *
 * The historical object name is kept for the existing message/parser boundary. This parser deliberately
 * does not build animation layers, choose templates, or convert an intent spec into local geometry.
 */
object LottieAnimationSpecParser {
    private const val MAX_RESPONSE_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(response: String): ParsedLottieAnimation {
        val sanitizedJson = LottieJsonSanitizer.sanitize(response).trim()
        requireLottie(sanitizedJson.isNotEmpty(), "invalid_lottie_json")
        requireLottie(
            sanitizedJson.encodeToByteArray().size <= MAX_RESPONSE_BYTES,
            "lottie_json_too_large"
        )

        val root = parseObject(sanitizedJson)
        requireLottie(
            root.containsKey("layers") || root.containsKey("v"),
            "unexpected_content_type"
        )
        requireLottie(root["type"]?.stringContentOrNull() != "lottie_animation_spec", "unexpected_content_type")
        LottieJsonValidator.validate(sanitizedJson)

        val finalRoot = parseObject(sanitizedJson)
        val width = finalRoot["w"].intOrNull() ?: 240
        val height = finalRoot["h"].intOrNull() ?: 240
        val fps = finalRoot["fr"].intOrNull()?.coerceAtLeast(1) ?: 60
        val ip = finalRoot["ip"].intOrNull() ?: 0
        val op = finalRoot["op"].intOrNull() ?: (fps * 2)
        val title = finalRoot["nm"].stringContentOrNull() ?: "Lottie Animation"
        val durationMs = (((op - ip).coerceAtLeast(1).toFloat() / fps) * 1000f).toLong()

        return ParsedLottieAnimation(
            title = title,
            width = width,
            height = height,
            fps = fps,
            durationMs = durationMs,
            loop = finalRoot["loop"].booleanOrNull() ?: true,
            json = sanitizedJson
        )
    }

    fun declaredType(response: String): String? {
        return runCatching {
            parseObject(response.trim())["type"].stringContentOrNull()
        }.getOrNull()
    }

    /** Compatibility fallback for ChatMessageParser's unsupported-content envelope. */
    const val CONTENT_TYPE = "lottie_animation"

    private fun parseObject(value: String): JsonObject {
        return runCatching {
            json.parseToJsonElement(value).jsonObject
        }.getOrElse {
            throw LottieParseException("invalid_lottie_json", it)
        }
    }
}

object LottieJsonValidator {
    private const val MAX_LOTTIE_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }
    private val requiredTopLevelFields = setOf("fr", "layers")
    private val drawableGeometryTypes = setOf("el", "rc", "sh", "sr")
    private val drawablePaintTypes = setOf("fl", "st", "gf", "gs")
    private val forbiddenKeys = setOf(
        "fonts",
        "chars",
        "ef",
        "x",
        "html",
        "css",
        "script",
        "base64",
        "masksProperties",
        "hasMask"
    )
    private val forbiddenValueFragments = listOf(
        "http://",
        "https://",
        "file://",
        "data:",
        ".lottie",
        "base64"
    )

    fun validate(lottieJson: String) {
        requireLottie(lottieJson.encodeToByteArray().size <= MAX_LOTTIE_BYTES, "lottie_json_too_large")
        val root = runCatching {
            json.parseToJsonElement(lottieJson).jsonObject
        }.getOrElse {
            throw LottieParseException("invalid_lottie_json", it)
        }

        requireLottie(requiredTopLevelFields.all(root::containsKey), "invalid_lottie_json")
        requireLottie(!containsForbiddenContent(root), "forbidden_lottie_external_resource")

        val assets = root["assets"]?.jsonArray
        if (assets != null) {
            requireLottie(assets.isEmpty(), "forbidden_lottie_external_resource")
        }

        val layers = root["layers"]?.jsonArray ?: throw LottieParseException("invalid_lottie_json")
        requireLottie(layers.isNotEmpty(), "empty_lottie_layers")
        requireLottie(layers.size <= 32, "lottie_layer_count_too_large")
        var hasDrawableGeometry = false
        var hasDrawablePaint = false
        layers.forEach { layer ->
            val layerObject = layer as? JsonObject
                ?: throw LottieParseException("invalid_lottie_layer")
            val layerType = layerObject["ty"].intOrNull() ?: 4
            requireLottie(layerType in setOf(0, 3, 4), "unsupported_lottie_layer_type")
            requireLottie(layerObject["ddd"].intOrNull() ?: 0 == 0, "unsupported_lottie_3d_layer")
            hasDrawableGeometry = hasDrawableGeometry ||
                containsShapeType(layerObject, drawableGeometryTypes)
            hasDrawablePaint = hasDrawablePaint ||
                containsShapeType(layerObject, drawablePaintTypes)
        }
        requireLottie(
            hasDrawableGeometry && hasDrawablePaint,
            "empty_lottie_drawable_content"
        )
    }

    private fun containsForbiddenContent(element: JsonElement): Boolean {
        return when (element) {
            is JsonObject -> element.any { (key, value) ->
                key in forbiddenKeys || containsForbiddenContent(value)
            }
            is JsonArray -> element.any(::containsForbiddenContent)
            is JsonPrimitive -> {
                val value = element.contentOrNull?.lowercase().orEmpty()
                forbiddenValueFragments.any(value::contains)
            }
        }
    }

    private fun containsShapeType(element: JsonElement, types: Set<String>): Boolean {
        return when (element) {
            is JsonObject -> {
                val ty = element["ty"].stringContentOrNull()
                ty in types ||
                    (element["shapes"] as? JsonArray)?.any { containsShapeType(it, types) } == true ||
                    (element["it"] as? JsonArray)?.any { containsShapeType(it, types) } == true
            }
            is JsonArray -> element.any { containsShapeType(it, types) }
            is JsonPrimitive -> false
        }
    }
}

data class ParsedLottieAnimation(
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val loop: Boolean,
    val json: String
)

class LottieParseException(
    val reason: String,
    cause: Throwable? = null
) : IllegalArgumentException(reason, cause)

private fun JsonElement?.stringContentOrNull(): String? {
    return (this as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
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
