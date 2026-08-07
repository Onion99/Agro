package org.onion.agro.lottie

import com.onion.model.LottieAnimationSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object LottieAnimationSpecParser {
    private const val MAX_SPEC_BYTES = 128 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(response: String): ParsedLottieAnimation {
        val sanitizedText = LottieJsonSanitizer.sanitize(response)
        val trimmed = sanitizedText.trim()
        requireLottie(trimmed.isNotEmpty(), "invalid_lottie_spec_json")
        requireLottie(trimmed.encodeToByteArray().size <= MAX_SPEC_BYTES, "lottie_spec_too_large")

        val root = runCatching {
            json.parseToJsonElement(trimmed).jsonObject
        }.getOrElse {
            throw LottieParseException("invalid_lottie_spec_json", it)
        }

        val declaredType = root["type"].stringContentOrNull()
        if (declaredType == CONTENT_TYPE) {
            LottieAnimationSpecValidator.validateRaw(root)
            val spec = runCatching {
                json.decodeFromString<LottieAnimationSpec>(trimmed)
            }.getOrElse {
                throw LottieParseException("invalid_lottie_spec_json", it)
            }
            LottieAnimationSpecValidator.validate(spec)

            val lottieJson = LottieJsonBuilder.build(
                spec = spec,
                seed = spec.seed ?: trimmed.hashCode()
            )
            LottieJsonValidator.validate(lottieJson)
            return ParsedLottieAnimation(
                title = spec.title.trim(),
                width = spec.canvas.width,
                height = spec.canvas.height,
                fps = spec.fps,
                durationMs = spec.durationMs,
                loop = spec.loop,
                json = lottieJson,
                spec = spec
            )
        } else if (root.containsKey("layers") || root.containsKey("v")) {
            LottieAnimationSpecValidator.validateRaw(root)
            val finalLottieJson = LottieJsonSanitizer.sanitize(trimmed)
            LottieJsonValidator.validate(finalLottieJson)
            val finalRoot = runCatching { json.parseToJsonElement(finalLottieJson).jsonObject }.getOrDefault(root)
            val width = finalRoot["w"].intOrNull() ?: 240
            val height = finalRoot["h"].intOrNull() ?: 240
            val fps = finalRoot["fr"].intOrNull() ?: 60
            val ip = finalRoot["ip"].intOrNull() ?: 0
            val op = finalRoot["op"].intOrNull() ?: (fps * 2)
            val title = finalRoot["nm"].stringContentOrNull() ?: "Lottie Animation"
            val durationMs = (((op - ip).coerceAtLeast(1).toFloat() / fps.coerceAtLeast(1)) * 1000f).toLong()
            val loop = finalRoot["loop"].booleanOrNull() ?: true

            return ParsedLottieAnimation(
                title = title,
                width = width,
                height = height,
                fps = fps,
                durationMs = durationMs,
                loop = loop,
                json = finalLottieJson,
                spec = null
            )
        } else {
            throw LottieParseException("unexpected_content_type")
        }
    }

    fun declaredType(response: String): String? {
        return runCatching {
            json.parseToJsonElement(response.trim())
                .jsonObject["type"]
                .stringContentOrNull()
        }.getOrNull()
    }

    const val CONTENT_TYPE = "lottie_animation_spec"
}

object LottieAnimationSpecValidator {
    private val supportedFps = setOf(24, 30, 60)
    private val allowedStylesByKind = mapOf(
        "loading_spinner" to setOf("spin_arc", "orbit_dots"),
        "success_check" to setOf("draw_then_pop", "circle_then_check"),
        "error_cross" to setOf("draw_then_shake", "cross_fade_in"),
        "progress_dots" to setOf("stagger_bounce", "stagger_fade"),
        "pulse_badge" to setOf("soft_pulse", "ripple"),
        "empty_state_sparkle" to setOf("float_sparkle", "fade_sparkle")
    )
    private val supportedLineCaps = setOf("butt", "round", "square")
    private val forbiddenKeys = setOf(
        "fonts",
        "chars",
        "ef",
        "x",
        "html",
        "css",
        "script",
        "base64"
    )
    private val forbiddenValueFragments = listOf(
        "http://",
        "https://",
        "file://",
        "data:",
        ".lottie",
        "base64"
    )

