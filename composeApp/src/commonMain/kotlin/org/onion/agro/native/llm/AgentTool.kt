package org.onion.agro.native.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AgentToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val concurrencySafe: Boolean = false
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        })
    }
}

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val data: JsonElement = JsonNull,
    val error: String? = null,
    val startedAtMillis: Long,
    val completedAtMillis: Long
) {
    val durationMillis: Long = completedAtMillis - startedAtMillis

    fun toJson(): JsonObject = buildJsonObject {
        put("success", success)
        put("tool", toolName)
        put("data", data)
        if (error != null) {
            put("error", error)
        } else {
            put("error", JsonNull)
        }
        put("metadata", buildJsonObject {
            put("startedAtMillis", startedAtMillis)
            put("completedAtMillis", completedAtMillis)
            put("durationMs", durationMillis)
        })
    }
}

interface AgentTool {
    val definition: AgentToolDefinition
    suspend fun execute(arguments: JsonObject): ToolExecutionResult
}

interface AgentToolExecutor {
    suspend fun executeTool(name: String, arguments: JsonObject): ToolExecutionResult
}
