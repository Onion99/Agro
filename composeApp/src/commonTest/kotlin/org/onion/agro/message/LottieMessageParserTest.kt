package org.onion.agro.message

import com.onion.model.ChatMessageContent
import io.github.alexzhirkevich.compottie.LottieComposition
import org.onion.agro.lottie.LottieJsonSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LottieMessageParserTest {
    @Test
    fun compilesCompactGemmaSceneAndCompottieAcceptsIt() {
        val payload = """
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

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertEquals("Falling Water Drops", lottie.title)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(30, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
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

        assertTrue(lottie.json.contains("\"ty\":\"sh\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"st\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"tm\""), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun repairsReportedGemmaAnonymousWaterDropNativeJson() {
        val payload = """
            {
              "v": "5.8.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "ddd": 0,
              "loop": true,
              "assets":[],
              "layers":[
                {
                  "id": 1,
                  "ty": "gr",
                  "nm": "Water Drop 1",
                  "sr": 1,
                  "st": 0,
                  "bm": 0,
                  "ip": 0,
                  "op": 60,
                  "a": 1,
                  "ks": {
                    "p": {
                      "k": [
                        { "t": 0, "s": [120, 0], "e": [120, 0] },
                        { "t": 60, "s": [120, 240], "e": [120, 240] }
                      ]
                    },
                    "o": {
                      "k": [
                        { "t": 0, "s": [100], "e": [100] },
                        { "t": 30, "s": [0], "e": [0] },
                        { "t": 60, "s": [100], "e": [100] }
                      ]
                    }
                  },
                  "shapes": [
                    {
                      "ty": "gr",
                      "it": [
                        { "a": 0, "k": [{ "t": 0, "s": [10, 30], "e": [10, 30] }] },
                        { "a": 0, "k": [{ "t": 0, "s": [100, 0, 255, 255], "e": [100, 0, 255, 255] }] }
                      ],
                      "s": { "a": 0, "k": [10, 30] }
                    }
                  ]
                },
                {
                  "id": 2,
                  "ty": "gr",
                  "nm": "Water Drop 2",
                  "sr": 1,
                  "st": 0,
                  "bm": 0,
                  "ip": 0,
                  "op": 60,
                  "a": 1,
                  "ks": {
                    "p": {
                      "k": [
                        { "t": 0, "s": [170, 0], "e": [170, 0] },
                        { "t": 60, "s": [170, 240], "e": [170, 240] }
                      ]
                    },
                    "o": {
                      "k": [
                        { "t": 0, "s": [100], "e": [100] },
                        { "t": 30, "s": [0], "e": [0] },
                        { "t": 60, "s": [100], "e": [100] }
                      ]
                    }
                  },
                  "shapes": [
                    {
                      "ty": "gr",
                      "it": [
                        { "a": 0, "k": [{ "t": 0, "s": [10, 25], "e": [10, 25] }] },
                        { "a": 0, "k": [{ "t": 0, "s": [0, 0, 2555], "e": [0, 0, 25] }] }
                      ],
                      "s": { "a": 0, "k": [10, 25] }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertEquals(30, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
        assertTrue(lottie.json.contains("\"ty\":4"), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"el\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"fl\""), lottie.json)
        assertTrue(lottie.json.contains("\"a\":1"), lottie.json)
        assertTrue(lottie.json.contains("\"e\":[120,240,0]"), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun rejectsLegacyKindTemplateIntentEnvelope() {
        val result = LottieMessageParser.parseCompletedResponse(legacyIntentSpecJson())
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("lottie_animation_spec", unsupported.declaredType)
        assertEquals("unexpected_content_type", unsupported.reason)
    }

    @Test
    fun rejectsShapeFreeFireAnimationAsUnsupported() {
        val result = LottieMessageParser.parseCompletedResponse(shapeFreeFireAnimationJson())
        val unsupported = assertIs<ChatMessageContent.Unsupported>(result, result.toString())

        assertEquals("empty_lottie_drawable_content", unsupported.reason)
    }

    @Test
    fun repairsFireAnimationAndCompottieAcceptsSanitizedJson() {
        val malformedJson = malformedFireAnimationJson()
        val result = LottieMessageParser.parseCompletedResponse(malformedJson)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result)

        assertEquals("Fire", lottie.title)
        assertEquals(30, lottie.fps)
        assertEquals(2_000L, lottie.durationMs)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertTrue(lottie.json.contains("\"ty\":\"el\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"fl\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"tr\""), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun repairsPointArrayShapePathFireAnimationAndCompottieAcceptsSanitizedJson() {
        val result = LottieMessageParser.parseCompletedResponse(pointArrayShapeFireAnimationJson())
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertEquals("Fire Flame", lottie.title)
        assertEquals(30, lottie.fps)
        assertEquals(3_333L, lottie.durationMs)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertTrue(lottie.json.contains("\"ty\":\"sh\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"fl\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"tr\""), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
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
    fun repairsCubeRotationJsonWithCorruptedAnimatedKeys() {
        val result = LottieMessageParser.parseCompletedResponse(corruptedCubeRotationJson())
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())

        assertEquals("Cube Rotation", lottie.title)
        assertEquals(30, lottie.fps)
        assertEquals(240, lottie.width)
        assertEquals(240, lottie.height)
        assertTrue(lottie.json.contains("\"ty\":\"el\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"fl\""), lottie.json)
        assertTrue(lottie.json.contains("\"ty\":\"tr\""), lottie.json)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun repairsCrashingEffectJsonWithCorruptedAnimatedKeys() {
        val rawSanitized = LottieJsonSanitizer.sanitize(crashingEffectJson())
        println("CRASHING_EFFECT_SANITIZED: $rawSanitized")
        val result = LottieMessageParser.parseCompletedResponse(crashingEffectJson())
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun repairsGemmaBlankScreenChipFlowJson() {
        val payload = """{"v":"5.7.4","fr":30,"ip":0,"op":90,"w":240,"h":240,"nm":"Tech Chip Flow","ddd":0,"loop":true,"assets":[],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"Chip Body","sr":1,"ao":0,"st":0,"bm":0,"ip":0,"op":90,"ks":{"p":{"a":0,"k":[120.0,120.0,0.0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":1,"k":[{"t":0,"s":[100,100,100],"e":[100,100,100]},{"t":30,"s":[110,110,110],"e":[110,110,110]},{"t":60,"s":[100,100,100],"e":[100,100,100]},{"t":90,"s":[100,100,100],"e":[100,100,100]}]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}},"shapes":[{"ty":"gr","nm":"Chip Group","it":[{"ty":"el","nm":"Chip Body","p":{"a":0,"k":[120,120]},"s":{"a":0,"k":[1000,1000]},"d":1},{"ty":"fl","nm":"Chip Fill","c":{"a":0,"k":[0.1,0.1,0.1,1]},"o":{"a":0,"k":100},"r":1},{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}]},{"ddd":0,"ind":2,"ty":4,"nm":"Data Flow","sr":1,"ao":0,"st":0,"bm":0,"ip":0,"op":90,"ks":{"p":{"a":0,"k":[120.0,120.0,0.0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[1000,100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":10}},"shapes":[{"ty":"gr","nm":"Flow Group","it":[{"ty":"el","nm":"Flow Line","p":{"a":0,"k":[0,0]},"s":{"a":0,"k":[150,50]},"d":1},{"ty":"fl","nm":"Flow Fill","c":{"a":0,"k":[0.38,0.7,1,1]},"o":{"a":0,"k":100},"r":1},{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}]}]}"""

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())
        assertEquals("Tech Chip Flow", lottie.title)
        assertEquals(30, lottie.fps)
        assertEquals(3000L, lottie.durationMs)
        assertNotNull(LottieComposition.parse(lottie.json))
    }

    @Test
    fun repairsGemmaWaterDropJsonWithStrayQuotesAndZeroDimension() {
        val payload = """
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 240,
              "nm": "Water Drop",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Drop Container",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 100 },
                    "r": { "a": 0, "k": 0 },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": {
                      "a": 1,
                      "k": [
                        { "t": 0, "s": [100, 100, 100], "e": [100, 100, 100] },
                        { "t": 30, "s": [110, 110, 110], "e": [100, 100, 100] }
                      ]
                    }
                  },
                  "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Drop Shape",
            ",
                      "it": [
                        {
                          "ty": "el",
                          "nm": "Drop Path",
                        "p": { "a": 0, "k": [0, 0]},
                        "s": { "a": 0, "k": [60, 0]},
                        "d": 1
                      },
                        {
                          "ty": "fl",
                          "nm": "Drop Fill",
                          "c": { "a": 0, "k": [0.1, 0.4, 0.9, 1]},
                          "o": { "a": 0, "k": 1000},
                          "r": 1
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

        val result = LottieMessageParser.parseCompletedResponse(payload)
        val lottie = assertIs<ChatMessageContent.LottieAnimation>(result, result.toString())
        assertEquals("Water Drop", lottie.title)
        assertEquals(30, lottie.fps)
        assertEquals(2000L, lottie.durationMs)
        assertNotNull(LottieComposition.parse(lottie.json))
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
                    "s": {
                      "a": 1,
                      "k": [
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

    private fun pointArrayShapeFireAnimationJson(): String {
        return """
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 100,
              "w": 240,
              "h": 240,
              "nm": "Fire Flame",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Fire Flame Layer",
                  "sr": 1,
                  "ks": {
                    "o": {
                      "a": 0,
                      "k": 100
                    },
                    "r": {
                      "a": 1,
                      "k": [0]
                    },
                    "p": {
                      "a": 1,
                      "k": [
                        {
                          "t": 0,
                          "s": [120, 120, 0],
                          "e": [120, 120, 0]
                        },
                        {
                          "t": 50,
                          "s": [130, 120, 0],
                          "e": [130, 120, 0]
                        },
                        {
                          "t": 100,
                          "s": [120, 120, 0],
                          "e": [120, 120, 0]
                        }
                      ]
                    },
                    "a": {
                      "a": 0,
                      "k": [0, 0, 0]
                    },
                    "s": {
                      "a": 1,
                      "k": [
                        {
                          "t": 0,
                          "s": [100, 100, 100],
                          "e": [100, 100, 100]
                        },
                        {
                          "t": 30,
                          "s": [110, 110, 110],
                          "e": [110, 110, 110]
                        },
                        {
                          "t": 60,
                          "s": [100, 100, 100],
                          "e": [100, 100, 100]
                        }
                      ]
                    }
                  }
                },
                {
                  "ddd": 0,
                  "ind": 2,
                  "ty": 4,
                  "nm": "Fire Shape",
                  "sr": 1,
                  "ks": {
                    "o": {
                      "a": 0,
                      "k": 100
                    },
                    "r": {
                      "a": 1,
                      "k": [0]
                    },
                    "p": {
                      "a": 0,
                      "k": [0, 0, 0]
                    },
                    "a": {
                      "a": 0,
                      "k": [0, 0, 0]
                    },
                    "s": {
                      "a": 0,
                      "k": [100, 100, 100]
                    }
                  },
                  "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Flame Group",
                      "it": [
                        {
                          "ty": "sh",
                          "nm": "Flame Path",
                          "ks": {
                            "k": [
                              {
                                "v": [0, 0],
                                "i": [0, 0],
                                "o": [0, 0],
                                "c": 0
                              },
                              {
                                "v": [20, -20],
                                "i": [0, 0],
                                "o": [0, 0],
                                "c": 0
                              },
                              {
                                "v": [40, 0],
                                "i": [0, 0],
                                "o": [0, 0],
                                "c": 0
                              },
                              {
                                "v": [20, 20],
                                "i": [0, 0],
                                "o": [0, 0],
                                "c": 0
                              },
                              {
                                "v": [0, 0],
                                "i": [0, 0],
                                "o": [0, 0],
                                "c": 0
                              }
                            ]
                          }
                        },
                        {
                          "ty": "fl",
                          "nm": "Fire Fill",
                          "c": {
                            "a": 0,
                            "k": [0.8, 0.2, 0.0, 1]
                          },
                          "o": {
                            "a": 0,
                            "k": 100
                          },
                          "r": 1
                        }
                      ]
                    }
                  ],
                  "ip": 0,
                  "op": 100,
                  "st": 0,
                  "bm": 0
                }
              ]
            }
        """.trimIndent()
    }

    private fun malformedFireAnimationJson(): String {
        return """
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 40,
              "nm": "Fire",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "FireFlame",
                  ,
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 10},
                    "r": { "a":0, "k": [0] },
                    "p": { "a": 0,k": [20,2] },
                    "a": { "a":0, "k": [0,0] },
                    "s": {a:1, "k": [
                      { "t": 0, "s": [10,1] },
                      {t: 1, "s": [1.1,0]
                      {t:2, "s": [0,0]
                      {t:3, "s": [0,0]}]
                    ]
                  }
                },
                "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "FlameGroup",,
                      "it": [
                        {
                          "ty": "el","nm":"FlamePath",p": {a:0,k:[2,2]},"s":{a:0,k:[1,1]},d:1
                        },
                        {
                          "ty":fl,nm "Color",c": {a:0,k:[.2,0,0.5,0]},"o":{a:0,k10},r:1
                        },
                        {
                          "ty":tr,nm "Transform",p":{a,k:[0,2]},a:{a:0,k:[0]},"s":{a:1,k:[0]}
                      ]
                    }
                  ],
                  "ip":0","op":0","st":0","bm":0
                }]}
        """.trimIndent()
    }

    private fun shapeFreeFireAnimationJson(): String {
        return """
            {
              "v": "5.7.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 240,
              "nm": "Fire Animation",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty":4,
                  "nm": "Fire Layer",
                  "sr":1,
                  "ks": {
                    "o": { "a":0, "k":10 },
                    "r": { "a":0, "k":0 },
                    "p": { "a":0, "k": [20,20,0] },
                    "a": { "a":0, "k": [0,0,0] },
                    "s": { "a":1, "k": [
                      { "t":0, "s": [10.5,1.0,0], "e": [1.0,1.5,1], "t":30, "s": [1.5,1,1], "e": [1,1,1], "t":60, "s": [1,1,1], "e": [1,1,1], "t":0 }
                    ] }
                  }
                },
                {
                  "ddd":0,
                  "ind":2,
                  "ty":4,
                  "nm": "Fire Trail",
                  "sr":1,
                  "ks": {
                    "o": { "a":0, "k":10 },
                    "r": { "a":0, "k":0 },
                    "p": { "a":0, "k": [20,20,0] },
                    "a": { "a":0, "k": [0,0,0] },
                    "s": { "a":1, "k": [
                      { "t":0, "s": [0.8,0.8], "e": [1,1], "t":0, "s": [1,1], "e": [1,1,1,1], "t":0 }
                    ] }
                  }
                },
                {
                  "ddd":0,
                  "ind":3,
                  "ty":4,
                  "nm": "Flames",
                  "sr":1,
                  "ks": {
                    "o": { "a":0, "k":10 },
                    "r": { "a":0, "k":0 },
                    "p": { "a":0, "k": [10,0,0] },
                    "a": { "a":0, "k": [0,0,0] },
                    "s": { "a":1, "k": [
                      { "t":0, "s": [0.5,0.5], "e": [1,1], "t":0, "s": [1,1], "e": [1,1,1,1], "t":0 }
                    ] }
                  }
                },
                {
                  "ddd":0,
                  "ind":4,
                  "ty":4,
                  "nm": "Flame Shape",
                  "sr":1,
                  "ks": {
                    "o": { "a":0, "k":1 },
                    "r": { "a":0, "k":0 },
                    "p": { "a":0, "k": [0,0,0] },
                    "a": { "a":0, "k": [0,0,0] },
                    "s": { "a":1, "k": [
                      { "t":0, "s": [1.5,5], "e": [1,1], "t":0, "s": [1,1], "e": [1,1,1,1], "t":0 }
                    ] }
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun corruptedCubeRotationJson(): String {
        return """
            {
              "v": "5.7.4",
              "fr": 30,  "ip": 0,  "op": 124,  "w": 480,  "h": 4 8,  "nm": "Cube Rotation",  "ddd": 0,  "loop": true,  "assets": [],  "layers": [  {  "ddd": 0,  "ind":1,  "ty":4,  "nm": "Cube Layer",  "sr":1,  "ks": { "o":{ "a":0,"k":10}, "r":{ "a":1,"k":[0]}, "p":{ "a":0,"k":[24,24]}, "a":{ "a":0,"k": [0,0,0] }, "s":{ "a":1, "k": [10,10,1] }} },  "ao":0, "shapes": [  {  "ty": "gr",  "nm": "Cube Group",  "it": [  {  "ty": "el",  "nm": "Cube Face",  "p":{ "a":0,"k":[124,124]}, "s":{ "a0","k":[48,8]}, "d":1},  {  "ty": "fl",  "nm": "CubeFill",  "c":{ "a0,"k":[0.12,0.4,0.8,1]}, "o":{ "a0,"k":1000}  },  {  "ty": "tr",  "p":{ "a0,"k":[0,0]}, "a":{ "a0,"k":[0,0,0] }, "s":{ "a1,"k":[1000,10000] }, "r":{ "a1,"k":[0,0]}, "o":{ "a0,"k":1000}  }  ]  },  "ip":0,  "op":248,  "st":0,  "bm":0  }  ]  }
        """.trimIndent()
    }

    private fun crashingEffectJson(): String {
        return """
            {
              "v": "5.7.4",  "fr":30,  "ip":0,  "op":124,  "w":24,  "h":24,  "nm": "Crashing Effect",  "ddd":0,  "loop":false,  "assets":[],  "layers":[  {  "ddd":0,  "ind":1,  "ty":4,  "nm": "Explosion Points",  "sr":1,  "ks": { "o":{ "a":0, "k":10}, "r":{ "a":0, "k":0}, "p":{ "a":0, "k": [2,2]}, "a":{ "a":0, "k": [0,0,0] }, "s":{ "a1, "k": [1000,10000] } } },  "ao":0,  "shapes":[  {  "ty": "gr",  "nm": "Point Group",  "  "it":[  {  "ty": "el",  "nm": "Point",  "p":{ "a":0, "k": [0,0] }, "s":{ "a0, "k": [0.1,0.1] }, "d":1},  {  "ty": "fl",  "nm": "PointFill",  "c":{ "a0,k":[0.9,0.1,0.1,1] }, "o":{ "a0,k":100}  },  {  "ty": "tr",  "p":{ "a0,k":[0,0] }, "a":{ "a0,k":[0,0,0] }, "s":{ "a1,k":[100,10000] }, "r":{ "a0,k":0}, "o":{ "a0,k":1000}  }  ]  },  "ip":0,  "op":48,  "st":0,  "bm":0  }  ]  }  "}
        """.trimIndent()
    }

}
