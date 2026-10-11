package io.writeopia.writingagent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.writingagent.controller.WritingAgentController
import io.writeopia.writingagent.model.FolderSuggestion

const val FOLDER_SUGGESTION_DIALOG_TAG = "FolderSuggestionDialog"

/**
 * The dialog that files a document opened in the root folder. It spins while the backend ranks
 * the folders, then lists the most likely ones to pick, and offers a new folder named by the AI.
 * Hidden while the controller has no dialog to show.
 */
@Composable
fun FolderSuggestionDialog(controller: WritingAgentController) {
    val state by controller.state.collectAsState()
    val dialog = state.folderDialog ?: return

    AlertDialog(
        onDismissRequest = { if (!dialog.isCreating) controller.dismissFolderDialog() },
        modifier = Modifier.testTag(FOLDER_SUGGESTION_DIALOG_TAG),
        icon = {
            Icon(
                imageVector = WrIcons.moveToFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = WrStrings.writingFolderDialogTitle()) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = WrStrings.writingFolderDialogBody(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    dialog.isCreating -> Progress(WrStrings.writingFolderDialogCreating())

                    dialog.isLoading -> Progress(WrStrings.writingFolderDialogLoading())

                    dialog.options.isEmpty() -> Text(
                        text = WrStrings.writingFolderDialogNone(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> dialog.options.forEach { folder ->
                        FolderOption(folder = folder, onClick = { controller.moveToFolder(folder.folderId) })
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = controller::createFolder,
                enabled = !dialog.isLoading && !dialog.isCreating,
                modifier = Modifier.testTag("$FOLDER_SUGGESTION_DIALOG_TAG.newFolder")
            ) {
                Text(text = WrStrings.writingFolderDialogNewFolder())
            }
        },
        dismissButton = {
            TextButton(
                onClick = controller::dismissFolderDialog,
                enabled = !dialog.isCreating,
                modifier = Modifier.testTag("$FOLDER_SUGGESTION_DIALOG_TAG.notNow")
            ) {
                Text(text = WrStrings.writingFolderDialogNotNow())
            }
        }
    )
}

@Composable
private fun Progress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FolderOption(folder: FolderSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("$FOLDER_SUGGESTION_DIALOG_TAG.folder.${folder.folderId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = WrIcons.folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "${(folder.probability * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
