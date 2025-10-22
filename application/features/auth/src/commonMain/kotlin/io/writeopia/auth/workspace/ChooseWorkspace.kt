package io.writeopia.auth.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BoxScope.ChooseWorkspace(
    workspacesState: StateFlow<ResultData<List<Workspace>>>,
    onWorkspaceSelected: (String) -> Unit
) {
    BasicText(
        text = "Choose your workspace",
        style = MaterialTheme.typography.titleLarge.copy(
            MaterialTheme.colorScheme.onBackground
        )
    )

    val workspacesResult = workspacesState.value

    when (workspacesResult) {
        is ResultData.Complete -> {
            val workspaces = workspacesResult.data

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workspaces.forEach { workspaces ->
                    CommonButton(
                        modifier = Modifier.width(200.dp),
                        text = workspaces.name,
                        clickListener = {
                            onWorkspaceSelected(workspaces.id)
                        }
                    )
                }
            }
        }

        is ResultData.Error -> {
            Column(modifier = Modifier.align(Alignment.Center)) {
                BasicText(
                    text = "Error loading workspaces",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),

                )

                Spacer(modifier = Modifier.height(8.dp))

                CommonButton(text = "Try again", clickListener = {})
            }
        }

        is ResultData.Idle -> {}
        is ResultData.InProgress, is ResultData.Loading -> {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
