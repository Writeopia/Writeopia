package io.writeopia.update.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.resources.WrStrings
import io.writeopia.update.model.DesktopUpdate

@Composable
internal fun DesktopUpdateDialog(
    update: DesktopUpdate,
    openFailed: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(WrStrings.updateAvailable())
        },
        text = {
            Column {
                Text("${WrStrings.newVersionAvailable()} ${update.latestVersion}")

                if (openFailed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = WrStrings.updateOpenFailed(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(WrStrings.downloadUpdate())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(WrStrings.later())
            }
        }
    )
}
