package org.onion.agro.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt

/** Compiles Gemma's shallow [LottieSceneContract] payload into Native Lottie JSON. */
object LottieSceneCompiler {
    private const val CANVAS_SIZE = 240
    private const val FPS = 30
    private const val MAX_OBJECTS = 12
    private const val MAX_TRACK_ROWS = 8
    private const val MAX_PATH_VERTICES = 32

    private val defaultColor = SceneColor(0.22f, 0.74f, 0.96f, 1f)
    private val forbiddenKeys = setOf(
        "assets",
        "base64",
        "chars",
        "css",
        "ef",
        "expressions",
        "fonts",
        "html",
        "images",
        "ks",
        "layers",
        "masksProperties",
        "script",
        "shapes",
    )
    private val forbiddenValueFragments = listOf(
        "http://",
        "https://",
        "file://",
        "data:",
        ".lottie",
        "base64",
    )

    fun compile(root: JsonObject): String {
        validateRoot(root)
        val scene = parseScene(root)
        val objects = scene.objects.toMutableList()
        if (objects.none(SceneObject::hasVisibleMotion)) {
            objects[0] = objects[0].copy(
                motion = objects[0].motion.copy(scale = fallbackPulseTrack()),
            )
        }

        val frameCount = (scene.durationSeconds * FPS)
            .roundToInt()
            .coerceIn(FPS, FPS * 4)
        return buildJsonObject {
            put("v", JsonPrimitive("5.7.4"))
            put("fr", JsonPrimitive(FPS))
            put("ip", JsonPrimitive(0))
            put("op", JsonPrimitive(frameCount))
            put("w", JsonPrimitive(CANVAS_SIZE))
            put("h", JsonPrimitive(CANVAS_SIZE))
            put("nm", JsonPrimitive(scene.title))
            put("ddd", JsonPrimitive(0))
            put("loop", JsonPrimitive(scene.loop))
            put("assets", buildJsonArray { })
            put("layers", buildJsonArray {
                objects.forEachIndexed { index, sceneObject ->
                    add(buildLayer(sceneObject, index + 1, frameCount))
                }
            })
        }.toString()
    }

    private fun validateRoot(root: JsonObject) {
        requireScene(
            root["type"].stringOrNull() == LottieSceneContract.CONTENT_TYPE,
            "unexpected_content_type",
        )
        requireScene(
            (root["schemaVersion"].intOrNull() ?: LottieSceneContract.SCHEMA_VERSION) ==
                LottieSceneContract.SCHEMA_VERSION,
            "unsupported_schema_version",
        )
        requireScene(!containsForbiddenContent(root), "forbidden_lottie_scene_content")
    }

    private fun parseScene(root: JsonObject): SceneSpec {
        val rawObjects = root["objects"] as? JsonArray
            ?: throw LottieParseException("invalid_lottie_scene_objects")
        requireScene(rawObjects.isNotEmpty(), "empty_lottie_scene_objects")
        requireScene(rawObjects.size <= MAX_OBJECTS, "lottie_scene_object_count_too_large")

        val duration = (
            root["duration"].numberOrNull()
                ?: root["durationSeconds"].numberOrNull()
                ?: 2f
            ).coerceIn(1f, 4f)
        val objects = rawObjects.mapNotNull { element ->
            (element as? JsonObject)?.let { parseObject(it, duration) }
        }
        requireScene(objects.isNotEmpty(), "empty_lottie_scene_objects")

        return SceneSpec(
            title = root["title"].stringOrNull()
                ?.trim()
                ?.take(64)
                ?.takeIf(String::isNotEmpty)
                ?: "Lottie Animation",
            durationSeconds = duration,
            loop = root["loop"].booleanOrNull() ?: true,
            objects = objects,
        )
    }