    fun validateRaw(root: JsonObject) {
        requireLottie(!containsForbiddenKey(root), "forbidden_lottie_spec_field")
        requireLottie(!containsForbiddenString(root), "forbidden_lottie_external_resource")
    }

    fun validate(spec: LottieAnimationSpec) {
        requireLottie(spec.type == LottieAnimationSpecParser.CONTENT_TYPE, "unexpected_content_type")
        requireLottie(spec.schemaVersion == 1, "unsupported_schema_version")
        requireLottie(spec.title.trim().length in 1..64, "invalid_lottie_title")
        requireLottie(spec.canvas.width in 64..512, "invalid_lottie_canvas")
        requireLottie(spec.canvas.height in 64..512, "invalid_lottie_canvas")
        requireLottie(
            spec.canvas.background == "transparent" || spec.canvas.background.isHexColor(),
            "invalid_lottie_background"
        )
        requireLottie(spec.fps in supportedFps, "invalid_lottie_fps")
        requireLottie(spec.durationMs in 300L..5_000L, "invalid_lottie_duration")
        requireLottie(frameCount(spec.durationMs, spec.fps) <= 300, "lottie_frame_count_too_large")
        requireLottie(spec.palette.primary.isHexColor(), "invalid_lottie_palette")
        spec.palette.secondary?.let {
            requireLottie(it.isHexColor(), "invalid_lottie_palette")
        }
        spec.palette.accent?.let {
            requireLottie(it.isHexColor(), "invalid_lottie_palette")
        }
        requireLottie(spec.motion.intensity.isFinite(), "invalid_lottie_motion")
        requireLottie(spec.motion.staggerMs in 0..1000, "invalid_lottie_stagger")

        val allowedStyles = allowedStylesByKind[spec.kind]
        if (allowedStyles != null) {
            requireLottie(spec.motion.style in allowedStyles, "unsupported_lottie_motion_style")
        }

        spec.stroke?.let { stroke ->
            requireLottie(stroke.width in 1..48, "invalid_lottie_stroke_width")
            requireLottie(stroke.lineCap in supportedLineCaps, "invalid_lottie_line_cap")
        }
    }

    internal fun frameCount(durationMs: Long, fps: Int): Int {
        return ceil(durationMs / 1_000.0 * fps).toInt()
    }

    private fun containsForbiddenKey(element: JsonElement): Boolean {
        return when (element) {
            is JsonObject -> element.any { (key, value) ->
                key in forbiddenKeys || containsForbiddenKey(value)
            }
            is JsonArray -> element.any(::containsForbiddenKey)
            else -> false
        }
    }

    private fun containsForbiddenString(element: JsonElement): Boolean {
        return when (element) {
            is JsonObject -> element.values.any(::containsForbiddenString)
            is JsonArray -> element.any(::containsForbiddenString)
            is JsonPrimitive -> {
                val value = element.contentOrNull?.lowercase().orEmpty()
                forbiddenValueFragments.any(value::contains)
            }
        }
    }
}

object LottieJsonBuilder {
    fun build(
        spec: LottieAnimationSpec,
        seed: Int
    ): String {
        val frameCount = LottieAnimationSpecValidator.frameCount(spec.durationMs, spec.fps)
        val primary = LottieColor.fromHex(spec.palette.primary)
        val secondary = LottieColor.fromHex(spec.palette.secondary ?: spec.palette.primary)
        val accent = LottieColor.fromHex(spec.palette.accent ?: "#FFFFFF")
        val strokeSpec = spec.stroke ?: com.onion.model.LottieStrokeSpec()
        val layers = when (spec.kind) {
            "loading_spinner" -> loadingSpinnerLayers(spec, frameCount, primary, secondary, strokeSpec)
            "success_check" -> successCheckLayers(spec, frameCount, primary, secondary, accent, strokeSpec)
            "error_cross" -> errorCrossLayers(spec, frameCount, primary, secondary, strokeSpec)
            "progress_dots" -> progressDotsLayers(spec, frameCount, seed, primary, secondary)
            "pulse_badge" -> pulseBadgeLayers(spec, frameCount, primary, secondary, accent, strokeSpec)
            "empty_state_sparkle" -> emptyStateSparkleLayers(spec, frameCount, seed, primary, secondary)
            else -> customCreativeLayers(spec, frameCount, seed, primary, secondary, accent, strokeSpec)
        }
        val background = if (spec.canvas.background == "transparent") {
            ""
        } else {
            ""","bg":"${spec.canvas.background}""""
        }
        return buildString {
            append("{")
            append(""""v":"5.7.4","fr":${spec.fps},"ip":0,"op":$frameCount,"w":${spec.canvas.width},"h":${spec.canvas.height},"nm":"${spec.title.trim().jsonEscape()}","ddd":0""")
            append(background)
            append(""","assets":[],"layers":[""")
            append(layers.joinToString(","))
            append("]}")
        }
    }

