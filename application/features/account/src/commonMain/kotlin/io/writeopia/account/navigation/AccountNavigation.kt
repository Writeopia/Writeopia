package io.writeopia.account.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.writeopia.account.di.AccountMenuKmpInjector
import io.writeopia.account.ui.AccountMenuScreen
import io.writeopia.account.ui.SettingsAccountScreen
import io.writeopia.account.ui.SettingsAppearanceScreen
import io.writeopia.account.ui.SettingsTeamsScreen
import io.writeopia.account.ui.UserAddScreen
import io.writeopia.account.ui.UserSearchScreen
import io.writeopia.account.ui.WorkspaceUsersScreen
import io.writeopia.common.utils.Destinations
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.model.AccentColor
import io.writeopia.model.ColorThemeOption
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

fun NavController.navigateToSettingsTeams() {
    navigate(Destinations.SETTINGS_TEAMS.id)
}

fun NavController.navigateToSettingsAppearance() {
    navigate(Destinations.SETTINGS_APPEARANCE.id)
}

fun NavController.navigateToSettingsAccount() {
    navigate(Destinations.SETTINGS_ACCOUNT.id)
}

fun NavController.navigateToWorkspaceUsers(workspaceId: String, workspaceName: String) {
    // Replace special characters that could break the URL
    val safeName = workspaceName.replace("/", "_").replace("?", "_").replace("#", "_")
    navigate("${Destinations.SETTINGS_WORKSPACE_USERS.id}/$workspaceId/$safeName")
}

fun NavController.navigateToUserSearch(workspaceId: String, workspaceName: String) {
    val safeName = workspaceName.replace("/", "_").replace("?", "_").replace("#", "_")
    navigate("${Destinations.SETTINGS_USER_SEARCH.id}/$workspaceId/$safeName")
}

