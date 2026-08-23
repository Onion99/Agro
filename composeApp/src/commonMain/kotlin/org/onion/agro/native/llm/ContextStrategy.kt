package org.onion.agro.native.llm

import com.onion.model.ChatSessionMode
import kotlin.math.max

/**
 * Runtime policy for a LiteRT-LM conversation.
 *
 * The policy is deliberately data-only.  This keeps the device-specific JNI
 * layer dumb and gives the coordinator one place to decide how much context a
 * mode is allowed to consume.
 */
sealed interface ContextStrategy {
    val prefillPrefaceOnInit: Boolean
    val filterChannelContent: Boolean
    val maxOutputTokens: Int
    val enableConstrainedDecoding: Boolean

    data class ChatSession(
        override val prefillPrefaceOnInit: Boolean = true,
        override val filterChannelContent: Boolean = true,
        override val maxOutputTokens: Int = 1_024,
        override val enableConstrainedDecoding: Boolean = false,
        val historyRetainWindow: Int = 8,
        val autoSummarizeThresholdRatio: Float = 0.85f,
    ) : ContextStrategy

    data class StructuredGeneration(
        val schemaOrGrammar: String? = null,
        override val prefillPrefaceOnInit: Boolean = true,
        override val filterChannelContent: Boolean = false,
        override val maxOutputTokens: Int = 4_096,
        // The current JNI boolean enables model tool-call constraints. It does
        // not bind schemaOrGrammar to the request, so enabling it with the
        // structured slot's empty tool list can terminate output after "{".
        override val enableConstrainedDecoding: Boolean = false,
        val maxRetryCount: Int = 2,
    ) : ContextStrategy
}

fun ChatSessionMode.contextStrategy(): ContextStrategy = when (this) {
    ChatSessionMode.DEFAULT -> ContextStrategy.ChatSession()
    ChatSessionMode.SVG_IMAGE,
    ChatSessionMode.CHIPTUNE_BGM_MML,
    ChatSessionMode.LOTTIE_ANIMATION,
    -> ContextStrategy.StructuredGeneration()
}

enum class ContextBudgetLevel {
    UNKNOWN,
    SAFE,
    WARNING,
    COMPACTION_REQUIRED,
    OVERFLOW,
}

data class ContextBudgetSnapshot(
    val usedTokens: Int,
    val capacityTokens: Int?,
    val incomingTokens: Int,
    val reservedOutputTokens: Int,
    val projectedTokens: Int,
    val level: ContextBudgetLevel,
    val didCompact: Boolean = false,
) {
    val ratio: Float
        get() = capacityTokens
            ?.takeIf { it > 0 }
            ?.let { projectedTokens.toFloat() / it }
            ?: 0f

    val isUsable: Boolean
        get() = level != ContextBudgetLevel.OVERFLOW
}

object ContextBudgetPolicy {
    private const val WARNING_RATIO = 0.70f

    /** A conservative, allocation-free estimate used before native token data exists. */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var nonAscii = 0
        var ascii = 0
        text.forEach { character ->
            if (character.code > 0x7f) nonAscii++ else ascii++
        }
        return max(1, nonAscii + (ascii + 3) / 4)
    }

    fun inspect(
        usedTokens: Int?,
        capacityTokens: Int?,
        incomingPrompt: String,
        strategy: ContextStrategy,
    ): ContextBudgetSnapshot {
        val incomingTokens = estimateTokens(incomingPrompt)
        val used = max(0, usedTokens ?: 0)
        val projected = used + incomingTokens + strategy.maxOutputTokens
        val level = when {
            capacityTokens == null || capacityTokens <= 0 -> ContextBudgetLevel.UNKNOWN
            projected > capacityTokens -> ContextBudgetLevel.OVERFLOW
            projected.toFloat() / capacityTokens >= 0.85f -> ContextBudgetLevel.COMPACTION_REQUIRED
            projected.toFloat() / capacityTokens >= WARNING_RATIO -> ContextBudgetLevel.WARNING
            else -> ContextBudgetLevel.SAFE
        }
        return ContextBudgetSnapshot(
            usedTokens = used,
            capacityTokens = capacityTokens,
            incomingTokens = incomingTokens,
            reservedOutputTokens = strategy.maxOutputTokens,
            projectedTokens = projected,
            level = level,
        )
    }
}
