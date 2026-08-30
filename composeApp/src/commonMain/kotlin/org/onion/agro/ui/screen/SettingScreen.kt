package org.onion.agro.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import agro.composeapp.generated.resources.Res
import com.onion.model.LlmEngineStatus
import agro.composeapp.generated.resources.*
import agro.composeapp.generated.resources.llm_setting_temp_title
import agro.composeapp.generated.resources.llm_setting_temp_desc
import agro.composeapp.generated.resources.llm_setting_temp_precise
import agro.composeapp.generated.resources.llm_setting_temp_creative
import agro.composeapp.generated.resources.llm_setting_topp_title
import agro.composeapp.generated.resources.llm_setting_topp_desc
import agro.composeapp.generated.resources.llm_setting_topp_restrictive
import agro.composeapp.generated.resources.llm_setting_topp_open
import agro.composeapp.generated.resources.llm_setting_context_limits
import agro.composeapp.generated.resources.llm_setting_max_tokens
import agro.composeapp.generated.resources.llm_setting_max_tokens_hint
import agro.composeapp.generated.resources.llm_setting_context_shift
import agro.composeapp.generated.resources.llm_setting_context_shift_desc
import agro.composeapp.generated.resources.llm_setting_system_blueprint
import agro.composeapp.generated.resources.llm_setting_system_blueprint_desc
import agro.composeapp.generated.resources.llm_setting_system_blueprint_placeholder
import agro.composeapp.generated.resources.llm_setting_topk_title
import agro.composeapp.generated.resources.llm_setting_topk_desc
import agro.composeapp.generated.resources.llm_setting_thinking_title
import agro.composeapp.generated.resources.llm_setting_thinking_desc
import agro.composeapp.generated.resources.llm_setting_speculative_title
import agro.composeapp.generated.resources.llm_setting_speculative_desc
import agro.composeapp.generated.resources.llm_setting_cognitive_features
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.onion.agro.viewmodel.ChatViewModel
import org.onion.agro.viewmodel.BenchmarkUiState
import ui.theme.AppTheme
import com.onion.theme.state.ContentType
import com.onion.theme.style.glassSurface
import com.onion.theme.style.watercolorGradient
import agro.composeapp.generated.resources.llm_setting_btn_apply
import agro.composeapp.generated.resources.llm_setting_btn_reset
import kotlin.math.roundToInt

enum class SettingTab {
    PARAMETERS,
    BENCHMARKS
}

internal val SpeedIcon: ImageVector = ImageVector.Builder(
    name = "Speed",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).path(
    fill = SolidColor(Color.White),
    pathFillType = PathFillType.NonZero
) {
    moveTo(20.38f, 8.57f)
    lineToRelative(-1.23f, 1.85f)
    curveToRelative(0.48f, 2.37f, -0.05f, 5.09f, -0.22f, 7.58f)
    horizontalLineTo(5.07f)
    curveToRelative(-0.17f, -2.49f, -0.7f, -5.21f, -0.22f, -7.58f)
    lineTo(3.62f, 8.57f)
    curveTo(2.35f, 11.45f, 2.45f, 15.11f, 3.35f, 19f)
    curveToRelative(0.35f, 0.6f, 1.0f, 1.0f, 1.72f, 1.0f)
    horizontalLineToRelative(13.85f)
    curveToRelative(0.72f, 0.0f, 1.37f, -0.4f, 1.74f, -1.0f)
    curveToRelative(0.9f, -3.89f, 1.0f, -7.55f, -0.28f, -10.43f)
    close()
    moveTo(10.59f, 15.41f)
    curveToRelative(0.78f, 0.78f, 2.05f, 0.78f, 2.83f, 0.0f)
    lineToRelative(5.66f, -8.49f)
    lineToRelative(-8.49f, 5.66f)
    curveToRelative(-0.78f, 0.78f, -0.78f, 2.05f, 0.0f, 2.83f)
    close()
}.build()

