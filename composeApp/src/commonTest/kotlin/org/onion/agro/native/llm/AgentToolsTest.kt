package org.onion.agro.native.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
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

        assertTrue("runJs" in names)
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
    fun blankJavaScriptFailsBeforeEvaluation() = runTest {
        val result = AgentTools().executeTool(
            name = "runJs",
            arguments = buildJsonObject {
                put("data", "")
            }
        )

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("blank"))
    }
}

