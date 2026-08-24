package org.onion.agro.message

import com.onion.model.ChatMessageContent
import org.onion.agro.lottie.LottieParseException
import org.onion.agro.lottie.LottieSceneResponseParser

object LottieMessageParser {
    fun parseCompletedResponse(response: String): ChatMessageContent {
        return try {
            val parsed = LottieSceneResponseParser.parse(response)
            ChatMessageContent.LottieAnimation(
                json = parsed.json,
                title = parsed.title,
                width = parsed.width,
                height = parsed.height,
                durationMs = parsed.durationMs,
                fps = parsed.fps,
                loop = parsed.loop,
                sourceSpecJson = response,
            )
        } catch (error: Exception) {
            val lottieError = error as? LottieParseException
            ChatMessageContent.Unsupported(
                declaredType = lottieError?.declaredType
                    ?: LottieSceneResponseParser.CONTENT_TYPE,
                rawPayload = response,
                reason = lottieError?.reason ?: "lottie_parse_failed",
            )
        }
    }
}
