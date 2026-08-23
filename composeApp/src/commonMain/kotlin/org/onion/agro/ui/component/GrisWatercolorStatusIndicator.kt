package org.onion.agro.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onion.model.LlmEngineStatus
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
    val specular: Color,
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
 * Gris-inspired runtime indicator uniting poetic watercolor diffusion with
 * delicate celestial astrolabe geometry and Steve Jobs' philosophy of ultimate simplicity.
 *
 * Visual hierarchy:
 * 1. Harmonic fluid watercolor wash (Lissajous organic drift)
 * 2. Gossamer celestial astrolabe ring (0.75dp hairline orbit)
 * 3. Orbiting stardust comet particle (semantic motion per runtime state)
 * 4. Cardinal micro-constellation anchors & radiant starlight core
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

    val breathEasing = remember { CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) }
    val breath by infiniteTransition.animateFloat(
        initialValue = status.minimumBreath(),
        targetValue = status.maximumBreath(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (motionDuration * 0.6f).toInt().coerceAtLeast(1200),
                easing = breathEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gris_status_breath",
    )

    // Secondary ripple for active thinking states
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (motionDuration * 0.9f).toInt().coerceAtLeast(1400),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gris_status_ripple",
    )

    val haloSize: Dp = if (compact) 22.dp else 28.dp

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
            val density = LocalDensity.current
            Canvas(modifier = Modifier.size(haloSize)) {
                drawGrisCelestialWatercolor(
                    status = status,
                    accent = accent,
                    companion = companion,
                    phase = phase,
                    breath = breath,
                    rippleProgress = rippleProgress,
                    compact = compact,
                    density = density.density,
                )
            }
        }

        if (showText) {
            Text(
                text = displayText,
                style = AppTheme.typography.bodySmall,
                fontWeight = when (status) {
                    LlmEngineStatus.READY,
                    LlmEngineStatus.GENERATING,
                    -> FontWeight.SemiBold
                    else -> FontWeight.Normal
                },
                color = accent,
                letterSpacing = 0.02.sp,
            )
        }
    }
}

/**
 * Luminous Frosted Crystal Capsule (Steve Jobs Craftsmanship + Gris Ethereal Aesthetic).
 *
 * Features:
 * - Ambient watercolor wash blooming from behind the vessel
 * - Micro-specular beveled light edge catching the top-left rim
 * - Subtle elevation lift and breath on hover
 * - Pure, harmonious typography and optical alignment
 */
