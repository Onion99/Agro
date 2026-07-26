package org.onion.agro.ui.navigation.route

import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.home
import agro.composeapp.generated.resources.ic_home
import kotlinx.serialization.Serializable


sealed interface DetailRoute {
    data object Home : RoutePage(Res.drawable.ic_home, Res.string.home)
    data object Setting : RoutePage(Res.drawable.ic_home, Res.string.home)
    data object Mine : RoutePage(Res.drawable.ic_home, Res.string.home)
    data object BookSource : RoutePage(Res.drawable.ic_home, Res.string.home)
}