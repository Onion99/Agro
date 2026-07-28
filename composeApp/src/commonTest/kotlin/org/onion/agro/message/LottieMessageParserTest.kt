package org.onion.agro.message

import com.onion.model.ChatMessageContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.onion.agro.lottie.LottieAnimationSpecParser

class LottieMessageParserTest {
    @Test
    fun parsesSpecAndBuildsRenderableLottieContent() {
        val result = LottieMessageParser.parseCompletedResponse(validSpecJson())

        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)
        assertEquals("Success Check", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(60, lottie.fps)
        assertEquals(1_200L, lottie.durationMs)
        assertTrue(lottie.json.contains("\"layers\""))
        assertTrue(lottie.json.contains("\"assets\":[]"))
        assertTrue(lottie.sourceSpecJson?.contains("lottie_animation_spec") == true)
    }

    @Test
    fun buildsSupportedKindsDeterministically() {
        val cases = listOf(
            "loading_spinner" to "spin_arc",
            "success_check" to "draw_then_pop",
            "progress_dots" to "stagger_bounce"
        )

        cases.forEach { (kind, style) ->
            val source = validSpecJson(
                kind = kind,
                style = style,
                loop = kind != "success_check"
            )
            val first = LottieAnimationSpecParser.parse(source)
            val second = LottieAnimationSpecParser.parse(source)

            assertEquals(first.json, second.json)
            assertTrue(first.json.contains("\"ty\":4"))
        }
    }

    @Test
    fun rejectsMarkdownWrappedSpec() {
        val result = LottieMessageParser.parseCompletedResponse(
            """
            ```json
            ${validSpecJson()}
            ```
            """.trimIndent()
        )

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("invalid_lottie_spec_json", unsupported.reason)
    }

    @Test
    fun rejectsFullLottieLayerTreeFromModel() {
        val result = LottieMessageParser.parseCompletedResponse(
            validSpecJson().replace(Regex("\\s*}\\s*$"), ",\"layers\":[]}")
        )

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("forbidden_lottie_spec_field", unsupported.reason)
    }

    @Test
    fun rejectsUnsupportedMotionStyleForKind() {
        val result = LottieMessageParser.parseCompletedResponse(
            validSpecJson(kind = "success_check", style = "spin_arc")
        )

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("unsupported_lottie_motion_style", unsupported.reason)
    }

    @Test
    fun rejectsOutOfBoundsDuration() {
        val result = LottieMessageParser.parseCompletedResponse(
            validSpecJson(durationMs = 3_001)
        )

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("invalid_lottie_duration", unsupported.reason)
    }

    private fun validSpecJson(
        kind: String = "success_check",
        style: String = "draw_then_pop",
        loop: Boolean = false,
        durationMs: Int = 1_200
    ): String {
        return """
            {
              "type": "lottie_animation_spec",
              "schemaVersion": 1,
              "title": "Success Check",
              "seed": 42,
              "canvas": {
                "width": 240,
                "height": 240,
                "background": "transparent"
              },
              "fps": 60,
              "durationMs": $durationMs,
              "loop": $loop,
              "kind": "$kind",
              "palette": {
                "primary": "#22C55E",
                "secondary": "#DCFCE7",
                "accent": "#FFFFFF"
              },
              "motion": {
                "style": "$style",
                "intensity": 0.72,
                "staggerMs": 120
              },
              "stroke": {
                "width": 10,
                "lineCap": "round"
              }
            }
        """.trimIndent()
    }
}
