package org.onion.agro.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onion.theme.state.ContentType
import com.onion.theme.style.glassSurface
import agro.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.onion.agro.database.ChatSessionEntity
import org.onion.agro.viewmodel.ChatViewModel
import ui.theme.AppTheme
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun LibraryScreen(
    onOpenChat: () -> Unit = {}
) {
    val isDesktop = AppTheme.contentType == ContentType.Dual
    val containerPadding = if (isDesktop) AppTheme.spacing.containerPaddingDesktop else AppTheme.spacing.containerPaddingMobile
    val chatViewModel = koinInject<ChatViewModel>()
    
    val primaryColor = AppTheme.colors.primary
    val secondaryColor = AppTheme.colors.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surface)
            .drawBehind {
                // Top left watercolor blob (Sage Green)
                val radius1 = minOf(size.width, size.height) * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.2f),
                        radius = radius1
                    ),
                    radius = radius1,
                    center = Offset(size.width * 0.15f, size.height * 0.2f)
                )

                // Bottom right watercolor blob (Dusty Blue)
                val radius2 = minOf(size.width, size.height) * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryColor.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.8f),
                        radius = radius2
                    ),
                    radius = radius2,
                    center = Offset(size.width * 0.85f, size.height * 0.8f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = containerPadding)
                .padding(top = containerPadding, bottom = containerPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl)
        ) {
            // Header Section
            Column(
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)
            ) {
                Text(
                    text = stringResource(Res.string.library_title),
                    style = if (isDesktop) AppTheme.typography.headlineLarge else AppTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    color = AppTheme.colors.primary
                )
                Text(
                    text = stringResource(Res.string.library_desc),
                    style = if (isDesktop) AppTheme.typography.bodyLarge else AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AppTheme.spacing.md))
                // Ink Line Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AppTheme.colors.primary.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Bento Grid Layout
            if (isDesktop) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                ) {
                    LivingMemoryCard(
                        sessions = chatViewModel.chatSessions.take(4),
                        onSessionClick = { sessionId ->
                            chatViewModel.openSession(sessionId)
                            onOpenChat()
                        },
                        modifier = Modifier
                            .weight(2f)
                            .height(424.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                    ) {
                        LottieAnimationCard(
                            onClick = {
                                chatViewModel.startLottieAnimationConversation()
                                onOpenChat()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        EightBitBgmCard(
                            onClick = {
                                chatViewModel.startChiptuneBgmConversation()
                                onOpenChat()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                    ) {
                        SvgImageCard(
                            onClick = {
                                chatViewModel.startSvgImageConversation()
                                onOpenChat()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        ForgeNewVesselCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
                ) {
                    LivingMemoryCard(
                        sessions = chatViewModel.chatSessions.take(4),
                        onSessionClick = { sessionId ->
                            chatViewModel.openSession(sessionId)
                            onOpenChat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
                    ) {
                        LottieAnimationCard(
                            onClick = {
                                chatViewModel.startLottieAnimationConversation()
                                onOpenChat()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(180.dp)
                        )
                        EightBitBgmCard(
                            onClick = {
                                chatViewModel.startChiptuneBgmConversation()
                                onOpenChat()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(180.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
                    ) {
                        SvgImageCard(
                            onClick = {
                                chatViewModel.startSvgImageConversation()
                                onOpenChat()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(180.dp)
                        )
                        ForgeNewVesselCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(180.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    backgroundColorBlob: Color = Color.Transparent,
    blobCenter: (width: Float, height: Float) -> Offset = { w, h -> Offset(w, h) },
    blobRadiusDp: Float = 100f,
    blobHoverRadiusDp: Float = 150f,
    blobAlpha: Float = 0.15f,
    blobHoverAlpha: Float = 0.25f,
    onClick: () -> Unit = {},
    content: @Composable BoxScope.(isHovered: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val translationY by animateDpAsState(
        targetValue = if (isHovered) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1.0f,
        animationSpec = tween(durationMillis = 300)
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.35f else AppTheme.elevation.glassBorderAlpha,
        animationSpec = tween(durationMillis = 300)
    )
    val glassAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.8f else AppTheme.elevation.glassSurfaceAlpha,
        animationSpec = tween(durationMillis = 300)
    )
    val animatedRadius by animateFloatAsState(
        targetValue = if (isHovered) blobHoverRadiusDp else blobRadiusDp,
        animationSpec = tween(durationMillis = 500)
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isHovered) blobHoverAlpha else blobAlpha,
        animationSpec = tween(durationMillis = 500)
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationY = translationY.toPx()
                this.scaleX = scale
                this.scaleY = scale
            }
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = glassAlpha,
                borderAlpha = borderAlpha
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (backgroundColorBlob != Color.Transparent) {
            val density = LocalDensity.current
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = with(density) { animatedRadius.dp.toPx() }
                val centerOffset = blobCenter(size.width, size.height)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(backgroundColorBlob.copy(alpha = animatedAlpha), Color.Transparent),
                        center = centerOffset,
                        radius = r
                    ),
                    radius = r,
                    center = centerOffset
                )
            }
        }
        content(isHovered)
    }
}

@Composable
fun LivingMemoryCard(
    sessions: List<ChatSessionEntity> = emptyList(),
    onSessionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier,
        backgroundColorBlob = AppTheme.colors.primaryFixedDim,
        blobCenter = { w, _ -> Offset(w + 40.dp.value, -40.dp.value) },
        blobRadiusDp = 200f,
        blobHoverRadiusDp = 260f,
        blobAlpha = 0.18f,
        blobHoverAlpha = 0.28f,
        onClick = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.spacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = AppTheme.colors.primaryContainer.copy(alpha = 0.2f),
                                shape = AppTheme.shape.full
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AllInclusive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = AppTheme.colors.primary
                        )
                    }
                    Text(
                        text = stringResource(Res.string.library_living_memory),
                        style = AppTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.primary
                    )
                }
                
                // Active badge
                Box(
                    modifier = Modifier
                        .background(
                            color = AppTheme.colors.primaryContainer.copy(alpha = 0.3f),
                            shape = AppTheme.shape.full
                        )
                        .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = AppTheme.colors.primary,
                                    shape = AppTheme.shape.full
                                )
                        )
                        Text(
                            text = stringResource(Res.string.library_status_active),
                            style = AppTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.spacing.md))

            Text(
                text = stringResource(Res.string.library_memory_desc),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(AppTheme.spacing.lg))

            // Thread list
            Column(
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                modifier = Modifier.weight(1f)
            ) {
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.history_empty),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    sessions.forEach { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppTheme.shape.md)
                            .background(AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.4f))
                            .border(
                                width = 0.5.dp,
                                color = AppTheme.colors.outline.copy(alpha = 0.08f),
                                shape = AppTheme.shape.md
                            )
                            .clickable { onSessionClick(session.id) }
                            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatLibraryHistoryTime(session.updatedAtMillis),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = session.title,
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = AppTheme.spacing.md)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AppTheme.colors.outline.copy(alpha = 0.5f)
                        )
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun formatLibraryHistoryTime(updatedAtMillis: Long): String {
    val diff = (Clock.System.now().toEpochMilliseconds() - updatedAtMillis).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        diff < minute -> "now"
        diff < hour -> "${diff / minute}m"
        diff < day -> "${diff / hour}h"
        else -> "${diff / day}d"
    }
}

@Composable
fun LottieAnimationCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val primaryColor = AppTheme.colors.secondary
    val secondaryColor = AppTheme.colors.primary
    val surfaceColor = AppTheme.colors.surface

    BentoCard(
        modifier = modifier,
        backgroundColorBlob = primaryColor,
        blobCenter = { _, h -> Offset(-20.dp.value, h + 20.dp.value) },
        blobRadiusDp = 124f,
        blobHoverRadiusDp = 190f,
        blobAlpha = 0.18f,
        blobHoverAlpha = 0.31f,
        onClick = onClick
    ) { isHovered ->
        val arcSweep by animateFloatAsState(
            targetValue = if (isHovered) 320f else 235f,
            animationSpec = tween(durationMillis = 360)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = primaryColor.copy(alpha = if (isHovered) 0.24f else 0.15f),
                            shape = AppTheme.shape.full
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = primaryColor
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AppTheme.colors.outline.copy(alpha = if (isHovered) 0.55f else 0.3f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(AppTheme.shape.md)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = 0.12f),
                                primaryColor.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = primaryColor.copy(alpha = if (isHovered) 0.28f else 0.14f),
                        shape = AppTheme.shape.md
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.dp.toPx()
                    val radius = size.minDimension * 0.28f
                    val center = Offset(size.width * 0.28f, size.height * 0.52f)
                    drawCircle(
                        color = surfaceColor.copy(alpha = 0.72f),
                        radius = radius,
                        center = center
                    )
                    drawArc(
                        color = primaryColor.copy(alpha = 0.78f),
                        startAngle = -90f,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth)
                    )
                    repeat(3) { index ->
                        val x = size.width * (0.52f + index * 0.12f)
                        val dotRadius = size.minDimension * (0.06f + index * 0.008f)
                        drawCircle(
                            color = if (index % 2 == 0) {
                                secondaryColor.copy(alpha = 0.72f)
                            } else {
                                primaryColor.copy(alpha = 0.68f)
                            },
                            radius = dotRadius,
                            center = Offset(x, size.height * 0.52f)
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.library_lottie_json_label),
                    style = AppTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.68f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs)
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.library_lottie_animation),
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.library_lottie_animation_desc),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant,
                    lineHeight = AppTheme.typography.bodySmall.lineHeight * 1.2f
                )
            }
        }
    }
}

