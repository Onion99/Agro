package org.onion.agro.message

import com.onion.model.ChatMessageContent
import org.onion.agro.audio.BgmAudioFileStore
import org.onion.agro.audio.BgmParseException
import org.onion.agro.audio.ChiptuneBgmMmlParser
import org.onion.agro.audio.EightBitBgmRenderer

object ChiptuneBgmMessageParser {
    private const val CONTENT_TYPE = "chiptune_bgm_mml"

    fun parseCompletedResponse(response: String): ChatMessageContent {
        return try {
            val parsed = ChiptuneBgmMmlParser.parse(response)
            val rendered = EightBitBgmRenderer.render(parsed)
            val path = BgmAudioFileStore.write(
                title = parsed.spec.title,
                sourceSpecJson = response,
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
                sourceSpecJson = response
            )
        } catch (error: Exception) {
            ChatMessageContent.Unsupported(
                declaredType = ChiptuneBgmMmlParser.declaredType(response) ?: CONTENT_TYPE,
                rawPayload = response,
                reason = (error as? BgmParseException)?.reason ?: "bgm_render_failed"
            )
        }
    }
}
