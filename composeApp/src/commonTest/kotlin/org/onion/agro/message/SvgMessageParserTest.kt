package org.onion.agro.message

import com.onion.model.ChatMessageContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SvgMessageParserTest {

    @Test
    fun parsesSafeSvgEnvelope() {
        val result = SvgMessageParser.parseCompletedResponse(
            """
            {"type":"svg_image","svg":"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 100'><rect width='200' height='100' fill='#336699'/></svg>"}
            """.trimIndent()
        )

        val svg = assertIs<ChatMessageContent.SvgImage>(
            result,
            "Expected a renderable SVG but received $result"
        )
        assertEquals(200f, svg.width)
        assertEquals(100f, svg.height)
    }

    @Test
    fun rejectsMarkdownWrappedEnvelope() {
        val result = SvgMessageParser.parseCompletedResponse(
            """
            ```json
            {"type":"svg_image","svg":"<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'></svg>"}
            ```
            """.trimIndent()
        )

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("invalid_svg_json", unsupported.reason)
    }

    @Test
    fun rejectsMalformedXml() {
        val result = SvgMessageParser.parseStoredSvg(
            "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><g></svg>"
        )

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("malformed_svg_xml", unsupported.reason)
    }

    @Test
    fun rejectsExecutableAndExternalContent() {
        val script = SvgMessageParser.parseStoredSvg(
            "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><script>alert(1)</script></svg>"
        )
        val link = SvgMessageParser.parseStoredSvg(
            "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><image href='https://example.com/a.png'/></svg>"
        )

        assertEquals(
            "forbidden_svg_element",
            assertIs<ChatMessageContent.Unsupported>(script).reason
        )
        assertEquals(
            "forbidden_svg_external_resource",
            assertIs<ChatMessageContent.Unsupported>(link).reason
        )
    }

    @Test
    fun rejectsOversizedSvg() {
        val oversizedSvg = buildString {
            append("<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'>")
            append(" ".repeat(1024 * 1024))
            append("</svg>")
        }

        val result = SvgMessageParser.parseStoredSvg(oversizedSvg)

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("svg_too_large", unsupported.reason)
    }
}