fun NavController.navigateToUserAdd(
    workspaceId: String,
    workspaceName: String,
    userId: String,
    userName: String,
    userEmail: String
) {
    val safeName = workspaceName.replace("/", "_").replace("?", "_").replace("#", "_")
    val safeUserName = userName.replace("/", "_").replace("?", "_").replace("#", "_")
    val safeEmail = userEmail.replace("/", "_").replace("?", "_").replace("#", "_")
    navigate("${Destinations.SETTINGS_USER_ADD.id}/$workspaceId/$safeName/$userId/$safeUserName/$safeEmail")
}

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.accountMenuNavigation(
    navigateToAuthMenu: () -> Unit,
    navigateToChooseWorkspace: () -> Unit,
    resetPassword: () -> Unit,
    navigationClick: () -> Unit,
    navigateToSettingsTeams: () -> Unit,
    navigateToSettingsAppearance: () -> Unit,
    navigateToSettingsAccount: () -> Unit,
    navigateToWorkspaceUsers: (String, String) -> Unit,
    navigateToUserSearch: (String, String) -> Unit,
    navigateToUserAdd: (String, String, String, String, String) -> Unit,
    navigateBackToWorkspaceUsers: () -> Unit,
    selectedColorTheme: StateFlow<ColorThemeOption?>,
    selectedAccentColor: StateFlow<AccentColor?>,
    selectColorTheme: (ColorThemeOption) -> Unit,
    selectAccentColor: (AccentColor) -> Unit,
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            WrStrings.settings(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            AccountMenuScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                navigateToAppearance = navigateToSettingsAppearance,
                navigateToTeams = navigateToSettingsTeams,
                navigateToAccount = navigateToSettingsAccount,
            )
        }
    }

    // Teams Section
    composable(
        Destinations.SETTINGS_TEAMS.id,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> intSize }
            )
        }
    ) {
        val accountMenuViewModel = AccountMenuKmpInjector.singleton().provideAccountMenuViewModel()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            WrStrings.teams(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            SettingsTeamsScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                workspacesState = accountMenuViewModel.availableWorkspaces,
                onWorkspaceClick = { workspaceId, workspaceName ->
                    navigateToWorkspaceUsers(workspaceId, workspaceName)
                }
            )
        }
    }

    // Workspace Users Section
    composable(
        route = "${Destinations.SETTINGS_WORKSPACE_USERS.id}/{workspaceId}/{workspaceName}",
        arguments = listOf(
            navArgument("workspaceId") { type = NavType.StringType },
            navArgument("workspaceName") { type = NavType.StringType }
        ),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> intSize }
            )
        }
    ) { backStackEntry ->
        val workspaceId = backStackEntry.savedStateHandle.get<String?>("workspaceId") ?: ""
        val workspaceName = backStackEntry.savedStateHandle.get<String?>("workspaceName") ?: ""

        val viewModel = AccountMenuKmpInjector.singleton()
            .provideWorkspaceUsersViewModel(workspaceId, workspaceName)

        LaunchedEffect(workspaceId) {
            viewModel.loadUsers()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            WrStrings.teams(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            WorkspaceUsersScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                workspaceName = viewModel.getWorkspaceName(),
                usersState = viewModel.users,
                isLoadingMore = viewModel.isLoadingMore,
                hasMorePages = viewModel.hasMorePages,
                onLoadMore = viewModel::loadMoreUsers,
                onRetry = viewModel::retry,
                onNavigateToUserSearch = {
                    navigateToUserSearch(workspaceId, workspaceName)
                }
            )
        }
    }

    // User Search Screen
    composable(
        route = "${Destinations.SETTINGS_USER_SEARCH.id}/{workspaceId}/{workspaceName}",
        arguments = listOf(
            navArgument("workspaceId") { type = NavType.StringType },
            navArgument("workspaceName") { type = NavType.StringType }
        ),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> intSize }
            )
        }
    ) { backStackEntry ->
        val workspaceId = backStackEntry.savedStateHandle.get<String?>("workspaceId") ?: ""
        val workspaceName = backStackEntry.savedStateHandle.get<String?>("workspaceName") ?: ""

        val viewModel = AccountMenuKmpInjector.singleton()
            .provideUserSearchViewModel(workspaceId, workspaceName)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Search Users",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            UserSearchScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                workspaceName = viewModel.getWorkspaceName(),
                searchQuery = viewModel.searchQuery,
                searchResults = viewModel.searchResults,
                isLoadingMore = viewModel.isLoadingMore,
                hasMorePages = viewModel.hasMorePages,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onLoadMore = viewModel::loadMoreResults,
                onUserClick = { user ->
                    navigateToUserAdd(workspaceId, workspaceName, user.id, user.name, user.email)
                }
            )
        }
    }

    // User Add Screen
    composable(
        route = "${Destinations.SETTINGS_USER_ADD.id}/{workspaceId}/{workspaceName}/{userId}/{userName}/{userEmail}",
        arguments = listOf(
            navArgument("workspaceId") { type = NavType.StringType },
            navArgument("workspaceName") { type = NavType.StringType },
            navArgument("userId") { type = NavType.StringType },
            navArgument("userName") { type = NavType.StringType },
            navArgument("userEmail") { type = NavType.StringType }
        ),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> intSize }
            )
        }
    ) { backStackEntry ->
        val workspaceId = backStackEntry.savedStateHandle.get<String?>("workspaceId") ?: ""
        val workspaceName = backStackEntry.savedStateHandle.get<String?>("workspaceName") ?: ""
        val userId = backStackEntry.savedStateHandle.get<String?>("userId") ?: ""
        val userName = backStackEntry.savedStateHandle.get<String?>("userName") ?: ""
        val userEmail = backStackEntry.savedStateHandle.get<String?>("userEmail") ?: ""

        val viewModel = AccountMenuKmpInjector.singleton()
            .provideUserAddViewModel(workspaceId, workspaceName, userId, userName, userEmail)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Add User",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            UserAddScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                workspaceName = viewModel.getWorkspaceName(),
                userName = viewModel.getUserName(),
                userEmail = viewModel.getUserEmail(),
                selectedRole = viewModel.selectedRole,
                addUserState = viewModel.addUserState,
                onRoleSelect = viewModel::selectRole,
                onAddUser = {
                    viewModel.addUser(onSuccess = navigateBackToWorkspaceUsers)
                },
                onCancel = navigationClick
            )
        }
    }

    // Appearance Section
    composable(
        Destinations.SETTINGS_APPEARANCE.id,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> intSize }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            WrStrings.appearance(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            SettingsAppearanceScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                selectedColorTheme = selectedColorTheme,
                selectedAccentColor = selectedAccentColor,
                selectColorTheme = selectColorTheme,
                selectAccentColor = selectAccentColor
            )
        }
    }

    // Account Section
    composable(
        Destinations.SETTINGS_ACCOUNT.id,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { intSize -> intSize }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { intSize -> intSize }
            )
        }
    ) {
        val accountMenuViewModel = AccountMenuKmpInjector.singleton().provideAccountMenuViewModel()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            WrStrings.account(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = navigationClick)
                                    .padding(10.dp),
                                imageVector = WrIcons.backArrowMobile,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            SettingsAccountScreen(
                modifier = Modifier.background(WriteopiaTheme.colorScheme.lightBackground)
                    .padding(paddingValues),
                isLoggedInState = accountMenuViewModel.isLoggedIn,
                goToRegister = navigateToAuthMenu,
                changeAccount = navigateToAuthMenu,
                changeWorkspace = {
                    accountMenuViewModel.changeWorkspace {
                        navigateToChooseWorkspace()
                    }
                },
                resetPassword = resetPassword,
                logout = {
                    accountMenuViewModel.logout {
                        navigateToAuthMenu()
                    }
                }
            )
        }
    }
}
