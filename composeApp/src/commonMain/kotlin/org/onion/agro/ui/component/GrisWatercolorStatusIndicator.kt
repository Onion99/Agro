package org.onion.agro.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.onion.model.LlmEngineStatus
import com.onion.theme.style.glassSurface
import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.llm_status_applying_context
import agro.composeapp.generated.resources.llm_status_error
import agro.composeapp.generated.resources.llm_status_generating
import agro.composeapp.generated.resources.llm_status_initializing
import agro.composeapp.generated.resources.llm_status_ready
import agro.composeapp.generated.resources.llm_status_standby
import org.jetbrains.compose.resources.stringResource
import ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
private data class GrisStatusPalette(
    val accent: Color,
    val companion: Color,
    val container: Color,
)

/** Returns the localized runtime label shared by status surfaces. */
@Composable
fun resolveGrisStatusLabel(status: LlmEngineStatus): String = when (status) {
    LlmEngineStatus.UNINITIALIZED -> stringResource(Res.string.llm_status_standby)
    LlmEngineStatus.INITIALIZING -> stringResource(Res.string.llm_status_initializing)
    LlmEngineStatus.APPLYING_CONTEXT -> stringResource(Res.string.llm_status_applying_context)
    LlmEngineStatus.READY -> stringResource(Res.string.llm_status_ready)
    LlmEngineStatus.GENERATING -> stringResource(Res.string.llm_status_generating)
    LlmEngineStatus.ERROR -> stringResource(Res.string.llm_status_error)
}

/** Returns the theme-token accent used for a runtime state. */
@Composable
fun resolveGrisStatusAccent(status: LlmEngineStatus): Color = grisStatusPalette(status).accent

/**
 * Gris-inspired runtime indicator drawn as layered pigment blooms on paper.
 *
 * Motion is semantic: initialization orbits, context application diffuses,
 * readiness breathes slowly, inference flows quickly, and errors settle into
 * a low-frequency muted tide rather than flashing.
 */
@Composable
fun GrisWatercolorStatusIndicator(
    status: LlmEngineStatus,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    compact: Boolean = false,
    customLabel: String? = null,
) {
    val targetPalette = grisStatusPalette(status)
    val accent by animateColorAsState(
        targetValue = targetPalette.accent,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_status_accent",
    )
    val companion by animateColorAsState(
        targetValue = targetPalette.companion,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_status_companion",
    )
    val displayText = customLabel ?: resolveGrisStatusLabel(status)
    val motionDuration = status.motionDurationMillis()
    val infiniteTransition = rememberInfiniteTransition(label = "gris_status_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motionDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gris_status_phase",
    )
    val breath by infiniteTransition.animateFloat(
        initialValue = status.minimumBreath(),
        targetValue = status.maximumBreath(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motionDuration / 2,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gris_status_breath",
    )
    val haloSize = if (compact) AppTheme.size.icon else AppTheme.size.iconLarge
    val coreSize = if (compact) {
        AppTheme.spacing.xs + AppTheme.size.borderWidth
    } else {
        AppTheme.spacing.sm
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) AppTheme.spacing.xs else AppTheme.spacing.sm
        ),
    ) {
        Box(
            modifier = Modifier.size(haloSize),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(haloSize)) {
                val angle = phase * (2f * PI.toFloat())
                val center = Offset(size.width / 2f, size.height / 2f)
                val drift = size.minDimension * status.driftRatio()
                val firstCenter = center + Offset(
                    x = cos(angle) * drift,
                    y = sin(angle * 0.82f) * drift,
                )
                val secondCenter = center + Offset(
                    x = cos(angle + PI.toFloat()) * drift * 0.72f,
                    y = sin(angle * 1.14f + PI.toFloat()) * drift * 0.72f,
                )
                val firstRadius = size.minDimension * (0.34f + breath * 0.13f)
                val secondRadius = size.minDimension * (0.28f + breath * 0.1f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = status.haloAlpha() * breath),
                            accent.copy(alpha = status.haloAlpha() * 0.34f),
                            Color.Transparent,
                        ),
                        center = firstCenter,
                        radius = firstRadius,
                    ),
                    radius = firstRadius,
                    center = firstCenter,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            companion.copy(alpha = status.haloAlpha() * breath * 0.72f),
                            companion.copy(alpha = status.haloAlpha() * 0.2f),
                            Color.Transparent,
                        ),
                        center = secondCenter,
                        radius = secondRadius,
                    ),
                    radius = secondRadius,
                    center = secondCenter,
                )
            }

            Box(
                modifier = Modifier
                    .size(coreSize)
                    .background(
                        color = accent.copy(alpha = status.coreAlpha()),
                        shape = AppTheme.shape.full,
                    )
                    .border(
                        width = AppTheme.size.borderWidthThin,
                        color = companion.copy(alpha = 0.42f),
                        shape = AppTheme.shape.full,
                    )
            )
        }

        if (showText) {
            Text(
                text = displayText,
                style = AppTheme.typography.bodySmall,
                fontWeight = when (status) {
                    LlmEngineStatus.READY,
                    LlmEngineStatus.GENERATING,
                    -> FontWeight.Medium
                    else -> FontWeight.Normal
                },
                color = accent,
            )
        }
    }
}

