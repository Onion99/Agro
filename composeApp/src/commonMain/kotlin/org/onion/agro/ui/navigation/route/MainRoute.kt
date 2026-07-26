package org.onion.agro.ui.navigation.route

import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.chat
import agro.composeapp.generated.resources.home
import agro.composeapp.generated.resources.ic_chat
import agro.composeapp.generated.resources.ic_home
import agro.composeapp.generated.resources.ic_library
import agro.composeapp.generated.resources.ic_setting
import agro.composeapp.generated.resources.library
import agro.composeapp.generated.resources.setting
import kotlinx.serialization.Serializable


@Serializable
sealed interface MainRoute {
    data object HomeRoute : RoutePage(Res.drawable.ic_home, Res.string.home)
    data object ChatRoute : RoutePage(Res.drawable.ic_chat, Res.string.chat)
    data object LibraryRoute : RoutePage(Res.drawable.ic_library, Res.string.library)
    data object SettingRoute : RoutePage(Res.drawable.ic_setting, Res.string.setting)
}

val NAV_BOTTOM_ITEMS = listOf(
    MainRoute.HomeRoute,
    MainRoute.ChatRoute,
    MainRoute.LibraryRoute,
    MainRoute.SettingRoute
)