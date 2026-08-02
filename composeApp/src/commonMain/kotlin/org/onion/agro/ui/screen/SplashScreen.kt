package org.onion.agro.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.onion.theme.state.ContentType
import com.onion.theme.style.watercolorGradient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.onion.agro.BuildConfig
import ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SplashScreen(autoToMainPage: () -> Unit) {
    val worldReveal = remember { Animatable(0f) }
    val brandReveal = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val currentAutoToMainPage by rememberUpdatedState(autoToMainPage)
    val ambientTransition = rememberInfiniteTransition(label = "splashAmbient")
    val ambientMotion by ambientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splashAmbientMotion",
    )
    val isSinglePane = AppTheme.contentType == ContentType.Single

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                worldReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1_600,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            launch {
                delay(820)
                brandReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 560,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
        delay(580)
        exitAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 280),
        )
        currentAutoToMainPage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha.value)
            .watercolorGradient(
                startColor = AppTheme.colors.primaryFixed.copy(alpha = 0.24f),
                endColor = AppTheme.colors.secondaryFixed.copy(alpha = 0.18f),
            ),
    ) {
        AwakeningWorld(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
            progress = worldReveal.value,
            ambientMotion = ambientMotion,
            focalPointX = if (isSinglePane) 0.50f else 0.37f,
        )
        ResponsiveSplashLockup(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isSinglePane) {
                        AppTheme.spacing.containerPaddingMobile
                    } else {
                        AppTheme.spacing.containerPaddingDesktop
                    },
                ),
            isSinglePane = isSinglePane,
            worldProgress = worldReveal.value,
            brandProgress = brandReveal.value,
            ambientMotion = ambientMotion,
        )
    }
}

@Composable
private fun ResponsiveSplashLockup(
    modifier: Modifier,
    isSinglePane: Boolean,
    worldProgress: Float,
    brandProgress: Float,
    ambientMotion: Float,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val iconSize = minOf(
            maxHeight * if (isSinglePane) 0.48f else 0.56f,
            maxWidth * if (isSinglePane) 0.68f else 0.30f,
            if (isSinglePane) AppTheme.size.cardMedium else AppTheme.size.cardLarge,
        )
        val iconModifier = Modifier
            .size(iconSize)
            .clearAndSetSemantics { }

        if (isSinglePane) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedAppIconSeed(
                    modifier = iconModifier,
                    progress = worldProgress,
                    ambientMotion = ambientMotion,
                )
                Spacer(modifier = Modifier.height(AppTheme.spacing.lg))
                SplashBrand(progress = brandProgress, isSinglePane = true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedAppIconSeed(
                    modifier = iconModifier,
                    progress = worldProgress,
                    ambientMotion = ambientMotion,
                )
                Spacer(modifier = Modifier.width(AppTheme.spacing.xxl))
                SplashBrand(progress = brandProgress, isSinglePane = false)
            }
        }
    }
}

@Composable
private fun SplashBrand(
    progress: Float,
    isSinglePane: Boolean,
) {
    Column(
        modifier = Modifier
            .offset(
                x = if (isSinglePane) AppTheme.spacing.xs else AppTheme.spacing.md * (1f - progress),
                y = if (isSinglePane) AppTheme.spacing.md * (1f - progress) else AppTheme.spacing.xs,
            )
            .alpha(progress),
        horizontalAlignment = if (isSinglePane) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = BuildConfig.APP_NAME,
            color = AppTheme.colors.onSurface,
            style = if (isSinglePane) {
                AppTheme.typography.headlineSmall
            } else {
                AppTheme.typography.headlineLarge
            },
        )
        /*Spacer(modifier = Modifier.height(AppTheme.spacing.sm))
        SeedPulse(
            modifier = Modifier
                .width(if (isSinglePane) AppTheme.spacing.xxl else AppTheme.spacing.sectionGap)
                .height(AppTheme.spacing.sm)
                .clearAndSetSemantics { },
            progress = progress,
        )*/
    }
}