    private fun parseObject(root: JsonObject, durationSeconds: Float): SceneObject {
        val requestedShape = root["shape"].stringOrNull()?.trim()?.lowercase().orEmpty()
        val shape = when (requestedShape) {
            "ellipse", "circle", "oval", "drop" -> SceneShape.ELLIPSE
            "rect", "rectangle", "rounded_rect", "rounded-rect" -> SceneShape.RECT
            "star", "sparkle" -> SceneShape.STAR
            "path", "line", "polygon" -> SceneShape.PATH
            else -> if (root["vertices"] is JsonArray) SceneShape.PATH else SceneShape.ELLIPSE
        }
        val position = root["position"].vector2OrNull(
            xFallback = CANVAS_SIZE / 2f,
            yFallback = CANVAS_SIZE / 2f,
        ).clamp(-CANVAS_SIZE * 2f, CANVAS_SIZE * 3f)
        val size = root["size"].vector2OrNull(80f, 80f)
            .map { it.coerceIn(4f, CANVAS_SIZE * 1.5f) }
        val closed = root["closed"].booleanOrNull() ?: shape != SceneShape.PATH
        val vertices = parseVertices(root["vertices"], closed)
        val trimRequested = (root["motion"] as? JsonObject)?.get("trim") is JsonArray

        val explicitFill = root["fill"].stringOrNull()
        var fill = when {
            explicitFill.equals("none", ignoreCase = true) -> null
            root.containsKey("fill") -> parseColor(root["fill"])
            shape == SceneShape.PATH && !closed -> null
            else -> defaultColor
        }
        var stroke = when {
            root["stroke"].stringOrNull().equals("none", ignoreCase = true) -> null
            root.containsKey("stroke") -> parseColor(root["stroke"])
            (shape == SceneShape.PATH && !closed) || trimRequested -> parseColor(root["fill"])
                ?: defaultColor
            else -> null
        }
        if (fill == null && stroke == null) {
            if (shape == SceneShape.PATH && !closed) {
                stroke = defaultColor
            } else {
                fill = defaultColor
            }
        }

        val staticScale = root["scale"].scaleVectorOrNull() ?: listOf(100f, 100f)
        return SceneObject(
            name = root["name"].stringOrNull()
                ?.trim()
                ?.take(48)
                ?.takeIf(String::isNotEmpty)
                ?: "Object",
            shape = shape,
            position = position,
            size = size,
            fill = fill,
            stroke = stroke,
            strokeWidth = (root["strokeWidth"].numberOrNull() ?: 6f).coerceIn(1f, 32f),
            roundness = (root["roundness"].numberOrNull() ?: 12f).coerceIn(0f, 80f),
            starPoints = (root["points"].intOrNull() ?: 5).coerceIn(3, 12),
            outerRadius = (root["radius"].numberOrNull() ?: size.maxOrNull()!! / 2f)
                .coerceIn(4f, CANVAS_SIZE.toFloat()),
            innerRadius = (root["innerRadius"].numberOrNull() ?: size.minOrNull()!! / 4f)
                .coerceIn(2f, CANVAS_SIZE.toFloat()),
            vertices = vertices,
            closed = closed,
            opacity = normalizePercent(root["opacity"].numberOrNull() ?: 100f),
            rotation = (root["rotation"].numberOrNull() ?: 0f).coerceIn(-1_440f, 1_440f),
            scale = staticScale,
            motion = parseMotion(root["motion"] as? JsonObject, durationSeconds),
        )
    }

    private fun parseMotion(root: JsonObject?, durationSeconds: Float): SceneMotion {
        if (root == null) return SceneMotion()
        return SceneMotion(
            position = parseTrack(root["position"], durationSeconds, TrackKind.POSITION),
            scale = parseTrack(root["scale"], durationSeconds, TrackKind.SCALE),
            rotation = parseTrack(root["rotation"], durationSeconds, TrackKind.ROTATION),
            opacity = parseTrack(root["opacity"], durationSeconds, TrackKind.OPACITY),
            trim = parseTrack(root["trim"], durationSeconds, TrackKind.TRIM),
        )
    }

