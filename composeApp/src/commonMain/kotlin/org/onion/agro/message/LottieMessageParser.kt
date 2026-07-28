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
                title = parsed.spec.title.trim(),
                width = parsed.spec.canvas.width,
                height = parsed.spec.canvas.height,
                durationMs = parsed.spec.durationMs,
                fps = parsed.spec.fps,
                loop = parsed.spec.loop,
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
