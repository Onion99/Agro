package org.onion.agro.native.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenEstimatorTest {

    @Test
    fun testEmptyAndWhitespace() {
        assertEquals(0, estimateTokenCount(""))
        assertEquals(0, estimateTokenCount("   "))
    }

    @Test
    fun testEnglishWordsAndSubwords() {
        // "Hello world" -> 2 words -> 2 tokens
        assertEquals(2, estimateTokenCount("Hello world"))

        // Short words
        val shortSentence = "This is a simple test"
        assertEquals(5, estimateTokenCount(shortSentence))

        // Long words get subword split (heuristic: > 4 chars)
        // "relativity" (10 chars) -> "rela", "tivi", "ty" -> 3 tokens
        val longWord = "relativity"
        assertTrue(estimateTokenCount(longWord) >= 2)
    }

    @Test
    fun testCjkCharacters() {
        // CJK characters: 1 character ~ 1 token
        val chinese = "你好世界"
        assertEquals(4, estimateTokenCount(chinese))

        val sentence = "相对论是物理学理论。"
        // 9 characters + 1 punctuation = 10 tokens
        assertEquals(10, estimateTokenCount(sentence))
    }

    @Test
    fun testMixedTextAndPunctuation() {
        val mixed = "LiteRT-LM 是一款 on-device 模型！"
        val count = estimateTokenCount(mixed)
        assertTrue(count in 8..15)
    }
}