    private fun parseTrack(
        element: JsonElement?,
        durationSeconds: Float,
        kind: TrackKind,
    ): List<TrackPoint> {
        val rows = (element as? JsonArray)
            ?.take(MAX_TRACK_ROWS)
            ?.mapNotNull { it as? JsonArray }
            ?.mapNotNull { row ->
                val numbers = row.mapNotNull { it.numberOrNull() }
                val minimumSize = if (kind == TrackKind.POSITION) 3 else 2
                if (numbers.size < minimumSize) return@mapNotNull null
                RawTrackPoint(numbers.first(), numbers.drop(1))
            }
            .orEmpty()
        if (rows.isEmpty()) return emptyList()

        val maxTime = rows.maxOf(RawTrackPoint::time).coerceAtLeast(0f)
        val timeDivisor = when {
            maxTime <= 1.0001f -> 1f
            maxTime <= durationSeconds + 0.0001f -> durationSeconds
            maxTime <= 100f -> 100f
            else -> maxTime
        }.coerceAtLeast(0.0001f)

        val byProgress = linkedMapOf<Float, TrackPoint>()
        rows.sortedBy(RawTrackPoint::time).forEach { row ->
            val progress = (row.time / timeDivisor).coerceIn(0f, 1f)
            val values = normalizeTrackValues(kind, row.values) ?: return@forEach
            byProgress[progress] = TrackPoint(progress, values)
        }
        val normalized = byProgress.values.sortedBy(TrackPoint::progress)
        if (normalized.isEmpty()) return emptyList()

        return buildList {
            if (normalized.first().progress > 0f) {
                add(normalized.first().copy(progress = 0f))
            }
            addAll(normalized)
            if (normalized.last().progress < 1f) {
                add(normalized.last().copy(progress = 1f))
            }
        }
    }

    private fun normalizeTrackValues(kind: TrackKind, rawValues: List<Float>): List<Float>? {
        return when (kind) {
            TrackKind.POSITION -> {
                if (rawValues.size < 2) return null
                listOf(
                    rawValues[0].coerceIn(-CANVAS_SIZE * 2f, CANVAS_SIZE * 3f),
                    rawValues[1].coerceIn(-CANVAS_SIZE * 2f, CANVAS_SIZE * 3f),
                )
            }
            TrackKind.SCALE -> {
                val x = normalizeScale(rawValues.firstOrNull() ?: return null)
                val y = normalizeScale(rawValues.getOrNull(1) ?: x)
                listOf(x, y)
            }
            TrackKind.ROTATION -> listOf(
                (rawValues.firstOrNull() ?: return null).coerceIn(-1_440f, 1_440f),
            )
            TrackKind.OPACITY,
            TrackKind.TRIM,
            -> listOf(normalizePercent(rawValues.firstOrNull() ?: return null))
        }
    }

    private fun buildLayer(sceneObject: SceneObject, index: Int, frameCount: Int): JsonObject {
        return buildJsonObject {
            put("ddd", JsonPrimitive(0))
            put("ind", JsonPrimitive(index))
            put("ty", JsonPrimitive(4))
            put("nm", JsonPrimitive(sceneObject.name))
            put("sr", JsonPrimitive(1))
            put("ks", buildJsonObject {
                put(
                    "o",
                    animatedOrStaticScalar(sceneObject.motion.opacity, sceneObject.opacity, frameCount),
                )
                put(
                    "r",
                    animatedOrStaticScalar(sceneObject.motion.rotation, sceneObject.rotation, frameCount),
                )
                put(
                    "p",
                    animatedOrStaticVector3(
                        sceneObject.motion.position,
                        sceneObject.position + 0f,
                        frameCount,
                    ),
                )
                put("a", staticVector3(0f, 0f, 0f))
                put(
                    "s",
                    animatedOrStaticVector3(
                        sceneObject.motion.scale,
                        sceneObject.scale + 100f,
                        frameCount,
                    ),
                )
            })
            put("ao", JsonPrimitive(0))
            put("shapes", buildJsonArray { add(buildShapeGroup(sceneObject, frameCount)) })
            put("ip", JsonPrimitive(0))
            put("op", JsonPrimitive(frameCount))
            put("st", JsonPrimitive(0))
            put("bm", JsonPrimitive(0))
        }
    }

