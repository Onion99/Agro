package org.onion.agro.message

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import com.onion.model.ChatMessageContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object SvgMessageParser {
    private const val MAX_SVG_BYTES = 1024 * 1024
    private const val SVG_TYPE = "svg_image"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parseCompletedResponse(response: String): ChatMessageContent {
        val envelope = runCatching {
            val payload = json.parseToJsonElement(response).jsonObject
            val type = payload["type"] as? JsonPrimitive
                ?: error("Missing SVG content type")
            val svg = payload["svg"] as? JsonPrimitive
                ?: error("Missing SVG payload")
            require(type.isString && svg.isString)
            type.content to svg.content
        }.getOrElse {
            return unsupported(response, "invalid_svg_json")
        }
        if (envelope.first != SVG_TYPE) {
            return ChatMessageContent.Unsupported(
                declaredType = envelope.first,
                rawPayload = response,
                reason = "unexpected_content_type"
            )
        }
        return validateSvg(envelope.second, response)
    }

    fun parseStoredSvg(svg: String): ChatMessageContent {
        return validateSvg(svg, svg)
    }

    private fun validateSvg(
        svg: String,
        fallbackPayload: String
    ): ChatMessageContent {
        val trimmed = svg.trim()
        if (trimmed.isEmpty()) {
            return unsupported(fallbackPayload, "empty_svg")
        }
        if (trimmed.encodeToByteArray().size > MAX_SVG_BYTES) {
            return unsupported(fallbackPayload, "svg_too_large")
        }
        val unsafeReason = findUnsafeReason(trimmed)
        if (unsafeReason != null) {
            return unsupported(fallbackPayload, unsafeReason)
        }
        if (!hasBalancedXmlTags(trimmed)) {
            return unsupported(fallbackPayload, "malformed_svg_xml")
        }

        val root = runCatching {
            Ksoup.parse(trimmed, parser = Parser.xmlParser())
                .children()
                .singleOrNull()
                ?.takeIf { it.tagName().equals("svg", ignoreCase = true) }
        }.getOrNull() ?: return unsupported(fallbackPayload, "invalid_svg_root")

        if (root.attr("xmlns") != "http://www.w3.org/2000/svg") {
            return unsupported(fallbackPayload, "missing_svg_namespace")
        }

        val viewBox = root.attr("viewBox")
            .split(WHITESPACE_REGEX)
            .filter { it.isNotBlank() }
            .mapNotNull(String::toFloatOrNull)
        val width = root.attr("width").svgDimensionOrNull()
            ?: viewBox.getOrNull(2)
        val height = root.attr("height").svgDimensionOrNull()
            ?: viewBox.getOrNull(3)
        if (width == null || height == null || width <= 0f || height <= 0f) {
            return unsupported(fallbackPayload, "invalid_svg_viewport")
        }

        return ChatMessageContent.SvgImage(
            svg = trimmed,
            width = width,
            height = height
        )
    }

    private fun findUnsafeReason(svg: String): String? {
        if (FORBIDDEN_DECLARATION_REGEX.containsMatchIn(svg)) {
            return "forbidden_svg_declaration"
        }
        if (FORBIDDEN_ELEMENT_REGEX.containsMatchIn(svg)) {
            return "forbidden_svg_element"
        }
        if (EVENT_HANDLER_REGEX.containsMatchIn(svg)) {
            return "forbidden_svg_event_handler"
        }
        if (CSS_IMPORT_REGEX.containsMatchIn(svg)) {
            return "forbidden_svg_external_resource"
        }
        HREF_REGEX.findAll(svg).forEach { match ->
            val value = match.groupValues[3].trim()
            if (!value.startsWith("#")) {
                return "forbidden_svg_external_resource"
            }
        }
        CSS_URL_REGEX.findAll(svg).forEach { match ->
            val value = match.groupValues[2].trim()
            if (!value.startsWith("#")) {
                return "forbidden_svg_external_resource"
            }
        }
        return null
    }

    private fun hasBalancedXmlTags(svg: String): Boolean {
        if (!svg.startsWith("<svg", ignoreCase = true) ||
            !svg.endsWith("</svg>", ignoreCase = true)
        ) {
            return false
        }
        val stack = ArrayDeque<String>()
        var matchCount = 0
        TAG_REGEX.findAll(svg).forEach { match ->
            matchCount += 1
            val isClosing = match.groupValues[1].isNotEmpty()
            val name = match.groupValues[2].lowercase()
            val suffix = match.groupValues[3]
            val isSelfClosing = suffix.trimEnd().endsWith("/")
            if (isClosing) {
                if (stack.removeLastOrNull() != name) {
                    return false
                }
            } else if (!isSelfClosing) {
                stack.addLast(name)
            }
        }
        return matchCount > 0 && stack.isEmpty()
    }

    private fun String.svgDimensionOrNull(): Float? {
        return trim()
            .removeSuffix("px")
            .toFloatOrNull()
    }

    private fun unsupported(
        rawPayload: String,
        reason: String
    ): ChatMessageContent.Unsupported {
        return ChatMessageContent.Unsupported(
            declaredType = SVG_TYPE,
            rawPayload = rawPayload,
            reason = reason
        )
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val FORBIDDEN_DECLARATION_REGEX = Regex(
        pattern = "<!\\s*(DOCTYPE|ENTITY)",
        option = RegexOption.IGNORE_CASE
    )
    private val FORBIDDEN_ELEMENT_REGEX = Regex(
        pattern = "<\\s*(script|foreignObject)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val EVENT_HANDLER_REGEX = Regex(
        pattern = "\\s+on[a-z][a-z0-9_-]*\\s*=",
        option = RegexOption.IGNORE_CASE
    )
    private val CSS_IMPORT_REGEX = Regex(
        pattern = "@import\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val HREF_REGEX = Regex(
        pattern = "\\s+(href|xlink:href)\\s*=\\s*(['\"])(.*?)\\2",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val CSS_URL_REGEX = Regex(
        pattern = "url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TAG_REGEX = Regex(
        pattern = "<\\s*(/?)\\s*([A-Za-z_][A-Za-z0-9_.:-]*)\\b([^>]*)>",
        option = RegexOption.DOT_MATCHES_ALL
    )
}
