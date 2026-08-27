package org.onion.agro.ui.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.onion.theme.state.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.onion.agro.viewmodel.ChatViewModel
import ui.theme.AppTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import com.onion.network.download.DownloadManager
import com.onion.network.download.DownloadStatus
import com.onion.network.download.DownloadTask
import com.onion.network.http.PlatformFileUtil
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TileMode
import com.onion.theme.style.glassSurface
import com.onion.theme.style.watercolorGradient
import kotlinx.coroutines.IO

private const val MINISTRAL_URL = "https://huggingface.co/litert-community/Ministral-3-3B-Instruct-2512/resolve/main/Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm?download=true"
private const val GEMMA_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"

@Composable
private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond <= 0 -> stringResource(Res.string.model_download_speed_bps, "0")
        bytesPerSecond < 1024 -> stringResource(Res.string.model_download_speed_bps, bytesPerSecond.toString())
        bytesPerSecond < 1024 * 1024 -> stringResource(
            Res.string.model_download_speed_kbps,
            (bytesPerSecond / 1024f).toString().take(4)
        )
        else -> stringResource(
            Res.string.model_download_speed_mbps,
            (bytesPerSecond / (1024f * 1024f)).toString().take(4)
        )
    }
}

@Composable
private fun formatEta(seconds: Long): String {
    if (seconds < 0) return stringResource(Res.string.model_download_eta_unknown)
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) {
        stringResource(Res.string.model_download_eta_min_sec, m.toString(), s.toString())
    } else {
        stringResource(Res.string.model_download_eta_sec, s.toString())
    }
}

private const val MINISTRAL_EXPECTED_SIZE = 2340982768L
private const val GEMMA_EXPECTED_SIZE = 3659530240L

@Composable
private fun FlowingProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.primary,
    secondaryColor: Color = AppTheme.colors.secondary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flowingProgress")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(AppTheme.shape.full)
            .background(AppTheme.colors.onSurface.copy(alpha = 0.05f))
            .drawWithContent {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.1f),
                            color,
                            secondaryColor,
                            color.copy(alpha = 0.1f)
                        ),
                        start = Offset(animatedOffset - 500f, 0f),
                        end = Offset(animatedOffset + 500f, 0f),
                        tileMode = TileMode.Repeated
                    )
                )
            }
    )
}

