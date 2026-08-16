package org.onion.agro.message

import com.onion.model.ChatMessageContent
import org.onion.agro.audio.BgmAudioFileStore
import org.onion.agro.audio.BgmParseException
import org.onion.agro.audio.ChiptuneBgmMmlParser
import org.onion.agro.audio.EightBitBgmRenderer

object ChiptuneBgmMessageParser {
    private const val CONTENT_TYPE = "chiptune_bgm_mml"

    fun parseCompletedResponse(response: String): ChatMessageContent {
        val sanitized = sanitizeBgmPayload(response)
        return try {
            val parsed = ChiptuneBgmMmlParser.parse(sanitized)
            val rendered = EightBitBgmRenderer.render(parsed)
            val path = BgmAudioFileStore.write(
                title = parsed.spec.title,
                sourceSpecJson = sanitized,
                wavBytes = rendered.wavBytes
            )
            ChatMessageContent.Audio(
                path = path,
                mimeType = "audio/wav",
                title = parsed.spec.title,
                durationMs = rendered.durationMs,
                sampleRate = rendered.sampleRate,
                bitDepth = rendered.bitDepth,
                bpm = parsed.spec.bpm,
                loopBars = parsed.spec.loopBars,
                loopEndMs = rendered.durationMs,
                sourceSpecJson = sanitized
            )
        } catch (error: Exception) {
            ChatMessageContent.Unsupported(
                declaredType = ChiptuneBgmMmlParser.declaredType(response) ?: CONTENT_TYPE,
                rawPayload = response,
                reason = (error as? BgmParseException)?.reason ?: "bgm_render_failed"
            )
        }
    }

    fun sanitizeBgmPayload(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return s

        // 1. Extract JSON object from Markdown codeblock or surrounding commentary
        val firstBrace = s.indexOf('{')
        val lastBrace = s.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1)
        }

        // 2. Strip single-line comments: // ...
        s = LINE_COMMENT_REGEX.replace(s, "")

        // 3. Remove trailing commas before } or ]
        s = TRAILING_COMMA_REGEX.replace(s, "$1")

        // 4. Auto-align track tempo T<num> with top-level bpm
        val bpmMatch = BPM_REGEX.find(s)
        if (bpmMatch != null) {
            val bpm = bpmMatch.groupValues[1]
            s = TRACK_MML_TEMPO_REGEX.replace(s) { match ->
                val prefix = match.groupValues[1]
                val suffix = match.groupValues[2]
                "${prefix}T$bpm$suffix"
            }
        }

        // 5. Normalize repeat block notation [ ... ] * 2 -> [ ... ]x2
        s = REPEAT_SYNTAX_REGEX.replace(s) { match ->
            val inner = match.groupValues[1]
            val count = match.groupValues[2]
            "[$inner]x$count"
        }

        return s
    }

    private val LINE_COMMENT_REGEX = Regex("(?m)//.*$")
    private val TRAILING_COMMA_REGEX = Regex(",\\s*([}\\]])")
    private val BPM_REGEX = Regex("\"bpm\"\\s*:\\s*(\\d+)")
    private val TRACK_MML_TEMPO_REGEX = Regex("(\"mml\"\\s*:\\s*\"[^\"]*?)\\bT\\d+([^\"]*\")")
    private val REPEAT_SYNTAX_REGEX = Regex("\\[([^\\[\\]]+)\\]\\s*[*xX]\\s*(\\d+)")
}