private fun formatContextSize(tokens: Int): String {
    return when {
        tokens >= 1024 -> "${tokens / 1024}K"
        tokens > 0 -> "$tokens"
        else -> "--"
    }
}

@Composable
fun SettingScreen() {
    val chatViewModel = koinInject<ChatViewModel>()
    val temp by chatViewModel.temperature
    val topPVal by chatViewModel.topP
    val topKVal by chatViewModel.topK
    val enableThinking by chatViewModel.enableThinking
    val enableSpeculativeDecoding by chatViewModel.enableSpeculativeDecoding
    val maxTokens by chatViewModel.lmMaxNumTokens
    val contextShift by chatViewModel.systemContextShift
    val sysPrompt by chatViewModel.systemPrompt
    val lmBackend by chatViewModel.lmBackend
    val benchmarkState by chatViewModel.benchmarkUiState.collectAsState()
    val enableBenchmark by chatViewModel.enableBenchmark
    val isGenerating by chatViewModel.isGenerating
    val llmEngineStatus by chatViewModel.llmEngineStatus.collectAsState()
    val isEngineBusy = isGenerating || llmEngineStatus == LlmEngineStatus.GENERATING

    var selectedTab by remember { mutableStateOf(SettingTab.PARAMETERS) }
    val currentTab = if (enableBenchmark) selectedTab else SettingTab.PARAMETERS

    LaunchedEffect(enableBenchmark) {
        if (!enableBenchmark && selectedTab == SettingTab.BENCHMARKS) {
            selectedTab = SettingTab.PARAMETERS
        }
    }

    val containerPadding = if (AppTheme.contentType == ContentType.Dual) {
        AppTheme.spacing.containerPaddingDesktop
    } else {
        AppTheme.spacing.containerPaddingMobile
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .watercolorGradient()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(containerPadding)
        ) {
            if (enableBenchmark) {
                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            val y = size.height - strokeWidth / 2
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.25f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeWidth
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl)
                ) {
                    TabButton(
                        title = stringResource(Res.string.llm_settings_tab_parameters),
                        selected = currentTab == SettingTab.PARAMETERS,
                        onClick = { selectedTab = SettingTab.PARAMETERS }
                    )
                    TabButton(
                        title = stringResource(Res.string.llm_settings_tab_benchmarks),
                        selected = currentTab == SettingTab.BENCHMARKS,
                        onClick = {
                            selectedTab = SettingTab.BENCHMARKS
                            chatViewModel.refreshHardwareStats()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(AppTheme.spacing.xl))
            }

            when (currentTab) {
                SettingTab.PARAMETERS -> {
                    // Header
                    Text(
                        text = stringResource(Res.string.llm_settings_title),
                        style = AppTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.5).sp
                        ),
                        color = AppTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(AppTheme.spacing.sm))
                    Text(
                        text = stringResource(Res.string.llm_settings_subtitle),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.tertiary.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(AppTheme.spacing.xl))

                    // Bento Grid
                    if (AppTheme.contentType == ContentType.Dual) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                            ) {
                                TemperatureCard(chatViewModel, temp)
                                TopPCard(chatViewModel, topPVal)
                                TopKCard(chatViewModel, topKVal)
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                            ) {
                                ContextLimitsCard(chatViewModel, maxTokens, contextShift)
                                CognitiveFeaturesCard(chatViewModel, enableThinking, enableSpeculativeDecoding)
                                SystemBlueprintCard(chatViewModel, sysPrompt)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                        ) {
                            TemperatureCard(chatViewModel, temp)
                            TopPCard(chatViewModel, topPVal)
                            TopKCard(chatViewModel, topKVal)
                            ContextLimitsCard(chatViewModel, maxTokens, contextShift)
                            CognitiveFeaturesCard(chatViewModel, enableThinking, enableSpeculativeDecoding)
                            SystemBlueprintCard(chatViewModel, sysPrompt)
                        }
                    }

                    Spacer(modifier = Modifier.height(AppTheme.spacing.xl))

                    // Bottom Action Area
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppTheme.colors.outlineVariant.copy(alpha = 0.2f))
                    )

                    Spacer(modifier = Modifier.height(AppTheme.spacing.lg))

                    val isSingle = AppTheme.contentType == ContentType.Single
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSingle) Arrangement.spacedBy(AppTheme.spacing.md) else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                chatViewModel.resetSettings()
                            },
                            modifier = if (isSingle) Modifier.weight(1f) else Modifier,
                            shape = AppTheme.shape.full,
                            border = BorderStroke(1.dp, AppTheme.colors.outline.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppTheme.colors.tertiary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.llm_setting_btn_reset),
                                style = AppTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }

                        if (!isSingle) {
                            Spacer(modifier = Modifier.width(AppTheme.spacing.md))
                        }

                        Button(
                            onClick = {
                                chatViewModel.applyConversationSettings()
                            },
                            modifier = if (isSingle) Modifier.weight(1f) else Modifier,
                            shape = AppTheme.shape.full,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primary,
                                contentColor = AppTheme.colors.onPrimary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.llm_setting_btn_apply),
                                style = AppTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }

                SettingTab.BENCHMARKS -> {
                    // Header
                    Text(
                        text = stringResource(Res.string.llm_benchmark_title),
                        style = AppTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.5).sp
                        ),
                        color = AppTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(AppTheme.spacing.sm))
                    Text(
                        text = stringResource(Res.string.llm_benchmark_subtitle),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.tertiary.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(AppTheme.spacing.xl))

                    // Benchmarks Bento Grid
                    if (AppTheme.contentType == ContentType.Dual) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                        ) {
                            ThroughputTestCard(
                                benchmarkState = benchmarkState,
                                maxTokens = maxTokens,
                                isEngineBusy = isEngineBusy,
                                onRunTest = { chatViewModel.runBenchmarkTest() },
                                onCancelTest = { chatViewModel.cancelBenchmarkTest() },
                                modifier = Modifier.weight(1f)
                            )
                            HardwareUtilizationCard(
                                benchmarkState = benchmarkState,
                                backend = lmBackend,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
                        ) {
                            ThroughputTestCard(
                                benchmarkState = benchmarkState,
                                maxTokens = maxTokens,
                                isEngineBusy = isEngineBusy,
                                onRunTest = { chatViewModel.runBenchmarkTest() },
                                onCancelTest = { chatViewModel.cancelBenchmarkTest() }
                            )
                            HardwareUtilizationCard(
                                benchmarkState = benchmarkState,
                                backend = lmBackend
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppTheme.spacing.lg))

                    // Live Output Preview & Test Prompt Card
                    BenchmarkLiveOutputCard(
                        benchmarkState = benchmarkState,
                        isEngineBusy = isEngineBusy,
                        onPromptChange = { chatViewModel.updateBenchmarkPrompt(it) },
                        onRunTestWithPrompt = { chatViewModel.runBenchmarkTest(it) },
                        onCancelTest = { chatViewModel.cancelBenchmarkTest() }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    accentColor: Color,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )
            .padding(AppTheme.spacing.lg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.15f), AppTheme.shape.md),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(AppTheme.size.iconLarge)
                    )
                }
                Text(
                    text = title,
                    style = AppTheme.typography.headlineMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = AppTheme.colors.onSurface
                )
            }
            content()
        }
    }
}

