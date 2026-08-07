package org.onion.agro.message

import com.onion.model.ChatMessageContent
import org.onion.agro.lottie.LottieAnimationSpecParser
import org.onion.agro.lottie.LottieParseException

object LottieMessageParser {
    fun parseCompletedResponse(response: String): ChatMessageContent {
        return try {
            val parsed = LottieAnimationSpecParser.parse(response)
            ChatMessageContent.LottieAnimation(
                json = parsed.json,
                title = parsed.title.trim(),
                width = parsed.width,
                height = parsed.height,
                durationMs = parsed.durationMs,
                fps = parsed.fps,
                loop = parsed.loop,
                sourceSpecJson = response
            )
        } catch (error: Exception) {
            ChatMessageContent.Unsupported(
                declaredType = LottieAnimationSpecParser.declaredType(response)
                    ?: LottieAnimationSpecParser.CONTENT_TYPE,
                rawPayload = response,
                reason = (error as? LottieParseException)?.reason ?: "lottie_build_failed"
            )
        }
    }
}
