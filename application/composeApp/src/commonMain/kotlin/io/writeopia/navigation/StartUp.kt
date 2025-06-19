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
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.common.utils.Destinations
import io.writeopia.di.AppConnectionInjection
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.theme.WrieopiaTheme
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

fun NavGraphBuilder.startScreen(
    navigationController: NavController,
    authInjection: AuthInjection,
    colorTheme: StateFlow<ColorThemeOption?>
) {
    composable(route = Destinations.START_APP.id) {
        val authMenuViewModel: AuthMenuViewModel =
            authInjection.provideAuthMenuViewModel()

        IntroScreen(colorTheme.value)

        LaunchedEffect(Unit) {
            AuthCoreInjectionNeo.singleton()
                .provideAuthRepository()
                .getAuthToken()
                ?.let(AppConnectionInjection.singleton()::setJwtToken)

            authMenuViewModel.isLoggedIn().collect { loggedIn ->
                delay(300)
                navigationController.navigate(
                    if (loggedIn) {
                        Destinations.MAIN_APP.id
                    } else {
                        Destinations.AUTH_MENU_INNER_NAVIGATION.id
                    }
                )
            }
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
    WrieopiaTheme(darkTheme = colorThemeOption.isDarkTheme()) {
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
