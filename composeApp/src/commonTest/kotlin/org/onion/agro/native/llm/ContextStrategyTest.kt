package org.onion.agro.native.llm

import com.onion.model.ChatMessage
import com.onion.model.ChatRole
import com.onion.model.ChatSessionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextStrategyTest {
    @Test
    fun structuredModesUseAnIsolatedHighOutputPolicyWithoutToolCallConstraint() {
        val strategy = ChatSessionMode.SVG_IMAGE.contextStrategy()

        assertTrue(strategy is ContextStrategy.StructuredGeneration)
        assertTrue(strategy.prefillPrefaceOnInit)
        assertFalse(strategy.enableConstrainedDecoding)
        assertEquals(4_096, strategy.maxOutputTokens)
        assertTrue(strategy.filterChannelContent.not())
    }

    @Test
    fun lottieSceneGenerationUsesCompactOutputBudget() {
        val strategy = ChatSessionMode.LOTTIE_ANIMATION.contextStrategy()

        assertTrue(strategy is ContextStrategy.StructuredGeneration)
        assertEquals(1_536, strategy.maxOutputTokens)
        assertFalse(strategy.enableConstrainedDecoding)
    }

    @Test
    fun budgetPolicyReservesOutputBeforeTheHardBoundary() {
        val snapshot = ContextBudgetPolicy.inspect(
            usedTokens = 6_000,
            capacityTokens = 8_192,
            incomingPrompt = "Please continue this analysis.",
            strategy = ContextStrategy.ChatSession(maxOutputTokens = 1_024),
        )

        assertEquals(ContextBudgetLevel.COMPACTION_REQUIRED, snapshot.level)
        assertTrue(snapshot.projectedTokens > snapshot.usedTokens)
        assertTrue(snapshot.reservedOutputTokens == 1_024)
    }

    @Test
    fun compactionKeepsRecentTurnsAndAddsMemoryForOlderTurns() {
        val messages = buildList {
            repeat(4) { index ->
                add(ChatMessage.text("old user $index", ChatRole.USER))
                add(ChatMessage.text("old assistant $index", ChatRole.ASSISTANT))
            }
            add(ChatMessage.text("recent user", ChatRole.USER))
            add(ChatMessage.text("recent assistant", ChatRole.ASSISTANT))
        }

        val compacted = ContextTranscript.compact(messages, retainTurns = 1)
        val text = compacted.joinToString("\n") { it.toString() }

        assertTrue(text.contains("Conversation memory"))
        assertTrue(text.contains("recent user"))
        assertTrue(text.contains("recent assistant"))
        assertTrue(compacted.size < ContextTranscript.toLmMessages(messages).size)
    }

    @Test
    fun structuredGenerationDoesNotReplayDurableHistory() {
        val messages = listOf(
            ChatMessage.text("draw a circle", ChatRole.USER),
            ChatMessage.text("{\"type\":\"svg_image\"}", ChatRole.ASSISTANT),
        )

        assertTrue(
            ContextCoordinator.initialMessages(
                ChatSessionMode.SVG_IMAGE,
                messages,
            ).isEmpty(),
        )
    }

    @Test
    fun conversationRebuildDoesNotReplayTheInFlightPrompt() {
        val messages = listOf(
            ChatMessage.text("completed prompt", ChatRole.USER),
            ChatMessage.text("completed answer", ChatRole.ASSISTANT),
            ChatMessage.text(
                "current prompt",
                ChatRole.USER,
                metadata = mapOf("turn_id" to "turn-current"),
            ),
            ChatMessage.text(
                "",
                ChatRole.ASSISTANT,
                metadata = mapOf(
                    "turn_id" to "turn-current",
                    "is_generating" to "true",
                ),
            ),
        )

        val replay = ContextCoordinator.initialMessages(ChatSessionMode.DEFAULT, messages)
        val replayText = replay.joinToString("\n")

        assertTrue(replayText.contains("completed prompt"))
        assertTrue(replayText.contains("completed answer"))
        assertFalse(replayText.contains("current prompt"))
    }

    @Test
    fun failedTurnIsExcludedFromConversationRebuild() {
        val messages = listOf(
            ChatMessage.text(
                "failed prompt",
                ChatRole.USER,
                metadata = mapOf("turn_id" to "turn-failed"),
            ),
            ChatMessage.text(
                "The model returned no usable response",
                ChatRole.ASSISTANT,
                metadata = mapOf(
                    "turn_id" to "turn-failed",
                    "exclude_from_context" to "true",
                ),
            ),
        )

        assertTrue(
            ContextCoordinator.initialMessages(ChatSessionMode.DEFAULT, messages).isEmpty()
        )
    }

    @Test
    fun punctuationOnlyTerminalOutputIsRejected() {
        assertFalse(GenerationOutputPolicy.hasUsableContent("{\n"))
        assertFalse(GenerationOutputPolicy.hasUsableContent("[]"))
        assertTrue(
            GenerationOutputPolicy.hasUsableContent("{\"type\":\"svg_image\"}")
        )
    }
}