@Composable
fun GrisWatercolorStatusChip(
    status: LlmEngineStatus,
    modifier: Modifier = Modifier,
    customLabel: String? = null,
) {
    val targetPalette = grisStatusPalette(status)
    val accent by animateColorAsState(
        targetValue = targetPalette.accent,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_chip_accent",
    )
    val companion by animateColorAsState(
        targetValue = targetPalette.companion,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_chip_companion",
    )
    val container by animateColorAsState(
        targetValue = targetPalette.container,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_chip_container",
    )
    val specular by animateColorAsState(
        targetValue = targetPalette.specular,
        animationSpec = tween(durationMillis = STATUS_COLOR_TRANSITION_MILLIS),
        label = "gris_chip_specular",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val translationY by animateDpAsState(
        targetValue = if (isHovered) (-1.5).dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "gris_chip_trans_y",
    )
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "gris_chip_scale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.26f else 0.14f,
        animationSpec = tween(durationMillis = 350),
        label = "gris_chip_glow_alpha",
    )

    val displayText = customLabel ?: resolveGrisStatusLabel(status)

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationY = translationY.toPx()
                this.scaleX = scale
                this.scaleY = scale
            }
            // Ambient watercolor bloom behind the crystal capsule
            .drawBehind {
                val glowRadius = size.maxDimension * (if (isHovered) 0.85f else 0.65f)
                val glowCenter = Offset(size.width * 0.28f, size.height * 0.5f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = glowAlpha),
                            companion.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent,
                        ),
                        center = glowCenter,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = glowCenter,
                )
            }
            .shadow(
                elevation = if (isHovered) 8.dp else 4.dp,
                shape = AppTheme.shape.full,
                ambientColor = AppTheme.elevation.ambientShadowColor,
                spotColor = Color.Transparent,
            )
            .clip(AppTheme.shape.full)
            // Luminous frosted glass substrate
            .background(
                color = container,
                shape = AppTheme.shape.full,
            )
            // Precision light-catching beveled edge
            .border(
                width = AppTheme.size.borderWidthThin,
                brush = Brush.linearGradient(
                    colors = listOf(
                        specular.copy(alpha = if (isHovered) 0.55f else 0.35f),
                        accent.copy(alpha = if (isHovered) 0.30f else 0.18f),
                        AppTheme.colors.outline.copy(alpha = if (isHovered) 0.12f else 0.06f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite,
                ),
                shape = AppTheme.shape.full,
            )
            .hoverable(interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .defaultMinSize(minHeight = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GrisWatercolorStatusIndicator(
                status = status,
                showText = false,
                compact = true,
            )

            AnimatedContent(
                targetState = displayText,
                transitionSpec = {
                    fadeIn(animationSpec = tween(280)) togetherWith
                        fadeOut(animationSpec = tween(200))
                },
                label = "gris_chip_label",
            ) { text ->
                Text(
                    text = text,
                    style = AppTheme.typography.bodySmall,
                    fontWeight = when (status) {
                        LlmEngineStatus.READY,
                        LlmEngineStatus.GENERATING,
                        -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    },
                    color = accent,
                    letterSpacing = 0.03.sp,
                )
            }
        }
    }
}

/**
 * Precision Canvas rendering of Gris celestial astrolabe & fluid watercolor bloom.
 */
private fun DrawScope.drawGrisCelestialWatercolor(
    status: LlmEngineStatus,
    accent: Color,
    companion: Color,
    phase: Float,
    breath: Float,
    rippleProgress: Float,
    compact: Boolean,
    density: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = size.minDimension / 2f
    val baseAngle = phase * (2f * PI.toFloat())

    // ── 1. Multi-Layer Fluid Watercolor Pigment Bloom ─────────────────────────
    val driftDist = size.minDimension * status.driftRatio()
    val bloom1Center = center + Offset(
        x = cos(baseAngle) * driftDist,
        y = sin(baseAngle * 0.82f) * driftDist,
    )
    val bloom2Center = center + Offset(
        x = cos(baseAngle + PI.toFloat()) * driftDist * 0.76f,
        y = sin(baseAngle * 1.14f + PI.toFloat()) * driftDist * 0.76f,
    )

    val bloom1Radius = maxRadius * (0.82f + breath * 0.18f)
    val bloom2Radius = maxRadius * (0.68f + breath * 0.14f)

    // Primary watercolor wash
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = status.haloAlpha() * breath),
                accent.copy(alpha = status.haloAlpha() * 0.38f),
                Color.Transparent,
            ),
            center = bloom1Center,
            radius = bloom1Radius,
        ),
        radius = bloom1Radius,
        center = bloom1Center,
    )

    // Companion harmonic wash
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                companion.copy(alpha = status.haloAlpha() * breath * 0.70f),
                companion.copy(alpha = status.haloAlpha() * 0.18f),
                Color.Transparent,
            ),
            center = bloom2Center,
            radius = bloom2Radius,
        ),
        radius = bloom2Radius,
        center = bloom2Center,
    )

    // ── 2. Fluid Ripple Waves (Active Inference / Context Weaving) ────────────
    if (status == LlmEngineStatus.GENERATING || status == LlmEngineStatus.APPLYING_CONTEXT) {
        val currentRippleRadius = maxRadius * (0.35f + rippleProgress * 0.65f)
        val rippleAlpha = (1f - rippleProgress) * (if (status == LlmEngineStatus.GENERATING) 0.42f else 0.28f)
        drawCircle(
            color = accent.copy(alpha = rippleAlpha),
            radius = currentRippleRadius,
            center = center,
            style = Stroke(width = 0.85f * density),
        )
    }

    // ── 3. Gossamer Celestial Astrolabe Ring ──────────────────────────────────
    val ringRadius = maxRadius * 0.68f
    val ringStroke = 0.75f * density
    val ringAlpha = status.ringAlpha() * (0.85f + breath * 0.15f)

    drawCircle(
        color = companion.copy(alpha = ringAlpha),
        radius = ringRadius,
        center = center,
        style = Stroke(width = ringStroke),
    )

    // ── 4. Cardinal Constellation Nodes (Micro Star Anchors) ─────────────────
    val cardinalAlpha = when (status) {
        LlmEngineStatus.READY -> 0.75f * breath
        LlmEngineStatus.UNINITIALIZED -> 0.35f
        LlmEngineStatus.INITIALIZING -> 0.5f
        else -> 0.6f * breath
    }
    val nodeRadius = (if (compact) 0.85f else 1.1f) * density
    for (i in 0 until 4) {
        val nodeAngle = i * (PI.toFloat() / 2f) + (if (status == LlmEngineStatus.INITIALIZING) baseAngle * 0.25f else 0f)
        val nodePos = center + Offset(
            x = cos(nodeAngle) * ringRadius,
            y = sin(nodeAngle) * ringRadius,
        )
        drawCircle(
            color = companion.copy(alpha = cardinalAlpha),
            radius = nodeRadius,
            center = nodePos,
        )
    }

    // ── 5. Orbiting Stardust Comet Particle (Active States) ───────────────────
    if (status == LlmEngineStatus.INITIALIZING ||
        status == LlmEngineStatus.GENERATING ||
        status == LlmEngineStatus.APPLYING_CONTEXT
    ) {
        val cometAngle = baseAngle
        val headPos = center + Offset(
            x = cos(cometAngle) * ringRadius,
            y = sin(cometAngle) * ringRadius,
        )
        val headRadius = (if (compact) 1.6f else 2.1f) * density

        // Draw comet trailing tail nodes
        val tailSegments = if (status == LlmEngineStatus.GENERATING) 4 else 3
        for (t in 1..tailSegments) {
            val tailAngle = cometAngle - (t * 0.16f)
            val tailPos = center + Offset(
                x = cos(tailAngle) * ringRadius,
                y = sin(tailAngle) * ringRadius,
            )
            val tailAlpha = (1f - (t.toFloat() / (tailSegments + 1))) * 0.65f
            drawCircle(
                color = accent.copy(alpha = tailAlpha),
                radius = headRadius * (1f - t * 0.18f),
                center = tailPos,
            )
        }

        // Comet radiant head
        drawCircle(
            color = accent,
            radius = headRadius,
            center = headPos,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = headRadius * 0.5f,
            center = headPos,
        )
    }

    // ── 6. Radiant Starlight Core ─────────────────────────────────────────────
    val coreBaseRadius = maxRadius * (if (compact) 0.22f else 0.25f)
    val dynamicCoreRadius = coreBaseRadius * (0.92f + breath * 0.16f)

    // Outer core soft aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = status.coreAlpha()),
                accent.copy(alpha = status.coreAlpha() * 0.45f),
                Color.Transparent,
            ),
            center = center,
            radius = dynamicCoreRadius * 1.8f,
        ),
        radius = dynamicCoreRadius * 1.8f,
        center = center,
    )

    // Solid luminous center
    drawCircle(
        color = accent.copy(alpha = status.coreAlpha()),
        radius = dynamicCoreRadius,
        center = center,
    )

    // Specular starlight glint at center
    val glintRadius = dynamicCoreRadius * 0.45f
    drawCircle(
        color = Color.White.copy(alpha = 0.72f * breath),
        radius = glintRadius,
        center = center,
    )
}

