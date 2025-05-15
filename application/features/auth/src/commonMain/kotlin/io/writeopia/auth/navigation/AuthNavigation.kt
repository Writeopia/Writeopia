package io.writeopia.auth.navigation

import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.menu.AuthMenuScreen
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.auth.register.RegisterScreen
import io.writeopia.common.utils.Destinations
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.theme.WrieopiaTheme
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

fun NavGraphBuilder.authNavigation(
    navController: NavController,
    authInjection: AuthInjection,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    toAppNavigation: () -> Unit
) {
    navigation(
        startDestination = Destinations.AUTH_MENU.id,
        route = Destinations.AUTH_MENU_INNER_NAVIGATION.id
    ) {
        composable(Destinations.AUTH_MENU.id) {
            val authMenuViewModel: AuthMenuViewModel = authInjection.provideAuthMenuViewModel()
            val colorTheme by colorThemeOption.collectAsState()

            WrieopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
                AuthMenuScreen(
                    modifier = Modifier.background(WriteopiaTheme.colorScheme.globalBackground),
                    emailState = authMenuViewModel.email,
                    passwordState = authMenuViewModel.password,
                    loginState = authMenuViewModel.loginState,
                    emailChanged = authMenuViewModel::emailChanged,
                    passwordChanged = authMenuViewModel::passwordChanged,
                    onLoginRequest = authMenuViewModel::onLoginRequest,
                    onLoginSuccess = {},
                    isConnectedState = authMenuViewModel.isConnected,
                    navigateToLogin = navController::navigateAuthLogin,
                    saveUserChoiceOffline = authMenuViewModel::saveUserChoiceOffline,
                    navigateToRegister = navController::navigateAuthRegister,
                    navigateToApp = toAppNavigation
                )
            }
        }

        composable(Destinations.AUTH_REGISTER.id) {
            val registerViewModel = authInjection.provideRegisterViewModel()
            val colorTheme by colorThemeOption.collectAsState()

            WrieopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
                RegisterScreen(
                    nameState = registerViewModel.name,
                    emailState = registerViewModel.email,
                    passwordState = registerViewModel.password,
                    registerState = registerViewModel.register,
                    nameChanged = registerViewModel::nameChanged,
                    emailChanged = registerViewModel::emailChanged,
                    passwordChanged = registerViewModel::passwordChanged,
                    onRegisterRequest = registerViewModel::onRegister,
                    onRegisterSuccess = toAppNavigation,
                    navigateBack = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}

fun NavController.navigateAuthRegister() {
    navigate(Destinations.AUTH_REGISTER.id)
}

fun NavController.navigateAuthLogin() {
    navigate(Destinations.AUTH_LOGIN.id)
}

fun NavController.navigateToAuthMenu() {
    navigate(Destinations.AUTH_MENU.id)
}