@Composable
fun TemperatureCard(chatViewModel: ChatViewModel, temp: Float) {
    SettingCard(
        accentColor = AppTheme.colors.primary,
        icon = Icons.Default.Thermostat,
        title = stringResource(Res.string.llm_setting_temp_title)
    ) {
        Text(
            text = stringResource(Res.string.llm_setting_temp_desc),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.llm_setting_value_label, ((temp * 10).roundToInt() / 10.0).toString()),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.onSurfaceVariant
            )
        }

        Slider(
            value = temp,
            onValueChange = { chatViewModel.temperature.value = it },
            valueRange = 0f..2f,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = AppTheme.colors.primary,
                activeTrackColor = AppTheme.colors.primary,
                inactiveTrackColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.5f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.llm_setting_temp_precise),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.tertiary.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(Res.string.llm_setting_temp_creative),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.tertiary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TopPCard(chatViewModel: ChatViewModel, topPVal: Float) {
    SettingCard(
        accentColor = AppTheme.colors.secondary,
        icon = Icons.Default.FilterList,
        title = stringResource(Res.string.llm_setting_topp_title)
    ) {
        Text(
            text = stringResource(Res.string.llm_setting_topp_desc),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.llm_setting_value_label, ((topPVal * 100).roundToInt() / 100.0).toString()),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.onSurfaceVariant
            )
        }

        Slider(
            value = topPVal,
            onValueChange = { chatViewModel.topP.value = it },
            valueRange = 0f..1f,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = AppTheme.colors.secondary,
                activeTrackColor = AppTheme.colors.secondary,
                inactiveTrackColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.5f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.llm_setting_topp_restrictive),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.tertiary.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(Res.string.llm_setting_topp_open),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.tertiary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TopKCard(chatViewModel: ChatViewModel, topKVal: Int) {
    SettingCard(
        accentColor = AppTheme.colors.secondary,
        icon = Icons.Default.FilterList,
        title = stringResource(Res.string.llm_setting_topk_title)
    ) {
        Text(
            text = stringResource(Res.string.llm_setting_topk_desc),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.llm_setting_value_label, topKVal.toString()),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.onSurfaceVariant
            )
        }

        Slider(
            value = topKVal.toFloat(),
            onValueChange = { chatViewModel.topK.value = it.roundToInt() },
            valueRange = 5f..100f,
            steps = 95,
            colors = SliderDefaults.colors(
                thumbColor = AppTheme.colors.secondary,
                activeTrackColor = AppTheme.colors.secondary,
                inactiveTrackColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun CognitiveFeaturesCard(
    chatViewModel: ChatViewModel,
    enableThinking: Boolean,
    enableSpeculativeDecoding: Boolean
) {
    SettingCard(
        accentColor = AppTheme.colors.primary,
        icon = Icons.Default.Psychology,
        title = stringResource(Res.string.llm_setting_cognitive_features)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.llm_setting_thinking_title),
                    style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.onSurface
                )
                Text(
                    text = stringResource(Res.string.llm_setting_thinking_desc),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            EtherealSwitch(
                checked = enableThinking,
                onCheckedChange = { chatViewModel.enableThinking.value = it }
            )
        }

        Spacer(modifier = Modifier.height(AppTheme.spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.llm_setting_speculative_title),
                    style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.onSurface
                )
                Text(
                    text = stringResource(Res.string.llm_setting_speculative_desc),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            EtherealSwitch(
                checked = enableSpeculativeDecoding,
                onCheckedChange = { chatViewModel.enableSpeculativeDecoding.value = it }
            )
        }
    }
}

