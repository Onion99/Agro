package org.onion.agro.native.llm

import com.google.ai.edge.litertlm.SamplerConfig
import com.onion.model.ChatMessage
import com.onion.model.ChatSessionMode

/**
 * Owns the lifetime and identity of model conversations.
 *
 * Chat and structured generation never share a KV cache.  A slot is replaced
 * when its durable session, mode, or system contract changes; this makes
 * switching sessions deterministic and makes history replay explicit.
 */
class ContextCoordinator {
    private enum class SlotKind { CHAT, STRUCTURED }

    private data class Slot(
        val key: String,
        val mode: ChatSessionMode,
        val systemInstruction: String,
        val strategy: ContextStrategy,
        val conversation: LmConversation,
    )

    private var engine: LmEngine? = null
    private var chatSlot: Slot? = null
    private var structuredSlot: Slot? = null
    private var activeKind: SlotKind? = null

    fun isEngineReady(): Boolean = engine?.isInitialized() == true

    fun attachEngine(newEngine: LmEngine) {
        closeAll()
        engine = newEngine
    }

    fun currentConversation(): LmConversation? = when (activeKind) {
        SlotKind.CHAT -> chatSlot?.conversation
        SlotKind.STRUCTURED -> structuredSlot?.conversation
        null -> null
    }

    fun currentEngine(): LmEngine? = engine

    suspend fun openConversation(
        key: String,
        mode: ChatSessionMode,
        systemInstruction: String,
        toolsJson: String,
        initialMessages: List<Message>,
        samplerConfig: SamplerConfig,
        forceRecreate: Boolean = false,
    ): LmConversation {
        val currentEngine = checkNotNull(engine) { "LM engine is not attached." }
        val strategy = mode.contextStrategy()
        val kind = if (mode == ChatSessionMode.DEFAULT) SlotKind.CHAT else SlotKind.STRUCTURED
        val current = slot(kind)
        if (!forceRecreate && current != null && current.key == key &&
            current.mode == mode && current.systemInstruction == systemInstruction &&
            current.strategy == strategy
        ) {
            activeKind = kind
            return current.conversation
        }

        current?.conversation?.close()
        val conversation = currentEngine.createConversation(
            systemInstruction = systemInstruction,
            initialMessages = initialMessages,
            toolsDescriptionJsonString = if (kind == SlotKind.CHAT) toolsJson else "[]",
            strategy = strategy,
            samplerConfig = samplerConfig,
        )
        val newSlot = Slot(key, mode, systemInstruction, strategy, conversation)
        setSlot(kind, newSlot)
        activeKind = kind
        return conversation
    }

    fun onModeSwitched(targetMode: ChatSessionMode) {
        if (targetMode == ChatSessionMode.DEFAULT) {
            structuredSlot?.conversation?.close()
            structuredSlot = null
            activeKind = SlotKind.CHAT.takeIf { chatSlot != null }
        }
    }

    fun closeActiveConversation() {
        when (activeKind) {
            SlotKind.CHAT -> {
                chatSlot?.conversation?.close()
                chatSlot = null
            }
            SlotKind.STRUCTURED -> {
                structuredSlot?.conversation?.close()
                structuredSlot = null
            }
            null -> Unit
        }
        activeKind = null
    }

    fun cancelActive() {
        currentConversation()?.cancelProcess()
    }

    fun closeAll() {
        chatSlot?.conversation?.close()
        structuredSlot?.conversation?.close()
        chatSlot = null
        structuredSlot = null
        activeKind = null
        engine?.close()
        engine = null
    }

    private fun slot(kind: SlotKind): Slot? = when (kind) {
        SlotKind.CHAT -> chatSlot
        SlotKind.STRUCTURED -> structuredSlot
    }

    private fun setSlot(kind: SlotKind, value: Slot) {
        when (kind) {
            SlotKind.CHAT -> chatSlot = value
            SlotKind.STRUCTURED -> structuredSlot = value
        }
    }

    companion object {
        /**
         * Structured generation sessions are intentionally stateless between
         * requests. Durable output stays in UI/history, but is not replayed
         * into the constrained-decoding KV cache.
         */
        fun initialMessages(mode: ChatSessionMode, messages: List<ChatMessage>): List<Message> =
            if (mode == ChatSessionMode.DEFAULT) replay(messages) else emptyList()

        fun replay(messages: List<ChatMessage>): List<Message> =
            ContextTranscript.toLmMessages(messages)

        fun compact(messages: List<ChatMessage>, retainTurns: Int): List<Message> =
            ContextTranscript.compact(messages, retainTurns)
    }
}
