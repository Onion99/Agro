package org.onion.agro.message

import com.onion.model.ChatMessageContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LottieMessageParserTest {
    @Test
    fun rejectsLegacyIntentSpecWithoutLocalBuilder() {
        val result = LottieMessageParser.parseCompletedResponse(legacyIntentSpecJson())

        val unsupported = assertIs<ChatMessageContent.Unsupported>(result)
        assertEquals("unexpected_content_type", unsupported.reason)
    }

    @Test
    fun parsesModelProducedMinimalNativeLottieJson() {
        val result = LottieMessageParser.parseCompletedResponse(minimalNativeLottieJson())

        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)
        assertEquals("Breathing Circle", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(30, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
        assertTrue(lottie.json.contains("\"ty\":\"el\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"fl\""), lottie.json)
    }

    @Test
    fun parsesNativeLottieJsonSuccessfully() {
        val nativeJson = """
            {
              "v": "5.7.4",
              "fr": 60,
              "ip": 0,
              "op": 120,
              "w": 240,
              "h": 240,
              "nm": "Rocket Launch",
              "ddd": 0,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Rocket Body",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 100 },
                    "r": { "a": 0, "k": 0 },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 0, "k": [100, 100, 100] }
                  },
                  "ao": 0,
                  "shapes": [],
                  "ip": 0,
                  "op": 120,
                  "st": 0,
                  "bm": 0
                }
              ]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(nativeJson)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)
        assertEquals("Rocket Launch", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(60, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
        assertTrue(lottie.json.contains("Rocket Launch"))
    }

    @Test
    fun repairsAndParsesMalformedNativeLottieJson() {
        val malformedJson = """
            {
              "v": "5.7.4",
              "fr": 60,
              "ip": 0,
              "op": 120,
              "w": 240,
              "h": 240,
              "nm": "Dynamic Water Ripple Flow",
              "ddd": 0,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Ripple Effect",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 1000 },
                    "r": { "a": 0, "k": 0 },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 0, "k": [100, 100, 100] }
                  },
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Waveform",
                      "it": [
                        {
                          "ty": "fl",
                          "c": { "rgba(0, 0, 250, 0.7)" },
                          "o": { "a": 0, "k": 1000 }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(malformedJson)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)
        assertEquals("Dynamic Water Ripple Flow", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(60, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
        // Verify opacity 1000 was clamped to 100
        assertTrue(lottie.json.contains("\"o\":{\"a\":0,\"k\":100}") || lottie.json.contains("\"o\":{\"a\":0,\"k\":100.0}"))
    }

    @Test
    fun repairsCosmicBurstNestedShapeAndSpacedNumberJson() {
        val rawInput = """
            {
              "v": "5.7.4",
              "fr": 60,
              "ip": 0,
              "op": 1240,
              "w": 2400,
              "h": 2 400,
              "nm": "Cosmic Energy Burst",
              "ddd": 0,
              "assets": [],
              "layers": [  {  "ddd": 0,  "ind": 1,  "ty": 4,  "nm": "Background Glow",  "sr": 1,  "ks": { "o": { "a": 0, "k": 10 }, "r": { "a": 0, "k": 0 }, "p": { "a": 0, "k": [1200,120,0] }, "a": { "a": 0, "k": [0,0,0] }, "s": { "a": 0, "k": [10,1,1] } }, "ao": 0, "shapes": [{ "ty": "gr", "nm": "Sphere", "it": [{ "ty": "el", "s": { "a": 0, "k": [1200,120] },"p": { "a": 0, "k": [0,0,0] },"fl": { "c": { "a": 0, "k": [0.1,0.05,1,1] },"o": { "a": 0, "k":1000 },"tr": { "p": { "a": 0, "k": [0,0] }, "a": { "a":0, "k": [0,0] }, "s": { "a": 0, "k": [1,1] }, "r": { "a": 0, "k":0 }, "o": { "a": 0, "k": 1000 } } } ]}] },"ip": 0, "op": 240, "st": 0, "bm": 0  },  {  "ddd": 0,  "ind": 2,  "ty": 4,  "nm": "Core Pulse",  "sr": 1,  "ks": { "o": { "a": 0, "k": 1000 }, "r": { "a": 1, "k": [ { "t": 0, "s": [0] }, { "t": 24, "s": [360] } ] }, "p": { "a": 0, "k": [1200,120,0] }, "a": { "a": 0, "k": [0,0,0] }, "s": { "a": 0, "k": [1,1,1] } }, "ao": 0, "shapes": [{ "ty": "gr", "nm": "Pulse", "it": [{ "ty": "el", "s": { "a": 0, "k": [50,5] }, "p": { "a": 0, "k": [0,0] }, "fl": { "c": { "a": 0, "k": [1,0.1,0,1] }, "o": { "a": 0, "k":1000 }, "tr": { "p": { "a": 0, "k": [0,0] }, "a": { "a": 0, "k": [0,0] }, "s": { "a": 0, "k": [1,1] }, "r": { "a": 0, "k": 0 }, "o": { "a": 0, "k":100 } } } } ]}, "ip": 0, "op": 24, "st": 0, "bm": 0  }]}
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(rawInput)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)
        assertEquals("Cosmic Energy Burst", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(60, lottie.fps)
        assertTrue(lottie.durationMs in 1000L..4000L)
        // Verify nested "fl" was extracted as standalone shape item with "ty":"fl"
        assertTrue(lottie.json.contains("\"ty\":\"fl\""))
        // Verify layer title is intact
        assertTrue(lottie.json.contains("Background Glow"))
    }

    @Test
    fun repairsCosmicNebulaSwirlCorruptedJson() {
        val rawInput = """
            {
              "v": "5.7.4",
              "fr": 60,
              "ip": 0,
              "op": 120,
              "w": 240,
              "h": 240,
              "nm": "Cosmic Nebula Swirl",
              "ddd": 0,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Nebula Core",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 100 },
                    "r": { "a": 1, "k": [ { "t": 0, "s": [0] }, { "t": 120, "s": [360] } ] },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 0, "k": [100, 100, 100] }
                  },
                  "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Swirl Shape",
                      "it": [
                        {
                          "ty": "el",
                          "s": { "a": 0, "k": [150, 150] },
                          "p": { "a": 0, "k": [0, 0] }
                        },
                        {
                          "ty": "fl",
                          "c": { "a": 0, "k": [0.0, 0.4, 0.8, 1] },
                          "o": { "a": 0, "k": 100 },
                          "s": { "a": 0, "k": [100, 100] },
                          "l": { "a": 0, "k": [0, 0] }
                        },
                        {
                          "ty": "tr",
                          "p": { "a": 0, "k": [0, 0] },
                          "a": { "a": 0, "k": [0, 0] },
                          "s": { "a": 0, "k": [100, 100] },
                          "r": { "a": 0, "k": 0 },
                          "o": { "a": 0, "k": 100 }
                        }
                      ]
                    }
                  ],
                  "ip": 0,
                  "op": 120,
                  "st": 0,
                  "bm": 0
                },
                {
                  "ddd": 0,
                  "ind": 2,
                  "ty": 4,
                  "nm": "Pulsing Stars",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 100 },
                    "r": { "a": 0, "k": [0] },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 1, "k": [ { "t": 0, "s": [100] }, { "t": 60, "s": [150] }, { "t": 120, "s": [10] } ] }
                  },
                  "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Star Group",
                      "it": [
                        {
                          "ty": "el",
                          "s": { "a": 0, "k": [5, 5] },
                          "p": { "a": 0, "k": [0, 0] }
                        },
                        {
                          "ty": "fl",
                          "c": { "a": 0, "k": [0.9, 0.9, 1, 1] },
                          "o": { "a":0, "k": 1000 },
                          "s": { "a": 0, "k": [10, 1] },
                          "l": { "a": 0, "k": [0,0] } }
                        }
                      ]
                    }
                  ],
                  "ip": 0,
                {
                  "ddd": 0,
                  "ind": 3,
                  "ty": 4,
                  "nm": "Energy Trails",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 1000 },
                    "r": { "a": 0, "k": [0] },
                    "p": { "a": 0, "k": [1200, 1200, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 0, "k": [1000, 1000, 1000] }
                  },
                  "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",          , "nm": "Trail", "it": [
                        {          "ty": "el",          , "s": { "a": 0, "k": [40, 40] }, "p": { "a": 0, "k": [0, 0] } }, "fl": { "c": { "a": 0, "k": [0.0, 0.6, 1, 1] }, "o": { "a": 0, "k": 1000 }, "s": { "a": 0, "k": [1000, 1000] } } }
                      ]}
                    ]
                  },
                  "ip": 0,
                  "op": 1200,
                  "st": 0,
                  "bm": 0
                }
              ]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(rawInput)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)
        assertEquals("Cosmic Nebula Swirl", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(60, lottie.fps)
        assertTrue(lottie.durationMs in 1000L..4000L)
    }

    private fun legacyIntentSpecJson(): String {
        return """
            {
              "type": "lottie_animation_spec",
              "schemaVersion": 1,
              "title": "Success Check",
              "canvas": {
                "width": 240,
                "height": 240,
                "background": "transparent"
              },
              "fps": 60,
              "durationMs": 1200,
              "loop": false,
              "kind": "success_check",
              "palette": {
                "primary": "#22C55E",
                "secondary": "#DCFCE7",
                "accent": "#FFFFFF"
              },
              "motion": {
                "style": "draw_then_pop",
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

    private fun minimalNativeLottieJson(): String {
        return """
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 240,
              "nm": "Breathing Circle",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Circle Layer",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 100 },
                    "r": { "a": 0, "k": 0 },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 1, "k": [
                      { "t": 0, "s": [90, 90, 100], "e": [100, 100, 100] },
                      { "t": 30, "s": [100, 100, 100], "e": [90, 90, 100] },
                      { "t": 60, "s": [90, 90, 100], "e": [90, 90, 100] }
                    ]
                  }
                },
                "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Circle Group",
                      "it": [
                        {
                          "ty": "el",
                          "nm": "Circle Path",
                          "p": { "a": 0, "k": [0, 0] },
                          "s": { "a": 0, "k": [100, 100] },
                          "d": 1
                        },
                        {
                          "ty": "fl",
                          "nm": "Circle Fill",
                          "c": { "a": 0, "k": [0.12, 0.65, 0.95, 1] },
                          "o": { "a": 0, "k": 100 },
                          "r": 1
                        },
                        {
                          "ty": "tr",
                          "p": { "a": 0, "k": [0, 0] },
                          "a": { "a": 0, "k": [0, 0] },
                          "s": { "a": 0, "k": [100, 100] },
                          "r": { "a": 0, "k": 0 },
                          "o": { "a": 0, "k": 100 }
                        }
                      ]
                    }
                  ],
                  "ip": 0,
                  "op": 60,
                  "st": 0,
                  "bm": 0
                }
              ]
            }
        """.trimIndent()
    }
}
