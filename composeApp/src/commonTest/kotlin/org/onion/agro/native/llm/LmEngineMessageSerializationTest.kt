package org.onion.agro.native.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class LmEngineMessageSerializationTest {
    @Test
    fun conversationPrefaceSanitizesNestedMessageStrings() {
        val messageJson = buildConversationMessageJsonString(
            systemInstruction = "system\u0001instruction",
            initialMessages = listOf(
                Message.tool(
                    listOf(
                        ToolResponse(
                            name = "testTool",
                            response = buildJsonObject {
                                put("value", "safe\u007Ftext")
                            },
                        )
                    )
                )
            ),
        )

        val messages = Json.parseToJsonElement(messageJson).jsonArray
        val systemText = messages[0]
            .jsonObject["content"]
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
        val toolResponse = messages[1]
            .jsonObject["content"]
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("response")

        assertEquals("systeminstruction", systemText)
        val responseObject = assertIs<JsonObject>(toolResponse)
        assertEquals("safetext", responseObject["value"]?.jsonPrimitive?.content)
        assertFalse(messageJson.contains('\u0001'))
        assertFalse(messageJson.contains('\u007F'))
        assertFalse(messageJson.contains("\\u0001"))
        assertFalse(messageJson.contains("\\u007f", ignoreCase = true))
    }

    @Test
    fun emptyConversationPrefaceRemainsAnEmptyArray() {
        assertEquals(
            "[]",
            buildConversationMessageJsonString(
                systemInstruction = null,
                initialMessages = emptyList(),
            ),
        )
    }

    @Test
    fun toolMessageRequiresAtLeastOneStructuredResponse() {
        assertFailsWith<IllegalArgumentException> {
            Message.tool(emptyList())
        }
    }
}
