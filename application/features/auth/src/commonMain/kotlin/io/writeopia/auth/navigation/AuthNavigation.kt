@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.writeopia.auth.di.AuthInjection
import io.writeopia.auth.menu.AuthMenuScreen
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.auth.register.RegisterPasswordScreen
import io.writeopia.auth.register.RegisterScreen
import io.writeopia.auth.workspace.ChooseWorkspace
import io.writeopia.common.utils.AuthRegisterRoute
import io.writeopia.common.utils.ChooseWorkspaceRoute
import io.writeopia.common.utils.MainAppRoute
import io.writeopia.model.ColorThemeOption
import io.writeopia.model.isDarkTheme
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.ExperimentalTime

/**
 * Auth Menu Screen content for Navigation 3.
 */
@Composable
fun AuthMenuScreenContent(
    backStack: NavBackStack<NavKey>,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    authInjection: AuthInjection = AuthInjection.singleton(),
    toAppNavigation: () -> Unit,
) {
    val authMenuViewModel: AuthMenuViewModel = authInjection.provideAuthMenuViewModel()
    val colorTheme by colorThemeOption.collectAsState()

    WriteopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
        AuthMenuScreen(
            modifier = Modifier.background(WriteopiaTheme.colorScheme.globalBackground),
            emailState = authMenuViewModel.email,
            passwordState = authMenuViewModel.password,
            loginState = authMenuViewModel.loginState,
            emailChanged = authMenuViewModel::emailChanged,
            passwordChanged = authMenuViewModel::passwordChanged,
            onLoginRequest = authMenuViewModel::onLoginRequest,
            navigateToRegister = { backStack.add(AuthRegisterRoute) },
            offlineUsage = {
                authMenuViewModel.useOffline(toAppNavigation)
            },
            navigateUp = { backStack.removeLastOrNull() },
            navigateNext = { backStack.add(ChooseWorkspaceRoute) }
        )
    }
}

/**
 * Auth Register Screen content for Navigation 3.
 */
@Composable
fun AuthRegisterScreenContent(
    backStack: NavBackStack<NavKey>,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    authInjection: AuthInjection = AuthInjection.singleton(),
) {
    val registerViewModel = authInjection.provideRegisterViewModel()
    val colorTheme by colorThemeOption.collectAsState()

    WriteopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
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
            onRegisterSuccess = { backStack.add(ChooseWorkspaceRoute) },
            navigateBack = { backStack.removeLastOrNull() }
        )
    }
}

/**
 * Auth Reset Password Screen content for Navigation 3.
 */
@Composable
fun AuthResetPasswordScreenContent(
    backStack: NavBackStack<NavKey>,
    colorThemeOption: StateFlow<ColorThemeOption?>,
    authInjection: AuthInjection = AuthInjection.singleton(),
    toAppNavigation: () -> Unit,
) {
    val viewModel = authInjection.provideResetPasswordViewModel()
    val colorTheme by colorThemeOption.collectAsState()

    WriteopiaTheme(darkTheme = colorTheme.isDarkTheme()) {
        RegisterPasswordScreen(
            modifier = Modifier.background(WriteopiaTheme.colorScheme.globalBackground),
            passwordState = viewModel.password,
            repeatPasswordState = viewModel.repeatPassword,
            resetPasswordState = viewModel.resetPassword,
            passwordChanged = viewModel::passwordChanged,
            repeatPasswordChanged = viewModel::repeatPasswordChanged,
            onPasswordResetRequest = viewModel::onResetPassword,
            onPasswordResetSuccess = toAppNavigation,
            navigateBack = { backStack.removeLastOrNull() }
        )
    }
}

/**
 * Choose Workspace Screen content for Navigation 3.
 */
@Composable
fun ChooseWorkspaceScreenContent(
    backStack: NavBackStack<NavKey>,
    authInjection: AuthInjection = AuthInjection.singleton(),
    toAppNavigation: () -> Unit,
) {
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

/**
 * Extension functions for navigation (backward compatibility).
 */
fun NavBackStack<NavKey>.navigateAuthRegister() {
    add(AuthRegisterRoute)
}

fun NavBackStack<NavKey>.navigateToApp() {
    add(MainAppRoute)
}

fun NavBackStack<NavKey>.navigateToWorkspaceChoice() {
    add(ChooseWorkspaceRoute)
}
