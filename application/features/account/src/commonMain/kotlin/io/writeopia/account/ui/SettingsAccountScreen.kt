package io.writeopia.account.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.toBoolean
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsAccountScreen(
    isLoggedInState: StateFlow<ResultData<Boolean>>,
    goToRegister: () -> Unit,
    changeAccount: () -> Unit,
    resetPassword: () -> Unit,
    logout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val isLoggedIn = isLoggedInState.collectAsState().value.toBoolean()

        if (!isLoggedIn) {
            Text(
                text = WrStrings.youAreOffline(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            CommonButton(text = WrStrings.singIn()) {
                goToRegister()
            }
        } else {
            CommonButton(
                text = WrStrings.changeAccount(),
                modifier = Modifier.fillMaxWidth()
            ) {
                changeAccount()
            }

            Spacer(modifier = Modifier.height(8.dp))

            CommonButton(
                text = WrStrings.resetPassword(),
                modifier = Modifier.fillMaxWidth()
            ) {
                resetPassword()
            }

            Spacer(modifier = Modifier.height(8.dp))

            CommonButton(
                text = WrStrings.logout(),
                modifier = Modifier.fillMaxWidth()
            ) {
                logout()
            }
        }
    }
}