    private fun customCreativeLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        seed: Int,
        primary: LottieColor,
        secondary: LottieColor,
        accent: LottieColor,
        stroke: com.onion.model.LottieStrokeSpec
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val minDim = minOf(width, height)
        val intensity = spec.motion.intensity.coerceIn(0.1f, 1.0f)
        val strokeWidth = stroke.width.toFloat()
        val colors = listOf(primary, secondary, accent)

        // Fully parametric & mathematical: layer count, geometry, rotation, and pulse keyframes derived from seed and motion intensity
        val layerCount = 2 + (kotlin.math.abs(seed) % 3)

        return (0 until layerCount).map { layerIndex ->
            val layerSeed = kotlin.math.abs(seed + layerIndex * 1337)
            val color = colors[layerIndex % colors.size]
            val radius = minDim * (0.15f + 0.10f * (layerIndex + 1))
            val isEven = layerIndex % 2 == 0

            val rotDirection = if (isEven) 1f else -1f
            val rotationAngle = 360f * intensity * rotDirection * (1f + (layerSeed % 3) * 0.5f)
            val rotation = scalarKeyframes(listOf(0 to 0f, frameCount to rotationAngle))

            val staggerOffset = (layerIndex * spec.motion.staggerMs / 10).coerceAtMost(frameCount / 4)
            val midFrame = ((frameCount / 2) + staggerOffset).coerceIn(1, frameCount - 1)
            val scaleMin = 90f - (15f * intensity)
            val scaleMax = 100f + (25f * intensity * (1f + layerIndex * 0.2f))
            val scale = vectorKeyframes(listOf(
                0 to listOf(scaleMin, scaleMin, 100f),
                midFrame to listOf(scaleMax, scaleMax, 100f),
                frameCount to listOf(scaleMin, scaleMin, 100f)
            ))

            val shapeItem = if ((layerSeed % 2) == 0) {
                ellipse("Path ${layerIndex + 1}", radius, radius)
            } else {
                val pointCount = 3 + (layerSeed % 5)
                val points = (0 until pointCount * 2).map { i ->
                    val angle = (i * PI / pointCount) - (PI / 2)
                    val r = if (i % 2 == 0) radius * 0.5f else radius * 0.25f
                    (r * cos(angle)).toFloat() to (r * sin(angle)).toFloat()
                }
                path("Polygon Path ${layerIndex + 1}", points, closed = true)
            }

            val styleItems = mutableListOf<String>()
            styleItems.add(shapeItem)
            if (isEven) {
                styleItems.add(stroke("Stroke ${layerIndex + 1}", color, (strokeWidth * (1f - layerIndex * 0.15f)).coerceAtLeast(1f), stroke.lineCap))
            } else {
                styleItems.add(fill("Fill ${layerIndex + 1}", color, opacity = (40f + (layerIndex * 20f)).coerceAtMost(90f)))
            }

            val groupName = "Parametric Shape ${layerIndex + 1}"
            val group = group(groupName, styleItems)

            shapeLayer(
                name = "Layer ${layerIndex + 1}",
                index = layerIndex + 1,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(group),
                rotation = rotation,
                scale = scale
            )
        }
    }

    private fun loadingSpinnerLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        primary: LottieColor,
        secondary: LottieColor,
        stroke: com.onion.model.LottieStrokeSpec
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val radius = minOf(width, height) * 0.56f
        val rotation = scalarKeyframes(
            listOf(0 to 0f, frameCount to 360f)
        )
        return if (spec.motion.style == "orbit_dots") {
            val dotRadius = minOf(width, height) * (0.07f + spec.motion.intensity * 0.03f)
            val orbitRadius = minOf(width, height) * 0.24f
            val dots = (0 until 3).map { index ->
                val angle = (PI * 2.0 / 3.0 * index).toFloat()
                val alpha = 100f - index * 20f
                group(
                    name = "Orbit Dot ${index + 1}",
                    items = listOf(
                        ellipse("Orbit Dot Path ${index + 1}", dotRadius, dotRadius, cos(angle) * orbitRadius, sin(angle) * orbitRadius),
                        fill("Orbit Dot Fill ${index + 1}", if (index == 0) primary else secondary, alpha)
                    )
                )
            }
            listOf(
                shapeLayer(
                    name = "Lottie Orbit Spinner",
                    index = 1,
                    frameCount = frameCount,
                    positionX = width / 2f,
                    positionY = height / 2f,
                    shapes = dots,
                    rotation = rotation
                )
            )
        } else {
            listOf(
                shapeLayer(
                    name = "Lottie Arc Spinner",
                    index = 1,
                    frameCount = frameCount,
                    positionX = width / 2f,
                    positionY = height / 2f,
                    shapes = listOf(
                        group(
                            name = "Spinner Arc",
                            items = listOf(
                                ellipse("Spinner Ring Path", radius, radius),
                                stroke("Spinner Stroke", primary, stroke.width.toFloat(), stroke.lineCap),
                                trimPath(
                                    name = "Spinner Trim",
                                    start = staticScalar(8f),
                                    end = staticScalar(58f + spec.motion.intensity * 26f)
                                )
                            )
                        )
                    ),
                    rotation = rotation
                )
            )
        }
    }

    private fun successCheckLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        primary: LottieColor,
        secondary: LottieColor,
        accent: LottieColor,
        stroke: com.onion.model.LottieStrokeSpec
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val size = minOf(width, height)
        val circleEndFrame = (frameCount * 0.54f).roundToInt().coerceAtLeast(1)
        val checkStartFrame = (frameCount * 0.38f).roundToInt()
        val checkEndFrame = (frameCount * 0.86f).roundToInt().coerceAtLeast(checkStartFrame + 1)
        val scale = vectorKeyframes(
            listOf(
                0 to listOf(92f, 92f, 100f),
                checkEndFrame to listOf(112f, 112f, 100f),
                frameCount to listOf(100f, 100f, 100f)
            )
        )
        return listOf(
            shapeLayer(
                name = "Success Halo",
                index = 1,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Success Circle",
                        items = listOf(
                            ellipse("Success Circle Path", size * 0.62f, size * 0.62f),
                            stroke("Success Circle Stroke", secondary, stroke.width.toFloat() * 0.72f, stroke.lineCap, opacity = 70f),
                            trimPath("Success Circle Trim", staticScalar(0f), scalarKeyframes(listOf(0 to 0f, circleEndFrame to 100f)))
                        )
                    )
                ),
                scale = scale
            ),
            shapeLayer(
                name = "Success Check",
                index = 2,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Checkmark",
                        items = listOf(
                            path("Checkmark Path", listOf(-size * 0.19f to size * 0.02f, -size * 0.05f to size * 0.16f, size * 0.25f to -size * 0.17f), closed = false),
                            stroke("Checkmark Stroke", if (spec.motion.style == "circle_then_check") accent else primary, stroke.width.toFloat(), stroke.lineCap),
                            trimPath("Checkmark Trim", staticScalar(0f), scalarKeyframes(listOf(checkStartFrame to 0f, checkEndFrame to 100f)))
                        )
                    )
                )
            )
        )
    }

    private fun errorCrossLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        primary: LottieColor,
        secondary: LottieColor,
        stroke: com.onion.model.LottieStrokeSpec
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val size = minOf(width, height)
        val crossEndFrame = (frameCount * 0.7f).roundToInt().coerceAtLeast(1)
        val rotation = if (spec.motion.style == "draw_then_shake") {
            scalarKeyframes(
                listOf(
                    0 to 0f,
                    crossEndFrame to 0f,
                    (crossEndFrame + 2).coerceAtMost(frameCount) to -4f,
                    (crossEndFrame + 5).coerceAtMost(frameCount) to 4f,
                    frameCount to 0f
                )
            )
        } else {
            staticScalar(0f)
        }
        return listOf(
            shapeLayer(
                name = "Error Halo",
                index = 1,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Error Circle",
                        items = listOf(
                            ellipse("Error Circle Path", size * 0.62f, size * 0.62f),
                            stroke("Error Circle Stroke", secondary, stroke.width.toFloat() * 0.65f, stroke.lineCap, opacity = 52f)
                        )
                    )
                )
            ),
            shapeLayer(
                name = "Error Cross",
                index = 2,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Cross Line One",
                        items = listOf(
                            path("Cross One Path", listOf(-size * 0.18f to -size * 0.18f, size * 0.18f to size * 0.18f), closed = false),
                            stroke("Cross One Stroke", primary, stroke.width.toFloat(), stroke.lineCap),
                            trimPath("Cross One Trim", staticScalar(0f), scalarKeyframes(listOf(0 to 0f, crossEndFrame to 100f)))
                        )
                    ),
                    group(
                        name = "Cross Line Two",
                        items = listOf(
                            path("Cross Two Path", listOf(size * 0.18f to -size * 0.18f, -size * 0.18f to size * 0.18f), closed = false),
                            stroke("Cross Two Stroke", primary, stroke.width.toFloat(), stroke.lineCap),
                            trimPath("Cross Two Trim", staticScalar(0f), scalarKeyframes(listOf((frameCount * 0.16f).roundToInt() to 0f, crossEndFrame to 100f)))
                        )
                    )
                ),
                rotation = rotation
            )
        )
    }

    private fun progressDotsLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        seed: Int,
        primary: LottieColor,
        secondary: LottieColor
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val dotCount = 3 + positiveModulo(seed, 3)
        val size = minOf(width, height) * (0.11f + spec.motion.intensity * 0.03f)
        val gap = size * 1.55f
        val firstX = width / 2f - gap * (dotCount - 1) / 2f
        val staggerFrames = millisecondsToFrames(spec.motion.staggerMs, spec.fps)
        return (0 until dotCount).map { index ->
            val start = (index * staggerFrames).coerceAtMost(frameCount - 1)
            val mid = (start + frameCount / 4).coerceAtMost(frameCount - 1)
            val end = (start + frameCount / 2).coerceAtMost(frameCount)
            val animatedScale = if (spec.motion.style == "stagger_bounce") {
                vectorKeyframes(
                    listOf(
                        0 to listOf(74f, 74f, 100f),
                        start to listOf(74f, 74f, 100f),
                        mid to listOf(126f, 126f, 100f),
                        end to listOf(74f, 74f, 100f),
                        frameCount to listOf(74f, 74f, 100f)
                    )
                )
            } else {
                staticVector3(100f, 100f, 100f)
            }
            val animatedOpacity = scalarKeyframes(
                listOf(
                    0 to 38f,
                    start to 38f,
                    mid to 100f,
                    end to 38f,
                    frameCount to 38f
                )
            )
            shapeLayer(
                name = "Progress Dot ${index + 1}",
                index = index + 1,
                frameCount = frameCount,
                positionX = firstX + gap * index,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Progress Dot Shape ${index + 1}",
                        items = listOf(
                            ellipse("Progress Dot Path ${index + 1}", size, size),
                            fill("Progress Dot Fill ${index + 1}", if (index % 2 == 0) primary else secondary, 100f)
                        )
                    )
                ),
                scale = animatedScale,
                opacity = animatedOpacity
            )
        }
    }

    private fun pulseBadgeLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        primary: LottieColor,
        secondary: LottieColor,
        accent: LottieColor,
        stroke: com.onion.model.LottieStrokeSpec
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val size = minOf(width, height)
        val baseScale = 88f + spec.motion.intensity * 12f
        return listOf(
            shapeLayer(
                name = "Pulse Ripple",
                index = 1,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Ripple Ring",
                        items = listOf(
                            ellipse("Ripple Path", size * 0.52f, size * 0.52f),
                            stroke("Ripple Stroke", secondary, stroke.width.toFloat() * 0.55f, stroke.lineCap, opacity = 62f)
                        )
                    )
                ),
                scale = vectorKeyframes(
                    listOf(
                        0 to listOf(78f, 78f, 100f),
                        (frameCount * 0.55f).roundToInt() to listOf(138f, 138f, 100f),
                        frameCount to listOf(78f, 78f, 100f)
                    )
                ),
                opacity = scalarKeyframes(
                    listOf(
                        0 to 64f,
                        (frameCount * 0.55f).roundToInt() to 8f,
                        frameCount to 64f
                    )
                )
            ),
            shapeLayer(
                name = "Pulse Badge",
                index = 2,
                frameCount = frameCount,
                positionX = width / 2f,
                positionY = height / 2f,
                shapes = listOf(
                    group(
                        name = "Badge Circle",
                        items = listOf(
                            ellipse("Badge Path", size * 0.42f, size * 0.42f),
                            fill("Badge Fill", primary, 86f),
                            stroke("Badge Accent Stroke", accent, stroke.width.toFloat() * 0.32f, stroke.lineCap, opacity = 55f)
                        )
                    )
                ),
                scale = vectorKeyframes(
                    listOf(
                        0 to listOf(baseScale, baseScale, 100f),
                        (frameCount * 0.5f).roundToInt() to listOf(112f, 112f, 100f),
                        frameCount to listOf(baseScale, baseScale, 100f)
                    )
                )
            )
        )
    }

    private fun emptyStateSparkleLayers(
        spec: LottieAnimationSpec,
        frameCount: Int,
        seed: Int,
        primary: LottieColor,
        secondary: LottieColor
    ): List<String> {
        val width = spec.canvas.width.toFloat()
        val height = spec.canvas.height.toFloat()
        val size = minOf(width, height)
        val sparkleCount = 4 + positiveModulo(seed, 2)
        val positions = listOf(
            width * 0.34f to height * 0.42f,
            width * 0.56f to height * 0.32f,
            width * 0.66f to height * 0.58f,
            width * 0.42f to height * 0.66f,
            width * 0.72f to height * 0.42f
        )
        val staggerFrames = millisecondsToFrames(spec.motion.staggerMs, spec.fps)
        return (0 until sparkleCount).map { index ->
            val start = (index * staggerFrames / 2).coerceAtMost(frameCount - 1)
            val mid = (start + frameCount / 3).coerceAtMost(frameCount - 1)
            val end = (start + frameCount * 2 / 3).coerceAtMost(frameCount)
            val radius = size * (0.045f + index * 0.008f)
            val (x, y) = positions[index]
            shapeLayer(
                name = "Empty Sparkle ${index + 1}",
                index = index + 1,
                frameCount = frameCount,
                positionX = x,
                positionY = y,
                shapes = listOf(
                    group(
                        name = "Sparkle Shape ${index + 1}",
                        items = listOf(
                            path(
                                name = "Sparkle Path ${index + 1}",
                                points = listOf(
                                    0f to -radius,
                                    radius * 0.38f to 0f,
                                    0f to radius,
                                    -radius * 0.38f to 0f
                                ),
                                closed = true
                            ),
                            fill("Sparkle Fill ${index + 1}", if (index % 2 == 0) primary else secondary, 92f)
                        )
                    )
                ),
                scale = vectorKeyframes(
                    listOf(
                        0 to listOf(40f, 40f, 100f),
                        start to listOf(40f, 40f, 100f),
                        mid to listOf(116f, 116f, 100f),
                        end to listOf(72f, 72f, 100f),
                        frameCount to listOf(40f, 40f, 100f)
                    )
                ),
                opacity = scalarKeyframes(
                    listOf(
                        0 to 0f,
                        start to 0f,
                        mid to 100f,
                        end to 36f,
                        frameCount to 0f
                    )
                ),
                rotation = scalarKeyframes(listOf(0 to 0f, frameCount to if (spec.motion.style == "float_sparkle") 30f else 0f))
            )
        }
    }

    private fun shapeLayer(
        name: String,
        index: Int,
        frameCount: Int,
        positionX: Float,
        positionY: Float,
        shapes: List<String>,
        rotation: String = staticScalar(0f),
        scale: String = staticVector3(100f, 100f, 100f),
        opacity: String = staticScalar(100f)
    ): String {
        return buildString {
            append("{")
            append(""""ddd":0,"ind":$index,"ty":4,"nm":"${name.jsonEscape()}","sr":1,"ks":{""")
            append(""""o":$opacity,"r":$rotation,"p":${staticVector3(positionX, positionY, 0f)},"a":${staticVector3(0f, 0f, 0f)},"s":$scale""")
            append("""},"ao":0,"shapes":[""")
            append(shapes.joinToString(","))
            append("""],"ip":0,"op":$frameCount,"st":0,"bm":0}""")
        }
    }

    private fun group(
        name: String,
        items: List<String>
    ): String {
        return buildString {
            append("{")
            append(""""ty":"gr","nm":"${name.jsonEscape()}","it":[""")
            append((items + groupTransform()).joinToString(","))
            append("""],"np":${items.size + 1},"cix":2,"bm":0,"ix":1,"mn":"ADBE Vector Group","hd":false}""")
        }
    }

    private fun groupTransform(): String {
        return """{"ty":"tr","p":${staticVector2(0f, 0f)},"a":${staticVector2(0f, 0f)},"s":${staticVector2(100f, 100f)},"r":${staticScalar(0f)},"o":${staticScalar(100f)},"sk":${staticScalar(0f)},"sa":${staticScalar(0f)},"nm":"Transform"}"""
    }

    private fun ellipse(
        name: String,
        width: Float,
        height: Float,
        x: Float = 0f,
        y: Float = 0f
    ): String {
        return """{"ty":"el","nm":"${name.jsonEscape()}","p":${staticVector2(x, y)},"s":${staticVector2(width, height)},"d":1,"mn":"ADBE Vector Shape - Ellipse","hd":false}"""
    }

    private fun path(
        name: String,
        points: List<Pair<Float, Float>>,
        closed: Boolean
    ): String {
        val tangents = points.joinToString(",") { "[0,0]" }
        val vertices = points.joinToString(",") { "[${it.first.n()},${it.second.n()}]" }
        return """{"ty":"sh","nm":"${name.jsonEscape()}","ks":{"a":0,"k":{"i":[$tangents],"o":[$tangents],"v":[$vertices],"c":$closed}},"mn":"ADBE Vector Shape - Group","hd":false}"""
    }

    private fun fill(
        name: String,
        color: LottieColor,
        opacity: Float
    ): String {
        return """{"ty":"fl","nm":"${name.jsonEscape()}","c":${staticColor(color)},"o":${staticScalar(opacity)},"r":1,"bm":0,"mn":"ADBE Vector Graphic - Fill","hd":false}"""
    }

    private fun stroke(
        name: String,
        color: LottieColor,
        width: Float,
        lineCap: String,
        opacity: Float = 100f
    ): String {
        val lineCapCode = when (lineCap) {
            "butt" -> 1
            "square" -> 3
            else -> 2
        }
        return """{"ty":"st","nm":"${name.jsonEscape()}","c":${staticColor(color)},"o":${staticScalar(opacity)},"w":${staticScalar(width)},"lc":$lineCapCode,"lj":1,"ml":4,"bm":0,"mn":"ADBE Vector Graphic - Stroke","hd":false}"""
    }

    private fun trimPath(
        name: String,
        start: String,
        end: String
    ): String {
        return """{"ty":"tm","nm":"${name.jsonEscape()}","s":$start,"e":$end,"o":${staticScalar(0f)},"m":1,"ix":1,"mn":"ADBE Vector Filter - Trim","hd":false}"""
    }

    private fun millisecondsToFrames(milliseconds: Int, fps: Int): Int {
        return (milliseconds.toLong() * fps / 1_000L).toInt()
    }

    private fun positiveModulo(value: Int, mod: Int): Int {
        return ((value % mod) + mod) % mod
    }
}

