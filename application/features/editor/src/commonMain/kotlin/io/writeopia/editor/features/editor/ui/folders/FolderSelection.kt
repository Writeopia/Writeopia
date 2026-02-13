@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.editor.ui.folders

import kotlin.time.ExperimentalTime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.sdk.models.document.Folder
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.icons.WrSdkIcons
import kotlinx.coroutines.flow.StateFlow

@Composable
fun FolderSelectionDialog(
    menuItemsState: StateFlow<List<MenuItemUi.FolderUi>>,
    selectedFolder: (String) -> Unit,
    expandFolder: (String) -> Unit,
    createFolder: (String) -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        FolderSelection(menuItemsState, selectedFolder, expandFolder, createFolder, editFolder)
    }
}

@Composable
private fun FolderSelection(
    menuItemsState: StateFlow<List<MenuItemUi.FolderUi>>,
    selectedFolder: (String) -> Unit,
    expandFolder: (String) -> Unit,
    createFolder: (String) -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
) {
    val menuItems by menuItemsState.collectAsState()

    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
            .height(400.dp)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(
            "Choose folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (menuItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = WrStrings.noFolders(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                menuItems.forEach { item ->
                    item(key = item.id) {
                        FolderItem(
                            folder = item,
                            selectedFolder = selectedFolder,
                            expandFolder = expandFolder,
                            editFolder = editFolder,
                            modifier = Modifier.animateItem()
                        )
                    }

                    if (item.expanded) {
                        item(key = "create_${item.id}") {
                            CreateFolderItem(
                                depth = item.depth + 1,
                                onClick = { createFolder(item.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CommonButton(
            modifier = Modifier.height(40.dp).fillMaxWidth(),
            text = WrStrings.createFolder(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            textStyle = MaterialTheme.typography.bodyMedium,
            clickListener = { createFolder("root") }
        )
    }
}

@Composable
private fun CreateFolderItem(
    depth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = WriteopiaTheme.colorScheme.globalBackground

    Row(
        modifier = modifier
            .padding(start = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(bottom = 6.dp, top = 6.dp, start = 2.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(4.dp + 12.dp * depth))

        Icon(
            imageVector = WrIcons.add,
            contentDescription = WrStrings.createFolder(),
            tint = WriteopiaTheme.colorScheme.textLight,
            modifier = Modifier
                .size(26.dp)
                .padding(6.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = WrIcons.folder,
            contentDescription = "Folder",
            tint = WriteopiaTheme.colorScheme.textLight,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = WrStrings.createFolder(),
            modifier = Modifier,
            color = WriteopiaTheme.colorScheme.textLight,
            style = MaterialTheme.typography.bodySmall
                .copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun FolderItem(
    folder: MenuItemUi.FolderUi,
    selectedFolder: (String) -> Unit,
    expandFolder: (String) -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val depth = folder.depth
    val bgColor = WriteopiaTheme.colorScheme.globalBackground

    var isHovered by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { selectedFolder(folder.id) }
            .hoverable(isHovered) { isHovered = it }
            .background(bgColor)
            .padding(bottom = 6.dp, top = 6.dp, start = 2.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(4.dp + 12.dp * depth))

        val imageVector = if (folder.expanded) {
            WrSdkIcons.smallArrowDown
        } else {
            WrSdkIcons.smallArrowRight
        }

        Icon(
            imageVector = imageVector,
            contentDescription = "Expand",
            tint = WriteopiaTheme.colorScheme.textLight,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    expandFolder(folder.id)
                }
                .size(26.dp)
                .padding(6.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        val tint = folder.icon?.tint?.let(::Color) ?: WriteopiaTheme.colorScheme.textLight

        Icon(
            imageVector = folder.icon?.label?.let(WrIcons::fromName) ?: WrIcons.folder,
            contentDescription = "Folder",
            tint = tint,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = folder.title,
            modifier = Modifier.weight(1f),
            color = WriteopiaTheme.colorScheme.textLight,
            style = MaterialTheme.typography.bodySmall
                .copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )

        if (isHovered) {
            Icon(
                imageVector = WrIcons.moreHoriz,
                contentDescription = "Edit folder",
                tint = WriteopiaTheme.colorScheme.textLight,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { editFolder(folder) }
                    .size(26.dp)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun Modifier.hoverable(
    isHovered: Boolean,
    onHoverChanged: (Boolean) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
                PointerEventType.Enter -> onHoverChanged(true)
                PointerEventType.Exit -> onHoverChanged(false)
            }
        }
    }
}

@Composable
fun EditFolderDialog(
    folder: Folder,
    onDismissRequest: () -> Unit,
    editFolder: (Folder) -> Unit,
    deleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(20.dp).width(400.dp)) {
                var folderTitle by remember { mutableStateOf(folder.title) }

                Text(
                    "Update Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = Color.Gray)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = folderTitle,
                    onValueChange = { title ->
                        folderTitle = title
                        editFolder(folder.copy(title = title.takeIf { it.isNotEmpty() } ?: " "))
                    },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { deleteFolder(folder.id) }) {
                        Text("Delete folder")
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