@Composable
fun ContextLimitsCard(chatViewModel: ChatViewModel, maxTokens: Int, contextShift: Boolean) {
    SettingCard(
        accentColor = AppTheme.colors.tertiary,
        icon = Icons.Default.Memory,
        title = stringResource(Res.string.llm_setting_context_limits)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.llm_setting_max_tokens),
                    style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.onSurface
                )
                Text(
                    text = stringResource(Res.string.llm_setting_max_tokens_hint),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppTheme.shape.md)
                        .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            chatViewModel.adjustLmMaxNumTokens(increase = false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", color = AppTheme.colors.onSurface, style = AppTheme.typography.labelMedium)
                }

                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(36.dp)
                        .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.2f), AppTheme.shape.md)
                        .background(AppTheme.colors.surfaceContainerLow.copy(alpha = 0.3f), AppTheme.shape.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = maxTokens.toString(),
                        color = AppTheme.colors.onSurface,
                        style = AppTheme.typography.labelMedium
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppTheme.shape.md)
                        .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            chatViewModel.adjustLmMaxNumTokens(increase = true)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = AppTheme.colors.onSurface, style = AppTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.llm_setting_context_shift),
                    style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.onSurface
                )
                Text(
                    text = stringResource(Res.string.llm_setting_context_shift_desc),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            EtherealSwitch(
                checked = contextShift,
                onCheckedChange = { chatViewModel.systemContextShift.value = it }
            )
        }
    }
}

