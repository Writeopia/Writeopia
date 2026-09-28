package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.notemenu.viewmodel.FolderDestination

/**
 * Lets the user pick the folder where the current folder goes. [loadDestinations] gives the tree
 * of the workspace; the folder it's already in is shown but can't be picked.
 */
@Composable
internal fun MoveFolderDialog(
    folderTitle: String,
    loadDestinations: suspend () -> List<FolderDestination>,
    onDismissRequest: () -> Unit,
    onMove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations by produceState<List<FolderDestination>?>(null) {
        value = loadDestinations()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(vertical = 20.dp).width(400.dp)) {
                Text(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    text = "Move \"${folderTitle.ifBlank { "Folder" }}\" to…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.padding(top = 12.dp))

                HorizontalDivider()

                val loadedDestinations = destinations

                if (loadedDestinations == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(loadedDestinations, key = { it.id }) { destination ->
                            DestinationRow(
                                destination = destination,
                                onClick = {
                                    onMove(destination.id)
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                TextButton(
                    modifier = Modifier.align(Alignment.End).padding(end = 12.dp, top = 8.dp),
                    onClick = onDismissRequest
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(destination: FolderDestination, onClick: () -> Unit) {
    val enabled = !destination.isCurrentParent
    val color = if (enabled) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4F)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                start = 20.dp + (destination.depth * 16).dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = if (destination.title == null) WrIcons.home else WrIcons.folder,
            contentDescription = null,
            tint = color,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            modifier = Modifier.weight(1F),
            text = destination.title?.ifBlank { "Untitled" } ?: "Workspace",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (destination.isCurrentParent) {
            Text(
                text = "Current",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
