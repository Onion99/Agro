package org.onion.agro.native.llm

import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


enum class Role(val value: String) {
    SYSTEM("system"),
    USER("user"),
    MODEL("model"),
    TOOL("tool")
}

class Message internal constructor(
    val role: Role,
    val contents: Contents = Contents.empty(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResponses: List<ToolResponse> = emptyList(),
    val channels: Map<String, String> = emptyMap()
) {
    internal fun toJson(): JsonObject = buildJsonObject {
        put("role", role.value)
        if (role == Role.TOOL) {
            val toolResponsesJson = buildJsonArray {
                for (toolResponse in toolResponses) {
                    add(toolResponse.toJson())
                }
            }
            put("content", toolResponsesJson)
        } else {
            if (contents.contents.isNotEmpty()) {
                put("content", contents.toJson())
            }
        }
        if (toolCalls.isNotEmpty()) {
            val toolCallsJson = buildJsonArray {
                for (toolCall in toolCalls) {
                    add(toolCall.toJson())
                }
            }
            put("tool_calls", toolCallsJson)
        }
        if (channels.isNotEmpty()) {
            val channelsJson = buildJsonObject {
                for ((key, value) in channels) {
                    put(key, value)
                }
            }
            put("channels", channelsJson)
        }
    }

    override fun toString() = contents.toString()

    companion object {
        fun system(text: String) = system(Contents.of(text))
        fun system(contents: Contents) = Message(Role.SYSTEM, contents)
        fun user(text: String) = user(Contents.of(text))
        fun user(contents: Contents) = Message(Role.USER, contents)
        fun model(text: String) = model(Contents.of(text))
        fun model(
            contents: Contents = Contents.empty(),
            toolCalls: List<ToolCall> = emptyList(),
            channels: Map<String, String> = emptyMap()
        ) = Message(Role.MODEL, contents, toolCalls, emptyList(), channels)
        fun tool(toolResponses: List<ToolResponse>): Message {
            require(toolResponses.isNotEmpty()) {
                "A tool message requires at least one structured response."
            }
            return Message(Role.TOOL, toolResponses = toolResponses)
        }
    }
}

class Contents private constructor(val contents: List<Content>) {
    internal fun toJson(): JsonArray = buildJsonArray {
        for (content in contents) {
            add(content.toJson())
        }
    }

    override fun toString() = contents.joinToString("")

    companion object {
        internal fun empty() = Contents(emptyList())
        fun of(text: String) = Contents.of(Content.Text(text))
        fun of(vararg contents: Content) = Contents.of(contents.toList())
        fun of(contents: List<Content>) = Contents(contents)
    }
}

data class ToolCall(val name: String, val arguments: JsonObject) {
    internal fun toJson() = buildJsonObject {
        put("type", "function")
        val functionObj = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        put("function", functionObj)
    }
}

/** A structured tool result passed across the LiteRT-LM boundary. */
data class ToolResponse(val name: String, val response: JsonObject) {
    internal fun toJson() = buildJsonObject {
        put("type", "tool_response")
        put("name", name)
        put("response", response)
    }
}

sealed class Content {
    internal abstract fun toJson(): JsonObject

    data class Text(val text: String) : Content() {
        override fun toJson() = buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        override fun toString() = text
    }

    @OptIn(ExperimentalEncodingApi::class)
    data class ImageBytes(val bytes: ByteArray) : Content() {
        override fun toJson() = buildJsonObject {
            put("type", "image")
            put("blob", Base64.encode(bytes))
        }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as ImageBytes
            return bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }

    data class ImageFile(val absolutePath: String) : Content() {
        override fun toJson() = buildJsonObject {
            put("type", "image")
            put("path", absolutePath)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    data class AudioBytes(val bytes: ByteArray) : Content() {
        override fun toJson() = buildJsonObject {
            put("type", "audio")
            put("blob", Base64.encode(bytes))
        }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as AudioBytes
            return bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }

    data class AudioFile(val absolutePath: String) : Content() {
        override fun toJson() = buildJsonObject {
            put("type", "audio")
            put("path", absolutePath)
        }
    }
}

private fun sanitizeString(input: String): String {
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c.isHighSurrogate()) {
            sb.append("[SpecialChar]")
            if (i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                i++
            }
        } else if (c.isLowSurrogate()) {
            // Ignore orphaned low surrogates
        } else {
            val code = c.code
            if (code < 32 && code != 9 && code != 10 && code != 13) {
                // Skip control characters except tab (9), LF (10), CR (13)
            } else if (code == 127) {
                // Skip DEL
            } else {
                sb.append(c)
            }
        }
        i++
    }
    return sb.toString()
}

fun JsonElement.sanitizeForLmLite(): JsonElement {
    return when (this) {
        is JsonObject -> {
            buildJsonObject {
                for ((k, v) in this@sanitizeForLmLite) {
                    put(k, v.sanitizeForLmLite())
                }
            }
        }
        is JsonArray -> {
            buildJsonArray {
                for (item in this@sanitizeForLmLite) {
                    add(item.sanitizeForLmLite())
                }
            }
        }
        is JsonPrimitive -> {
            if (this.isString) {
                JsonPrimitive(sanitizeString(this.content))
            } else {
                this
            }
        }
    }
}

