package io.writeopia.auth.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.auth.utils.arrowPadding
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.buttons.LargeButton
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BoxScope.ChooseWorkspace(
    workspacesState: StateFlow<ResultData<List<Workspace>>>,
    createWorkspaceState: StateFlow<ResultData<Unit>>,
    onWorkspaceSelected: (Workspace) -> Unit,
    onCreateWorkspace: (String) -> Unit,
    onResetCreateWorkspaceState: () -> Unit,
    retry: () -> Unit,
    onBackClick: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateWorkspaceDialog(
            createWorkspaceState = createWorkspaceState,
            onCreateWorkspace = onCreateWorkspace,
            onDismiss = {
                showCreateDialog = false
                onResetCreateWorkspaceState()
            }
        )
    }
    Icon(
        modifier = Modifier
            .align(Alignment.TopStart)
            .arrowPadding()
            .clip(CircleShape)
            .clickable { onBackClick() }
            .padding(6.dp),
        imageVector = PlatformIcons.backArrowMobile,
        contentDescription = "Arrow back",
        tint = MaterialTheme.colorScheme.onBackground
    )

    Column(
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = WrStrings.chooseWorkspace(),
            style = MaterialTheme.typography.displayMedium.copy(
                MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        val workspacesResult = workspacesState.collectAsState().value

        when (workspacesResult) {
            is ResultData.Complete -> {
                val workspaces = workspacesResult.data

                LazyColumn(
                    modifier = Modifier.width(400.dp).defaultMinSize(minHeight = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(workspaces) { workspace ->
                        LargeButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = workspace.name,
                            clickListener = {
                                onWorkspaceSelected(workspace)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        CommonButton(
                            text = WrStrings.createWorkspace(),
                            clickListener = { showCreateDialog = true }
                        )
                    }
                }
            }

            is ResultData.Error -> {
                BasicText(
                    text = "Error loading workspaces",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Spacer(modifier = Modifier.height(8.dp))

                CommonButton(text = "Try again", clickListener = retry)
            }

            is ResultData.Idle -> { }

            is ResultData.InProgress, is ResultData.Loading -> {
                CircularProgressIndicator()
            }
        }
    }
}
