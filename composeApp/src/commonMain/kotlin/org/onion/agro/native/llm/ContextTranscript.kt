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
        messages.forEach { message ->
            if (message.metadata?.get("is_generating") == "true") {
                return@forEach
            }

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
        val replayable = messages.filter { message ->
            message.role != ChatRole.SYSTEM &&
            message.metadata?.get("is_generating") != "true"
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
}
