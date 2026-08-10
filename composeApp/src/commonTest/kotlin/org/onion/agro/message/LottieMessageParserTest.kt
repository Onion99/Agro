package org.onion.agro.message

import com.onion.model.ChatMessageContent
import io.github.alexzhirkevich.compottie.LottieComposition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LottieMessageParserTest {
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

}
