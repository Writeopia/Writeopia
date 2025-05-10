package io.writeopia.notemenu.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.core.folders.api.SelfHostedConnectionState
import io.writeopia.core.folders.di.DocumentsInjection
import kotlinx.coroutines.launch

@Composable
fun SelfHostedBackendDialog(
    documentsInjection: DocumentsInjection,
    initialUrl: String = "",
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val connectionState = documentsInjection.provideSelfHostedBackendManager().connectionState.value
        if (connectionState is SelfHostedConnectionState.Connected) {
            url = connectionState.url
            isConnected = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Self-Hosted Backend",
                    style = MaterialTheme.typography.h6
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Configure a self-hosted backend server to sync your notes with. " +
                        "Your notes will be synced when you enter a folder with unsynced notes."
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://localhost:8080") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isConnecting
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isConnected) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    documentsInjection.disconnectFromSelfHostedBackend()
                                    isConnected = false
                                }
                            },
                            enabled = !isConnecting
                        ) {
                            Text("Disconnect")
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = {
                                if (url.isBlank()) {
                                    errorMessage = "Please enter a valid URL"
                                    return@Button
                                }

                                isConnecting = true
                                errorMessage = null

                                coroutineScope.launch {
                                    val success = documentsInjection.connectToSelfHostedBackend(url)

                                    if (success) {
                                        isConnected = true
                                        onDismiss()
                                    } else {
                                        errorMessage = "Could not connect to backend server"
                                    }

                                    isConnecting = false
                                }
                            }
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}