@Composable
fun ModelScreen() {
    val chatViewModel = koinInject<ChatViewModel>()
    val loadingState by chatViewModel.loadingModelState.collectAsState(0)
    val currentPath by chatViewModel.llmPath
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = koinInject<DownloadManager>()
    val downloadTasks by downloadManager.tasksFlow.collectAsState(emptyList())

    val cacheDirPath = remember { FileKit.cacheDir.path ?: "" }
    val ministralFilePath = remember(cacheDirPath) { "$cacheDirPath/Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm" }
    val gemmaFilePath = remember(cacheDirPath) { "$cacheDirPath/gemma-4-E4B.litertlm" }

    val ministralTask = remember(downloadTasks) { downloadTasks.firstOrNull { it.url == MINISTRAL_URL || it.filePath == ministralFilePath } }
    val gemmaTask = remember(downloadTasks) { downloadTasks.firstOrNull { it.url == GEMMA_URL || it.filePath == gemmaFilePath } }

    val ministralExists = remember(downloadTasks) {
        if (ministralTask != null) {
            ministralTask.status == DownloadStatus.COMPLETED
        } else {
            PlatformFileUtil.getFileSize(ministralFilePath) >= MINISTRAL_EXPECTED_SIZE
        }
    }
    val gemmaExists = remember(downloadTasks) {
        if (gemmaTask != null) {
            gemmaTask.status == DownloadStatus.COMPLETED
        } else {
            PlatformFileUtil.getFileSize(gemmaFilePath) >= GEMMA_EXPECTED_SIZE
        }
    }

    val contentType = AppTheme.contentType

    val activeId = remember(currentPath) {
        when {
            currentPath.contains("ministral", ignoreCase = true) -> "ministral"
            currentPath.contains("gemma", ignoreCase = true) -> "gemma"
            currentPath.isNotEmpty() -> "custom"
            else -> ""
        }
    }

    val onLoadClick: (String) -> Unit = { path ->
        coroutineScope.launch(Dispatchers.IO) {
            if (loadingState == 1) return@launch
            chatViewModel.llmPath.value = path
            chatViewModel.loadingModelState.emit(1)
            chatViewModel.initLLM()
        }
    }

    val onDownloadClick: (String, String, DownloadTask?) -> Unit = { url, filePath, task ->
        if (task != null) {
            when (task.status) {
                DownloadStatus.DOWNLOADING -> downloadManager.pause(task.id)
                DownloadStatus.PAUSED -> downloadManager.resume(task.id)
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> downloadManager.resume(task.id)
                else -> {}
            }
        } else {
            downloadManager.download(url, filePath)
        }
    }

    val onSelectCustomClick: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            if (loadingState == 1) return@launch
            chatViewModel.loadingModelState.emit(1)
            val path = chatViewModel.selectLlmFile()
            if (path.isNotBlank()) {
                chatViewModel.initLLM()
            }else chatViewModel.loadingModelState.emit(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (contentType == ContentType.Single) {
            // Mobile Layout: Column inside Scroll
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.surface)
                    .verticalScroll(rememberScrollState())
            ) {
                ModelColumnCard(
                    vendor = "Mistral AI",
                    title = "Ministral-3-3B",
                    desc = stringResource(Res.string.model_ministral3_desc),
                    contextWindow = "4k",
                    vram = "4GB",
                    imageResource = Res.drawable.model_ministral_card,
                    isActive = activeId == "ministral",
                    isLoading = loadingState == 1 && activeId == "ministral",
                    isDesktop = false,
                    isDownloaded = ministralExists,
                    downloadTask = ministralTask,
                    onDownloadClick = { onDownloadClick(MINISTRAL_URL, ministralFilePath, ministralTask) },
                    onClick = { onLoadClick(ministralFilePath) }
                )
                ModelColumnCard(
                    vendor = "Google",
                    title = "Gemma 4 4B",
                    desc = stringResource(Res.string.model_gemma4_desc),
                    contextWindow = "8k",
                    vram = "8GB",
                    imageResource = Res.drawable.model_gemma_card,
                    isActive = activeId == "gemma",
                    isLoading = loadingState == 1 && activeId == "gemma",
                    isDesktop = false,
                    isDownloaded = gemmaExists,
                    downloadTask = gemmaTask,
                    onDownloadClick = { onDownloadClick(GEMMA_URL, gemmaFilePath, gemmaTask) },
                    onClick = { onLoadClick(gemmaFilePath) }
                )
                CustomModelColumnCard(
                    imageResource = Res.drawable.model_custom_card,
                    isActive = activeId == "custom",
                    isLoading = loadingState == 1 && activeId == "custom",
                    isDesktop = false,
                    onClick = onSelectCustomClick
                )
            }
        } else {
            // Desktop Layout: Flex Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.surface)
            ) {
                val interact1 = remember { MutableInteractionSource() }
                val hover1 by interact1.collectIsHoveredAsState()
                val w1 by animateFloatAsState(if (hover1) 1.5f else 1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                
                val interact2 = remember { MutableInteractionSource() }
                val hover2 by interact2.collectIsHoveredAsState()
                val w2 by animateFloatAsState(if (hover2) 1.5f else 1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                
                val interact3 = remember { MutableInteractionSource() }
                val hover3 by interact3.collectIsHoveredAsState()
                val w3 by animateFloatAsState(if (hover3) 1.5f else 1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))

                ModelColumnCard(
                    modifier = Modifier.weight(w1),
                    interactionSource = interact1,
                    vendor = "Mistral AI",
                    title = "Ministral-3-3B",
                    desc = stringResource(Res.string.model_ministral3_desc),
                    contextWindow = "4k",
                    vram = "4GB",
                    imageResource = Res.drawable.model_ministral_card,
                    isActive = activeId == "ministral",
                    isLoading = loadingState == 1 && activeId == "ministral",
                    isDesktop = true,
                    isDownloaded = ministralExists,
                    downloadTask = ministralTask,
                    onDownloadClick = { onDownloadClick(MINISTRAL_URL, ministralFilePath, ministralTask) },
                    onClick = { onLoadClick(ministralFilePath) }
                )
                ModelColumnCard(
                    modifier = Modifier.weight(w2),
                    interactionSource = interact2,
                    vendor = "Google",
                    title = "Gemma 4 4B",
                    desc = stringResource(Res.string.model_gemma4_desc),
                    contextWindow = "8k",
                    vram = "8GB",
                    imageResource = Res.drawable.model_gemma_card,
                    isActive = activeId == "gemma",
                    isLoading = loadingState == 1 && activeId == "gemma",
                    isDesktop = true,
                    isDownloaded = gemmaExists,
                    downloadTask = gemmaTask,
                    onDownloadClick = { onDownloadClick(GEMMA_URL, gemmaFilePath, gemmaTask) },
                    onClick = { onLoadClick(gemmaFilePath) }
                )
                CustomModelColumnCard(
                    modifier = Modifier.weight(w3),
                    interactionSource = interact3,
                    imageResource = Res.drawable.model_custom_card,
                    isActive = activeId == "custom",
                    isLoading = loadingState == 1 && activeId == "custom",
                    isDesktop = true,
                    onClick = onSelectCustomClick
                )
            }
        }

        if (loadingState == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .pointerInput(Unit) {}
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .padding(horizontal = 24.dp)
                        .glassSurface(
                            shape = AppTheme.shape.xxl,
                            alpha = 0.85f,
                            borderAlpha = 0.2f
                        )
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = AppTheme.colors.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = stringResource(Res.string.model_loading_title),
                            style = AppTheme.typography.headlineMedium.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light,
                                color = AppTheme.colors.onSurface
                            ),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(Res.string.model_loading_subtitle),
                            style = AppTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Light,
                                color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        FlowingProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ModelColumnCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    vendor: String,
    title: String,
    desc: String,
    contextWindow: String,
    vram: String,
    imageResource: DrawableResource,
    isActive: Boolean,
    isLoading: Boolean,
    isDesktop: Boolean,
    isDownloaded: Boolean,
    downloadTask: DownloadTask?,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = AppTheme.isDark.value

    var mousePos by remember { mutableStateOf(Offset.Unspecified) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Sub-content reveal animation
    val subContentAlpha by animateFloatAsState(
        targetValue = if (isHovered || !isDesktop) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "alphaAnim"
    )

    val subContentOffsetY by animateDpAsState(
        targetValue = if (isHovered || !isDesktop) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 700, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "offsetAnim"
    )

    // Parallax logic matching JS
    val targetTranslateX = if (isHovered && mousePos.isSpecified && size.width > 0) {
        ((mousePos.x / size.width) - 0.5f) * 40f
    } else 0f
    val targetTranslateY = if (isHovered && mousePos.isSpecified && size.height > 0) {
        ((mousePos.y / size.height) - 0.5f) * 40f
    } else 0f

    val translateX by animateFloatAsState(
        targetValue = targetTranslateX,
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "translateXAnim"
    )
    val translateY by animateFloatAsState(
        targetValue = targetTranslateY,
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "translateYAnim"
    )

    // Image scale animation matching CSS
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.1f else 1f,
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "scaleAnim"
    )

    // CSS filter: saturate(1.2) brightness(1.05)
    val filterProgress by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "filterAnim"
    )
    val colorMatrix = remember(filterProgress, isDark) {
        ColorMatrix().apply {
            setToSaturation(1f + (if (isDark) 0.1f else 0.2f) * filterProgress)
            val baseBrightness = if (isDark) 0.7f else 1f
            val b = baseBrightness + 0.05f * filterProgress
            this.timesAssign(
                ColorMatrix(
                    floatArrayOf(
                        b, 0f, 0f, 0f, 0f,
                        0f, b, 0f, 0f, 0f,
                        0f, 0f, b, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val baseModifier = if (isDesktop) modifier else modifier.height(480.dp)
    
    val borderColor = AppTheme.colors.outlineVariant.copy(alpha = 0.2f)

    val cardOnClick = {
        if (isDownloaded) {
            onClick()
        } else {
            onDownloadClick()
        }
    }

    Box(
        modifier = baseModifier
            .fillMaxHeight()
            .onSizeChanged { size = it }
            .clip(RectangleShape)
            .drawBehind {
                if (isDesktop) {
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width.toFloat(), 0f),
                        end = Offset(size.width.toFloat(), size.height.toFloat()),
                        strokeWidth = 1.dp.toPx()
                    )
                } else {
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, size.height.toFloat()),
                        end = Offset(size.width.toFloat(), size.height.toFloat()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                mousePos = event.changes.first().position
                            }
                            PointerEventType.Exit -> {
                                mousePos = Offset.Unspecified
                            }
                        }
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = cardOnClick)
    ) {
        // Watercolor Background
        Image(
            painter = painterResource(imageResource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(colorMatrix),
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isDark) 0.35f else 1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationX = translateX
                    this.translationY = translateY
                }
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppTheme.colors.surface.copy(alpha = if (isDark) 0.4f else 0.2f),
                            Color.Transparent,
                            Color.Transparent,
                            AppTheme.colors.surface.copy(alpha = if (isDark) 0.9f else 0.7f)
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isDesktop) 40.dp else 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = AppTheme.colors.primary.copy(alpha = 0.3f),
                            shape = AppTheme.shape.full
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = vendor.uppercase(),
                        style = AppTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 2.sp),
                        color = AppTheme.colors.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                val titleStyle = if (isDesktop) {
                    AppTheme.typography.headlineLarge.copy(fontSize = 56.sp)
                } else {
                    AppTheme.typography.headlineLarge.copy(fontSize = 36.sp)
                }
                Text(
                    text = title,
                    style = titleStyle,
                    color = AppTheme.colors.onSurface,
                    modifier = Modifier.graphicsLayer {
                        translationX = if (isHovered) 8f else 0f
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = desc,
                    style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }

            // Bottom Section (Specs & Button)
            Column(
                modifier = Modifier
                    .alpha(subContentAlpha)
                    .offset(y = subContentOffsetY)
            ) {
                // Specs
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    // Border top line
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(AppTheme.colors.onSurface.copy(alpha = 0.1f))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CONTEXT WINDOW",
                                style = AppTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 2.sp),
                                color = AppTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = contextWindow,
                                style = AppTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                                color = AppTheme.colors.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VRAM REQUIRED",
                                style = AppTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 2.sp),
                                color = AppTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = vram,
                                style = AppTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                                color = AppTheme.colors.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Button or Download Progress Section
                if (!isDownloaded && downloadTask != null && 
                    (downloadTask.status == DownloadStatus.DOWNLOADING || 
                     downloadTask.status == DownloadStatus.QUEUED || 
                     downloadTask.status == DownloadStatus.PAUSED ||
                     downloadTask.status == DownloadStatus.FAILED)) {
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusText = when (downloadTask.status) {
                                DownloadStatus.QUEUED -> stringResource(Res.string.model_download_queued)
                                DownloadStatus.PAUSED -> stringResource(Res.string.model_download_paused)
                                DownloadStatus.FAILED -> stringResource(
                                    Res.string.model_download_failed,
                                    downloadTask.errorMessage?.take(40) ?: stringResource(Res.string.unknown_error)
                                )
                                else -> stringResource(Res.string.model_download_downloading)
                            }
                            val textColor = if (downloadTask.status == DownloadStatus.FAILED) {
                                AppTheme.colors.error
                            } else {
                                AppTheme.colors.onSurface.copy(alpha = 0.8f)
                            }
                            Text(
                                text = statusText,
                                style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (downloadTask.status != DownloadStatus.FAILED) {
                                Text(
                                    text = "${(downloadTask.progress * 100).toInt()}%",
                                    style = AppTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                                    color = AppTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        // Progress Bar
                        val progressBarColor = if (downloadTask.status == DownloadStatus.FAILED) {
                            AppTheme.colors.error.copy(alpha = 0.6f)
                        } else {
                            AppTheme.colors.primary.copy(alpha = 0.6f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    color = AppTheme.colors.onSurface.copy(alpha = 0.05f),
                                    shape = AppTheme.shape.full
                                )
                                .clickable {
                                    onDownloadClick()
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = downloadTask.progress.coerceIn(0f, 1f))
                                    .background(
                                        color = progressBarColor,
                                        shape = AppTheme.shape.full
                                    )
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (downloadTask.status == DownloadStatus.FAILED) {
                                Text(
                                    text = stringResource(Res.string.model_download_retry),
                                    style = AppTheme.typography.bodySmall.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                                    color = AppTheme.colors.error
                                )
                            } else {
                                Text(
                                    text = formatSpeed(downloadTask.speedBytesPerSecond),
                                    style = AppTheme.typography.bodySmall.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Light),
                                    color = AppTheme.colors.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = formatEta(downloadTask.etaSeconds),
                                    style = AppTheme.typography.bodySmall.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Light),
                                    color = AppTheme.colors.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = cardOnClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primary,
                            contentColor = AppTheme.colors.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AppTheme.colors.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.loading),
                                style = AppTheme.typography.labelMedium
                            )
                        } else if (!isDownloaded) {
                            Text(
                                text = stringResource(Res.string.model_action_download),
                                style = AppTheme.typography.labelMedium
                            )
                        } else if (isActive) {
                            Text(
                                text = stringResource(Res.string.model_action_active),
                                style = AppTheme.typography.labelMedium
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.model_action_load),
                                style = AppTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomModelColumnCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    imageResource: DrawableResource,
    isActive: Boolean,
    isLoading: Boolean,
    isDesktop: Boolean,
    onClick: () -> Unit
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = AppTheme.isDark.value

    var mousePos by remember { mutableStateOf(Offset.Unspecified) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val addIconScale by animateFloatAsState(
        targetValue = if (isHovered) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scaleAnim"
    )

    // Parallax logic matching JS
    val targetTranslateX = if (isHovered && mousePos.isSpecified && size.width > 0) {
        ((mousePos.x / size.width) - 0.5f) * 40f
    } else 0f
    val targetTranslateY = if (isHovered && mousePos.isSpecified && size.height > 0) {
        ((mousePos.y / size.height) - 0.5f) * 40f
    } else 0f

    val translateX by animateFloatAsState(
        targetValue = targetTranslateX,
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "translateXAnim"
    )
    val translateY by animateFloatAsState(
        targetValue = targetTranslateY,
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "translateYAnim"
    )

    // Image scale animation matching CSS
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.1f else 1f,
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "scaleBgAnim"
    )

    val baseModifier = if (isDesktop) modifier else modifier.height(480.dp)

    val primaryColor = AppTheme.colors.primary

    Box(
        modifier = baseModifier
            .fillMaxHeight()
            .onSizeChanged { size = it }
            .clip(RectangleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                mousePos = event.changes.first().position
                            }
                            PointerEventType.Exit -> {
                                mousePos = Offset.Unspecified
                            }
                        }
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        // Dashed border inside
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .drawBehind {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.2f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                    )
                }
        )

        // Background image with opacity
        Image(
            painter = painterResource(imageResource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isDark) 0.15f else 0.4f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationX = translateX
                    this.translationY = translateY
                }
        )

        // Content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .size(64.dp)
                    .background(
                        color = AppTheme.colors.primaryContainer.copy(alpha = 0.3f),
                        shape = AppTheme.shape.full
                    )
                    .border(
                        width = 1.dp,
                        color = AppTheme.colors.primary.copy(alpha = 0.1f),
                        shape = AppTheme.shape.full
                    )
                    .graphicsLayer {
                        scaleX = addIconScale
                        scaleY = addIconScale
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = AppTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = stringResource(Res.string.model_custom_title),
                style = AppTheme.typography.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.Light),
                color = AppTheme.colors.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.model_custom_tagline_format, stringResource(Res.string.model_custom_tagline)),
                style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.widthIn(max = 180.dp),
                textAlign = TextAlign.Center
            )

            if (isActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.model_action_active),
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.colors.primary
                )
            }
        }
    }
}