    private fun buildShapeGroup(sceneObject: SceneObject, frameCount: Int): JsonObject {
        return buildJsonObject {
            put("ty", JsonPrimitive("gr"))
            put("nm", JsonPrimitive("${sceneObject.name} Group"))
            put("it", buildJsonArray {
                add(buildGeometry(sceneObject))
                sceneObject.fill?.let { add(buildFill(it)) }
                sceneObject.stroke?.let { add(buildStroke(it, sceneObject.strokeWidth)) }
                if (sceneObject.motion.trim.isNotEmpty()) {
                    add(buildTrim(sceneObject.motion.trim, frameCount))
                }
                add(defaultGroupTransform())
            })
        }
    }

    private fun buildGeometry(sceneObject: SceneObject): JsonObject {
        return when (sceneObject.shape) {
            SceneShape.ELLIPSE -> buildJsonObject {
                put("ty", JsonPrimitive("el"))
                put("nm", JsonPrimitive("${sceneObject.name} Path"))
                put("p", staticVector2(0f, 0f))
                put("s", staticVector2(sceneObject.size[0], sceneObject.size[1]))
                put("d", JsonPrimitive(1))
            }
            SceneShape.RECT -> buildJsonObject {
                put("ty", JsonPrimitive("rc"))
                put("nm", JsonPrimitive("${sceneObject.name} Path"))
                put("p", staticVector2(0f, 0f))
                put("s", staticVector2(sceneObject.size[0], sceneObject.size[1]))
                put("r", staticScalar(sceneObject.roundness))
                put("d", JsonPrimitive(1))
            }
            SceneShape.STAR -> buildJsonObject {
                put("ty", JsonPrimitive("sr"))
                put("nm", JsonPrimitive("${sceneObject.name} Path"))
                put("sy", JsonPrimitive(1))
                put("pt", staticScalar(sceneObject.starPoints.toFloat()))
                put("p", staticVector2(0f, 0f))
                put("r", staticScalar(0f))
                put("or", staticScalar(sceneObject.outerRadius))
                put("os", staticScalar(0f))
                put("ir", staticScalar(sceneObject.innerRadius))
                put("is", staticScalar(0f))
                put("d", JsonPrimitive(1))
            }
            SceneShape.PATH -> buildJsonObject {
                put("ty", JsonPrimitive("sh"))
                put("nm", JsonPrimitive("${sceneObject.name} Path"))
                put("ks", buildJsonObject {
                    put("a", JsonPrimitive(0))
                    put("k", buildPath(sceneObject.vertices, sceneObject.closed))
                })
                put("d", JsonPrimitive(1))
            }
        }
    }

    private fun buildPath(vertices: List<List<Float>>, closed: Boolean): JsonObject {
        return buildJsonObject {
            put("i", zeroTangents(vertices.size))
            put("o", zeroTangents(vertices.size))
            put("v", buildJsonArray {
                vertices.forEach { vertex ->
                    add(buildJsonArray {
                        add(number(vertex[0]))
                        add(number(vertex[1]))
                    })
                }
            })
            put("c", JsonPrimitive(closed))
        }
    }

    private fun buildFill(color: SceneColor): JsonObject = buildJsonObject {
        put("ty", JsonPrimitive("fl"))
        put("nm", JsonPrimitive("Fill"))
        put("c", staticColor(color))
        put("o", staticScalar(100f))
        put("r", JsonPrimitive(1))
    }

    private fun buildStroke(color: SceneColor, width: Float): JsonObject = buildJsonObject {
        put("ty", JsonPrimitive("st"))
        put("nm", JsonPrimitive("Stroke"))
        put("c", staticColor(color))
        put("o", staticScalar(100f))
        put("w", staticScalar(width))
        put("lc", JsonPrimitive(2))
        put("lj", JsonPrimitive(2))
        put("ml", JsonPrimitive(4))
    }