@Composable
private fun grisStatusPalette(status: LlmEngineStatus): GrisStatusPalette = when (status) {
    LlmEngineStatus.UNINITIALIZED -> GrisStatusPalette(
        accent = AppTheme.colors.tertiary,
        companion = AppTheme.colors.outlineVariant,
        container = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.55f),
        specular = AppTheme.colors.outline.copy(alpha = 0.25f),
    )
    LlmEngineStatus.INITIALIZING -> GrisStatusPalette(
        accent = AppTheme.colors.secondary,
        companion = AppTheme.colors.primary,
        container = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.65f),
        specular = AppTheme.colors.secondaryFixedDim.copy(alpha = 0.45f),
    )
    LlmEngineStatus.APPLYING_CONTEXT -> GrisStatusPalette(
        accent = AppTheme.colors.primary,
        companion = AppTheme.colors.secondary,
        container = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.65f),
        specular = AppTheme.colors.primaryFixedDim.copy(alpha = 0.45f),
    )
    LlmEngineStatus.READY -> GrisStatusPalette(
        accent = AppTheme.colors.primary,
        companion = AppTheme.colors.primaryFixedDim,
        container = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.60f),
        specular = AppTheme.colors.primaryFixed.copy(alpha = 0.50f),
    )
    LlmEngineStatus.GENERATING -> GrisStatusPalette(
        accent = AppTheme.colors.secondary,
        companion = AppTheme.colors.primary,
        container = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.70f),
        specular = AppTheme.colors.secondaryFixed.copy(alpha = 0.55f),
    )
    LlmEngineStatus.ERROR -> GrisStatusPalette(
        accent = AppTheme.colors.error,
        companion = AppTheme.colors.tertiary,
        container = AppTheme.colors.errorContainer.copy(alpha = 0.28f),
        specular = AppTheme.colors.error.copy(alpha = 0.40f),
    )
}