object LottieJsonValidator {
    private const val MAX_LOTTIE_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }
    private val requiredTopLevelFields = setOf("fr", "layers")
    private val forbiddenGeneratedKeys = setOf("fonts", "chars", "ef", "x", "masksProperties", "hasMask")

    fun validate(lottieJson: String) {
        requireLottie(lottieJson.encodeToByteArray().size <= MAX_LOTTIE_BYTES, "lottie_json_too_large")
        val root = runCatching {
            json.parseToJsonElement(lottieJson).jsonObject
        }.getOrElse {
            throw LottieParseException("invalid_lottie_json", it)
        }
        requireLottie(requiredTopLevelFields.all(root::containsKey), "invalid_lottie_json")
        val assets = root["assets"]?.jsonArray
        if (assets != null) {
            requireLottie(assets.isEmpty(), "forbidden_lottie_external_resource")
        }
        val layers = root["layers"]?.jsonArray ?: throw LottieParseException("invalid_lottie_json")
        requireLottie(layers.isNotEmpty(), "empty_lottie_layers")
        requireLottie(layers.size <= 32, "lottie_layer_count_too_large")
        layers.forEach { layer ->
            val layerObject = layer.jsonObject
            val ty = layerObject["ty"].intOrNull() ?: 4
            requireLottie(ty in setOf(0, 3, 4), "unsupported_lottie_layer_type")
            requireLottie(layerObject["ddd"].intOrNull() ?: 0 == 0, "unsupported_lottie_3d_layer")
        }
        requireLottie(!containsForbiddenGeneratedKey(root), "forbidden_lottie_generated_field")
    }

    private fun containsForbiddenGeneratedKey(element: JsonElement): Boolean {
        return when (element) {
            is JsonObject -> element.any { (key, value) ->
                key in forbiddenGeneratedKeys || containsForbiddenGeneratedKey(value)
            }
            is JsonArray -> element.any(::containsForbiddenGeneratedKey)
            else -> false
        }
    }
}