    private fun buildTrim(track: List<TrackPoint>, frameCount: Int): JsonObject = buildJsonObject {
        put("ty", JsonPrimitive("tm"))
        put("nm", JsonPrimitive("Draw Progress"))
        put("s", staticScalar(0f))
        put("e", animatedOrStaticScalar(track, 100f, frameCount))
        put("o", staticScalar(0f))
        put("m", JsonPrimitive(1))
    }

    private fun defaultGroupTransform(): JsonObject = buildJsonObject {
        put("ty", JsonPrimitive("tr"))
        put("p", staticVector2(0f, 0f))
        put("a", staticVector2(0f, 0f))
        put("s", staticVector2(100f, 100f))
        put("r", staticScalar(0f))
        put("o", staticScalar(100f))
    }

    private fun animatedOrStaticScalar(
        track: List<TrackPoint>,
        fallback: Float,
        frameCount: Int,
    ): JsonObject {
        val frames = track.toFrames(frameCount)
        if (!frames.hasValueChange()) return staticScalar(frames.firstValueOr(fallback))
        return animatedProperty(frames) { values -> listOf(values.firstOrNull() ?: fallback) }
    }

    private fun animatedOrStaticVector3(
        track: List<TrackPoint>,
        fallback: List<Float>,
        frameCount: Int,
    ): JsonObject {
        val safeFallback = listOf(
            fallback.getOrElse(0) { 0f },
            fallback.getOrElse(1) { fallback.firstOrNull() ?: 0f },
            fallback.getOrElse(2) { 0f },
        )
        val frames = track.toFrames(frameCount)
        if (!frames.hasValueChange()) {
            val value = frames.firstOrNull()?.values ?: safeFallback
            return staticVector3(
                value.getOrElse(0) { safeFallback[0] },
                value.getOrElse(1) { safeFallback[1] },
                value.getOrElse(2) { safeFallback[2] },
            )
        }
        return animatedProperty(frames) { values ->
            listOf(
                values.getOrElse(0) { safeFallback[0] },
                values.getOrElse(1) { safeFallback[1] },
                values.getOrElse(2) { safeFallback[2] },
            )
        }
    }

    private fun animatedProperty(
        frames: List<FramePoint>,
        normalize: (List<Float>) -> List<Float>,
    ): JsonObject = buildJsonObject {
        put("a", JsonPrimitive(1))
        put("k", buildJsonArray {
            frames.forEachIndexed { index, frame ->
                val start = normalize(frame.values)
                val end = normalize(frames.getOrNull(index + 1)?.values ?: frame.values)
                add(buildJsonObject {
                    put("t", JsonPrimitive(frame.frame))
                    put("s", numberArray(start))
                    put("e", numberArray(end))
                })
            }
        })
    }

    private fun List<TrackPoint>.toFrames(frameCount: Int): List<FramePoint> {
        val byFrame = linkedMapOf<Int, FramePoint>()
        forEach { point ->
            val frame = (point.progress * frameCount).roundToInt().coerceIn(0, frameCount)
            byFrame[frame] = FramePoint(frame, point.values)
        }
        return byFrame.values.sortedBy(FramePoint::frame)
    }

    private fun List<FramePoint>.hasValueChange(): Boolean {
        if (size < 2) return false
        return drop(1).any { frame -> !frame.values.sameValues(first().values) }
    }

    private fun List<FramePoint>.firstValueOr(fallback: Float): Float {
        return firstOrNull()?.values?.firstOrNull() ?: fallback
    }

    private fun List<Float>.sameValues(other: List<Float>): Boolean {
        if (size != other.size) return false
        return indices.all { index -> kotlin.math.abs(this[index] - other[index]) < 0.0001f }
    }

