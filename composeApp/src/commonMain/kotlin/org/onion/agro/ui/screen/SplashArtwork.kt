package org.onion.agro.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.onion.theme.style.glassSurface
import ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun AnimatedAppIconSeed(
    modifier: Modifier,
    progress: Float,
    ambientMotion: Float,
) {
    val tileReveal = progress.segment(0f, 0.38f)
    Box(
        modifier = modifier
            .scale(0.82f + tileReveal * 0.18f)
            .alpha(tileReveal)
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha,
            ),
    ) {
        AppIconCanvas(
            modifier = Modifier.fillMaxSize(),
            progress = progress,
            ambientMotion = ambientMotion,
        )
    }
}

@Composable
private fun AppIconCanvas(
    modifier: Modifier,
    progress: Float,
    ambientMotion: Float,
) {
    val colors = AppTheme.colors
    val borderWidth = AppTheme.size.borderWidth
    val thinLine = AppTheme.size.borderWidthThin
    Canvas(modifier = modifier) {
        val markSize = size.minDimension
        val phase = ambientMotion * 2f * PI.toFloat()
        val floatOffset = sin(phase) * markSize * 0.012f
        val orbReveal = progress.segment(0.10f, 0.52f)
        val spiritReveal = progress.segment(0.22f, 0.62f)
        val sageReveal = progress.segment(0.32f, 0.72f)
        val blueReveal = progress.segment(0.42f, 0.82f)
        val crownReveal = progress.segment(0.58f, 0.90f)
        val lightReveal = progress.segment(0.72f, 1f)

        fun p(x: Float, y: Float) = Offset(markSize * x, markSize * y + floatOffset)

        val leftWash = Path().apply {
            moveTo(p(0.09f, 0.68f))
            cubicTo(p(0.16f, 0.57f), p(0.30f, 0.52f), p(0.43f, 0.57f))
            cubicTo(p(0.55f, 0.62f), p(0.57f, 0.74f), p(0.48f, 0.83f))
            cubicTo(p(0.39f, 0.92f), p(0.22f, 0.89f), p(0.13f, 0.81f))
            cubicTo(p(0.08f, 0.76f), p(0.07f, 0.71f), p(0.09f, 0.68f))
            close()
        }
        drawPath(
            path = leftWash,
            brush = Brush.linearGradient(
                colors = listOf(
                    colors.primaryFixed.copy(alpha = 0.46f),
                    colors.primaryContainer.copy(alpha = 0.36f),
                ),
                start = p(0.09f, 0.57f),
                end = p(0.55f, 0.86f),
            ),
        )
        drawCircle(
            color = colors.secondaryFixed.copy(alpha = 0.38f),
            center = p(0.73f, 0.30f),
            radius = markSize * 0.16f,
        )

        val orbCenter = p(0.50f, 0.49f)
        val orbRadius = markSize * 0.29f
        drawOval(
            color = colors.tertiary.copy(alpha = 0.11f * orbReveal),
            topLeft = p(0.29f, 0.79f),
            size = androidx.compose.ui.geometry.Size(markSize * 0.42f, markSize * 0.09f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.surfaceBright.copy(alpha = 0.97f * orbReveal),
                    colors.surfaceContainerLow.copy(alpha = 0.82f * orbReveal),
                    colors.secondaryFixed.copy(alpha = 0.24f * orbReveal),
                ),
                center = p(0.37f, 0.30f),
                radius = markSize * 0.46f,
            ),
            center = orbCenter,
            radius = orbRadius,
        )
        drawCircle(
            color = colors.surfaceBright.copy(alpha = 0.74f * orbReveal),
            center = orbCenter,
            radius = orbRadius,
            style = Stroke(width = borderWidth.toPx()),
        )

        val spirit = Path().apply {
            moveTo(p(0.50f, 0.20f))
            cubicTo(p(0.62f, 0.20f), p(0.71f, 0.30f), p(0.71f, 0.44f))
            cubicTo(p(0.71f, 0.62f), p(0.60f, 0.75f), p(0.50f, 0.82f))
            cubicTo(p(0.40f, 0.75f), p(0.29f, 0.62f), p(0.29f, 0.44f))
            cubicTo(p(0.29f, 0.30f), p(0.38f, 0.20f), p(0.50f, 0.20f))
            close()
        }
        withTransform({ scale(spiritReveal, spiritReveal, pivot = p(0.50f, 0.82f)) }) {
            drawPath(
                path = spirit,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceBright.copy(alpha = 0.78f),
                        colors.secondaryFixed.copy(alpha = 0.30f),
                    ),
                ),
            )
        }

        val seed = Path().apply {
            moveTo(p(0.50f, 0.30f))
            cubicTo(p(0.57f, 0.39f), p(0.57f, 0.49f), p(0.50f, 0.57f))
            cubicTo(p(0.43f, 0.49f), p(0.43f, 0.39f), p(0.50f, 0.30f))
            close()
        }
        withTransform({ scale(spiritReveal, spiritReveal, pivot = p(0.50f, 0.57f)) }) {
            drawPath(
                path = seed,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.surfaceBright, colors.primaryFixed),
                ),
            )
        }

        val sageLeaf = Path().apply {
            moveTo(p(0.28f, 0.77f))
            cubicTo(p(0.34f, 0.60f), p(0.40f, 0.49f), p(0.49f, 0.41f))
            cubicTo(p(0.45f, 0.57f), p(0.40f, 0.69f), p(0.28f, 0.77f))
            close()
        }
        withTransform({
            scale(sageReveal, sageReveal, pivot = p(0.28f, 0.77f))
            rotate(-8f * (1f - sageReveal), pivot = p(0.28f, 0.77f))
        }) {
            drawPath(
                path = sageLeaf,
                brush = Brush.linearGradient(
                    colors = listOf(colors.primary, colors.primaryFixed.copy(alpha = 0.72f)),
                    start = p(0.28f, 0.77f),
                    end = p(0.49f, 0.41f),
                ),
            )
        }

        val blueLeaf = Path().apply {
            moveTo(p(0.28f, 0.77f))
            cubicTo(p(0.40f, 0.72f), p(0.55f, 0.60f), p(0.65f, 0.43f))
            cubicTo(p(0.61f, 0.59f), p(0.50f, 0.71f), p(0.28f, 0.77f))
            close()
        }
        withTransform({
            scale(blueReveal, blueReveal, pivot = p(0.28f, 0.77f))
            rotate(10f * (1f - blueReveal), pivot = p(0.28f, 0.77f))
        }) {
            drawPath(
                path = blueLeaf,
                brush = Brush.linearGradient(
                    colors = listOf(colors.secondary, colors.secondaryFixed.copy(alpha = 0.72f)),
                    start = p(0.28f, 0.77f),
                    end = p(0.65f, 0.43f),
                ),
            )
            drawLine(
                color = colors.surfaceBright.copy(alpha = 0.40f),
                start = p(0.36f, 0.71f),
                end = p(0.60f, 0.49f),
                strokeWidth = thinLine.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val crownLeaf = Path().apply {
            moveTo(p(0.57f, 0.28f))
            cubicTo(p(0.63f, 0.22f), p(0.70f, 0.22f), p(0.74f, 0.28f))
            cubicTo(p(0.68f, 0.32f), p(0.62f, 0.31f), p(0.57f, 0.28f))
            close()
        }
        withTransform({ scale(crownReveal, crownReveal, pivot = p(0.57f, 0.28f)) }) {
            drawPath(
                path = crownLeaf,
                brush = Brush.horizontalGradient(
                    colors = listOf(colors.primaryContainer, colors.secondaryContainer),
                    startX = p(0.57f, 0f).x,
                    endX = p(0.74f, 0f).x,
                ),
            )
        }

        val sparkleCenter = p(0.57f, 0.34f)
        val sparkle = Path().apply {
            moveTo(Offset(sparkleCenter.x, sparkleCenter.y - markSize * 0.07f))
            lineTo(Offset(sparkleCenter.x + markSize * 0.02f, sparkleCenter.y - markSize * 0.02f))
            lineTo(Offset(sparkleCenter.x + markSize * 0.07f, sparkleCenter.y))
            lineTo(Offset(sparkleCenter.x + markSize * 0.02f, sparkleCenter.y + markSize * 0.02f))
            lineTo(Offset(sparkleCenter.x, sparkleCenter.y + markSize * 0.07f))
            lineTo(Offset(sparkleCenter.x - markSize * 0.02f, sparkleCenter.y + markSize * 0.02f))
            lineTo(Offset(sparkleCenter.x - markSize * 0.07f, sparkleCenter.y))
            lineTo(Offset(sparkleCenter.x - markSize * 0.02f, sparkleCenter.y - markSize * 0.02f))
            close()
        }
        withTransform({ scale(lightReveal, lightReveal, pivot = sparkleCenter) }) {
            drawPath(sparkle, color = colors.surfaceBright.copy(alpha = 0.98f))
        }
    }
}

private fun Path.moveTo(point: Offset) {
    moveTo(point.x, point.y)
}

private fun Path.lineTo(point: Offset) {
    lineTo(point.x, point.y)
}

private fun Path.cubicTo(
    control1: Offset,
    control2: Offset,
    end: Offset,
) {
    cubicTo(
        control1.x,
        control1.y,
        control2.x,
        control2.y,
        end.x,
        end.y,
    )
}

private fun Float.segment(start: Float, end: Float): Float {
    return ((this - start) / (end - start)).coerceIn(0f, 1f)
}