@Composable
fun EightBitBgmCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val primaryColor = AppTheme.colors.tertiary
    val secondaryColor = AppTheme.colors.primary

    BentoCard(
        modifier = modifier,
        backgroundColorBlob = primaryColor,
        blobCenter = { w, h -> Offset(w * 0.82f, h * 0.18f) },
        blobRadiusDp = 112f,
        blobHoverRadiusDp = 172f,
        blobAlpha = 0.2f,
        blobHoverAlpha = 0.32f,
        onClick = onClick
    ) { isHovered ->
        val equalizerProgress by animateFloatAsState(
            targetValue = if (isHovered) 1f else 0f,
            animationSpec = tween(durationMillis = 360)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = primaryColor.copy(alpha = if (isHovered) 0.24f else 0.15f),
                            shape = AppTheme.shape.full
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = primaryColor
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AppTheme.colors.outline.copy(alpha = if (isHovered) 0.55f else 0.3f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(AppTheme.shape.md)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = 0.12f),
                                primaryColor.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = primaryColor.copy(alpha = if (isHovered) 0.28f else 0.14f),
                        shape = AppTheme.shape.md
                    )
            ) {
                val label = stringResource(Res.string.library_8bit_chip_label)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.dp.toPx()
                    val columns = 12
                    val step = size.width / columns
                    for (index in 0 until columns) {
                        val idleHeightFraction = if (index % 3 == 0) 0.68f else if (index % 2 == 0) 0.42f else 0.28f
                        val hoverHeightFraction = when (index % 4) {
                            0 -> 0.86f
                            1 -> 0.48f
                            2 -> 0.72f
                            else -> 0.58f
                        }
                        val heightFraction = idleHeightFraction +
                            (hoverHeightFraction - idleHeightFraction) * equalizerProgress
                        val x = step * index + step * 0.5f
                        drawLine(
                            color = if (index % 2 == 0) {
                                primaryColor.copy(alpha = 0.72f + 0.12f * equalizerProgress)
                            } else {
                                secondaryColor.copy(alpha = 0.62f + 0.12f * equalizerProgress)
                            },
                            start = Offset(x, size.height * (1f - heightFraction)),
                            end = Offset(x, size.height * 0.76f),
                            strokeWidth = strokeWidth
                        )
                    }
                }
                Text(
                    text = label,
                    style = AppTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs)
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.library_8bit_bgm),
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.library_chiptune_bgm_desc),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant,
                    lineHeight = AppTheme.typography.bodySmall.lineHeight * 1.2f
                )
            }
        }
    }
}