    private fun parseVertices(element: JsonElement?, closed: Boolean): List<List<Float>> {
        val parsed = (element as? JsonArray)
            ?.take(MAX_PATH_VERTICES)
            ?.mapNotNull { vertex ->
                val values = (vertex as? JsonArray)
                    ?.mapNotNull { it.numberOrNull() }
                    .orEmpty()
                if (values.size < 2) null else listOf(
                    values[0].coerceIn(-CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat()),
                    values[1].coerceIn(-CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat()),
                )
            }
            .orEmpty()
        val minimum = if (closed) 3 else 2
        if (parsed.size >= minimum) return parsed
        return if (closed) {
            listOf(listOf(0f, -40f), listOf(36f, 28f), listOf(-36f, 28f))
        } else {
            listOf(listOf(-40f, 0f), listOf(40f, 0f))
        }
    }

    private fun parseColor(element: JsonElement?): SceneColor? {
        val primitive = element as? JsonPrimitive
        val text = primitive?.takeIf(JsonPrimitive::isString)?.contentOrNull
        if (text != null) return SceneColor.fromHex(text)

        val channels = (element as? JsonArray)
            ?.mapNotNull { it.numberOrNull() }
            .orEmpty()
        if (channels.size < 3) return null
        val divisor = if (channels.take(3).any { it > 1f }) 255f else 1f
        val alphaDivisor = if ((channels.getOrNull(3) ?: 1f) > 1f) 255f else 1f
        return SceneColor(
            red = (channels[0] / divisor).coerceIn(0f, 1f),
            green = (channels[1] / divisor).coerceIn(0f, 1f),
            blue = (channels[2] / divisor).coerceIn(0f, 1f),
            alpha = ((channels.getOrNull(3) ?: 1f) / alphaDivisor).coerceIn(0f, 1f),
        )
    }

    private fun containsForbiddenContent(element: JsonElement): Boolean {
        return when (element) {
            is JsonObject -> element.any { (key, value) ->
                key in forbiddenKeys || containsForbiddenContent(value)
            }
            is JsonArray -> element.any(::containsForbiddenContent)
            is JsonPrimitive -> {
                val value = element.contentOrNull?.lowercase().orEmpty()
                forbiddenValueFragments.any(value::contains)
            }
        }
    }

    private fun fallbackPulseTrack(): List<TrackPoint> = listOf(
        TrackPoint(0f, listOf(96f, 96f)),
        TrackPoint(0.5f, listOf(104f, 104f)),
        TrackPoint(1f, listOf(96f, 96f)),
    )

    private fun normalizeScale(value: Float): Float {
        val normalized = if (value in 0.0001f..2f) value * 100f else value
        return normalized.coerceIn(0f, 500f)
    }

    private fun normalizePercent(value: Float): Float {
        val normalized = if (value in 0.0001f..1f) value * 100f else value
        return normalized.coerceIn(0f, 100f)
    }

