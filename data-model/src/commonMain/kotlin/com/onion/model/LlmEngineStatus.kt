package com.onion.model

/**
 * User-observable lifecycle of the local language-model runtime.
 *
 * This state is intentionally separate from persisted chat sessions: it
 * describes the currently attached native engine and conversation only.
 */
enum class LlmEngineStatus {
    UNINITIALIZED,
    INITIALIZING,
    APPLYING_CONTEXT,
    READY,
    GENERATING,
    ERROR,
}
