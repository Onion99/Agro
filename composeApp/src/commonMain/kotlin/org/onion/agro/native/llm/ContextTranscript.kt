package org.onion.agro.native.llm

import com.onion.model.ChatMessage
import com.onion.model.ChatRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Converts durable UI history into the smaller, model-facing transcript. */
object ContextTranscript {
    private val json = Json { ignoreUnknownKeys = true }

    fun toLmMessages(messages: List<ChatMessage>): List<Message> = buildList {
        contextEligibleMessages(messages).forEach { message ->

            when (message.role) {
                ChatRole.SYSTEM -> Unit
                ChatRole.USER -> message.plainText
                    .takeIf { it.isNotBlank() }
                    ?.let { add(Message.user(it)) }
                ChatRole.ASSISTANT -> addAssistantMessage(message)
                ChatRole.TOOL -> message.plainText
                    .takeIf { it.isNotBlank() }
                    ?.let { add(Message.tool(listOf(ToolResponse("tool", it)))) }
            }
        }
    }

    /**
     * Keeps the recent turns and turns everything before them into a compact
     * system memory.  The durable transcript remains untouched.
     */
    fun compact(messages: List<ChatMessage>, retainTurns: Int): List<Message> {
        val replayable = contextEligibleMessages(messages).filter { message ->
            message.role != ChatRole.SYSTEM
        }
        val keepCount = (retainTurns.coerceAtLeast(1) * 2).coerceAtMost(replayable.size)
        val omitted = replayable.dropLast(keepCount)
        val recent = replayable.takeLast(keepCount)

        return buildList {
            if (omitted.isNotEmpty()) {
                add(Message.system(buildSummary(omitted)))
            }
            addAll(toLmMessages(recent))
        }
    }

    private fun MutableList<Message>.addAssistantMessage(message: ChatMessage) {
        val text = message.plainText
        val toolCalls = message.toolCalls.mapNotNull { call ->
            val arguments = runCatching {
                json.parseToJsonElement(call.arguments).jsonObject
            }.getOrElse { JsonObject(emptyMap()) }
            ToolCall(call.name, arguments)
        }
        if (toolCalls.isNotEmpty()) {
            add(
                Message.model(
                    contents = Contents.of(text),
                    toolCalls = toolCalls,
                )
            )
        } else if (text.isNotBlank()) {
            add(Message.model(text))
        }

        if (message.toolResponses.isNotEmpty()) {
            add(
                Message.tool(
                    message.toolResponses.map { response ->
                        ToolResponse(response.name, response.response)
                    }
                )
            )
        }
    }

    private fun buildSummary(messages: List<ChatMessage>): String {
        val body = buildString {
            appendLine("[Conversation memory: earlier turns are summarized below]")
            messages.forEach { message ->
                val text = message.plainText.trim()
                if (text.isNotEmpty()) {
                    append(message.role.name.lowercase())
                    append(": ")
                    appendLine(text.take(800))
                }
            }
        }
        return body.take(6_000)
    }

    /**
     * Removes transient or failed turns before rebuilding native KV state.
     *
     * The UI appends the current user prompt and a pending assistant placeholder
     * before inference starts. Replaying either and then sending the prompt again
     * duplicates the first turn, so the complete in-flight pair is excluded.
     */
    private fun contextEligibleMessages(messages: List<ChatMessage>): List<ChatMessage> {
        val excludedTurnIds = messages.asSequence()
            .filter { it.metadata?.get(METADATA_EXCLUDE_FROM_CONTEXT) == "true" }
            .mapNotNull { it.metadata?.get(METADATA_TURN_ID) }
            .toSet()
        val eligible = messages.filter { message ->
            val metadata = message.metadata
            metadata?.get(METADATA_EXCLUDE_FROM_CONTEXT) != "true" &&
                metadata?.get(METADATA_TURN_ID) !in excludedTurnIds
        }
        val pendingAssistantIndex = eligible.indexOfLast { message ->
            message.role == ChatRole.ASSISTANT &&
                message.metadata?.get(METADATA_IS_GENERATING) == "true"
        }
        if (pendingAssistantIndex < 0) return eligible

        val pendingTurnId = eligible[pendingAssistantIndex].metadata?.get(METADATA_TURN_ID)
        val userIndex = (pendingAssistantIndex - 1 downTo 0).firstOrNull { index ->
            val message = eligible[index]
            message.role == ChatRole.USER &&
                (pendingTurnId == null || message.metadata?.get(METADATA_TURN_ID) == pendingTurnId)
        } ?: return eligible.filterIndexed { index, _ -> index != pendingAssistantIndex }

        return eligible.filterIndexed { index, _ ->
            index !in userIndex..pendingAssistantIndex
        }
    }

    private const val METADATA_IS_GENERATING = "is_generating"
    private const val METADATA_TURN_ID = "turn_id"
    private const val METADATA_EXCLUDE_FROM_CONTEXT = "exclude_from_context"
}
