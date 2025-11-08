package io.writeopia.account.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.account.viewmodel.AccountMenuViewModel
import io.writeopia.sdk.models.utils.toBoolean
import io.writeopia.commonui.buttons.AccentButton
import io.writeopia.model.ColorThemeOption
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AccountMenuScreen(
    accountMenuViewModel: AccountMenuViewModel,
    isLoggedInState: StateFlow<ResultData<Boolean>>,
    onLogout: () -> Unit,
    goToRegister: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Connect(accountMenuViewModel, isLoggedInState, onLogout, goToRegister)

        Spacer(modifier = Modifier.height(16.dp))

        SettingsScreen(
            showPath = false,
            showOllamaConfig = false,
            selectedThemePosition = MutableStateFlow(0),
            selectColorTheme = selectColorTheme,
            workplacePathState = MutableStateFlow(""),
            syncWorkspaceState = MutableStateFlow(""),
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
        )
    }
}

@Composable
private fun Connect(
    accountMenuViewModel: AccountMenuViewModel,
    isLoggedInState: StateFlow<ResultData<Boolean>>,
    onLogout: () -> Unit,
    goToRegister: () -> Unit
) {
    val isLoggedIn = isLoggedInState.collectAsState().value.toBoolean()

    val titleStyle = MaterialTheme.typography.titleLarge
    val titleColor = MaterialTheme.colorScheme.onBackground

    Text(WrStrings.account(), style = titleStyle, color = titleColor)

    if (!isLoggedIn) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            modifier = Modifier,
            text = WrStrings.youAreOffline(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    AccentButton(text = if (isLoggedIn) WrStrings.logout() else WrStrings.singIn()) {
        if (isLoggedIn) {
            accountMenuViewModel.logout(onLogout)
        } else {
            goToRegister()
        }
    }
}
