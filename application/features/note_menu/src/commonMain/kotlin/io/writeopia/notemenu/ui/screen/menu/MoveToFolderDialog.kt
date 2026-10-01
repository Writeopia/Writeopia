package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem

/** A folder of the tree, flattened for the list, with how deep it is. */
private data class FolderEntry(val id: String, val title: String, val depth: Int, val icon: MenuItem.Icon?)

/**
 * Picks the folder to move the selected items to: the home, then the folder tree. The selected
 * folders and what's inside them can't be picked, nor the folder the items already are in.
 */
@Composable
fun MoveToFolderDialog(
    itemsPerFolderId: Map<String, List<MenuItem>>,
    excludedIds: Set<String>,
    currentParentId: String,
    onPick: (parentId: String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(itemsPerFolderId, excludedIds) {
        flattenFolders(itemsPerFolderId, excludedIds)
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(20.dp).width(400.dp)) {
                Text(WrStrings.moveTo(), style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = Color.Gray)

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        FolderRow(
                            title = "Home",
                            depth = 0,
                            icon = null,
                            enabled = currentParentId != Folder.ROOT_PATH,
                            onClick = { onPick(Folder.ROOT_PATH) }
                        )
                    }

                    items(entries, key = { it.id }) { entry ->
                        FolderRow(
                            title = entry.title,
                            depth = entry.depth,
                            icon = entry.icon,
                            enabled = entry.id != currentParentId,
                            onClick = { onPick(entry.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text(WrStrings.cancel())
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    title: String,
    depth: Int,
    icon: MenuItem.Icon?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4F)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = (8 + depth * 20).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background((icon?.tint?.let(::Color) ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.14F))
                .padding(7.dp),
            imageVector = if (depth == 0 &&
                icon == null &&
                title == "Home"
            ) {
                WrIcons.home
            } else {
                icon?.label?.let(WrIcons::fromName) ?: WrIcons.folder
            },
            contentDescription = null,
            tint = icon?.tint?.let(::Color) ?: textColor
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(text = title, color = textColor, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The folders under the root in order, skipping [excludedIds] and everything inside them. */
private fun flattenFolders(
    itemsPerFolderId: Map<String, List<MenuItem>>,
    excludedIds: Set<String>,
): List<FolderEntry> {
    val result = mutableListOf<FolderEntry>()

    fun visit(parentId: String, depth: Int) {
        itemsPerFolderId[parentId]
            .orEmpty()
            .filterIsInstance<Folder>()
            .filter { folder -> !excludedIds.contains(folder.id) }
            .sortedBy { folder -> folder.title.lowercase() }
            .forEach { folder ->
                result += FolderEntry(folder.id, folder.title, depth, folder.icon)
                if (depth < 8) visit(folder.id, depth + 1)
            }
    }

    visit(Folder.ROOT_PATH, 0)
    return result
}
