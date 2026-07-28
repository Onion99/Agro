package org.onion.agro.database

import com.onion.model.ChatMessage
import com.onion.model.ChatMessageContent
import com.onion.model.ChatRole
import com.onion.model.ChatSessionMode
import com.onion.model.PersistentToolCall
import com.onion.model.PersistentToolResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.onion.agro.message.SvgMessageParser
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ChatHistoryRepository(
    private val dao: ChatHistoryDao
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun observeSessions(query: String = ""): Flow<List<ChatSessionEntity>> {
        return if (query.isBlank()) dao.observeSessions() else dao.searchSessions(query.trim())
    }

    suspend fun getMostRecentSession(): ChatSessionEntity? = dao.getMostRecentSession()

    suspend fun getSession(sessionId: String): ChatSessionEntity? = dao.getSession(sessionId)

    @OptIn(ExperimentalTime::class)
    suspend fun createSession(
        title: String = DEFAULT_TITLE,
        mode: ChatSessionMode = ChatSessionMode.DEFAULT,
        systemInstruction: String = ""
    ): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = newId("session")
        dao.upsertSession(
            ChatSessionEntity(
                id = id,
                title = title.ifBlank { DEFAULT_TITLE },
                mode = mode.toDatabaseValue(),
                systemInstruction = systemInstruction,
                createdAtMillis = now,
                updatedAtMillis = now,
                messageCount = 0,
                lastMessagePreview = ""
            )
        )
        return id
    }

    suspend fun updateSessionContext(
        sessionId: String,
        mode: ChatSessionMode,
        systemInstruction: String
    ) {
        dao.updateSessionContext(
            sessionId = sessionId,
            mode = mode.toDatabaseValue(),
            systemInstruction = systemInstruction,
            updatedAtMillis = nowMillis()
        )
    }

    suspend fun loadMessages(sessionId: String): List<ChatMessage> {
        val contentsByMessage = dao.getMessageContents(sessionId).groupBy { it.messageId }
        return dao.getMessages(sessionId).map { entity ->
            entity.toChatMessage(contentsByMessage[entity.id].orEmpty())
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun saveMessage(sessionId: String, message: ChatMessage) {
        dao.upsertMessageWithContents(
            message = message.toEntity(sessionId),
            contents = message.contents.mapIndexed { index, content ->
                content.toEntity(message.id.toString(), index)
            }
        )
        refreshSessionSummary(sessionId, message.searchableText())
    }

    @OptIn(ExperimentalTime::class)
    suspend fun renameSession(sessionId: String, title: String) {
        dao.renameSession(
            sessionId = sessionId,
            title = title.trim().ifBlank { DEFAULT_TITLE },
            updatedAtMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun clearSessionMessages(sessionId: String) {
        dao.deleteMessages(sessionId)
        refreshSessionSummary(sessionId, "")
    }

    suspend fun deleteMessage(sessionId: String, messageId: Long) {
        dao.deleteMessage(sessionId, messageId.toString())
        refreshSessionSummary(sessionId, "")
    }

    suspend fun upsertToolLog(toolLog: ChatToolLogEntity): Boolean {
        if (!dao.messageExists(toolLog.sessionId, toolLog.messageId)) {
            return false
        }
        return runCatching {
            dao.upsertToolLog(toolLog)
            true
        }.getOrElse { false }
    }

    suspend fun exportSessionMarkdown(sessionId: String): String {
        val session = dao.getSession(sessionId) ?: return ""
        val messages = loadMessages(sessionId)
        val toolLogs = dao.getToolLogs(sessionId).groupBy { it.messageId }
        return buildString {
            appendLine("# ${session.title}")
            appendLine()
            appendLine("- Created: ${session.createdAtMillis}")
            appendLine("- Updated: ${session.updatedAtMillis}")
            appendLine("- Mode: ${session.mode}")
            appendLine()
            messages.forEach { message ->
                appendLine("## ${message.role.name.lowercase()}")
                appendLine()
                message.contents.forEach { content ->
                    when (content) {
                        is ChatMessageContent.Text -> appendLine(content.text)
                        is ChatMessageContent.SvgImage -> {
                            appendLine("```svg")
                            appendLine(content.svg)
                            appendLine("```")
                        }
                        is ChatMessageContent.RasterImage -> {
                            appendLine("[Raster image: ${content.mimeType ?: "unknown"}]")
                        }
                        is ChatMessageContent.Audio -> {
                            appendLine("[Audio: ${content.title}]")
                            content.sourceSpecJson?.let { source ->
                                appendLine("```json")
                                appendLine(source)
                                appendLine("```")
                            }
                        }
                        is ChatMessageContent.LottieAnimation -> {
                            appendLine("[Lottie animation: ${content.title}]")
                            appendLine("```json")
                            appendLine(content.json)
                            appendLine("```")
                            content.sourceSpecJson?.let { source ->
                                appendLine("```json")
                                appendLine(source)
                                appendLine("```")
                            }
                        }
                        is ChatMessageContent.Unsupported -> {
                            appendLine("```")
                            appendLine(content.rawPayload)
                            appendLine("```")
                        }
                    }
                    appendLine()
                }
                val logs = toolLogs[message.id.toString()].orEmpty()
                if (logs.isNotEmpty()) {
                    appendLine("### Tool Logs")
                    logs.forEach { log ->
                        appendLine("- ${log.status}: ${log.toolName}")
                        appendLine("  - arguments: ${log.arguments}")
                        if (log.response.isNotBlank()) {
                            appendLine("  - response: ${log.response}")
                        }
                    }
                    appendLine()
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun refreshSessionSummary(sessionId: String, latestContent: String) {
        val current = dao.getSession(sessionId) ?: return
        val count = dao.countMessages(sessionId)
        val preview = latestContent.ifBlank {
            dao.getLastMessagePreview(sessionId).orEmpty()
        }.take(PREVIEW_LIMIT)
        val title = if (current.title == DEFAULT_TITLE && latestContent.isNotBlank()) {
            latestContent.titleFromContent()
        } else {
            current.title
        }
        dao.upsertSession(
            current.copy(
                title = title,
                updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
                messageCount = count,
                lastMessagePreview = preview
            )
        )
    }

    private fun ChatMessage.toEntity(sessionId: String): ChatMessageEntity {
        return ChatMessageEntity(
            id = id.toString(),
            sessionId = sessionId,
            role = role.name.lowercase(),
            content = searchableText(),
            toolCallsJson = json.encodeToString(toolCalls),
            toolResponsesJson = json.encodeToString(toolResponses),
            metadataJson = json.encodeToString(metadata ?: emptyMap()),
            createdAtMillis = createdAtMillis
        )
    }

    private fun ChatMessageEntity.toChatMessage(
        contentEntities: List<ChatMessageContentEntity>
    ): ChatMessage {
        val parsedRole = runCatching {
            ChatRole.valueOf(role.uppercase())
        }.getOrDefault(ChatRole.ASSISTANT)
        val parsedMetadata = runCatching {
            json.decodeFromString<Map<String, String>>(metadataJson)
        }.getOrDefault(emptyMap())
        val parsedToolCalls = runCatching {
            json.decodeFromString<List<PersistentToolCall>>(toolCallsJson)
        }.getOrDefault(emptyList())
        val parsedToolResponses = runCatching {
            json.decodeFromString<List<PersistentToolResponse>>(toolResponsesJson)
        }.getOrDefault(emptyList())
        val parsedContents = contentEntities
            .sortedBy { it.position }
            .map { it.toChatMessageContent() }
            .ifEmpty { listOf(ChatMessageContent.Text(content)) }
        return ChatMessage(
            contents = parsedContents,
            role = parsedRole,
            metadata = parsedMetadata,
            toolCalls = parsedToolCalls,
            toolResponses = parsedToolResponses,
            createdAtMillis = createdAtMillis,
            id = id.toLongOrNull() ?: Random.nextLong()
        )
    }

    private fun ChatMessageContent.toEntity(
        messageId: String,
        position: Int
    ): ChatMessageContentEntity {
        val encoded = when (this) {
            is ChatMessageContent.Text -> EncodedContent(
                type = ChatMessageContent.TYPE_TEXT,
                schemaVersion = schemaVersion,
                payloadJson = json.encodeToString(TextPayload(text))
            )
            is ChatMessageContent.RasterImage -> EncodedContent(
                type = ChatMessageContent.TYPE_RASTER_IMAGE,
                schemaVersion = schemaVersion,
                payloadJson = json.encodeToString(
                    RasterImagePayload(
                        mimeType = mimeType,
                        width = width,
                        height = height
                    )
                ),
                payloadBlob = bytes
            )
            is ChatMessageContent.SvgImage -> EncodedContent(
                type = ChatMessageContent.TYPE_SVG_IMAGE,
                schemaVersion = schemaVersion,
                payloadJson = json.encodeToString(
                    SvgPayload(svg = svg, width = width, height = height)
                )
            )
            is ChatMessageContent.Audio -> EncodedContent(
                type = ChatMessageContent.TYPE_AUDIO,
                schemaVersion = schemaVersion,
                payloadJson = json.encodeToString(
                    AudioPayload(
                        path = path,
                        mimeType = mimeType,
                        title = title,
                        durationMs = durationMs,
                        sampleRate = sampleRate,
                        bitDepth = bitDepth,
                        bpm = bpm,
                        loopBars = loopBars,
                        loopStartMs = loopStartMs,
                        loopEndMs = loopEndMs,
                        sourceSpecJson = sourceSpecJson
                    )
                )
            )
            is ChatMessageContent.LottieAnimation -> EncodedContent(
                type = ChatMessageContent.TYPE_LOTTIE_ANIMATION,
                schemaVersion = schemaVersion,
                payloadJson = this@ChatHistoryRepository.json.encodeToString(
                    LottieAnimationPayload(
                        json = this.json,
                        title = title,
                        width = width,
                        height = height,
                        durationMs = durationMs,
                        fps = fps,
                        loop = loop,
                        sourceSpecJson = sourceSpecJson
                    )
                )
            )
            is ChatMessageContent.Unsupported -> EncodedContent(
                type = ChatMessageContent.TYPE_UNSUPPORTED,
                schemaVersion = schemaVersion,
                payloadJson = json.encodeToString(
                    UnsupportedPayload(
                        declaredType = declaredType,
                        rawPayload = rawPayload,
                        reason = reason
                    )
                )
            )
        }
        return ChatMessageContentEntity(
            id = "$messageId:content:$position",
            messageId = messageId,
            position = position,
            type = encoded.type,
            schemaVersion = encoded.schemaVersion,
            payloadJson = encoded.payloadJson,
            payloadBlob = encoded.payloadBlob
        )
    }

    private fun ChatMessageContentEntity.toChatMessageContent(): ChatMessageContent {
        if (schemaVersion > ChatMessageContent.CURRENT_SCHEMA_VERSION) {
            return unsupported("unsupported_schema_version")
        }
        return when (type) {
            ChatMessageContent.TYPE_TEXT -> runCatching {
                ChatMessageContent.Text(
                    text = json.decodeFromString<TextPayload>(payloadJson).text,
                    schemaVersion = schemaVersion
                )
            }.getOrElse { unsupported("invalid_text_payload") }
            ChatMessageContent.TYPE_RASTER_IMAGE -> runCatching {
                val payload = json.decodeFromString<RasterImagePayload>(payloadJson)
                ChatMessageContent.RasterImage(
                    bytes = payloadBlob ?: byteArrayOf(),
                    mimeType = payload.mimeType,
                    width = payload.width,
                    height = payload.height,
                    schemaVersion = schemaVersion
                )
            }.getOrElse { unsupported("invalid_raster_payload") }
            ChatMessageContent.TYPE_SVG_IMAGE -> {
                val svg = runCatching {
                    json.decodeFromString<SvgPayload>(payloadJson).svg
                }.recoverCatching {
                    json.decodeFromString<SvgEnvelopePayload>(payloadJson).svg
                }.getOrNull()
                svg?.let(SvgMessageParser::parseStoredSvg)
                    ?: unsupported("invalid_svg_payload")
            }
            ChatMessageContent.TYPE_AUDIO -> runCatching {
                val payload = json.decodeFromString<AudioPayload>(payloadJson)
                ChatMessageContent.Audio(
                    path = payload.path,
                    mimeType = payload.mimeType,
                    title = payload.title,
                    durationMs = payload.durationMs,
                    sampleRate = payload.sampleRate,
                    bitDepth = payload.bitDepth,
                    bpm = payload.bpm,
                    loopBars = payload.loopBars,
                    loopStartMs = payload.loopStartMs,
                    loopEndMs = payload.loopEndMs,
                    sourceSpecJson = payload.sourceSpecJson,
                    schemaVersion = schemaVersion
                )
            }.getOrElse { unsupported("invalid_audio_payload") }
            ChatMessageContent.TYPE_LOTTIE_ANIMATION -> runCatching {
                val payload = json.decodeFromString<LottieAnimationPayload>(payloadJson)
                ChatMessageContent.LottieAnimation(
                    json = payload.json,
                    title = payload.title,
                    width = payload.width,
                    height = payload.height,
                    durationMs = payload.durationMs,
                    fps = payload.fps,
                    loop = payload.loop,
                    sourceSpecJson = payload.sourceSpecJson,
                    schemaVersion = schemaVersion
                )
            }.getOrElse { unsupported("invalid_lottie_payload") }
            ChatMessageContent.TYPE_UNSUPPORTED -> runCatching {
                val payload = json.decodeFromString<UnsupportedPayload>(payloadJson)
                ChatMessageContent.Unsupported(
                    declaredType = payload.declaredType,
                    rawPayload = payload.rawPayload,
                    reason = payload.reason,
                    schemaVersion = schemaVersion
                )
            }.getOrElse { unsupported("invalid_unsupported_payload") }
            else -> unsupported("unknown_content_type")
        }
    }

    private fun ChatMessageContentEntity.unsupported(reason: String): ChatMessageContent.Unsupported {
        return ChatMessageContent.Unsupported(
            declaredType = type,
            rawPayload = payloadJson,
            reason = reason,
            schemaVersion = schemaVersion
        )
    }

    private fun ChatMessage.searchableText(): String {
        return contents.mapNotNull { content ->
            when (content) {
                is ChatMessageContent.Text -> content.text
                is ChatMessageContent.Unsupported -> content.rawPayload
                is ChatMessageContent.Audio -> content.title
                is ChatMessageContent.LottieAnimation -> content.title
                is ChatMessageContent.RasterImage,
                is ChatMessageContent.SvgImage -> null
            }
        }.joinToString(separator = "\n").trim()
    }

    private fun String.titleFromContent(): String {
        return lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(TITLE_LIMIT)
            ?: DEFAULT_TITLE
    }

    private fun ChatSessionMode.toDatabaseValue(): String = when (this) {
        ChatSessionMode.DEFAULT -> "default"
        ChatSessionMode.SVG_IMAGE -> "svg_image"
        ChatSessionMode.CHIPTUNE_BGM_MML -> "chiptune_bgm_mml"
        ChatSessionMode.LOTTIE_ANIMATION -> "lottie_animation"
    }

    private data class EncodedContent(
        val type: String,
        val schemaVersion: Int,
        val payloadJson: String,
        val payloadBlob: ByteArray? = null
    )

    @Serializable
    private data class TextPayload(val text: String)

    @Serializable
    private data class RasterImagePayload(
        val mimeType: String? = null,
        val width: Int? = null,
        val height: Int? = null
    )

    @Serializable
    private data class SvgPayload(
        val svg: String,
        val width: Float? = null,
        val height: Float? = null
    )

    @Serializable
    private data class SvgEnvelopePayload(
        val type: String,
        val svg: String
    )

    @Serializable
    private data class AudioPayload(
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
        val sourceSpecJson: String? = null
    )

    @Serializable
    private data class LottieAnimationPayload(
        val json: String,
        val title: String,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val fps: Int,
        val loop: Boolean,
        val sourceSpecJson: String? = null
    )

    @Serializable
    private data class UnsupportedPayload(
        val declaredType: String,
        val rawPayload: String,
        val reason: String
    )

    companion object {
        const val DEFAULT_TITLE = "New Chat"
        private const val TITLE_LIMIT = 36
        private const val PREVIEW_LIMIT = 120

        @OptIn(ExperimentalTime::class)
        fun newId(prefix: String): String {
            val now = Clock.System.now().toEpochMilliseconds()
            val suffix = Random.nextLong().toULong().toString(16)
            return "${prefix}_${now}_$suffix"
        }

        @OptIn(ExperimentalTime::class)
        private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