/** Glass pill variant for cards and compact headers. */
@Composable
fun GrisWatercolorStatusChip(
    status: LlmEngineStatus,
    modifier: Modifier = Modifier,
    customLabel: String? = null,
) {
    val targetPalette = grisStatusPalette(status)
    val container by animateColorAsState(
        targetValue = targetPalette.container,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_status_container",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AppTheme.size.chipHeight)
            .glassSurface(
                shape = AppTheme.shape.full,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha,
            )
            .background(color = container, shape = AppTheme.shape.full)
            .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        GrisWatercolorStatusIndicator(
            status = status,
            showText = true,
            compact = true,
            customLabel = customLabel,
        )
    }
}

@Composable
private fun grisStatusPalette(status: LlmEngineStatus): GrisStatusPalette = when (status) {
    LlmEngineStatus.UNINITIALIZED -> GrisStatusPalette(
        accent = AppTheme.colors.tertiary,
        companion = AppTheme.colors.outlineVariant,
        container = AppTheme.colors.surfaceVariant.copy(alpha = 0.22f),
    )
    LlmEngineStatus.INITIALIZING -> GrisStatusPalette(
        accent = AppTheme.colors.secondary,
        companion = AppTheme.colors.tertiary,
        container = AppTheme.colors.secondaryContainer.copy(alpha = 0.3f),
    )
    LlmEngineStatus.APPLYING_CONTEXT -> GrisStatusPalette(
        accent = AppTheme.colors.primary,
        companion = AppTheme.colors.secondary,
        container = AppTheme.colors.primaryContainer.copy(alpha = 0.3f),
    )
    LlmEngineStatus.READY -> GrisStatusPalette(
        accent = AppTheme.colors.primary,
        companion = AppTheme.colors.primaryFixedDim,
        container = AppTheme.colors.primaryContainer.copy(alpha = 0.28f),
    )
    LlmEngineStatus.GENERATING -> GrisStatusPalette(
        accent = AppTheme.colors.secondary,
        companion = AppTheme.colors.primary,
        container = AppTheme.colors.secondaryContainer.copy(alpha = 0.38f),
    )
    LlmEngineStatus.ERROR -> GrisStatusPalette(
        accent = AppTheme.colors.error,
        companion = AppTheme.colors.tertiary,
        container = AppTheme.colors.errorContainer.copy(alpha = 0.24f),
    )
}

private fun LlmEngineStatus.motionDurationMillis(): Int = when (this) {
    LlmEngineStatus.GENERATING -> 1_400
    LlmEngineStatus.INITIALIZING -> 1_900
    LlmEngineStatus.APPLYING_CONTEXT -> 2_200
    LlmEngineStatus.READY -> 3_800
    LlmEngineStatus.ERROR -> 4_600
    LlmEngineStatus.UNINITIALIZED -> 5_200
}

private fun LlmEngineStatus.minimumBreath(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.58f
    LlmEngineStatus.INITIALIZING,
    LlmEngineStatus.APPLYING_CONTEXT,
    -> 0.48f
    else -> 0.38f
}

private fun LlmEngineStatus.maximumBreath(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 1f
    LlmEngineStatus.INITIALIZING -> 0.92f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.86f
    LlmEngineStatus.READY -> 0.7f
    LlmEngineStatus.ERROR -> 0.58f
    LlmEngineStatus.UNINITIALIZED -> 0.5f
}

private fun LlmEngineStatus.driftRatio(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.12f
    LlmEngineStatus.INITIALIZING -> 0.1f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.08f
    LlmEngineStatus.READY -> 0.035f
    LlmEngineStatus.ERROR -> 0.025f
    LlmEngineStatus.UNINITIALIZED -> 0.015f
}

private fun LlmEngineStatus.haloAlpha(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.72f
    LlmEngineStatus.INITIALIZING -> 0.62f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.56f
    LlmEngineStatus.READY -> 0.42f
    LlmEngineStatus.ERROR -> 0.34f
    LlmEngineStatus.UNINITIALIZED -> 0.24f
}

private fun LlmEngineStatus.coreAlpha(): Float = when (this) {
    LlmEngineStatus.UNINITIALIZED -> 0.58f
    LlmEngineStatus.ERROR -> 0.78f
    else -> 0.92f
}

private const val STATUS_COLOR_TRANSITION_MILLIS = 520
