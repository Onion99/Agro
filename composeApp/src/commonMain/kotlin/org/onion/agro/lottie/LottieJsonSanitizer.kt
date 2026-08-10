package org.onion.agro.lottie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * Advanced sanitizer, repair, and auto-completion engine for Lottie JSON payloads.
 * Handles severe model formatting errors:
 * 1. Space-separated number syntax (e.g. "h": 2 400 -> "h": 2400)
 * 2. Nested fill/stroke/transform properties inside shape nodes (e.g. "fl": {...} inside "el")
 * 3. Out-of-bounds canvas dimensions (rescales > 240 canvas down to 240x240)
 * 4. Scale factor mismatch (e.g. scale [1, 1, 1] or [10, 1, 1] normalized to [100, 100, 100])
 * 5. Out-of-bounds frame counts (clamps op to max 180 frames)
 * 6. Malformed RGBA strings & hex color conversions
 * 7. Opacity clamping (> 100 -> 100)
 * 8. Missing property quotes/colons, quoted numbers, leading-decimal numbers, and array separators
 * 9. Missing closing brackets & Markdown fences
 */
object LottieJsonSanitizer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val rgbaRegex = Regex(""""c"\s*:\s*(?:\{\s*)?"rgba\s*\(\s*([\d\.]+)\s*,\s*([\d\.]+)\s*,\s*([\d\.]+)\s*(?:,\s*([\d\.]+)\s*)?\)"?(?:\s*\})?""", RegexOption.IGNORE_CASE)
    private val hexRegex = Regex(""""c"\s*:\s*"#([0-9A-Fa-f]{6})"""", RegexOption.IGNORE_CASE)
    private val spacedNumberRegex = Regex(""""([a-zA-Z0-9_-]+)"\s*:\s*(\d+)\s+(\d+)""")
    private val followedByPropertyKeyRegex = Regex("""^\s*,\s*"[a-zA-Z0-9_]+"\s*:""")
    private val unquotedKeyRegex = Regex("""([,{\[]\s*)([A-Za-z_][A-Za-z0-9_-]*)"?\s*:""")
    private val bareKeyBeforeQuotedValueRegex = Regex(
        """([,{]\s*)([A-Za-z_][A-Za-z0-9_-]*)\s+("(?:\\.|[^"\\])*")(?=\s*[,}\]])"""
    )
    private val quotedKeyBeforeQuotedValueRegex = Regex(
        """([,{]\s*)("[A-Za-z_][A-Za-z0-9_-]*")\s+("(?:\\.|[^"\\])*")(?=\s*[,}\]])"""
    )
    private val quotedNumericValueRegex = Regex(
        """("[A-Za-z_][A-Za-z0-9_-]*"\s*:\s*)(-?(?:\d+(?:\.\d+)?|\.\d+))\s*"(?=\s*[,}\]])"""
    )
    private val missingKeyValueRegex = Regex("""([,{]\s*)(["']?a["']?)\s*(?=,)""")
    private val malformedAnimatedKeyRegex = Regex(
        """([,{]\s*)["']?a([01])["']?(?:,|\s|")*["']?k["']?\s*:""",
        RegexOption.IGNORE_CASE
    )
    private val strayQuotesBeforeKeyRegex = Regex(
        """([,{]\s*)(?:["']\s*)+([A-Za-z_][A-Za-z0-9_-]*)\s*"?\s*:"""
    )
    private val malformedScaleKeyRegex = Regex("""([,{]\s*)(["']?k["']?)(?=\s*-?(?:\d|\.\d))""")
    private val unquotedValueRegex = Regex(
        """(:\s*)([A-Za-z_][A-Za-z0-9_.-]*)(?=\s*[,}\]])"""
    )
    private val leadingDecimalRegex = Regex("""([:\[,]\s*)(-?)\.(\d+)""")
    private val adjacentArrayObjectRegex = Regex("""([}\]])(\s*)(?=\{)""")

    fun sanitize(input: String): String {
        var text = input.trim()

        // 1. Unwrap Markdown code fences
        if (text.startsWith("```")) {
            text = text.replace(Regex("^```(?:json)?\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()
        }

        // 2. Repair text-level syntax errors (spaced numbers, malformed colors, stray commas, unenclosed shapes, missing object closures)
        text = repairSpacedNumbers(text)
        text = repairColorSyntax(text)
        text = repairMalformedPropertySyntax(text)
        text = repairStrayCommas(text)
        text = repairUnenclosedShapePropertiesInArray(text)
        text = repairMissingObjectClosuresInArray(text)
        text = repairUnclosedShapesArrayBeforeLayerProperties(text)

        // 3. Repair unbalanced brackets & auto-close missing ones
        text = repairUnbalancedBrackets(text)

        // 4. AST Structural Normalization, Rescaling & Unnesting
        return runCatching {
            val element = json.parseToJsonElement(text)
            if (element is JsonObject && (element.containsKey("v") || element.containsKey("layers"))) {
                val sanitizedObj = sanitizeLottieRoot(element)
                sanitizedObj.toString()
            } else {
                text
            }
        }.getOrDefault(text)
    }

    /**
     * Repairs token-level omissions before the JSON parser is invoked. These are deliberately
     * constrained to object delimiters and JSON punctuation so that text inside a quoted name is
     * never rewritten as a property.
     */
    private fun repairMalformedPropertySyntax(raw: String): String {
        var repaired = raw
        repeat(2) {
            repaired = quotedNumericValueRegex.replace(repaired) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}"
            }
            repaired = malformedScaleKeyRegex.replace(repaired) { match ->
                "${match.groupValues[1]}\"k\": "
            }
            repaired = missingKeyValueRegex.replace(repaired) { match ->
                "${match.groupValues[1]}\"a\": 0"
            }
            repaired = malformedAnimatedKeyRegex.replace(repaired) { match ->
                "${match.groupValues[1]}\"a\": ${match.groupValues[2]}, \"k\":"
            }
            repaired = strayQuotesBeforeKeyRegex.replace(repaired) { match ->
                "${match.groupValues[1]}\"${match.groupValues[2]}\":"
            }
            repaired = bareKeyBeforeQuotedValueRegex.replace(repaired) { match ->
                "${match.groupValues[1]}\"${match.groupValues[2]}\": ${match.groupValues[3]}"
            }
            repaired = quotedKeyBeforeQuotedValueRegex.replace(repaired) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}: ${match.groupValues[3]}"
            }
            repaired = unquotedKeyRegex.replace(repaired) { match ->
                "${match.groupValues[1]}\"${match.groupValues[2]}\":"
            }
            repaired = unquotedValueRegex.replace(repaired) { match ->
                val value = match.groupValues[2]
                if (value == "true" || value == "false" || value == "null") {
                    match.value
                } else {
                    "${match.groupValues[1]}\"$value\""
                }
            }
            repaired = leadingDecimalRegex.replace(repaired) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}0.${match.groupValues[3]}"
            }
            repaired = adjacentArrayObjectRegex.replace(repaired) { match ->
                "${match.groupValues[1]}},${match.groupValues[2]}"
            }
        }
        return repaired
    }

    private fun repairSpacedNumbers(raw: String): String {
        var repaired = raw
        var matches = spacedNumberRegex.containsMatchIn(repaired)
        while (matches) {
            repaired = spacedNumberRegex.replace(repaired) { match ->
                """"${match.groupValues[1]}": ${match.groupValues[2]}${match.groupValues[3]}"""
            }
            matches = spacedNumberRegex.containsMatchIn(repaired)
        }
        return repaired
    }

    private fun repairColorSyntax(raw: String): String {
        var repaired = raw

        repaired = rgbaRegex.replace(repaired) { match ->
            val r = (match.groupValues[1].toFloatOrNull() ?: 0f) / 255f
            val g = (match.groupValues[2].toFloatOrNull() ?: 0f) / 255f
            val b = (match.groupValues[3].toFloatOrNull() ?: 0f) / 255f
            val a = match.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1.0f
            """"c":{"a":0,"k":[${r.n()},${g.n()},${b.n()},${a.n()}]}"""
        }

        repaired = hexRegex.replace(repaired) { match ->
            val hex = match.groupValues[1]
            val r = hex.substring(0, 2).toInt(16) / 255f
            val g = hex.substring(2, 4).toInt(16) / 255f
            val b = hex.substring(4, 6).toInt(16) / 255f
            """"c":{"a":0,"k":[${r.n()},${g.n()},${b.n()},1]}"""
        }

        return repaired
    }

    private fun repairStrayCommas(raw: String): String {
        var repaired = raw
        repaired = repaired.replace(Regex("""\s*,\s*,+"""), ",")
        repaired = repaired.replace(Regex("""\s*,\s*(\}|\])"""), "$1")
        return repaired
    }

    private fun repairUnenclosedShapePropertiesInArray(raw: String): String {
        val regex = Regex("""(?<=\[\s*)"(fl|st|tr|el|sh|sr|gr)"\s*:\s*\{""")
        return regex.replace(raw) { match ->
            """{ "ty": "${match.groupValues[1]}", """
        }
    }

    private fun repairMissingObjectClosuresInArray(raw: String): String {
        val regex = Regex("""("ip"\s*:\s*\d+)\s*,\s*(\{)""")
        return regex.replace(raw) { match ->
            "${match.groupValues[1]} }, ${match.groupValues[2]}"
        }
    }

    private fun repairUnclosedShapesArrayBeforeLayerProperties(raw: String): String {
        val regex = Regex("""("shapes"\s*:\s*\[[\s\S]*?\})\s*,\s*(?="(?:ip|op|st|bm|ks|ao)"\s*:)""")
        return regex.replace(raw) { match ->
            "${match.groupValues[1]}], "
        }
    }

    private fun repairUnbalancedBrackets(raw: String): String {
        val stack = mutableListOf<Char>()
        val builder = StringBuilder()
        var inString = false
        var escape = false
        var rootStarted = false

        for ((index, char) in raw.withIndex()) {
            if (escape) {
                escape = false
                builder.append(char)
                continue
            }
            if (char == '\\') {
                escape = true
                builder.append(char)
                continue
            }
            if (char == '"') {
                inString = !inString
                builder.append(char)
                continue
            }
            if (inString) {
                builder.append(char)
                continue
            }

            when (char) {
                '{' -> {
                    rootStarted = true
                    stack.add('}')
                    builder.append(char)
                }
                '[' -> {
                    stack.add(']')
                    builder.append(char)
                }
                '}', ']' -> {
                    val stopBoundary = if (char == '}') ']' else '}'
                    var matchIdx = -1
                    for (i in stack.lastIndex downTo 0) {
                        if (stack[i] == stopBoundary) break
                        if (stack[i] == char) {
                            matchIdx = i
                            break
                        }
                    }

                    if (matchIdx >= 0) {
                        // Check if closing brace '}' is premature because it is followed by a comma and property key
                        // but the remaining stack context after popping would be empty or an array (where key:value is invalid).
                        if (char == '}' && followedByPropertyKeyRegex.containsMatchIn(raw.substring(index + 1))) {
                            val wouldBeInArrayOrEmpty = matchIdx == 0 || (matchIdx > 0 && stack[matchIdx - 1] == ']')
                            if (wouldBeInArrayOrEmpty) {
                                // Discard premature closing brace
                                continue
                            }
                        }
                        while (stack.size > matchIdx) {
                            builder.append(stack.removeAt(stack.lastIndex))
                        }
                        if (rootStarted && stack.isEmpty()) {
                            // Closed root JSON object. Discard trailing junk outside root.
                            break
                        }
                    } else {
                        // Extra/stray closing char with no matching open context in current scope — discard!
                    }
                }
                else -> builder.append(char)
            }
        }

        while (stack.isNotEmpty()) {
            builder.append(stack.removeAt(stack.lastIndex))
        }

        return builder.toString()
    }

    private fun sanitizeLottieRoot(root: JsonObject): JsonObject {
        val rawWidth = root["w"]?.jsonPrimitive?.intOrNull ?: 240
        val rawHeight = root["h"]?.jsonPrimitive?.intOrNull ?: 240

        val targetWidth = 240
        val targetHeight = 240
        val scaleFactorX = if (rawWidth > 0 && rawWidth != targetWidth) targetWidth.toFloat() / rawWidth else 1.0f
        val scaleFactorY = if (rawHeight > 0 && rawHeight != targetHeight) targetHeight.toFloat() / rawHeight else 1.0f

        val fps = (root["fr"]?.jsonPrimitive?.intOrNull ?: 60).coerceIn(15, 60)
        val ip = (root["ip"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
        val rawOp = root["op"]?.jsonPrimitive?.intOrNull ?: (fps * 2)
        val op = if (rawOp - ip > 180 || rawOp <= ip) (ip + fps * 2).coerceAtMost(180) else rawOp

        val layersArray = root["layers"]?.jsonArray
        val sanitizedLayers = buildJsonArray {
            layersArray?.forEachIndexed { index, layer ->
                if (layer is JsonObject) {
                    add(sanitizeLayer(layer, index + 1, targetWidth, targetHeight, scaleFactorX, scaleFactorY))
                }
            }
        }

        return buildJsonObject {
            root.forEach { (k, v) ->
                if (k != "layers" && k != "assets") {
                    put(k, v)
                }
            }
            put("v", JsonPrimitive(root["v"]?.jsonPrimitive?.content ?: "5.7.4"))
            put("fr", JsonPrimitive(fps as Number))
            put("ip", JsonPrimitive(ip as Number))
            put("op", JsonPrimitive(op as Number))
            put("w", JsonPrimitive(targetWidth as Number))
            put("h", JsonPrimitive(targetHeight as Number))
            put("nm", JsonPrimitive(root["nm"]?.jsonPrimitive?.content ?: "Lottie Animation"))
            put("ddd", JsonPrimitive(0 as Number))
            put("assets", buildJsonArray { })
            put("layers", sanitizedLayers)
        }
    }

    private fun sanitizeLayer(
        layer: JsonObject,
        index: Int,
        width: Int,
        height: Int,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonObject {
        val ksObj = layer["ks"] as? JsonObject
        val sanitizedKs = sanitizeTransform(ksObj, width, height, scaleFactorX, scaleFactorY)

        val shapesArray = layer["shapes"] as? JsonArray
        val sanitizedShapes = buildJsonArray {
            shapesArray?.forEach { shape ->
                if (shape is JsonObject) {
                    add(sanitizeShapeGroup(shape, scaleFactorX, scaleFactorY))
                }
            }
        }

        val layerIp = (layer["ip"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
        val layerOp = (layer["op"]?.jsonPrimitive?.intOrNull ?: 120).coerceIn(layerIp + 1, 180)

        return buildJsonObject {
            layer.forEach { (k, v) ->
                if (k != "ks" && k != "shapes" && k != "ip" && k != "op") {
                    put(k, v)
                }
            }
            put("ind", JsonPrimitive((layer["ind"]?.jsonPrimitive?.intOrNull ?: index) as Number))
            put("ty", JsonPrimitive((layer["ty"]?.jsonPrimitive?.intOrNull ?: 4) as Number))
            put("ddd", JsonPrimitive(0 as Number))
            put("sr", JsonPrimitive(1 as Number))
            put("ao", JsonPrimitive(0 as Number))
            put("bm", JsonPrimitive(0 as Number))
            put("st", JsonPrimitive(0 as Number))
            put("ip", JsonPrimitive(layerIp as Number))
            put("op", JsonPrimitive(layerOp as Number))
            put("ks", sanitizedKs)
            put("shapes", sanitizedShapes)
        }
    }

    private fun sanitizeTransform(
        ks: JsonObject?,
        width: Int,
        height: Int,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonObject {
        val rawP = ks?.get("p")
        val sanitizedP = sanitizePosition(rawP, width, height, scaleFactorX, scaleFactorY)
        val rawS = ks?.get("s")
        val sanitizedS = sanitizeScale(rawS)

        return buildJsonObject {
            put("p", sanitizedP)
            put("a", sanitizeProperty(ks?.get("a"), staticVector3(0f, 0f, 0f)))
            put("s", sanitizedS)
            put("r", sanitizeScalarProperty(ks?.get("r"), 0f))
            put("o", sanitizeOpacity(ks?.get("o")))
        }
    }

    private fun sanitizePosition(element: JsonElement?, width: Int, height: Int, scaleFactorX: Float, scaleFactorY: Float): JsonElement {
        if (element == null) return staticVector3((width / 2).toFloat(), (height / 2).toFloat(), 0f)
        if (element is JsonObject) {
            val k = element["k"]
            if (k is JsonArray) {
                val coords = k.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                if (coords.size >= 2) {
                    val px = coords[0] * scaleFactorX
                    val py = coords[1] * scaleFactorY
                    val pz = coords.getOrNull(2) ?: 0f
                    return buildJsonObject {
                        element.forEach { (key, valElement) ->
                            when (key) {
                                "a" -> put("a", JsonPrimitive(0 as Number))
                                "k" -> put("k", buildJsonArray {
                                    add(JsonPrimitive(px as Number))
                                    add(JsonPrimitive(py as Number))
                                    add(JsonPrimitive(pz as Number))
                                })
                                else -> put(key, valElement)
                            }
                        }
                        if (!element.containsKey("a")) {
                            put("a", JsonPrimitive(0 as Number))
                        }
                    }
                }
            }
        }
        return element
    }

    private fun sanitizeScale(element: JsonElement?): JsonElement {
        if (element == null) return staticVector3(100f, 100f, 100f)
        if (element is JsonObject) {
            val a = element["a"]?.jsonPrimitive?.intOrNull ?: 0
            val k = element["k"]
            val isKeyframeArray = k is JsonArray && k.isNotEmpty() && k.all { it is JsonObject }
            if (a == 1 && isKeyframeArray) {
                val sanitizedKf = (k as JsonArray).map { kf ->
                    if (kf is JsonObject) {
                        buildJsonObject {
                            kf.forEach { (key, valElement) ->
                                if (key == "s" && valElement is JsonArray) {
                                    val sList = valElement.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                                    val maxVal = sList.maxOrNull() ?: 100f
                                    val mult = if (maxVal <= 10f && maxVal > 0f) 100f else 1f
                                    val sx = (sList.getOrNull(0) ?: 100f) * mult
                                    val sy = (sList.getOrNull(1) ?: sx) * mult
                                    val sz = (sList.getOrNull(2) ?: 100f) * mult
                                    put("s", buildJsonArray {
                                        add(formatNumber(sx))
                                        add(formatNumber(sy))
                                        add(formatNumber(sz))
                                    })
                                } else {
                                    put(key, valElement)
                                }
                            }
                        }
                    } else kf
                }
                return buildJsonObject {
                    element.forEach { (key, valElement) ->
                        if (key == "k") put("k", buildJsonArray { sanitizedKf.forEach { add(it) } })
                        else put(key, valElement)
                    }
                }
            } else if (k is JsonArray) {
                val scales = k.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                if (scales.isNotEmpty()) {
                    val maxVal = scales.maxOrNull() ?: 100f
                    val multiplier = if (maxVal <= 10f && maxVal > 0f) 100f else 1f
                    val sx = (scales.getOrNull(0) ?: 100f) * multiplier
                    val sy = (scales.getOrNull(1) ?: sx) * multiplier
                    val sz = (scales.getOrNull(2) ?: 100f) * multiplier
                    return buildJsonObject {
                        element.forEach { (key, valElement) ->
                            when (key) {
                                "a" -> put("a", JsonPrimitive(0 as Number))
                                "k" -> put("k", buildJsonArray {
                                    add(formatNumber(sx))
                                    add(formatNumber(sy))
                                    add(formatNumber(sz))
                                })
                                else -> put(key, valElement)
                            }
                        }
                        if (!element.containsKey("a")) {
                            put("a", JsonPrimitive(0 as Number))
                        }
                    }
                }
            }
        }
        return element
    }

    private fun sanitizeShapeGroup(shapeGroup: JsonObject, scaleFactorX: Float, scaleFactorY: Float): JsonObject {
        val ty = shapeGroup["ty"]?.jsonPrimitive?.content ?: "gr"
        val itArray = shapeGroup["it"] as? JsonArray

        val expandedItems = mutableListOf<JsonObject>()
        itArray?.forEach { child ->
            if (child is JsonObject) {
                expandedItems.addAll(flattenAndSanitizeShapeItem(child, scaleFactorX, scaleFactorY))
            }
        }
        val normalizedItems = if (ty == "gr" && expandedItems.none(::isShapeTransform)) {
            expandedItems + defaultShapeTransform()
        } else {
            expandedItems
        }

        return buildJsonObject {
            shapeGroup.forEach { (k, v) ->
                if (k != "it") put(k, v)
            }
            put("ty", JsonPrimitive(ty))
            put("it", buildJsonArray {
                normalizedItems.forEach { add(it) }
            })
        }
    }

    private fun flattenAndSanitizeShapeItem(
        shape: JsonObject,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val ty = shape["ty"]?.jsonPrimitive?.content ?: "gr"

        var nestedFill: JsonObject? = null
        var nestedStroke: JsonObject? = null

        val cleanedProps = buildJsonObject {
            shape.forEach { (k, v) ->
                when (k) {
                    "fl" -> if (v is JsonObject) nestedFill = v
                    "st" -> if (v is JsonObject) nestedStroke = v
                    "c" -> if (ty == "fl" || ty == "st") put("c", sanitizeColor(v))
                    "o" -> if (ty == "fl" || ty == "st" || ty == "tr" || ty == "gr") put("o", sanitizeOpacity(v))
                    "ks" -> if (ty == "sh") put("ks", sanitizeShapePath(v, scaleFactorX, scaleFactorY)) else put(k, v)
                    "s" -> when (ty) {
                        "el", "rc", "sr" -> put("s", rescaleShapeSize(v, scaleFactorX, scaleFactorY))
                        "tr" -> put("s", sanitizeShapeScale(v))
                        else -> put(k, v)
                    }
                    "p" -> if (ty == "el" || ty == "rc" || ty == "sr" || ty == "tr" || ty == "sh") put("p", rescaleShapePosition(v, scaleFactorX, scaleFactorY))
                    "tr" -> if (v is JsonObject) put("tr", sanitizeTransform(v, 0, 0, scaleFactorX, scaleFactorY)) else put(k, v)
                    "it" -> {
                        val items = v as? JsonArray
                        val flattenedChildren = mutableListOf<JsonObject>()
                        items?.forEach { child ->
                            if (child is JsonObject) {
                                flattenedChildren.addAll(flattenAndSanitizeShapeItem(child, scaleFactorX, scaleFactorY))
                            }
                        }
                        put("it", buildJsonArray {
                            flattenedChildren.forEach { add(it) }
                            if (ty == "gr" && flattenedChildren.none(::isShapeTransform)) {
                                add(defaultShapeTransform())
                            }
                        })
                    }
                    "l" -> { /* Ignore stray location property on fill/stroke */ }
                    else -> put(k, v)
                }
            }
            put("ty", JsonPrimitive(ty))
        }

        result.add(
            if (ty == "tr") {
                sanitizeShapeTransform(cleanedProps, scaleFactorX, scaleFactorY)
            } else {
                cleanedProps
            }
        )

        // If shape contained nested fill, extract it as standalone shape item
        nestedFill?.let { fillObj ->
            result.add(buildJsonObject {
                put("ty", JsonPrimitive("fl"))
                put("c", sanitizeColor(fillObj["c"]))
                put("o", sanitizeOpacity(fillObj["o"]))
            })
        }

        // If shape contained nested stroke, extract it as standalone shape item
        nestedStroke?.let { strokeObj ->
            result.add(buildJsonObject {
                put("ty", JsonPrimitive("st"))
                put("c", sanitizeColor(strokeObj["c"]))
                put("o", sanitizeOpacity(strokeObj["o"]))
                strokeObj["w"]?.let { put("w", it) }
            })
        }

        return result
    }

    private fun sanitizeShapeTransform(
        transform: JsonObject,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonObject {
        return buildJsonObject {
            transform.forEach { (key, value) -> put(key, value) }
            put("ty", JsonPrimitive("tr"))
            put("p", normalizeShapeVector(transform["p"], staticVector2(0f, 0f), scaleFactorX, scaleFactorY))
            put("a", normalizeShapeVector(transform["a"], staticVector2(0f, 0f), 1f, 1f))
            put("s", sanitizeShapeScale(transform["s"]))
            put("r", sanitizeScalarProperty(transform["r"], 0f))
            put("o", sanitizeOpacity(transform["o"]))
        }
    }

    private fun sanitizeShapePath(
        element: JsonElement?,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonElement {
        val property = element as? JsonObject ?: return staticShapePath()
        val rawK = property["k"]
        val normalizedK: JsonElement = when {
            rawK is JsonArray && rawK.isPointArrayPath() -> {
                normalizePointArrayPath(rawK, scaleFactorX, scaleFactorY)
            }
            rawK is JsonObject -> {
                normalizeShapePathObject(rawK, scaleFactorX, scaleFactorY)
            }
            rawK != null -> rawK
            else -> defaultShapePathObject()
        }
        val staticPath = normalizedK is JsonObject
        val animatedFlag = if (staticPath) {
            0
        } else {
            property["a"]?.jsonPrimitive?.intOrNull ?: 1
        }

        return buildJsonObject {
            property.forEach { (key, value) ->
                when (key) {
                    "a" -> put("a", JsonPrimitive(animatedFlag as Number))
                    "k" -> put("k", normalizedK)
                    else -> put(key, value)
                }
            }
            if (!property.containsKey("a")) {
                put("a", JsonPrimitive(animatedFlag as Number))
            }
            if (!property.containsKey("k")) {
                put("k", normalizedK)
            }
        }
    }

    private fun JsonArray.isPointArrayPath(): Boolean {
        return isNotEmpty() && all { point ->
            point is JsonObject &&
                point["v"] is JsonArray &&
                (point["v"] as JsonArray).size >= 2
        }
    }

    private fun normalizePointArrayPath(
        points: JsonArray,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonObject {
        val vertices = mutableListOf<Pair<Float, Float>>()
        val inTangents = mutableListOf<Pair<Float, Float>>()
        val outTangents = mutableListOf<Pair<Float, Float>>()
        var closed = false

        points.forEach { pointElement ->
            val point = pointElement as? JsonObject ?: return@forEach
            val vertex = point["v"].vector2OrNull(scaleFactorX, scaleFactorY) ?: return@forEach
            vertices.add(vertex)
            inTangents.add(point["i"].vector2OrNull(scaleFactorX, scaleFactorY) ?: (0f to 0f))
            outTangents.add(point["o"].vector2OrNull(scaleFactorX, scaleFactorY) ?: (0f to 0f))
            closed = closed || point["c"].isTruthy()
        }

        if (vertices.size > 1 && sameVector(vertices.first(), vertices.last())) {
            vertices.removeAt(vertices.lastIndex)
            inTangents.removeAt(inTangents.lastIndex)
            outTangents.removeAt(outTangents.lastIndex)
            closed = true
        }

        return shapePathObject(
            vertices = vertices,
            inTangents = inTangents.padVectors(vertices.size),
            outTangents = outTangents.padVectors(vertices.size),
            closed = closed
        )
    }

    private fun normalizeShapePathObject(
        path: JsonObject,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonObject {
        val vertices = path["v"].vectorArrayOrNull(scaleFactorX, scaleFactorY)
            ?: return path
        val inTangents = path["i"].vectorArrayOrNull(scaleFactorX, scaleFactorY)
            .orEmpty()
            .padVectors(vertices.size)
        val outTangents = path["o"].vectorArrayOrNull(scaleFactorX, scaleFactorY)
            .orEmpty()
            .padVectors(vertices.size)
        val closed = path["c"].isTruthy()

        return shapePathObject(
            vertices = vertices,
            inTangents = inTangents,
            outTangents = outTangents,
            closed = closed
        )
    }

    private fun shapePathObject(
        vertices: List<Pair<Float, Float>>,
        inTangents: List<Pair<Float, Float>>,
        outTangents: List<Pair<Float, Float>>,
        closed: Boolean
    ): JsonObject {
        val safeVertices = vertices.ifEmpty {
            listOf(0f to 0f, 1f to 0f, 0f to 1f)
        }
        return buildJsonObject {
            put("i", vectorArray(inTangents.padVectors(safeVertices.size)))
            put("o", vectorArray(outTangents.padVectors(safeVertices.size)))
            put("v", vectorArray(safeVertices))
            put("c", JsonPrimitive(closed))
        }
    }

    private fun staticShapePath(): JsonObject {
        return buildJsonObject {
            put("a", JsonPrimitive(0 as Number))
            put("k", defaultShapePathObject())
        }
    }

    private fun defaultShapePathObject(): JsonObject {
        return shapePathObject(
            vertices = listOf(0f to 0f, 1f to 0f, 0f to 1f),
            inTangents = emptyList(),
            outTangents = emptyList(),
            closed = true
        )
    }

    private fun JsonElement?.vector2OrNull(
        scaleFactorX: Float,
        scaleFactorY: Float
    ): Pair<Float, Float>? {
        val array = this as? JsonArray ?: return null
        val x = array.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: return null
        val y = array.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: return null
        return (x * scaleFactorX) to (y * scaleFactorY)
    }

    private fun JsonElement?.vectorArrayOrNull(
        scaleFactorX: Float,
        scaleFactorY: Float
    ): List<Pair<Float, Float>>? {
        val array = this as? JsonArray ?: return null
        return array.mapNotNull { it.vector2OrNull(scaleFactorX, scaleFactorY) }
            .takeIf { it.isNotEmpty() }
    }

    private fun List<Pair<Float, Float>>.padVectors(size: Int): List<Pair<Float, Float>> {
        if (size <= 0) return emptyList()
        if (this.size >= size) return take(size)
        return this + List(size - this.size) { 0f to 0f }
    }

    private fun vectorArray(vectors: List<Pair<Float, Float>>): JsonArray {
        return buildJsonArray {
            vectors.forEach { vector ->
                add(buildJsonArray {
                    add(formatNumber(vector.first))
                    add(formatNumber(vector.second))
                })
            }
        }
    }

    private fun sameVector(first: Pair<Float, Float>, second: Pair<Float, Float>): Boolean {
        return abs(first.first - second.first) < 0.001f &&
            abs(first.second - second.second) < 0.001f
    }

    private fun JsonElement?.isTruthy(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return primitive.content.equals("true", ignoreCase = true) ||
            primitive.intOrNull == 1
    }

    private fun isShapeTransform(shape: JsonObject): Boolean {
        return shape["ty"]?.jsonPrimitive?.content == "tr"
    }

    private fun defaultShapeTransform(): JsonObject {
        return buildJsonObject {
            put("ty", JsonPrimitive("tr"))
            put("p", staticVector2(0f, 0f))
            put("a", staticVector2(0f, 0f))
            put("s", staticVector2(100f, 100f))
            put("r", staticScalar(0f))
            put("o", staticScalar(100f))
        }
    }

    private fun normalizeShapeVector(
        element: JsonElement?,
        fallback: JsonElement,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): JsonElement {
        val property = element as? JsonObject ?: return fallback
        if ((property["a"]?.jsonPrimitive?.intOrNull ?: 0) != 0) return fallback
        val values = (property["k"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
            ?: return fallback
        if (values.size < 2) return fallback

        return buildJsonObject {
            property.forEach { (key, value) ->
                if (key == "k") {
                    put("k", buildJsonArray {
                        add(formatNumber(values[0] * scaleFactorX))
                        add(formatNumber(values[1] * scaleFactorY))
                    })
                } else {
                    put(key, value)
                }
            }
        }
    }

    private fun sanitizeShapeScale(element: JsonElement?): JsonElement {
        val property = element as? JsonObject ?: return staticVector2(100f, 100f)
        val animated = property["a"]?.jsonPrimitive?.intOrNull == 1
        val keyframes = property["k"] as? JsonArray ?: return staticVector2(100f, 100f)

        if (!animated) {
            val values = keyframes.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
            return buildJsonObject {
                property.forEach { (key, value) ->
                    if (key == "k") {
                        put("k", normalizedScaleVector(values))
                    } else {
                        put(key, value)
                    }
                }
            }
        }

        if (keyframes.isEmpty() || keyframes.any { it !is JsonObject }) {
            return staticVector2(100f, 100f)
        }

        return buildJsonObject {
            property.forEach { (key, value) ->
                if (key == "k") {
                    put("k", buildJsonArray {
                        keyframes.forEach { keyframe ->
                            val frame = keyframe.jsonObject
                            buildJsonObject {
                                frame.forEach { frameKey, frameValue ->
                                    when (frameKey) {
                                        "s", "e" -> {
                                            val values = (frameValue as? JsonArray)
                                                ?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                                                .orEmpty()
                                            put(frameKey, normalizedScaleVector(values))
                                        }
                                        else -> put(frameKey, frameValue)
                                    }
                                }
                            }.also(::add)
                        }
                    })
                } else {
                    put(key, value)
                }
            }
        }
    }

    private fun normalizedScaleVector(values: List<Float>): JsonArray {
        if (values.size < 2) return buildJsonArray {
            add(formatNumber(100f))
            add(formatNumber(100f))
        }

        val maxValue = values.maxOrNull() ?: 0f
        val multiplier = if (maxValue in 0.0001f..10f) 100f else 1f
        val sx = (values[0] * multiplier).coerceIn(1f, 1000f)
        val sy = (values[1] * multiplier).coerceIn(1f, 1000f)
        return buildJsonArray {
            add(formatNumber(sx))
            add(formatNumber(sy))
        }
    }

    private fun rescaleShapeSize(element: JsonElement?, scaleFactorX: Float, scaleFactorY: Float): JsonElement {
        if (element is JsonObject) {
            val k = element["k"]
            if (k is JsonArray) {
                val sizes = k.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                if (sizes.size >= 2) {
                    val sx = sizes[0] * scaleFactorX
                    val sy = sizes[1] * scaleFactorY
                    return buildJsonObject {
                        element.forEach { (key, valElement) ->
                            if (key == "k") put("k", buildJsonArray {
                                add(formatNumber(sx))
                                add(formatNumber(sy))
                            }) else put(key, valElement)
                        }
                    }
                }
            }
        }
        return element ?: staticVector2(100f, 100f)
    }

    private fun rescaleShapePosition(element: JsonElement?, scaleFactorX: Float, scaleFactorY: Float): JsonElement {
        if (element is JsonObject) {
            val k = element["k"]
            if (k is JsonArray) {
                val pos = k.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                if (pos.size >= 2) {
                    val px = pos[0] * scaleFactorX
                    val py = pos[1] * scaleFactorY
                    return buildJsonObject {
                        element.forEach { (key, valElement) ->
                            if (key == "k") put("k", buildJsonArray {
                                add(formatNumber(px))
                                add(formatNumber(py))
                            }) else put(key, valElement)
                        }
                    }
                }
            }
        }
        return element ?: staticVector2(0f, 0f)
    }

    private fun sanitizeColor(colorElement: JsonElement?): JsonElement {
        if (colorElement == null) return staticColor(0.2f, 0.6f, 1.0f, 1.0f)
        if (colorElement is JsonObject) {
            val k = colorElement["k"]
            if (k is JsonArray) {
                val normalizedArray = normalizeColorArray(k)
                return buildJsonObject {
                    colorElement.forEach { (key, valElement) ->
                        if (key == "k") put("k", normalizedArray) else put(key, valElement)
                    }
                }
            }
        }
        return colorElement
    }

    private fun normalizeColorArray(kArray: JsonArray): JsonArray {
        val numbers = kArray.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
        if (numbers.isEmpty()) return buildJsonArray {
            add(formatNumber(0.2f))
            add(formatNumber(0.6f))
            add(formatNumber(1.0f))
            add(formatNumber(1.0f))
        }

        val maxVal = numbers.maxOrNull() ?: 1.0f
        val scaleFactor = if (maxVal > 1.0f) 255.0f else 1.0f

        val r = (numbers.getOrNull(0) ?: 0f) / scaleFactor
        val g = (numbers.getOrNull(1) ?: 0f) / scaleFactor
        val b = (numbers.getOrNull(2) ?: 0f) / scaleFactor
        val a = if (numbers.size >= 4) (numbers[3] / (if (numbers[3] > 1.0f) 255.0f else 1.0f)) else 1.0f

        return buildJsonArray {
            add(formatNumber(r.coerceIn(0f, 1f)))
            add(formatNumber(g.coerceIn(0f, 1f)))
            add(formatNumber(b.coerceIn(0f, 1f)))
            add(formatNumber(a.coerceIn(0f, 1f)))
        }
    }

    private fun sanitizeOpacity(opacityElement: JsonElement?): JsonElement {
        if (opacityElement == null) return staticScalar(100f)
        if (opacityElement is JsonObject) {
            val k = opacityElement["k"]
            if (k is JsonPrimitive) {
                val value = k.floatOrNull ?: 100f
                val clamped = if (value > 100f) 100f else value.coerceAtLeast(0f)
                return buildJsonObject {
                    opacityElement.forEach { (key, valElement) ->
                        if (key == "k") put("k", formatNumber(clamped)) else put(key, valElement)
                    }
                }
            }
        }
        return opacityElement
    }

    private fun sanitizeProperty(element: JsonElement?, fallback: JsonElement): JsonElement {
        return element ?: fallback
    }

    private fun sanitizeScalarProperty(element: JsonElement?, fallback: Float): JsonElement {
        val property = element as? JsonObject ?: return staticScalar(fallback)
        val k = property["k"]
        val scalar = when (k) {
            is JsonPrimitive -> k.floatOrNull
            is JsonArray -> (k.firstOrNull() as? JsonPrimitive)?.floatOrNull
            else -> null
        }

        if (scalar == null) return property

        return buildJsonObject {
            property.forEach { (key, value) ->
                when (key) {
                    "a" -> put("a", JsonPrimitive(0 as Number))
                    "k" -> put("k", formatNumber(scalar))
                    else -> put(key, value)
                }
            }
            if (!property.containsKey("a")) {
                put("a", JsonPrimitive(0 as Number))
            }
        }
    }

    private fun staticVector2(x: Float, y: Float): JsonObject {
        return buildJsonObject {
            put("a", JsonPrimitive(0 as Number))
            put("k", buildJsonArray {
                add(formatNumber(x))
                add(formatNumber(y))
            })
        }
    }

    private fun staticVector3(x: Float, y: Float, z: Float): JsonObject {
        return buildJsonObject {
            put("a", JsonPrimitive(0 as Number))
            put("k", buildJsonArray {
                add(formatNumber(x))
                add(formatNumber(y))
                add(formatNumber(z))
            })
        }
    }

    private fun staticScalar(value: Float): JsonObject {
        return buildJsonObject {
            put("a", JsonPrimitive(0 as Number))
            put("k", formatNumber(value))
        }
    }

    private fun staticColor(r: Float, g: Float, b: Float, a: Float): JsonObject {
        return buildJsonObject {
            put("a", JsonPrimitive(0 as Number))
            put("k", buildJsonArray {
                add(formatNumber(r))
                add(formatNumber(g))
                add(formatNumber(b))
                add(formatNumber(a))
            })
        }
    }

    private fun formatNumber(value: Float): JsonPrimitive {
        val rounded = (value * 1000f).toInt() / 1000f
        return if (rounded % 1f == 0f) {
            JsonPrimitive(rounded.toInt() as Number)
        } else {
            JsonPrimitive(rounded as Number)
        }
    }

    private fun Float.n(): String {
        return ((this * 1000f).toInt() / 1000f).toString()
    }
}
