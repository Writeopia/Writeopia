package io.writeopia.auth.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CreateWorkspaceDialog(
    createWorkspaceState: StateFlow<ResultData<Unit>>,
    onCreateWorkspace: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var workspaceName by remember { mutableStateOf("") }
    val createState by createWorkspaceState.collectAsState()
    val isLoading = createState is ResultData.Loading
    val isError = createState is ResultData.Error

    LaunchedEffect(createState) {
        if (createState is ResultData.Complete) {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 16.dp, top = 24.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    WrStrings.createWorkspace(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = workspaceName,
                    onValueChange = { workspaceName = it },
                    label = { Text(WrStrings.workspaceName()) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.width(280.dp)
                )

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        WrStrings.createWorkspaceFailed(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoading) {
                    CircularProgressIndicator()
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
                            WrStrings.create(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (workspaceName.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = workspaceName.isNotBlank()) {
                                    onCreateWorkspace(workspaceName)
                                }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}
