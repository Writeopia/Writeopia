package io.writeopia.account.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.account.di.AccountMenuKmpInjector
import io.writeopia.account.ui.AccountMenuScreen
import io.writeopia.common.utils.Destinations
import io.writeopia.model.ColorThemeOption

fun NavGraphBuilder.accountMenuNavigation(
    navigateToAuthMenu: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
) {
    composable(
        Destinations.ACCOUNT.id,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> -intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> -intSize }
            )
        }
    ) {
        val accountMenuViewModel = AccountMenuKmpInjector.singleton().provideAccountMenuViewModel()

        AccountMenuScreen(
            accountMenuViewModel = accountMenuViewModel,
            isLoggedInState = accountMenuViewModel.isLoggedIn,
            onLogout = navigateToAuthMenu,
            goToRegister = navigateToAuthMenu,
            selectColorTheme = selectColorTheme
        )
    }
}