    private fun staticScalar(value: Float): JsonObject = buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", number(value))
    }

    private fun staticVector2(x: Float, y: Float): JsonObject = buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", numberArray(listOf(x, y)))
    }

    private fun staticVector3(x: Float, y: Float, z: Float): JsonObject = buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", numberArray(listOf(x, y, z)))
    }

    private fun staticColor(color: SceneColor): JsonObject = buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", numberArray(listOf(color.red, color.green, color.blue, color.alpha)))
    }

    private fun numberArray(values: List<Float>): JsonArray = buildJsonArray {
        values.forEach { add(number(it)) }
    }

    private fun zeroTangents(size: Int): JsonArray = buildJsonArray {
        repeat(size) { add(numberArray(listOf(0f, 0f))) }
    }

    private fun number(value: Float): JsonPrimitive {
        val rounded = (value * 1_000f).roundToInt() / 1_000f
        return if (rounded % 1f == 0f) {
            JsonPrimitive(rounded.toInt())
        } else {
            JsonPrimitive(rounded)
        }
    }

    private fun JsonElement?.stringOrNull(): String? {
        return (this as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
    }

    private fun JsonElement?.numberOrNull(): Float? {
        return (this as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite)
    }

    private fun JsonElement?.intOrNull(): Int? {
        return (this as? JsonPrimitive)?.intOrNull
    }

    private fun JsonElement?.booleanOrNull(): Boolean? {
        return (this as? JsonPrimitive)?.booleanOrNull
    }

    private fun JsonElement?.vector2OrNull(xFallback: Float, yFallback: Float): List<Float> {
        val values = (this as? JsonArray)?.mapNotNull { it.numberOrNull() }.orEmpty()
        return listOf(values.getOrElse(0) { xFallback }, values.getOrElse(1) { yFallback })
    }

    private fun JsonElement?.scaleVectorOrNull(): List<Float>? {
        val primitive = numberOrNull()
        if (primitive != null) {
            val scale = normalizeScale(primitive)
            return listOf(scale, scale)
        }
        val values = (this as? JsonArray)?.mapNotNull { it.numberOrNull() }.orEmpty()
        if (values.isEmpty()) return null
        val x = normalizeScale(values[0])
        return listOf(x, normalizeScale(values.getOrElse(1) { values[0] }))
    }

    private fun List<Float>.clamp(minimum: Float, maximum: Float): List<Float> {
        return map { it.coerceIn(minimum, maximum) }
    }

    private fun requireScene(condition: Boolean, reason: String) {
        if (!condition) throw LottieParseException(reason)
    }

    private enum class SceneShape {
        ELLIPSE,
        RECT,
        STAR,
        PATH,
    }

    private enum class TrackKind {
        POSITION,
        SCALE,
        ROTATION,
        OPACITY,
        TRIM,
    }

    private data class SceneSpec(
        val title: String,
        val durationSeconds: Float,
        val loop: Boolean,
        val objects: List<SceneObject>,
    )

    private data class SceneObject(
        val name: String,
        val shape: SceneShape,
        val position: List<Float>,
        val size: List<Float>,
        val fill: SceneColor?,
        val stroke: SceneColor?,
        val strokeWidth: Float,
        val roundness: Float,
        val starPoints: Int,
        val outerRadius: Float,
        val innerRadius: Float,
        val vertices: List<List<Float>>,
        val closed: Boolean,
        val opacity: Float,
        val rotation: Float,
        val scale: List<Float>,
        val motion: SceneMotion,
    ) {
        fun hasVisibleMotion(): Boolean = motion.tracks.any { track ->
            track.size >= 2 && track.drop(1).any { !it.values.sameValues(track.first().values) }
        }
    }

    private data class SceneMotion(
        val position: List<TrackPoint> = emptyList(),
        val scale: List<TrackPoint> = emptyList(),
        val rotation: List<TrackPoint> = emptyList(),
        val opacity: List<TrackPoint> = emptyList(),
        val trim: List<TrackPoint> = emptyList(),
    ) {
        val tracks: List<List<TrackPoint>>
            get() = listOf(position, scale, rotation, opacity, trim)
    }

    private data class RawTrackPoint(val time: Float, val values: List<Float>)

    private data class TrackPoint(val progress: Float, val values: List<Float>)

    private data class FramePoint(val frame: Int, val values: List<Float>)

    private data class SceneColor(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        companion object {
            fun fromHex(raw: String): SceneColor? {
                val compact = raw.trim().removePrefix("#")
                val expanded = when (compact.length) {
                    3 -> compact.map { "$it$it" }.joinToString("")
                    6, 8 -> compact
                    else -> return null
                }
                val value = runCatching { expanded.toLong(16) }.getOrNull() ?: return null
                val hasAlpha = expanded.length == 8
                val redShift = if (hasAlpha) 24 else 16
                val greenShift = if (hasAlpha) 16 else 8
                val blueShift = if (hasAlpha) 8 else 0
                return SceneColor(
                    red = ((value shr redShift) and 0xff).toFloat() / 255f,
                    green = ((value shr greenShift) and 0xff).toFloat() / 255f,
                    blue = ((value shr blueShift) and 0xff).toFloat() / 255f,
                    alpha = if (hasAlpha) (value and 0xff).toFloat() / 255f else 1f,
                )
            }
        }
    }
}
