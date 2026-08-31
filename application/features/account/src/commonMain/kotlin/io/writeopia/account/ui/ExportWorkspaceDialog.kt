package io.writeopia.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun ExportWorkspaceDialog(
    workspacesState: StateFlow<ResultData<List<Workspace>>>,
    exportState: StateFlow<ResultData<Unit>>,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedWorkspaceId by remember { mutableStateOf<String?>(null) }
    val workspaces by workspacesState.collectAsState()
    val exportResult by exportState.collectAsState()
    val isLoading = exportResult is ResultData.Loading
    val isSuccess = exportResult is ResultData.Complete
    val isError = exportResult is ResultData.Error

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 16.dp, top = 24.dp)
                    .width(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    WrStrings.exportWorkspace(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    WrStrings.selectWorkspaceToExport(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (val result = workspaces) {
                    is ResultData.Complete -> {
                        val disconnectedId = Workspace.disconnectedWorkspace().id
                        val onlineWorkspaces = result.data.filter { it.id != disconnectedId }

                        if (onlineWorkspaces.isEmpty()) {
                            Text(
                                WrStrings.youAreOffline(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            ) {
                                items(onlineWorkspaces) { workspace ->
                                    WorkspaceItem(
                                        workspace = workspace,
                                        isSelected = selectedWorkspaceId == workspace.id,
                                        onSelect = { selectedWorkspaceId = workspace.id },
                                        enabled = !isLoading && !isSuccess
                                    )
                                }
                            }
                        }
                    }

                    is ResultData.Loading -> {
                        CircularProgressIndicator()
                    }

                    is ResultData.Error -> {
                        Text(
                            WrStrings.errorLoadingTeams(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {}
                }

                if (isSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        WrStrings.exportStarted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        WrStrings.exportFailed(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else if (isSuccess) {
                    Text(
                        WrStrings.ok(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onDismiss)
                            .padding(6.dp)
                    )
                } else {
                    Row {
                        Text(
                            WrStrings.cancel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                                .clickable(onClick = onDismiss)
                                .padding(6.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            WrStrings.startExport(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedWorkspaceId != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = selectedWorkspaceId != null) {
                                    selectedWorkspaceId?.let { onExport(it) }
                                }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceItem(
    workspace: Workspace,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            enabled = enabled
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                workspace.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "${workspace.documentCount} documents",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
