package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.toBoolean
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsAccountScreen(
    isLoggedInState: StateFlow<ResultData<Boolean>>,
    workspacesState: StateFlow<ResultData<List<Workspace>>>,
    exportWorkspaceState: StateFlow<ResultData<Unit>>,
    goToRegister: () -> Unit,
    changeWorkspace: () -> Unit,
    exportWorkspace: (String) -> Unit,
    resetExportState: () -> Unit,
    resetPassword: () -> Unit,
    logout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

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

            AccountMenuItem(
                title = WrStrings.singIn(),
                icon = WrIcons.person,
                onClick = goToRegister
            )
        } else {

            AccountMenuItem(
                title = WrStrings.changeWorkspace(),
                icon = WrIcons.group,
                onClick = changeWorkspace
            )

            Spacer(modifier = Modifier.height(8.dp))

            AccountMenuItem(
                title = WrStrings.exportWorkspace(),
                icon = WrIcons.download,
                onClick = { showExportDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AccountMenuItem(
                title = WrStrings.resetPassword(),
                icon = WrIcons.lock,
                onClick = resetPassword
            )

            Spacer(modifier = Modifier.height(8.dp))

            AccountMenuItem(
                title = WrStrings.logout(),
                icon = WrIcons.backArrowDesktop,
                onClick = { showLogoutDialog = true }
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = WrStrings.logout())
            },
            text = {
                Text(text = WrStrings.areYouSure())
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        logout()
                    }
                ) {
                    Text(
                        text = WrStrings.logout(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = WrStrings.cancel())
                }
            }
        )
    }

    if (showExportDialog) {
        ExportWorkspaceDialog(
            workspacesState = workspacesState,
            exportState = exportWorkspaceState,
            onExport = exportWorkspace,
            onDismiss = {
                showExportDialog = false
                resetExportState()
            }
        )
    }
}

@Composable
private fun AccountMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(WriteopiaTheme.colorScheme.optionsSelector)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = WrIcons.arrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
