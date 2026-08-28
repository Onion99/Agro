package com.onion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PersistentToolProtocolTest {
    @Test
    fun toolPayloadsSerializeAsNestedJsonObjects() {
        val calls = listOf(
            PersistentToolCall(
                name = "searchWeb",
                arguments = buildJsonObject {
                    put("query", "Agro")
                },
                createdAtMillis = 1,
            )
        )
        val responses = listOf(
            PersistentToolResponse(
                name = "searchWeb",
                response = buildJsonObject {
                    put("success", true)
                },
                createdAtMillis = 2,
            )
        )

        val callJson = Json.parseToJsonElement(Json.encodeToString(calls))
            .jsonArray
            .single()
            .jsonObject
        val responseJson = Json.parseToJsonElement(Json.encodeToString(responses))
            .jsonArray
            .single()
            .jsonObject

        assertIs<JsonObject>(callJson["arguments"])
        assertIs<JsonObject>(responseJson["response"])
        assertEquals(
            calls,
            Json.decodeFromString<List<PersistentToolCall>>(Json.encodeToString(calls)),
        )
        assertEquals(
            responses,
            Json.decodeFromString<List<PersistentToolResponse>>(Json.encodeToString(responses)),
        )
    }
}