@Composable
private fun AwakeningWorld(
    modifier: Modifier,
    progress: Float,
    ambientMotion: Float,
    focalPointX: Float,
) {
    val colors = AppTheme.colors
    val thinLine = AppTheme.size.borderWidthThin
    Canvas(modifier = modifier) {
        val phase = ambientMotion * 2f * PI.toFloat()
        val driftX = sin(phase) * size.width * 0.018f
        val driftY = sin(phase + PI.toFloat() / 2f) * size.height * 0.014f
        val colorReveal = progress.segment(0.08f, 0.72f)
        val lineReveal = progress.segment(0f, 0.46f)
        val longSide = maxOf(size.width, size.height)
        val shortSide = minOf(size.width, size.height)
        val focalPoint = Offset(size.width * focalPointX, size.height * 0.46f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.primaryFixed.copy(alpha = 0.34f * colorReveal),
                    colors.primaryFixed.copy(alpha = 0f),
                ),
                center = Offset(size.width * 0.08f + driftX, size.height * 0.78f + driftY),
                radius = longSide * 0.48f,
            ),
            center = Offset(size.width * 0.08f + driftX, size.height * 0.78f + driftY),
            radius = longSide * 0.48f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.secondaryFixed.copy(alpha = 0.30f * colorReveal),
                    colors.secondaryFixed.copy(alpha = 0f),
                ),
                center = Offset(size.width * 0.92f - driftX, size.height * 0.18f - driftY),
                radius = longSide * 0.43f,
            ),
            center = Offset(size.width * 0.92f - driftX, size.height * 0.18f - driftY),
            radius = longSide * 0.43f,
        )

        val gateRadius = shortSide * 0.31f
        drawArc(
            color = colors.tertiary.copy(alpha = 0.11f * lineReveal),
            startAngle = 198f,
            sweepAngle = 144f * lineReveal,
            useCenter = false,
            topLeft = Offset(focalPoint.x - gateRadius, focalPoint.y - gateRadius),
            size = Size(gateRadius * 2f, gateRadius * 2f),
            style = Stroke(width = thinLine.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = colors.tertiary.copy(alpha = 0.14f * lineReveal),
            start = Offset(focalPoint.x - size.width * 0.40f * lineReveal, size.height * 0.78f),
            end = Offset(focalPoint.x + size.width * 0.40f * lineReveal, size.height * 0.78f),
            strokeWidth = thinLine.toPx(),
            cap = StrokeCap.Round,
        )

        val ground = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, size.height * 0.89f)
            cubicTo(
                size.width * 0.22f,
                size.height * 0.78f,
                size.width * 0.38f,
                size.height * 0.93f,
                size.width * 0.56f,
                size.height * 0.84f,
            )
            cubicTo(
                size.width * 0.74f,
                size.height * 0.76f,
                size.width * 0.86f,
                size.height * 0.91f,
                size.width,
                size.height * 0.83f,
            )
            lineTo(size.width, size.height)
            close()
        }
        drawPath(
            path = ground,
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.surfaceContainerLow.copy(alpha = 0.06f + 0.24f * colorReveal),
                    colors.surfaceContainerHighest.copy(alpha = 0.64f),
                ),
                startY = size.height * 0.76f,
                endY = size.height,
            ),
        )

        val moteReveal = progress.segment(0.36f, 0.90f)
        drawCircle(
            color = colors.primary.copy(alpha = 0.15f * moteReveal),
            center = Offset(
                focalPoint.x - shortSide * 0.28f,
                focalPoint.y - shortSide * 0.18f + driftY,
            ),
            radius = shortSide * 0.008f * moteReveal,
        )
        drawCircle(
            color = colors.secondary.copy(alpha = 0.14f * moteReveal),
            center = Offset(
                focalPoint.x + shortSide * 0.30f,
                focalPoint.y + shortSide * 0.02f - driftY,
            ),
            radius = shortSide * 0.006f * moteReveal,
        )
    }
}

@Composable
private fun SeedPulse(
    modifier: Modifier,
    progress: Float,
) {
    val colors = AppTheme.colors
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfLength = size.width * 0.46f * progress
        drawLine(
            brush = Brush.horizontalGradient(listOf(colors.primary, colors.secondary)),
            start = Offset(center.x - halfLength, center.y),
            end = Offset(center.x + halfLength, center.y),
            strokeWidth = size.height * 0.20f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = colors.surfaceBright,
            center = center,
            radius = size.height * 0.18f * progress,
        )
    }
}

private fun Float.segment(start: Float, end: Float): Float {
    return ((this - start) / (end - start)).coerceIn(0f, 1f)
}
