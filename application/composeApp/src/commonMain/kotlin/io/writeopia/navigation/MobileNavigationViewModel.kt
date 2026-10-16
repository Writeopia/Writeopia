package io.writeopia.navigation

import androidx.lifecycle.ViewModel
import io.writeopia.common.utils.Destinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MobileNavigationViewModel : ViewModel(), NavigationViewModel {

    private val _selectedNavigation = MutableStateFlow(bottomBatItems())
    override val selectedNavigation: StateFlow<List<BottomBarItem>> =
        _selectedNavigation.asStateFlow()

    private fun bottomBatItems() =
        listOf(
            BottomBarItem(
                navItemName = NavItemName.HOME,
                destination = "${Destinations.CHOOSE_NOTE.id}/{type}/{path}",
            ),
            BottomBarItem(
                navItemName = NavItemName.SEARCH,
                destination = Destinations.SEARCH.id,
            ),
            BottomBarItem(
                navItemName = NavItemName.SETTINGS,
                destination = Destinations.ACCOUNT.id,
            ),
        )
}