@Composable
fun SystemBlueprintCard(chatViewModel: ChatViewModel, sysPrompt: String) {
    SettingCard(
        accentColor = AppTheme.colors.tertiary,
        icon = Icons.Default.EditNote,
        title = stringResource(Res.string.llm_setting_system_blueprint)
    ) {
        Text(
            text = stringResource(Res.string.llm_setting_system_blueprint_desc),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
        )

        OutlinedTextField(
            value = sysPrompt,
            onValueChange = { chatViewModel.systemPrompt.value = it },
            placeholder = { Text(stringResource(Res.string.llm_setting_system_blueprint_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = AppTheme.shape.md,
            textStyle = AppTheme.typography.bodyMedium.copy(color = AppTheme.colors.onSurface),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.primary,
                unfocusedBorderColor = AppTheme.colors.outline.copy(alpha = 0.2f),
                focusedContainerColor = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.4f),
                unfocusedContainerColor = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.2f),
                cursorColor = AppTheme.colors.primary
            )
        )
    }
}

@Composable
fun EtherealSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val trackBg = if (checked) {
        AppTheme.colors.primary.copy(alpha = 0.3f)
    } else {
        AppTheme.colors.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderCol = if (checked) {
        AppTheme.colors.primary.copy(alpha = 0.6f)
    } else {
        AppTheme.colors.outline.copy(alpha = 0.2f)
    }
    val thumbCol = if (checked) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .size(44.dp, 24.dp)
            .clip(AppTheme.shape.full)
            .background(trackBg)
            .border(1.dp, borderCol, AppTheme.shape.full)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .background(thumbCol, CircleShape)
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        if (selected) AppTheme.colors.primary else AppTheme.colors.tertiary.copy(alpha = 0.7f)
    )
    val indicatorColor by animateColorAsState(
        if (selected) AppTheme.colors.primary else Color.Transparent
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(bottom = AppTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = textColor
        )
        Spacer(modifier = Modifier.height(AppTheme.spacing.xs))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(indicatorColor, AppTheme.shape.full)
        )
    }
}

