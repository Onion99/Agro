package org.onion.agro.native.llm

/**
 * Lightweight, cross-platform token count estimator for LLM text.
 * Approximates BPE (SentencePiece / Byte-level BPE) tokenization for live streaming metrics:
 * - English/Latin: words (split on spaces/punctuation) + subwords (~1 token per ~4 chars for long words)
 * - CJK characters (Chinese, Japanese Kanji/Kana, Korean Hangul): ~1 token per character
 * - Numbers and punctuation: clustered symbols/punctuation count as individual tokens
 */
fun estimateTokenCount(text: String): Int {
    if (text.isBlank()) return 0
    var count = 0
    var inWord = false
    var wordCharCount = 0

    for (char in text) {
        val code = char.code
        // CJK Unified Ideographs, Hiragana, Katakana, Hangul Syllables
        val isCjk = (code in 0x4E00..0x9FFF) ||
                (code in 0x3400..0x4DBF) ||
                (code in 0x3040..0x30FF) ||
                (code in 0xAC00..0xD7AF)

        if (isCjk) {
            inWord = false
            wordCharCount = 0
            count += 1
        } else if (char.isWhitespace()) {
            inWord = false
            wordCharCount = 0
        } else if (char.isLetterOrDigit()) {
            if (!inWord) {
                count += 1
                inWord = true
                wordCharCount = 1
            } else {
                wordCharCount += 1
                // Long word subword heuristic: words longer than 7 characters split into subword tokens (~1 token / 4-5 chars)
                if (wordCharCount > 7) {
                    count += 1
                    wordCharCount = 1
                }
            }
        } else {
            // Punctuation / delimiter / symbol
            inWord = false
            wordCharCount = 0
            count += 1
        }
    }
    return count
}
