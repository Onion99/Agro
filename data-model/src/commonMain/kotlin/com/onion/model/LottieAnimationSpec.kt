package com.onion.model

import kotlinx.serialization.Serializable

@Serializable
data class LottieAnimationSpec(
    val type: String,
    val schemaVersion: Int = 1,
    val title: String,
    val seed: Int? = null,
    val canvas: LottieCanvasSpec,
    val fps: Int,
    val durationMs: Long,
    val loop: Boolean,
    val kind: String,
    val palette: LottiePaletteSpec,
    val motion: LottieMotionSpec,
    val stroke: LottieStrokeSpec? = null
)

@Serializable
data class LottieCanvasSpec(
    val width: Int,
    val height: Int,
    val background: String = "transparent"
)

@Serializable
data class LottiePaletteSpec(
    val primary: String,
    val secondary: String? = null,
    val accent: String? = null
)

@Serializable
data class LottieMotionSpec(
    val style: String,
    val intensity: Float = 0.6f,
    val staggerMs: Int = 0
)

@Serializable
data class LottieStrokeSpec(
    val width: Int = 8,
    val lineCap: String = "round"
)