@Composable
fun ThroughputTestCard(
    benchmarkState: BenchmarkUiState,
    maxTokens: Int,
    isEngineBusy: Boolean,
    onRunTest: () -> Unit,
    onCancelTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )
            .padding(AppTheme.spacing.xl)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppTheme.shape.md)
                            .background(AppTheme.colors.primaryContainer.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = SpeedIcon,
                            contentDescription = "Speed",
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.llm_benchmark_throughput_title),
                        style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.onSurface
                    )
                }

                if (benchmarkState.isRunning) {
                    Button(
                        onClick = onCancelTest,
                        shape = AppTheme.shape.full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.errorContainer.copy(alpha = 0.8f),
                            contentColor = AppTheme.colors.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.llm_benchmark_stop),
                            style = AppTheme.typography.labelMedium
                        )
                    }
                } else {
                    Button(
                        onClick = onRunTest,
                        enabled = !isEngineBusy,
                        shape = AppTheme.shape.full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryContainer.copy(alpha = 0.45f),
                            contentColor = AppTheme.colors.primary,
                            disabledContainerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.3f),
                            disabledContentColor = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isEngineBusy) {
                                stringResource(Res.string.llm_benchmark_engine_busy)
                            } else {
                                stringResource(Res.string.llm_benchmark_run_test)
                            },
                            style = AppTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.spacing.xl))

            // Center large number display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val displayText = if (benchmarkState.isWarmingUp) {
                    "..."
                } else if (benchmarkState.decodeTokensPerSecond > 0) {
                    "${benchmarkState.decodeTokensPerSecond}"
                } else if (benchmarkState.isRunning) {
                    "..."
                } else {
                    "--"
                }

                Text(
                    text = displayText,
                    style = AppTheme.typography.headlineLarge.copy(
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp
                    ),
                    color = AppTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (benchmarkState.isWarmingUp) {
                        stringResource(Res.string.llm_benchmark_warming_up)
                    } else {
                        stringResource(Res.string.llm_benchmark_tokens_per_second)
                    },
                    style = AppTheme.typography.bodyMedium,
                    color = if (benchmarkState.isWarmingUp) {
                        AppTheme.colors.primary
                    } else {
                        AppTheme.colors.onSurfaceVariant.copy(alpha = 0.75f)
                    }
                )

                if (benchmarkState.prefillTokensPerSecond > 0) {
                    Spacer(modifier = Modifier.height(AppTheme.spacing.xs))
                    Text(
                        text = stringResource(Res.string.llm_benchmark_prefill_speed, benchmarkState.prefillTokensPerSecond),
                        style = AppTheme.typography.labelSmall,
                        color = AppTheme.colors.primary.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.spacing.lg))

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.colors.outlineVariant.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(AppTheme.spacing.md))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val latencyText = if (benchmarkState.latencyMs > 0) {
                    stringResource(Res.string.llm_benchmark_latency, benchmarkState.latencyMs)
                } else {
                    "Latency: --"
                }
                val contextVal = if (benchmarkState.contextTokens > 0) benchmarkState.contextTokens else maxTokens
                val contextText = stringResource(Res.string.llm_benchmark_context, formatContextSize(contextVal))

                Text(
                    text = latencyText,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = contextText,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun HardwareUtilizationCard(
    benchmarkState: BenchmarkUiState,
    backend: String,
    modifier: Modifier = Modifier
) {
    val normalizedBackend = backend.ifBlank { "CPU" }.uppercase()
    val resourceUsage = benchmarkState.resourceUsage
    val isCpuBackend = normalizedBackend == "CPU"
    val displayedCpuLoad = if (benchmarkState.isRunning) {
        resourceUsage.currentCpuLoadPercent
    } else {
        resourceUsage.peakCpuLoadPercent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )
            .padding(AppTheme.spacing.xl)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppTheme.shape.md)
                            .background(AppTheme.colors.secondaryContainer.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = stringResource(Res.string.llm_benchmark_hardware_title),
                            tint = AppTheme.colors.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.llm_benchmark_hardware_title),
                        style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.onSurface
                    )
                }

                // Backend badge
                Box(
                    modifier = Modifier
                        .clip(AppTheme.shape.full)
                        .background(AppTheme.colors.secondaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = normalizedBackend,
                        style = AppTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AppTheme.colors.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.spacing.xl))

            // Backend-aware metrics. Accelerator utilization is intentionally left
            // unavailable when the platform cannot expose a reliable driver counter.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
            ) {
                val computeValue = when {
                    !isCpuBackend -> stringResource(Res.string.llm_benchmark_metric_unavailable)
                    displayedCpuLoad != null -> {
                        if (benchmarkState.isRunning) {
                            stringResource(
                                Res.string.llm_benchmark_cpu_current,
                                displayedCpuLoad.roundToInt(),
                            )
                        } else {
                            stringResource(
                                Res.string.llm_benchmark_cpu_peak,
                                displayedCpuLoad.roundToInt(),
                            )
                        }
                    }
                    else -> stringResource(Res.string.llm_benchmark_metric_not_measured)
                }
                BenchmarkProgressBar(
                    label = if (isCpuBackend) {
                        stringResource(Res.string.llm_benchmark_process_cpu)
                    } else {
                        stringResource(Res.string.llm_benchmark_backend_compute, normalizedBackend)
                    },
                    valueText = computeValue,
                    progress = if (isCpuBackend) {
                        displayedCpuLoad?.div(100.0)?.toFloat()
                    } else {
                        null
                    },
                    barColor = AppTheme.colors.secondary
                )

                val hasBenchmarkMemoryWindow = benchmarkState.isRunning || benchmarkState.hasCompletedTest
                val displayedMemoryBytes = if (hasBenchmarkMemoryWindow) {
                    resourceUsage.peakResidentMemoryBytes
                } else {
                    resourceUsage.currentResidentMemoryBytes
                }
                val totalMemoryBytes = resourceUsage.totalPhysicalMemoryBytes
                val peakMemoryDeltaBytes = resourceUsage.peakResidentMemoryDeltaBytes
                val memoryText = when {
                    displayedMemoryBytes == null -> {
                        stringResource(Res.string.llm_benchmark_metric_unavailable)
                    }
                    hasBenchmarkMemoryWindow -> stringResource(
                        Res.string.llm_benchmark_memory_peak_delta,
                        displayedMemoryBytes.toMebibytes(),
                        (peakMemoryDeltaBytes ?: 0L).toMebibytes(),
                    )
                    else -> stringResource(
                        Res.string.llm_benchmark_memory_current,
                        displayedMemoryBytes.toMebibytes(),
                    )
                }
                BenchmarkProgressBar(
                    label = stringResource(Res.string.llm_benchmark_process_memory, normalizedBackend),
                    valueText = memoryText,
                    progress = if (
                        displayedMemoryBytes != null &&
                        totalMemoryBytes != null &&
                        totalMemoryBytes > 0L
                    ) {
                        (displayedMemoryBytes.toDouble() / totalMemoryBytes.toDouble()).toFloat()
                    } else {
                        null
                    },
                    barColor = AppTheme.colors.primary
                )
            }
        }
    }
}

