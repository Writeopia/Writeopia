package io.writeopia.auth.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.menu.AuthMenuScreen
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.auth.register.RegisterPasswordScreen
import io.writeopia.auth.register.RegisterScreen
import io.writeopia.auth.workspace.ChooseWorkspace
import io.writeopia.common.utils.Destinations
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.theme.WrieopiaTheme
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

fun NavGraphBuilder.authNavigation(
    navController: NavController,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    authInjection: AuthInjection = AuthInjection.singleton(),
    toAppNavigation: () -> Unit,
) {
    composable(Destinations.AUTH_RESET_PASSWORD.id) {
        val viewModel = authInjection.provideResetPasswordViewModel()
        val colorTheme by colorThemeOption.collectAsState()

        WrieopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
            RegisterPasswordScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.globalBackground),
                passwordState = viewModel.password,
                repeatPasswordState = viewModel.repeatPassword,
                resetPasswordState = viewModel.resetPassword,
                passwordChanged = viewModel::passwordChanged,
                repeatPasswordChanged = viewModel::repeatPasswordChanged,
                onPasswordResetRequest = viewModel::onResetPassword,
                onPasswordResetSuccess = {
                    toAppNavigation()
                },
                navigateBack = {
                    navController.navigateUp()
                }
            )
        }
    }

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
                    navigateToRegister = navController::navigateAuthRegister,
                    offlineUsage = {
                        authMenuViewModel.useOffline(toAppNavigation)
                    },
                    navigateUp = navController::navigateUp,
                    navigateNext = navController::navigateToWorkspaceChoice
                )
            }
        }

        composable(Destinations.CHOOSE_WORKSPACE.id) {
            val workspacesViewModel = authInjection.provideChooseWorkspaceViewModel()

            LaunchedEffect(Unit) {
                workspacesViewModel.loadWorkspaces()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WriteopiaTheme.colorScheme.globalBackground)
            ) {
                ChooseWorkspace(
                    workspacesState = workspacesViewModel.workspacesState,
                    onWorkspaceSelected = { workspace ->
                        workspacesViewModel.chooseWorkspace(
                            workspace.copy(selected = true),
                            sideEffect = toAppNavigation
                        )
                    },
                    retry = workspacesViewModel::loadWorkspaces,
                )
            }
        }

        composable(Destinations.AUTH_REGISTER.id) {
            val registerViewModel = authInjection.provideRegisterViewModel()
            val colorTheme by colorThemeOption.collectAsState()

            WrieopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
                RegisterScreen(
                    modifier = Modifier.background(WriteopiaTheme.colorScheme.globalBackground),
                    nameState = registerViewModel.name,
                    companyState = registerViewModel.company,
                    emailState = registerViewModel.email,
                    passwordState = registerViewModel.password,
                    registerState = registerViewModel.register,
                    nameChanged = registerViewModel::nameChanged,
                    companyChanged = registerViewModel::workspaceChanged,
                    emailChanged = registerViewModel::emailChanged,
                    passwordChanged = registerViewModel::passwordChanged,
                    onRegisterRequest = registerViewModel::onRegister,
                    onRegisterSuccess = navController::navigateToWorkspaceChoice,
                    navigateBack = navController::navigateUp
                )
            }
        }
    }
}

fun NavController.navigateAuthRegister() {
    navigate(Destinations.AUTH_REGISTER.id)
}

fun NavController.navigateToApp() {
    navigate(Destinations.MAIN_APP.id)
}

fun NavController.navigateToWorkspaceChoice() {
    navigate(Destinations.CHOOSE_WORKSPACE.id)
}


