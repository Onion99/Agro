package org.onion.agro.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import org.onion.agro.ui.navigation.route.RoutePage
import androidx.navigation3.ui.NavDisplay
import com.onion.model.LlmEngineStatus
import com.onion.theme.helper.verticalSafePadding
import com.onion.theme.state.AdaptiveLayoutType
import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.dark_theme
import agro.composeapp.generated.resources.ic_moon
import agro.composeapp.generated.resources.ic_sun
import agro.composeapp.generated.resources.light_theme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.onion.agro.BuildConfig
import org.onion.agro.ui.component.GrisWatercolorStatusIndicator
import org.onion.agro.ui.navigation.NavActions
import org.onion.agro.ui.navigation.route.MainRoute
import org.onion.agro.ui.navigation.route.NAV_BOTTOM_ITEMS
import org.onion.agro.viewmodel.ChatViewModel
import ui.theme.AppTheme


@Composable
fun MainScreen(
    onAdvancedSettingsClick: () -> Unit = {}
) {
    val backstack = remember { mutableStateListOf<Any>(MainRoute.HomeRoute) }
    val mainNavActions = remember(backstack) { NavActions(backstack) }

    MainContent(backstack, mainNavActions, onAdvancedSettingsClick)
}

@Composable
fun MainContent(
    backstack: MutableList<Any>,
    mainNavActions: NavActions,
    onAdvancedSettingsClick: () -> Unit
) {
    val chatViewModel = koinInject<ChatViewModel>()
    val llmEngineStatus by chatViewModel.llmEngineStatus.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            // ── Desktop/Tablet: Glassmorphism vertical navigation rail ──
            AnimatedVisibility(
                visible = AppTheme.adaptiveLayoutType == AdaptiveLayoutType.Medium
                        || AppTheme.adaptiveLayoutType == AdaptiveLayoutType.Expanded
            ) {
                val currentRoute = backstack.lastOrNull()
                EtherealNavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(verticalSafePadding()),
                    selectedRoute = currentRoute,
                    onRouteSelected = { mainNavActions.popAndNavigation(it) },
                    llmEngineStatus = llmEngineStatus,
                )
            }

            // ── Content area ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = AppTheme.size.maxContentWidth)
            ) {
                NavDisplay(
                    backStack = backstack,
                    onBack = { mainNavActions.back() }
                ) { key ->
                    when (key) {
                        is MainRoute.HomeRoute -> NavEntry(key) {
                            ModelScreen()
                        }

                        is MainRoute.ChatRoute -> NavEntry(key) {
                            ChatScreen(
                                onSettingsClick = {
                                    mainNavActions.popAndNavigation(MainRoute.SettingRoute)
                                },
                                onAdvancedSettingsClick = onAdvancedSettingsClick
                            )
                        }

                        is MainRoute.LibraryRoute -> NavEntry(key) {
                            LibraryScreen(
                                onOpenChat = {
                                    mainNavActions.popAndNavigation(MainRoute.ChatRoute)
                                }
                            )
                        }

                        is MainRoute.SettingRoute -> NavEntry(key) {
                            SettingScreen()
                        }

                        else -> NavEntry(key) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Page not found: $key")
                            }
                        }
                    }
                }
            }
        }

        // ── Mobile: Glassmorphism bottom navigation bar ──
        AnimatedVisibility(
            visible = AppTheme.adaptiveLayoutType == AdaptiveLayoutType.Compact
        ) {
            val currentRoute = backstack.lastOrNull()
            EtherealBottomNavigationBar(
                selectedRoute = currentRoute,
                onRouteSelected = { mainNavActions.popAndNavigation(it) },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Desktop — Ethereal Glassmorphism Vertical Navigation Rail (SideNavBar)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun EtherealNavigationRail(
    modifier: Modifier,
    selectedRoute: Any?,
    onRouteSelected: (Any) -> Unit,
    llmEngineStatus: LlmEngineStatus,
) {
    val isDark by AppTheme.isDark
    val containerBg = if (isDark) {
        AppTheme.colors.surfaceDim.copy(alpha = 0.4f)
    } else {
        AppTheme.colors.surface.copy(alpha = 0.4f)
    }
    val borderColor = AppTheme.colors.outlineVariant.copy(alpha = 0.3f)

    Column(
        modifier
            .width(288.dp)
            /*.glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )*/
            .background(containerBg)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(AppTheme.spacing.lg)
            .selectableGroup(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // ── Header ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.md)
                    .padding(top = AppTheme.spacing.md, bottom = AppTheme.spacing.xxl)
            ) {
                Text(
                    text = BuildConfig.APP_NAME,
                    style = AppTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp
                    ),
                    color = if (isDark) AppTheme.colors.inversePrimary else AppTheme.colors.primary
                )
                Spacer(modifier = Modifier.height(AppTheme.spacing.xs))
                Text(
                    text = "LOCAL INTELLIGENCE",
                    style = AppTheme.typography.bodySmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AppTheme.colors.tertiary
                )
            }

            // ── Scrollable nav items ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
            ) {
                NAV_BOTTOM_ITEMS.forEach { routePage ->
                    val isSelected = routePage == selectedRoute
                    DesktopNavigationItem(
                        routePage = routePage,
                        isSelected = isSelected,
                        onClick = { onRouteSelected(routePage) }
                    )
                }
            }
        }

        // ── Bottom Status indicator & Theme Toggle ──
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.colors.outlineVariant.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(AppTheme.spacing.lg))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GrisWatercolorStatusIndicator(status = llmEngineStatus)

                val isDarkState = AppTheme.isDark
                val isDark by isDarkState
                Box(
                    modifier = Modifier
                        .size(AppTheme.size.iconButtonSmall)
                        .clip(AppTheme.shape.full)
                        .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.3f))
                        .clickable { isDarkState.value = !isDarkState.value },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = vectorResource(
                            if (isDark) Res.drawable.ic_sun else Res.drawable.ic_moon
                        ),
                        contentDescription = stringResource(
                            if (isDark) Res.string.light_theme else Res.string.dark_theme
                        ),
                        modifier = Modifier.size(AppTheme.size.icon),
                        tint = AppTheme.colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopNavigationItem(
    routePage: RoutePage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isDark by AppTheme.isDark

    val targetBgColor = when {
        isSelected && isDark -> AppTheme.colors.primaryFixedDim.copy(alpha = 0.2f)
        isSelected && !isDark -> AppTheme.colors.primaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_bg"
    )

    val targetContentColor = when {
        isSelected && isDark -> AppTheme.colors.inversePrimary
        isSelected && !isDark -> AppTheme.colors.primary
        else -> AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
    }
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_content"
    )

    val paddingStart by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_padding"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shape.regular)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(start = paddingStart, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
    ) {
        Icon(
            imageVector = vectorResource(routePage.iconRes),
            contentDescription = stringResource(routePage.textRes),
            modifier = Modifier.size(AppTheme.size.iconLarge),
            tint = contentColor
        )

        Text(
            text = stringResource(routePage.textRes),
            style = AppTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = contentColor
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════
// Mobile — Ethereal Glassmorphism Bottom Navigation Bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun EtherealBottomNavigationBar(
    selectedRoute: Any?,
    onRouteSelected: (Any) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = AppTheme.colors.surfaceContainerLow.copy(
            alpha = AppTheme.elevation.glassSurfaceAlpha
        ),
        tonalElevation = 0.dp
    ) {
        NAV_BOTTOM_ITEMS.forEach { routePage ->
            val isSelected = routePage == selectedRoute

            NavigationBarItem(
                selected = isSelected,
                onClick = { onRouteSelected(routePage) },
                icon = {
                    Icon(
                        imageVector = vectorResource(routePage.iconRes),
                        contentDescription = stringResource(routePage.textRes),
                        modifier = Modifier.size(AppTheme.size.iconLarge)
                    )
                },
                label = {
                    Text(
                        text = stringResource(routePage.textRes),
                        style = if (isSelected) AppTheme.typography.labelMedium
                        else AppTheme.typography.labelSmall
                    )
                },
                colors = etherealBarItemColors()
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════
// Shared — Color configurations for navigation items
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun etherealBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AppTheme.colors.onPrimaryContainer,
    selectedTextColor = AppTheme.colors.onSurface,
    indicatorColor = AppTheme.colors.primaryContainer.copy(alpha = 0.7f),
    unselectedIconColor = AppTheme.colors.onSurfaceVariant,
    unselectedTextColor = AppTheme.colors.onSurfaceVariant
)
