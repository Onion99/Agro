package org.onion.agro.native.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LmChatSession {
    fun sendMessageAsync(
        message: Message,
        extraContext: Map<String, String> = emptyMap()
    ): Flow<Message>

    fun cancelProcess()

    /** Native KV token count when the backend exposes it. */
    fun tokenCount(): Int? = null
}

data class AgentLoopConfig(
    val maxToolTurns: Int = 10
)

enum class AgentLoopTransition {
    USER_MESSAGE,
    MODEL_MESSAGE,
    TOOL_RESULTS,
    COMPLETED,
    MAX_TURNS_REACHED
}

data class AgentLoopState(
    val turnCount: Int = 0,
    val transition: AgentLoopTransition = AgentLoopTransition.USER_MESSAGE,
    val lastToolCallCount: Int = 0
)

sealed interface AgentLoopEvent {
    val state: AgentLoopState

    data class TextDelta(
        val text: String,
        override val state: AgentLoopState
    ) : AgentLoopEvent

    data class ThoughtDelta(
        val text: String,
        override val state: AgentLoopState
    ) : AgentLoopEvent

    data class ToolCallsReceived(
        val toolCalls: List<ToolCall>,
        override val state: AgentLoopState
    ) : AgentLoopEvent

    data class ToolStarted(
        val toolCall: ToolCall,
        val turnIndex: Int,
        val callIndex: Int,
        override val state: AgentLoopState
    ) : AgentLoopEvent

    data class ToolFinished(
        val toolCall: ToolCall,
        val result: ToolExecutionResult,
        val response: ToolResponse,
        val turnIndex: Int,
        val callIndex: Int,
        override val state: AgentLoopState
    ) : AgentLoopEvent

    data class Completed(
        override val state: AgentLoopState
    ) : AgentLoopEvent

    data class MaxTurnsReached(
        override val state: AgentLoopState
    ) : AgentLoopEvent
}

const val KEY_THINK_MODE = "enable_thinking"

class AgentLoopRunner(
    private val session: LmChatSession,
    private val toolExecutor: AgentToolExecutor,
    private val config: AgentLoopConfig = AgentLoopConfig()
) {
    fun run(
        initialMessage: Message,
        extraContextProvider: () -> Map<String, String> = { emptyMap() }
    ): Flow<AgentLoopEvent> = flow {
        var currentMessage = initialMessage
        var state = AgentLoopState()

        while (true) {
            var toolCallsReceived = emptyList<ToolCall>()
            val modelState = state.copy(transition = AgentLoopTransition.MODEL_MESSAGE)

            session.sendMessageAsync(currentMessage, extraContextProvider()).collect { responseMessage ->
                if (responseMessage.toolCalls.isNotEmpty()) {
                    toolCallsReceived = responseMessage.toolCalls
                    emit(
                        AgentLoopEvent.ToolCallsReceived(
                            toolCalls = responseMessage.toolCalls,
                            state = modelState.copy(lastToolCallCount = responseMessage.toolCalls.size)
                        )
                    )
                }

                val thoughtChunk = responseMessage.channels["thought"].orEmpty()
                if (thoughtChunk.isNotEmpty() && extraContextProvider()[KEY_THINK_MODE] == "true") {
                    emit(AgentLoopEvent.ThoughtDelta(thoughtChunk, modelState))
                }

                val textChunk = responseMessage.contents.toString()
                if (textChunk.isNotEmpty()) {
                    emit(AgentLoopEvent.TextDelta(textChunk, modelState))
                }
            }

            if (toolCallsReceived.isEmpty()) {
                state = state.copy(transition = AgentLoopTransition.COMPLETED, lastToolCallCount = 0)
                emit(AgentLoopEvent.Completed(state))
                return@flow
            }

            if (state.turnCount >= config.maxToolTurns) {
                state = state.copy(
                    transition = AgentLoopTransition.MAX_TURNS_REACHED,
                    lastToolCallCount = toolCallsReceived.size
                )
                emit(AgentLoopEvent.MaxTurnsReached(state))
                return@flow
            }

            val turnIndex = state.turnCount
            val responses = mutableListOf<ToolResponse>()
            val toolState = state.copy(
                transition = AgentLoopTransition.TOOL_RESULTS,
                lastToolCallCount = toolCallsReceived.size
            )

            toolCallsReceived.forEachIndexed { callIndex, toolCall ->
                emit(
                    AgentLoopEvent.ToolStarted(
                        toolCall = toolCall,
                        turnIndex = turnIndex,
                        callIndex = callIndex,
                        state = toolState
                    )
                )

                val result = toolExecutor.executeTool(toolCall.name, toolCall.arguments)
                val response = ToolResponse(toolCall.name, result.toJsonString())
                responses += response

                emit(
                    AgentLoopEvent.ToolFinished(
                        toolCall = toolCall,
                        result = result,
                        response = response,
                        turnIndex = turnIndex,
                        callIndex = callIndex,
                        state = toolState
                    )
                )
            }

            state = toolState.copy(turnCount = state.turnCount + 1)
            currentMessage = Message.tool(responses)
        }
    }
}
