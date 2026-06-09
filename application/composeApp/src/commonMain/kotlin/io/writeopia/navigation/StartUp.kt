package io.writeopia.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.auth.core.manager.LoginStatus
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.common.utils.AuthMenuInnerNavigationRoute
import io.writeopia.common.utils.ChooseWorkspaceRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * StartScreen composable for Navigation 3.
 * This is used as content for the StartAppRoute entry.
 */
@Composable
fun StartScreen(
    backStack: NavBackStack<NavKey>,
    colorTheme: StateFlow<ColorThemeOption?>,
    authInjection: AuthInjection = AuthInjection.singleton()
) {
    val authMenuViewModel: AuthMenuViewModel =
        authInjection.provideAuthMenuViewModel()

    IntroScreen(colorTheme.value)

    LaunchedEffect(Unit) {
        authMenuViewModel.isLoggedIn().collect { loggedIn ->
            delay(300)
            // Clear the start screen and add the appropriate destination
            backStack.clear()
            backStack.add(
                when (loggedIn) {
                    LoginStatus.OFFLINE_NOT_CHOSEN -> AuthMenuInnerNavigationRoute
                    LoginStatus.CHOOSE_WORKSPACE -> ChooseWorkspaceRoute
                    LoginStatus.ONLINE, LoginStatus.OFFLINE_CHOSEN -> MainAppRoute
                }
            )
        }
    }
}

@Composable
fun ScreenLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun IntroScreen(colorThemeOption: ColorThemeOption?) {
    WriteopiaTheme(darkTheme = colorThemeOption.isDarkTheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    WriteopiaTheme.colorScheme.globalBackground
                )
        ) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
