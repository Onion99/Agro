package com.onion.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("tool")
    TOOL
}

@Serializable
enum class ChatSessionMode {
    @SerialName("default")
    DEFAULT,

    @SerialName("svg_image")
    SVG_IMAGE,

    @SerialName("chiptune_bgm_mml")
    CHIPTUNE_BGM_MML,

    @SerialName("lottie_animation")
    LOTTIE_ANIMATION
}

data class ConversationContextState(
    val mode: ChatSessionMode = ChatSessionMode.DEFAULT,
    val systemInstruction: String = "",
    val isApplied: Boolean = false,
    val usedTokens: Int = 0,
    val maxTokens: Int = 0,
    val projectedTokens: Int = 0,
    val budgetRatio: Float = 0f,
    val budgetLevel: String = "UNKNOWN",
    val compactionCount: Int = 0,
)

@Serializable
data class PersistentToolCall(
    val name: String,
    val arguments: String,
    val createdAtMillis: Long
)

@Serializable
data class PersistentToolResponse(
    val name: String,
    val response: String,
    val createdAtMillis: Long
)