data class ParsedLottieAnimation(
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val loop: Boolean,
    val json: String,
    val spec: LottieAnimationSpec? = null
)

class LottieParseException(
    val reason: String,
    cause: Throwable? = null
) : IllegalArgumentException(reason, cause)

private data class LottieColor(
    val r: Float,
    val g: Float,
    val b: Float
) {
    companion object {
        fun fromHex(hex: String): LottieColor {
            val value = hex.removePrefix("#")
            return LottieColor(
                r = value.substring(0, 2).toInt(16) / 255f,
                g = value.substring(2, 4).toInt(16) / 255f,
                b = value.substring(4, 6).toInt(16) / 255f
            )
        }
    }
}

private fun JsonElement?.stringContentOrNull(): String? {
    return (this as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
}

private fun JsonElement?.intOrNull(): Int? {
    return (this as? JsonPrimitive)?.intOrNull
}

private fun JsonElement?.booleanOrNull(): Boolean? {
    return (this as? JsonPrimitive)?.booleanOrNull
}

private fun requireLottie(condition: Boolean, reason: String) {
    if (!condition) throw LottieParseException(reason)
}

private fun String.isHexColor(): Boolean {
    return HEX_COLOR_REGEX.matches(this)
}

private fun String.jsonEscape(): String {
    return buildString {
        this@jsonEscape.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

private fun staticScalar(value: Float): String {
    return """{"a":0,"k":${value.n()}}"""
}

private fun staticVector2(x: Float, y: Float): String {
    return """{"a":0,"k":[${x.n()},${y.n()}]}"""
}

private fun staticVector3(x: Float, y: Float, z: Float): String {
    return """{"a":0,"k":[${x.n()},${y.n()},${z.n()}]}"""
}

private fun staticColor(color: LottieColor): String {
    return """{"a":0,"k":[${color.r.n()},${color.g.n()},${color.b.n()},1]}"""
}

private fun scalarKeyframes(points: List<Pair<Int, Float>>): String {
    return keyframes(points) { listOf(it) }
}

private fun vectorKeyframes(points: List<Pair<Int, List<Float>>>): String {
    return keyframes(points) { it }
}

private fun <T> keyframes(
    points: List<Pair<Int, T>>,
    values: (T) -> List<Float>
): String {
    val normalized = points
        .sortedBy { it.first }
        .fold(mutableListOf<Pair<Int, T>>()) { acc, point ->
            if (acc.lastOrNull()?.first == point.first) {
                acc[acc.lastIndex] = point
            } else {
                acc += point
            }
            acc
        }
    return buildString {
        append("""{"a":1,"k":[""")
        normalized.forEachIndexed { index, point ->
            if (index > 0) append(",")
            append("""{"t":${point.first},"s":${values(point.second).jsonArray()}""")
            val next = normalized.getOrNull(index + 1)
            if (next != null) {
                append(""","e":${values(next.second).jsonArray()}""")
            }
            append("}")
        }
        append("]}")
    }
}

private fun List<Float>.jsonArray(): String {
    return joinToString(prefix = "[", postfix = "]") { it.n() }
}

private fun Float.n(): String {
    val value = (this * 1000f).roundToInt() / 1000f
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        value.toString().trimEnd('0').trimEnd('.')
    }
}

private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
