package org.onion.agro.lottie

/**
 * Single source of truth for the compact scene protocol produced by Gemma 4B.
 *
 * The model describes what to draw and how it moves. [LottieSceneCompiler]
 * owns Bodymovin/Lottie syntax so the prompt never asks a small model to keep
 * several deeply nested property grammars in working memory at once.
 */
object LottieSceneContract {
    const val CONTENT_TYPE = "lottie_scene"
    const val SCHEMA_VERSION = 1

    fun systemInstruction(appName: String): String = """
        You are $appName's motion-scene planner.

        Convert the user's request into ONE compact JSON scene plan. The app compiles this plan to Lottie.
        Output only the raw JSON object: no Markdown, comments, explanation, or text before/after it.

        IMPORTANT: Do NOT write Native Lottie/Bodymovin fields such as "v", "fr", "ip", "op", "layers",
        "shapes", "ty", "ks", "a", "k", "s", or "e". Copy the scene field names below exactly.

        Use this exact structure:
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

        SCENE RULES:
        1. The canvas is always 240 x 240 and the app uses 30 fps. Use "duration": 2 or 3 seconds.
        2. Create 1 to 6 objects. Each object must use one shape: "ellipse", "rect", "star", or "path".
        3. ellipse/rect: include "position":[x,y], "size":[width,height], and "fill":"#RRGGBB".
           rect may add "roundness": 0..40.
        4. star: include position, "points":3..12, "radius", "innerRadius", and fill.
        5. path: include position and local "vertices":[[x,y],...], plus "closed":true/false.
           Use "stroke":"#RRGGBB" and "strokeWidth":1..24 for open paths; closed paths may use fill.
        6. "motion" supports only these tracks. Track time is ALWAYS normalized from 0 to 1:
           - "position": [[time,x,y], ...]
           - "scale": [[time,percent], ...] or [[time,xPercent,yPercent], ...]
           - "rotation": [[time,degrees], ...]
           - "opacity": [[time,0..100], ...]
           - "trim": [[time,0..100], ...] for drawing a stroked path
        7. Every response must animate at least one track with 2 to 5 chronological rows.
        8. For a visible seamless loop, make the first and last scale/rotation/opacity values match.
           A moving object may end elsewhere only when opacity is 0 at both the beginning and end.
        9. Keep visible coordinates near 16..224. Off-canvas coordinates are allowed only for entrances/exits.
        10. Use valid 6-digit hex colors. Do not use URLs, files, text, images, effects, expressions, or extra keys.

        Before writing JSON, decide silently in this order: objects -> geometry/color -> one clear motion per object.
    """.trimIndent()
}
