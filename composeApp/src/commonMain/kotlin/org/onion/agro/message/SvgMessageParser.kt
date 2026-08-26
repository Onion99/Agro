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
        val sanitized = sanitizeSvg(envelope.second)
        return validateSvg(sanitized, response)
    }

    fun parseStoredSvg(svg: String): ChatMessageContent {
        return validateSvg(svg, svg)
    }

    fun sanitizeSvg(svg: String): String {
        var s = svg.trim()
        if (s.isEmpty()) return s

        // 1. Repair malformed filter attribute quotes: filter='url='glow'' -> filter='url(#glow)'
        s = MALFORMED_FILTER_URL_REGEX.replace(s) { match ->
            val id = match.groupValues[1]
            "filter='url(#$id)'"
        }
        s = MALFORMED_FILTER_PLAIN_REGEX.replace(s) { match ->
            val id = match.groupValues[1]
            "filter='url(#$id)'"
        }

        // 2. Normalize hallucinated filter elements (e.g. feMergeIn -> feMerge)
        s = FEMERGEIN_OPEN_REGEX.replace(s, "<feMerge>")
        s = FEMERGEIN_CLOSE_REGEX.replace(s, "</feMerge>")

        // 3. Balance tags and drop orphan closing tags
        s = balanceSvgTags(s)
        return s
    }

    private fun balanceSvgTags(svg: String): String {
        if (!svg.startsWith("<svg", ignoreCase = true) ||
            !svg.contains("</svg>", ignoreCase = true)
        ) {
            return svg
        }

        val stack = ArrayDeque<String>()
        val result = StringBuilder()
        var lastIndex = 0

        TAG_REGEX.findAll(svg).forEach { match ->
            // Append any non-tag text between matches
            if (match.range.first > lastIndex) {
                result.append(svg.substring(lastIndex, match.range.first))
            }
            lastIndex = match.range.last + 1

            val isClosing = match.groupValues[1].isNotEmpty()
            val rawName = match.groupValues[2]
            val lowerName = rawName.lowercase()
            val suffix = match.groupValues[3]
            val isSelfClosing = suffix.trimEnd().endsWith("/")

            if (isClosing) {
                if (lowerName == "svg") {
                    // Close all remaining open container tags before closing svg
                    while (stack.isNotEmpty() && stack.last() != "svg") {
                        val openTag = stack.removeLast()
                        result.append("</$openTag>")
                    }
                    if (stack.isNotEmpty() && stack.last() == "svg") {
                        stack.removeLast()
                    }
                    result.append("</$rawName>")
                } else if (stack.contains(lowerName)) {
                    // Close any intermediate open tags before this matching closing tag
                    while (stack.isNotEmpty() && stack.last() != lowerName) {
                        val openTag = stack.removeLast()
                        result.append("</$openTag>")
                    }
                    stack.removeLastOrNull()
                    result.append("</$rawName>")
                } else {
                    // Orphan closing tag without matching opening tag -> drop it
                }
            } else if (isSelfClosing) {
                result.append(match.value)
            } else if (lowerName in VOID_ELEMENTS) {
                // Known SVG void element opened without self-closing slash -> ensure self-closing
                val cleanSuffix = suffix.trimEnd()
                result.append("<$rawName$cleanSuffix/>")
            } else {
                stack.addLast(lowerName)
                result.append(match.value)
            }
        }

        if (lastIndex < svg.length) {
            result.append(svg.substring(lastIndex))
        }

        return result.toString()
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
        pattern = "\\s+(href|xlink:href)\\s*=\\s*(['\"])([\\s\\S]*?)\\2",
        option = RegexOption.IGNORE_CASE
    )
    private val CSS_URL_REGEX = Regex(
        pattern = "url\\(\\s*(['\"]?)([\\s\\S]*?)\\1\\s*\\)",
        option = RegexOption.IGNORE_CASE
    )
    private val TAG_REGEX = Regex("<\\s*(/?)\\s*([A-Za-z_][A-Za-z0-9_.:-]*)\\b([^>]*)>")

    private val MALFORMED_FILTER_URL_REGEX = Regex(
        pattern = "\\bfilter\\s*=\\s*['\"]url=\\s*['\"]?#?([a-zA-Z0-9_-]+)['\"]?['\"]",
        option = RegexOption.IGNORE_CASE
    )
    private val MALFORMED_FILTER_PLAIN_REGEX = Regex(
        pattern = "\\bfilter\\s*=\\s*['\"]([a-zA-Z0-9_-]+)['\"]",
        option = RegexOption.IGNORE_CASE
    )
    private val FEMERGEIN_OPEN_REGEX = Regex(
        pattern = "<\\s*feMergeIn\\b[^>]*>",
        option = RegexOption.IGNORE_CASE
    )
    private val FEMERGEIN_CLOSE_REGEX = Regex(
        pattern = "<\\s*/\\s*feMergeIn\\s*>",
        option = RegexOption.IGNORE_CASE
    )

    private val VOID_ELEMENTS = setOf(
        "rect", "circle", "ellipse", "line", "polyline", "polygon", "path", "stop", "image", "use",
        "fegaussianblur", "feoffset", "feblend", "femergenode", "fecolormatrix", "fedropshadow",
        "feflood", "fecomposite", "feturbulence", "fedisplacementmap", "femorphology",
        "fepointlight", "fedistantlight", "fespotlight"
    )
}