private fun LlmEngineStatus.motionDurationMillis(): Int = when (this) {
    LlmEngineStatus.GENERATING -> 1_500
    LlmEngineStatus.INITIALIZING -> 2_100
    LlmEngineStatus.APPLYING_CONTEXT -> 2_400
    LlmEngineStatus.READY -> 4_200
    LlmEngineStatus.ERROR -> 4_800
    LlmEngineStatus.UNINITIALIZED -> 5_600
}

private fun LlmEngineStatus.minimumBreath(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.62f
    LlmEngineStatus.INITIALIZING,
    LlmEngineStatus.APPLYING_CONTEXT,
    -> 0.52f
    LlmEngineStatus.READY -> 0.45f
    else -> 0.35f
}

private fun LlmEngineStatus.maximumBreath(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 1.0f
    LlmEngineStatus.INITIALIZING -> 0.94f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.88f
    LlmEngineStatus.READY -> 0.78f
    LlmEngineStatus.ERROR -> 0.60f
    LlmEngineStatus.UNINITIALIZED -> 0.48f
}

private fun LlmEngineStatus.driftRatio(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.11f
    LlmEngineStatus.INITIALIZING -> 0.09f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.08f
    LlmEngineStatus.READY -> 0.032f
    LlmEngineStatus.ERROR -> 0.022f
    LlmEngineStatus.UNINITIALIZED -> 0.015f
}

private fun LlmEngineStatus.haloAlpha(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.68f
    LlmEngineStatus.INITIALIZING -> 0.58f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.52f
    LlmEngineStatus.READY -> 0.38f
    LlmEngineStatus.ERROR -> 0.30f
    LlmEngineStatus.UNINITIALIZED -> 0.20f
}

private fun LlmEngineStatus.ringAlpha(): Float = when (this) {
    LlmEngineStatus.GENERATING -> 0.55f
    LlmEngineStatus.INITIALIZING -> 0.50f
    LlmEngineStatus.APPLYING_CONTEXT -> 0.45f
    LlmEngineStatus.READY -> 0.35f
    LlmEngineStatus.ERROR -> 0.28f
    LlmEngineStatus.UNINITIALIZED -> 0.22f
}

private fun LlmEngineStatus.coreAlpha(): Float = when (this) {
    LlmEngineStatus.UNINITIALIZED -> 0.60f
    LlmEngineStatus.ERROR -> 0.78f
    LlmEngineStatus.READY -> 0.92f
    else -> 0.96f
}

private const val STATUS_COLOR_TRANSITION_MILLIS = 450