@Composable
fun SvgImageCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val primaryColor = AppTheme.colors.primary
    val secondaryColor = AppTheme.colors.secondary
    val surfaceColor = AppTheme.colors.surface

    BentoCard(
        modifier = modifier,
        backgroundColorBlob = secondaryColor,
        blobCenter = { w, h -> Offset(w * 0.72f, h * 0.32f) },
        blobRadiusDp = 120f,
        blobHoverRadiusDp = 180f,
        blobAlpha = 0.18f,
        blobHoverAlpha = 0.3f,
        onClick = onClick
    ) { isHovered ->
        val illustrationProgress by animateFloatAsState(
            targetValue = if (isHovered) 1f else 0f,
            animationSpec = tween(durationMillis = 360)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = secondaryColor.copy(alpha = if (isHovered) 0.24f else 0.15f),
                            shape = AppTheme.shape.full
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = secondaryColor
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AppTheme.colors.outline.copy(alpha = if (isHovered) 0.55f else 0.3f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(AppTheme.shape.md)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.12f),
                                secondaryColor.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = secondaryColor.copy(alpha = if (isHovered) 0.26f else 0.14f),
                        shape = AppTheme.shape.md
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 1.4.dp.toPx()
                    val circleCenter = Offset(size.width * 0.26f, size.height * 0.52f)
                    val previewRadius = size.minDimension * (0.3f + 0.035f * illustrationProgress)
                    val ringRadius = size.minDimension * (0.22f + 0.035f * illustrationProgress)
                    val mountainPeak = Offset(
                        size.width * (0.62f + 0.02f * illustrationProgress),
                        size.height * (0.28f - 0.06f * illustrationProgress)
                    )
                    drawCircle(
                        color = surfaceColor.copy(alpha = 0.72f),
                        radius = previewRadius,
                        center = circleCenter
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.62f + 0.12f * illustrationProgress),
                        radius = ringRadius,
                        center = circleCenter,
                        style = Stroke(width = strokeWidth)
                    )
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.72f + 0.12f * illustrationProgress),
                        start = Offset(size.width * 0.46f, size.height * 0.62f),
                        end = mountainPeak,
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = primaryColor.copy(alpha = 0.68f + 0.12f * illustrationProgress),
                        start = mountainPeak,
                        end = Offset(
                            size.width * (0.78f + 0.04f * illustrationProgress),
                            size.height * (0.6f + 0.03f * illustrationProgress)
                        ),
                        strokeWidth = strokeWidth
                    )
                }
                Text(
                    text = "<svg>",
                    style = AppTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.68f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs)
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.library_svg_image),
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.library_svg_desc),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant,
                    lineHeight = AppTheme.typography.bodySmall.lineHeight * 1.2f
                )
            }
        }
    }
}

