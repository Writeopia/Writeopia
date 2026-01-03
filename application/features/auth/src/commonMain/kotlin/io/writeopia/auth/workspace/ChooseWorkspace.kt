package io.writeopia.auth.workspace

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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.buttons.LargeButton
import io.writeopia.resources.WrStrings
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
