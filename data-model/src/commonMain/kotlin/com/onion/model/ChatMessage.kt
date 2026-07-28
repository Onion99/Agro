package com.onion.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
sealed interface ChatMessageContent {
    val schemaVersion: Int

    @Serializable
    @SerialName(TYPE_TEXT)
    data class Text(
        val text: String,
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ) : ChatMessageContent

    @Serializable
    @SerialName(TYPE_RASTER_IMAGE)
    data class RasterImage(
        val bytes: ByteArray,
        val mimeType: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ) : ChatMessageContent

    @Serializable
    @SerialName(TYPE_SVG_IMAGE)
    data class SvgImage(
        val svg: String,
        val width: Float,
        val height: Float,
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ) : ChatMessageContent

    @Serializable
    @SerialName(TYPE_AUDIO)
    data class Audio(
        val path: String,
        val mimeType: String,
        val title: String,
        val durationMs: Long,
        val sampleRate: Int,
        val bitDepth: Int,
        val bpm: Int? = null,
        val loopBars: Int? = null,
        val loopStartMs: Long = 0,
        val loopEndMs: Long = durationMs,
        val sourceSpecJson: String? = null,
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ) : ChatMessageContent

    @Serializable
    @SerialName(TYPE_LOTTIE_ANIMATION)
    data class LottieAnimation(
        val json: String,
        val title: String,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val fps: Int,
        val loop: Boolean,
        val sourceSpecJson: String? = null,
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ) : ChatMessageContent

    @Serializable
    @SerialName(TYPE_UNSUPPORTED)
    data class Unsupported(
        val declaredType: String,
        val rawPayload: String,
        val reason: String,
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION
    ) : ChatMessageContent

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val TYPE_TEXT = "text"
        const val TYPE_RASTER_IMAGE = "raster_image"
        const val TYPE_SVG_IMAGE = "svg_image"
        const val TYPE_AUDIO = "audio"
        const val TYPE_LOTTIE_ANIMATION = "lottie_animation"
        const val TYPE_UNSUPPORTED = "unsupported"
    }
}

@OptIn(ExperimentalTime::class)
data class ChatMessage(
    val contents: List<ChatMessageContent>,
    val role: ChatRole,
    val metadata: Map<String, String>? = null,
    val toolCalls: List<PersistentToolCall> = emptyList(),
    val toolResponses: List<PersistentToolResponse> = emptyList(),
    val createdAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val id: Long = Random.nextLong()
) {
    val isUser: Boolean
        get() = role == ChatRole.USER

    val plainText: String
        get() = contents
            .filterIsInstance<ChatMessageContent.Text>()
            .joinToString(separator = "\n") { it.text }

    companion object {
        fun text(
            text: String,
            role: ChatRole,
            metadata: Map<String, String>? = null,
            toolCalls: List<PersistentToolCall> = emptyList(),
            toolResponses: List<PersistentToolResponse> = emptyList(),
            createdAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
            id: Long = Random.nextLong()
        ): ChatMessage {
            return ChatMessage(
                contents = listOf(ChatMessageContent.Text(text)),
                role = role,
                metadata = metadata,
                toolCalls = toolCalls,
                toolResponses = toolResponses,
                createdAtMillis = createdAtMillis,
                id = id
            )
        }
    }
}