@Composable
fun ThemedBentoCard(
    modifier: Modifier = Modifier,
    title: String,
    desc: String,
    icon: ImageVector,
    backgroundColorBlob: Color,
    blobCenter: (width: Float, height: Float) -> Offset,
    onClick: () -> Unit = {}
) {
    BentoCard(
        modifier = modifier,
        backgroundColorBlob = backgroundColorBlob,
        blobCenter = blobCenter,
        blobRadiusDp = 100f,
        blobHoverRadiusDp = 150f,
        blobAlpha = 0.16f,
        blobHoverAlpha = 0.28f,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = backgroundColorBlob.copy(alpha = 0.15f),
                            shape = AppTheme.shape.full
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = backgroundColorBlob
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AppTheme.colors.outline.copy(alpha = 0.3f)
                )
            }

            Column {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = desc,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant,
                    lineHeight = AppTheme.typography.bodySmall.lineHeight * 1.2f
                )
            }
        }
    }
}

@Composable
fun ForgeNewVesselCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val translationY by animateDpAsState(
        targetValue = if (isHovered) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1.0f,
        animationSpec = tween(durationMillis = 300)
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.6f else 0.25f,
        animationSpec = tween(durationMillis = 300)
    )
    val glassAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.7f else 0.4f,
        animationSpec = tween(durationMillis = 300)
    )

    val density = LocalDensity.current
    val outlineColor = if (isHovered) AppTheme.colors.primary else AppTheme.colors.outline
    val cornerRadius = with(density) { 32.dp.toPx() } // Shape.xxl round corner size (32.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationY = translationY.toPx()
                this.scaleX = scale
                this.scaleY = scale
            }
            .clip(AppTheme.shape.xxl)
            .background(AppTheme.colors.surfaceContainerLow.copy(alpha = glassAlpha))
            .drawBehind {
                val stroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
                drawRoundRect(
                    color = outlineColor.copy(alpha = borderAlpha),
                    style = stroke,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            }
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isHovered) AppTheme.colors.primary.copy(alpha = 0.15f) else AppTheme.colors.outline.copy(alpha = 0.1f),
                        shape = AppTheme.shape.full
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isHovered) AppTheme.colors.primary else AppTheme.colors.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(Res.string.library_forge_vessel),
                style = AppTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isHovered) AppTheme.colors.primary else AppTheme.colors.onSurfaceVariant
            )
        }
    }
}

