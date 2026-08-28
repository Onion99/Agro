package org.onion.agro.native.llm

import com.onion.model.ChatMessage
import com.onion.model.ChatRole
import com.onion.model.PersistentToolCall
import com.onion.model.PersistentToolResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContextTranscriptToolReplayTest {
    @Test
    fun completedToolExchangeReplaysInNativeTurnOrder() {
        val assistant = ChatMessage.text(
            text = "Final answer",
            role = ChatRole.ASSISTANT,
            toolCalls = listOf(
                PersistentToolCall(
                    name = "analyzeUrl",
                    arguments = buildJsonObject {
                        put("url", "https://example.com")
                    },
                    createdAtMillis = 1,
                )
            ),
            toolResponses = listOf(
                PersistentToolResponse(
                    name = "analyzeUrl",
                    response = buildJsonObject {
                        put("success", true)
                        put("status", 200)
                    },
                    createdAtMillis = 2,
                )
            ),
        )

        val replay = ContextTranscript.toLmMessages(listOf(assistant))

        assertEquals(listOf(Role.MODEL, Role.TOOL, Role.MODEL), replay.map { it.role })
        assertEquals(1, replay[0].toolCalls.size)
        assertEquals("", replay[0].contents.toString())
        assertEquals("Final answer", replay[2].contents.toString())

        val response = assertIs<JsonObject>(replay[1].toolResponses.single().response)
        assertEquals("true", response["success"]?.jsonPrimitive?.content)
        assertEquals("200", response["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun incompleteToolExchangeFallsBackToFinalAssistantText() {
        val assistant = ChatMessage.text(
            text = "Recovered answer",
            role = ChatRole.ASSISTANT,
            toolCalls = listOf(
                PersistentToolCall(
                    name = "searchWeb",
                    arguments = buildJsonObject {
                        put("query", "Agro")
                    },
                    createdAtMillis = 1,
                )
            ),
        )

        val replay = ContextTranscript.toLmMessages(listOf(assistant))

        assertEquals(1, replay.size)
        assertEquals(Role.MODEL, replay.single().role)
        assertEquals("Recovered answer", replay.single().contents.toString())
        assertEquals(0, replay.single().toolCalls.size)
    }

    @Test
    fun toolExchangeWithoutFinalAssistantTextIsNotReplayed() {
        val assistant = ChatMessage.text(
            text = "",
            role = ChatRole.ASSISTANT,
            toolCalls = listOf(
                PersistentToolCall(
                    name = "testTool",
                    arguments = buildJsonObject {},
                    createdAtMillis = 1,
                )
            ),
            toolResponses = listOf(
                PersistentToolResponse(
                    name = "testTool",
                    response = buildJsonObject {},
                    createdAtMillis = 2,
                )
            ),
        )

        assertEquals(emptyList(), ContextTranscript.toLmMessages(listOf(assistant)))
    }
}
