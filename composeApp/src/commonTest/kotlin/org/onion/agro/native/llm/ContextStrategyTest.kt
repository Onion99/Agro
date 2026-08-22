package org.onion.agro.native.llm

import com.onion.model.ChatMessage
import com.onion.model.ChatRole
import com.onion.model.ChatSessionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextStrategyTest {
    @Test
    fun structuredModesUseAnIsolatedHighOutputPolicy() {
        val strategy = ChatSessionMode.SVG_IMAGE.contextStrategy()

        assertTrue(strategy is ContextStrategy.StructuredGeneration)
        assertTrue(strategy.prefillPrefaceOnInit)
        assertTrue(strategy.enableConstrainedDecoding)
        assertEquals(4_096, strategy.maxOutputTokens)
        assertTrue(strategy.filterChannelContent.not())
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
}
