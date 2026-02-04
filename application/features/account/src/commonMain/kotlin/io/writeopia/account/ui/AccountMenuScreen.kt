package io.writeopia.account.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.account.viewmodel.AccountMenuViewModel
import io.writeopia.model.ColorThemeOption
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AccountMenuScreen(
    accountMenuViewModel: AccountMenuViewModel,
    isLoggedInState: StateFlow<ResultData<Boolean>>,
    onLogout: () -> Unit,
    goToRegister: () -> Unit,
    changeAccount: () -> Unit,
    resetPassword: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(state = rememberScrollState())
    ) {
        SettingsScreen(
            showPath = false,
            showOllamaConfig = false,
            selectedThemePosition = MutableStateFlow(0),
            selectColorTheme = selectColorTheme,
            workplacePathState = MutableStateFlow(""),
            syncWorkspaceState = accountMenuViewModel.lastWorkspaceSync,
            selectWorkplacePath = {},
            ollamaAvailableModels = MutableStateFlow(ResultData.Idle()),
            ollamaUrl = "",
            ollamaSelectedModel = MutableStateFlow(""),
            downloadModelState = MutableStateFlow(ResultData.Idle()),
            ollamaUrlChange = {},
            ollamaModelChange = {},
            ollamaModelsRetry = {},
            downloadModel = {},
            deleteModel = {},
            syncWorkspace = accountMenuViewModel::syncWorkspace,
            workspacesState = accountMenuViewModel.availableWorkspaces,
            selectedWorkspaceState = accountMenuViewModel.selectedWorkspace,
            selectWorkspace = accountMenuViewModel::selectWorkspace,
            addUserToTeam = accountMenuViewModel::addUserToWorkspace,
            usersInSelectedWorkspace = accountMenuViewModel.usersOfSelectedWorkspace,
            isLoggedInState = isLoggedInState,
            goToRegister = goToRegister,
            changeAccount = changeAccount,
            resetPassword = resetPassword,
            logout = {
                accountMenuViewModel.logout {
                    onLogout()
                }
            },
        )
    }
}
