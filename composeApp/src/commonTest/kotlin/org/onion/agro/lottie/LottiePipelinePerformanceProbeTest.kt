package org.onion.agro.lottie

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

class LottiePipelinePerformanceProbeTest {
    @Test
    fun compactScenePipelineProbe() {
        repeat(WARM_UP_ITERATIONS) {
            LottieSceneResponseParser.parse(SCENE)
        }

        var title = ""
        val elapsed = measureTime {
            repeat(MEASURED_ITERATIONS) {
                title = LottieSceneResponseParser.parse(SCENE).title
            }
        }

        assertEquals("Falling Drops", title)
        println("LOTTIE_PIPELINE_${MEASURED_ITERATIONS}_MS=${elapsed.inWholeMilliseconds}")
    }

    private companion object {
        const val WARM_UP_ITERATIONS = 40
        const val MEASURED_ITERATIONS = 500

        val SCENE = """
            {
              "type": "lottie_scene",
              "schemaVersion": 1,
              "title": "Falling Drops",
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
}
