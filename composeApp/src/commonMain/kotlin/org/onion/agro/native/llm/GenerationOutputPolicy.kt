package org.onion.agro.native.llm

/** Guards the UI and durable transcript from terminal punctuation-only output. */
object GenerationOutputPolicy {
    fun hasUsableContent(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty() || normalized == "{}" || normalized == "[]") return false
        return normalized.any { character -> character.isLetterOrDigit() }
    }
}
