package org.onion.agro.native.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentToolsTest {

    @Test
    fun toolDescriptionDoesNotExposeDisabledPlaceholderTools() {
        val tools = Json.parseToJsonElement(AgentTools().getToolsDescriptionJson()).jsonArray
        val names = tools.mapNotNull { tool ->
            tool.jsonObject["function"]
                ?.jsonObject
                ?.get("name")
                ?.jsonPrimitive
                ?.contentOrNull
        }

        assertFalse("runJs" in names)
        assertTrue("analyzeUrl" in names)
        assertTrue("searchWeb" in names)
        assertFalse("loadSkill" in names)
        assertFalse("runMcpTool" in names)
        assertFalse("runIntent" in names)
    }

    @Test
    fun disabledToolReturnsStructuredFailure() = runTest {
        val result = AgentTools().executeTool("loadSkill", buildJsonObject {})
        val payload = result.toJson()

        assertFalse(result.success)
        assertFalse(payload["success"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(result.error.orEmpty().contains("disabled"))
    }

    @Test
    fun disabledJavaScriptToolReturnsStructuredFailure() = runTest {
        val result = AgentTools().executeTool(
            name = "runJs",
            arguments = buildJsonObject {
                put("data", "")
            }
        )

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("disabled"))
    }

    @Test
    fun searchWebPrintsLiveBingResultPayload() = runTest {
        val result = AgentTools().executeTool(
            name = "searchWeb",
            arguments = buildJsonObject {
                put("query", "泥石流")
                put("count", 5)
                put("includeContent", false)
            }
        )
        val payload = result.toJson()

        println("searchWeb live ToolExecutionResult structure:")
        println(PRETTY_JSON.encodeToString(payload))

        assertTrue(result.success, result.error)
        assertEquals("searchWeb", payload["tool"]?.jsonPrimitive?.content)
        assertNull(result.error)

        val data = assertNotNull(payload["data"]?.jsonObject)
        assertEquals(true, data["success"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals("bing", data["provider"]?.jsonPrimitive?.content)
        assertEquals("泥石流", data["query"]?.jsonPrimitive?.content)
        assertEquals(5, data["requestedCount"]?.jsonPrimitive?.int)
        assertEquals(BING_SEARCH_URL, data["searchUrl"]?.jsonPrimitive?.content)

        val results = assertNotNull(data["results"]?.jsonArray)
        assertTrue(results.isNotEmpty(), "Expected Bing to return at least one standard search result")
        results.forEachIndexed { index, item ->
            val searchResult = item.jsonObject
            assertEquals(index + 1, searchResult["rank"]?.jsonPrimitive?.int)
            assertTrue(searchResult["title"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
            assertTrue(searchResult["url"]?.jsonPrimitive?.content.orEmpty().startsWith("http"))
            assertEquals(false, searchResult["contentFetched"]?.jsonPrimitive?.content?.toBoolean())
        }

        val metadata = assertNotNull(payload["metadata"]?.jsonObject)
        assertNotNull(metadata["startedAtMillis"]?.jsonPrimitive?.contentOrNull)
        assertNotNull(metadata["completedAtMillis"]?.jsonPrimitive?.contentOrNull)
        assertNotNull(metadata["durationMs"]?.jsonPrimitive?.contentOrNull)
    }

    private companion object {
        val PRETTY_JSON = Json { prettyPrint = true }
        const val BING_SEARCH_URL =
            "https://cn.bing.com/search?form=bing&q=%E6%B3%A5%E7%9F%B3%E6%B5%81"
    }
}