@Composable
private fun BenchmarkProgressBar(
    label: String,
    valueText: String,
    progress: Float?,
    barColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = AppTheme.typography.labelSmall,
                color = AppTheme.colors.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = AppTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = AppTheme.colors.onSurface
            )
        }
        Spacer(modifier = Modifier.height(AppTheme.spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(AppTheme.shape.full)
                .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(AppTheme.shape.full)
                    .background(barColor)
            )
        }
    }
}

private fun Long.toMebibytes(): Long = (this / BYTES_PER_MEBIBYTE).coerceAtLeast(0L)

private const val BYTES_PER_MEBIBYTE = 1024L * 1024L

@Composable
fun BenchmarkLiveOutputCard(
    benchmarkState: BenchmarkUiState,
    isEngineBusy: Boolean,
    onPromptChange: (String) -> Unit,
    onRunTestWithPrompt: (String) -> Unit,
    onCancelTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outputScrollState = rememberScrollState()

    LaunchedEffect(benchmarkState.liveOutputText) {
        if (benchmarkState.isRunning && benchmarkState.liveOutputText.isNotEmpty()) {
            outputScrollState.scrollTo(outputScrollState.maxValue)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )
            .padding(AppTheme.spacing.xl)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppTheme.shape.md)
                            .background(AppTheme.colors.tertiaryContainer.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Output",
                            tint = AppTheme.colors.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(Res.string.llm_benchmark_live_output),
                            style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = AppTheme.colors.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.llm_benchmark_prompt_label),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (benchmarkState.isRunning) {
                    Button(
                        onClick = onCancelTest,
                        shape = AppTheme.shape.full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.errorContainer.copy(alpha = 0.8f),
                            contentColor = AppTheme.colors.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.llm_benchmark_stop),
                            style = AppTheme.typography.labelMedium
                        )
                    }
                } else {
                    Button(
                        onClick = { onRunTestWithPrompt(benchmarkState.testPrompt) },
                        enabled = !isEngineBusy,
                        shape = AppTheme.shape.full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primaryContainer.copy(alpha = 0.45f),
                            contentColor = AppTheme.colors.primary,
                            disabledContainerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.3f),
                            disabledContentColor = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isEngineBusy) {
                                stringResource(Res.string.llm_benchmark_engine_busy)
                            } else {
                                stringResource(Res.string.llm_benchmark_run_test)
                            },
                            style = AppTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.spacing.md))

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
            ) {
                val presetAi = "Explain the fundamentals of artificial intelligence and machine learning concisely."
                val presetRelativity = "Explain the theory of relativity and its core principles concisely."
                val presetStory = "Write a brief 100-word science fiction opening scene about discovering an alien signal."

                val presetAiLabel = stringResource(Res.string.llm_benchmark_prompt_preset_ai)
                val presetRelativityLabel = stringResource(Res.string.llm_benchmark_prompt_preset_relativity)
                val presetStoryLabel = stringResource(Res.string.llm_benchmark_prompt_preset_story)

                BenchmarkPresetChip(
                    text = presetAiLabel,
                    isSelected = benchmarkState.testPrompt == presetAi,
                    onClick = { onPromptChange(presetAi) }
                )
                BenchmarkPresetChip(
                    text = presetRelativityLabel,
                    isSelected = benchmarkState.testPrompt == presetRelativity,
                    onClick = { onPromptChange(presetRelativity) }
                )
                BenchmarkPresetChip(
                    text = presetStoryLabel,
                    isSelected = benchmarkState.testPrompt == presetStory,
                    onClick = { onPromptChange(presetStory) }
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.spacing.md))

            // Editable prompt field
            OutlinedTextField(
                value = benchmarkState.testPrompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = AppTheme.typography.bodyMedium,
                shape = AppTheme.shape.md,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppTheme.colors.primary,
                    unfocusedBorderColor = AppTheme.colors.outlineVariant.copy(alpha = 0.4f),
                    focusedContainerColor = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.5f),
                    unfocusedContainerColor = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.5f)
                ),
                maxLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!benchmarkState.isRunning && !isEngineBusy) {
                            onRunTestWithPrompt(benchmarkState.testPrompt)
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (benchmarkState.isRunning) {
                                onCancelTest()
                            } else {
                                onRunTestWithPrompt(benchmarkState.testPrompt)
                            }
                        },
                        enabled = benchmarkState.isRunning || !isEngineBusy
                    ) {
                        Icon(
                            imageVector = if (benchmarkState.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (benchmarkState.isRunning) "Stop" else "Run",
                            tint = if (benchmarkState.isRunning) AppTheme.colors.error else AppTheme.colors.primary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(AppTheme.spacing.lg))

            // Streamed output surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(AppTheme.shape.md)
                    .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, AppTheme.colors.outlineVariant.copy(alpha = 0.25f), AppTheme.shape.md)
                    .padding(AppTheme.spacing.md)
                    .verticalScroll(outputScrollState)
            ) {
                if (benchmarkState.liveOutputText.isNotEmpty()) {
                    Text(
                        text = benchmarkState.liveOutputText,
                        style = AppTheme.typography.bodySmall.copy(
                            lineHeight = 20.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = AppTheme.colors.onSurface
                    )
                } else if (benchmarkState.isRunning) {
                    Text(
                        text = if (benchmarkState.isWarmingUp) {
                            stringResource(Res.string.llm_benchmark_warming_up)
                        } else {
                            stringResource(Res.string.llm_benchmark_running)
                        },
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.primary.copy(alpha = 0.8f)
                    )
                } else if (benchmarkState.errorMessage != null) {
                    Text(
                        text = benchmarkState.errorMessage,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.error
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.llm_benchmark_hint),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkPresetChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) AppTheme.colors.primaryContainer.copy(alpha = 0.35f) else AppTheme.colors.surfaceVariant.copy(alpha = 0.3f)
    val border = if (isSelected) AppTheme.colors.primary else AppTheme.colors.outlineVariant.copy(alpha = 0.3f)
    val textCol = if (isSelected) AppTheme.colors.primary else AppTheme.colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(AppTheme.shape.full)
            .background(bg)
            .border(1.dp, border, AppTheme.shape.full)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = AppTheme.typography.labelSmall,
            color = textCol,
            maxLines = 1
        )
    }
}
