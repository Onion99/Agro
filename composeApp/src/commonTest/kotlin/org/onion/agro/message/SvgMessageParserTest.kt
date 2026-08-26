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
        val multilineCssUrl = SvgMessageParser.parseStoredSvg(
            """
            <svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'>
                <rect width='10' height='10' style='fill:url(
                    https://example.com/fill.svg
                )'/>
            </svg>
            """.trimIndent()
        )

        assertEquals(
            "forbidden_svg_element",
            assertIs<ChatMessageContent.Unsupported>(script).reason
        )
        assertEquals(
            "forbidden_svg_external_resource",
            assertIs<ChatMessageContent.Unsupported>(link).reason
        )
        assertEquals(
            "forbidden_svg_external_resource",
            assertIs<ChatMessageContent.Unsupported>(multilineCssUrl).reason
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

    @Test
    fun healsGemmaMalformedSvgResponse() {
        val rawResponse = """
            {
              "type": "svg_image",
              "svg": "<svg xmlns='http://www.w3.org/2000/svg' width='512' height='512' viewBox='0 0 512 512' fill='none'><defs><linearGradient id='gradient1' x1='0%' y1='0%' x2='100%' y2='100%'><stop offset='0%' style='stop-color:#000044;stop-opacity:1' /><stop offset='100%' style='stop-color:#1a0033;stop-opacity:1' /></linearGradient><filter id='glow'><feGaussianBlur stdDeviation='4' result='blurOut' in2='SourceGraphic' result='blurOut'/><feMergeIn><feMergeNode in='blurOut'/><feMergeNode in='SourceGraphic'/></feMergeNode></feMergeIn></filter></defs><rect width='512' height='512' fill='#000011'/><g transform='translate(256, 256)'><g id='chip_core'><rect x='-80' y='-80' width='160' height='160' fill='url(#gradient1)'/><path d='M-40 -40 L40 40 Z' filter='url=#glow'/></g></g></g><g id='extra'><circle cx='10' cy='10' r='5'></g></g></svg>"
            }
        """.trimIndent()

        val result = SvgMessageParser.parseCompletedResponse(rawResponse)
        val svg = assertIs<ChatMessageContent.SvgImage>(
            result,
            "Expected auto-healed SvgImage but got $result"
        )
        assertEquals(512f, svg.width)
        assertEquals(512f, svg.height)
    }
}

