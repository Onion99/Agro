package org.onion.agro.ui.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import agro.composeapp.generated.resources.Res
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
import ui.theme.AppTheme
import com.onion.theme.state.ContentType
import com.onion.theme.style.glassSurface
import com.onion.theme.style.watercolorGradient
import agro.composeapp.generated.resources.llm_setting_btn_apply
import agro.composeapp.generated.resources.llm_setting_btn_reset
import kotlin.math.roundToInt

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
    onCheckedChange: (Boolean) -> Unit
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
        modifier = Modifier
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
