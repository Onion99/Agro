package org.onion.agro.native.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentLoopRunnerTest {

    @Test
    fun completesWhenModelDoesNotRequestTools() = runTest {
        val session = FakeChatSession(
            outputs = mutableListOf(
                listOf(Message.model("done"))
            )
        )
        val runner = AgentLoopRunner(session, FakeToolExecutor())

        val events = runner.run(Message.user("hello")).toList()

        assertEquals(1, session.sentMessages.size)
        assertTrue(events.any { it is AgentLoopEvent.TextDelta && it.text == "done" })
        assertIs<AgentLoopEvent.Completed>(events.last())
    }

    @Test
    fun feedsToolResultsBackToTheModel() = runTest {
        val session = FakeChatSession(
            outputs = mutableListOf(
                listOf(
                    Message.model(
                        toolCalls = listOf(
                            ToolCall(
                                name = "testTool",
                                arguments = buildJsonObject { put("value", "input") }
                            )
                        )
                    )
                ),
                listOf(Message.model("final"))
            )
        )
        val tools = FakeToolExecutor()
        val runner = AgentLoopRunner(session, tools)

        val events = runner.run(Message.user("hello")).toList()

        assertEquals(2, session.sentMessages.size)
        assertEquals(Role.TOOL, session.sentMessages[1].role)
        assertEquals(1, session.sentMessages[1].toolResponses.size)
        assertIs<JsonObject>(session.sentMessages[1].toolResponses.single().response)
        assertEquals("testTool", tools.calls.single().first)
        assertTrue(events.any { it is AgentLoopEvent.ToolStarted })
        assertTrue(events.any { it is AgentLoopEvent.ToolFinished })
        assertIs<AgentLoopEvent.Completed>(events.last())
    }

    private class FakeChatSession(
        private val outputs: MutableList<List<Message>>
    ) : LmChatSession {
        val sentMessages = mutableListOf<Message>()

        override fun sendMessageAsync(
            message: Message,
            extraContext: Map<String, String>
        ): Flow<Message> = flow {
            sentMessages += message
            outputs.removeAt(0).forEach { emit(it) }
        }

        override fun cancelProcess() = Unit
    }

    private class FakeToolExecutor : AgentToolExecutor {
        val calls = mutableListOf<Pair<String, JsonObject>>()

        override suspend fun executeTool(name: String, arguments: JsonObject): ToolExecutionResult {
            calls += name to arguments
            return ToolExecutionResult(
                toolName = name,
                success = true,
                data = buildJsonObject {
                    put("success", true)
                    put("echo", arguments.toString())
                },
                startedAtMillis = 1,
                completedAtMillis = 2
            )
        }
    }
}
