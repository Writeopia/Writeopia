package io.writeopia.auth.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BoxScope.ChooseWorkspace(
    workspacesState: StateFlow<ResultData<List<Workspace>>>,
    onWorkspaceSelected: (Workspace) -> Unit,
    retry: () -> Unit
) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = "Choose your workspace",
            style = MaterialTheme.typography.titleLarge.copy(
                MaterialTheme.colorScheme.onBackground
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        val workspacesResult = workspacesState.collectAsState().value

        when (workspacesResult) {
            is ResultData.Complete -> {
                val workspaces = workspacesResult.data

                LazyColumn(
                    modifier = Modifier.width(200.dp).defaultMinSize(minHeight = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workspaces) { workspace ->
                        CommonButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = workspace.name,
                            clickListener = {
                                onWorkspaceSelected(workspace)
                            }
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
