package org.onion.agro.ui.navigation.route

import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.ic_help
import agro.composeapp.generated.resources.unknown
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlinx.serialization.Serializable


sealed interface RootRoute {
    data object Splash : RoutePage()
    data object MainRoute : RoutePage()
    data object SettingRoute : RoutePage()
    data object AdvancedSettingRoute : RoutePage()
}

open class RoutePage(
    val iconRes: DrawableResource = Res.drawable.ic_help,
    val textRes: StringResource = Res.string.unknown
)