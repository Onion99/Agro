package org.onion.agro.message

import com.onion.model.ChatMessageContent
import io.github.alexzhirkevich.compottie.LottieComposition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LottieMessageParserTest {
    @Test
    fun compilesCompactGemmaSceneAndCompottieAcceptsIt() {
        val payload = fallingDropsScene()

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertEquals("Falling Water Drops", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(30, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
        assertEquals(payload, lottie.sourceSpecJson)
        assertTrue(lottie.json.contains("\"ty\":4"), lottie.json)
        assertTrue(lottie.json.contains("\"a\":1"), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"el\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"fl\""), lottie.json)
        assertTrue(lottie.json.contains("\"e\":[75,260,0]"), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun compilesStrokedPathTrimSceneAndCompottieAcceptsIt() {
        val payload = """
            {
              "type": "lottie_scene",
              "schemaVersion": 1,
              "title": "Success Check",
              "duration": 2,
              "loop": false,
              "objects": [
                {
                  "name": "Check",
                  "shape": "path",
                  "position": [120, 120],
                  "vertices": [[-45, 0], [-12, 35], [52, -38]],
                  "closed": false,
                  "stroke": "#22C55E",
                  "strokeWidth": 12,
                  "motion": { "trim": [[0, 0], [0.7, 100], [1, 100]] }
                }
              ]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertFalse(lottie.loop)
        assertTrue(lottie.json.contains("\"ty\":\"sh\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"st\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"tm\""), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun extractsFencedSceneWithBracesInsideStringsInOnePass() {
        val payload = """
            Scene follows:
            ```json
            {
              "type": "lottie_scene",
              "schemaVersion": 1,
              "title": "Pulse {safe}",
              "duration": 1,
              "objects": [
                {
                  "name": "Dot",
                  "shape": "ellipse",
                  "position": [120, 120],
                  "size": [40, 40],
                  "fill": "#38BDF8",
                  "motion": { "scale": [[0, 90], [0.5, 110], [1, 90]] }
                }
              ]
            }
            ```
            Done.
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertEquals("Pulse {safe}", lottie.title)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun dropsUnknownNativeFieldsThroughClosedSceneCompilation() {
        val payload = """
            {
              "type": "lottie_scene",
              "schemaVersion": 1,
              "title": "Closed Projection",
              "duration": 1,
              "assets": [{"u":"https://example.invalid/image.png"}],
              "layers": [{"ty":2}],
              "objects": [
                {
                  "name": "Dot",
                  "shape": "ellipse",
                  "position": [120, 120],
                  "size": [40, 40],
                  "fill": "#38BDF8",
                  "script": "ignored()",
                  "motion": { "opacity": [[0, 100], [0.5, 50], [1, 100]] }
                }
              ]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertTrue(lottie.json.contains("\"assets\":[]"), lottie.json)
        assertFalse(lottie.json.contains("https://"), lottie.json)
        assertFalse(lottie.json.contains("script"), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun addsFallbackPulseWhenSceneIsStatic() {
        val result = LottieMessageParser.parseCompletedResponse(
            """
                {
                  "type": "lottie_scene",
                  "schemaVersion": 1,
                  "title": "Static Dot",
                  "duration": 1,
                  "objects": [
                    {
                      "name": "Dot",
                      "shape": "ellipse",
                      "position": [120, 120],
                      "size": [40, 40],
                      "fill": "#38BDF8"
                    }
                  ]
                }
            """.trimIndent(),
        )
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertTrue(lottie.json.contains("\"s\":[96,96,100]"), lottie.json)
        assertTrue(lottie.json.contains("\"e\":[104,104,100]"), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun rejectsLegacyIntentAndPreservesItsDeclaredType() {
        val result = LottieMessageParser.parseCompletedResponse(
            """
                {
                  "type": "lottie_animation_spec",
                  "schemaVersion": 1,
                  "kind": "success_check"
                }
            """.trimIndent(),
        )
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("lottie_animation_spec", unsupported.declaredType)
        assertEquals("unexpected_content_type", unsupported.reason)
    }

    @Test
    fun rejectsNativeLottieResponseAfterSceneProtocolMigration() {
        val result = LottieMessageParser.parseCompletedResponse(
            """
                {
                  "v": "5.8.4",
                  "fr": 30,
                  "ip": 0,
                  "op": 60,
                  "layers": []
                }
            """.trimIndent(),
        )
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("lottie_scene", unsupported.declaredType)
        assertEquals("unexpected_content_type", unsupported.reason)
    }

    @Test
    fun rejectsMalformedJsonWithoutLegacyRepairPasses() {
        val result = LottieMessageParser.parseCompletedResponse(
            """{type:"lottie_scene",schemaVersion:1,objects:[]}""",
        )
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("lottie_scene", unsupported.declaredType)
        assertEquals("invalid_lottie_json", unsupported.reason)
    }

    @Test
    fun rejectsUnsupportedSceneSchemaVersion() {
        val result = LottieMessageParser.parseCompletedResponse(
            """
                {
                  "type": "lottie_scene",
                  "schemaVersion": 2,
                  "objects": [{"shape":"ellipse"}]
                }
            """.trimIndent(),
        )
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("lottie_scene", unsupported.declaredType)
        assertEquals("unsupported_schema_version", unsupported.reason)
    }

    @Test
    fun rejectsUtf8PayloadOverLimitWithoutByteArrayConversion() {
        val oversized = """
            {
              "type": "lottie_scene",
              "schemaVersion": 1,
              "title": "${"水".repeat(90_000)}",
              "objects": [{"shape":"ellipse"}]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(oversized)
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("lottie_scene", unsupported.declaredType)
        assertEquals("lottie_json_too_large", unsupported.reason)
    }

    private fun fallingDropsScene(): String = """
        {
          "type": "lottie_scene",
          "schemaVersion": 1,
          "title": "Falling Water Drops",
          "duration": 2,
          "loop": true,
          "objects": [
            {
              "name": "Drop 1",
              "shape": "ellipse",
              "position": [75, 20],
              "size": [12, 34],
              "fill": "#38BDF8",
              "motion": {
                "position": [[0, 75, -20], [1, 75, 260]],
                "opacity": [[0, 0], [0.12, 100], [0.82, 100], [1, 0]]
              }
            },
            {
              "name": "Drop 2",
              "shape": "ellipse",
              "position": [165, 20],
              "size": [10, 28],
              "fill": "#60A5FA",
              "motion": {
                "position": [[0, 165, -45], [0.22, 165, -20], [1, 165, 260]],
                "opacity": [[0, 0], [0.22, 0], [0.32, 100], [0.86, 100], [1, 0]]
              }
            }
          ]
        }
    """.trimIndent()
}
