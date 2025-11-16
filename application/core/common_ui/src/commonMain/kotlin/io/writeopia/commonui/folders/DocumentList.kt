package io.writeopia.commonui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.IconsPicker
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.draganddrop.target.DragRowTarget
import io.writeopia.ui.draganddrop.target.DropTarget
import io.writeopia.ui.icons.WrSdkIcons

@Composable
fun DocumentList(
    menuItems: List<MenuItemUi>,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    selectedFolder: (String) -> Unit,
    selectedDocument: (String, String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    expandFolder: (String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        itemsIndexed(
            menuItems,
            key = { _, item -> item.id }
        ) { i, item ->
            val itemModifier = Modifier.animateItem()

            when (item) {
                is MenuItemUi.DocumentUi -> {
                    DocumentItem(
                        item,
                        position = i,
                        selectedDocument,
                        changeIcon = { id, icon, tint ->
                            changeIcon(id, icon, tint, IconChange.DOCUMENT)
                        },
                        modifier = itemModifier
                    )
                }

                is MenuItemUi.FolderUi -> {
                    FolderItem(
                        item,
                        position = i,
                        editFolder,
                        selectedFolder,
                        moveRequest,
                        expandFolder = expandFolder,
                        changeIcon = { id, icon, tint ->
                            changeIcon(id, icon, tint, IconChange.FOLDER)
                        },
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: MenuItemUi.FolderUi,
    position: Int,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    navigateToFolder: (String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    expandFolder: (String) -> Unit,
    changeIcon: (String, String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val depth = folder.depth - 1
    val dropInfo = DropInfo(folder, position)

    DropTarget { inBound, data ->
        if (inBound && data != null) {
            moveRequest(data.info as MenuItemUi, folder.id)
        }

        val bgColor =
            when {
                inBound -> Color.LightGray
                folder.highlighted -> Color.LightGray.copy(
                    alpha = 0.7F
                )

                else -> WriteopiaTheme.colorScheme.globalBackground
            }

        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        DragRowTarget(
            modifier = modifier
                .padding(start = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable { navigateToFolder(folder.id) }
                .background(bgColor)
                .hoverable(interactionSource)
                .padding(start = 4.dp),
            dataToDrop = dropInfo,
            position = position,
            emptySpaceClick = {
                navigateToFolder(folder.id)
            },
            isHoldDraggable = false,
            showIcon = isHovered,
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

            Spacer(modifier = Modifier.width(2.dp))

            var showIconsOptions by remember {
                mutableStateOf(false)
            }

            val tint = folder.icon?.tint?.let(::Color) ?: WriteopiaTheme.colorScheme.textLight

            Icon(
                imageVector = folder.icon?.label?.let(WrIcons::fromName) ?: WrIcons.folder,
                contentDescription = "Folder",
                tint = tint,
                modifier = Modifier.size(16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        showIconsOptions = !showIconsOptions
                    }
            )

            DropdownMenu(
                expanded = showIconsOptions,
                onDismissRequest = { showIconsOptions = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                offset = DpOffset(y = 6.dp, x = 6.dp)
            ) {
                IconsPicker(
                    iconSelect = { icon, tint -> changeIcon(folder.id, icon, tint) }
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = folder.title,
                modifier = Modifier,
                color = WriteopiaTheme.colorScheme.textLight,
                style = MaterialTheme.typography.bodySmall
                    .copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            Spacer(modifier = Modifier.weight(1F))

            Icon(
                imageVector = WrIcons.moreHoriz,
                contentDescription = "More",
                tint = WriteopiaTheme.colorScheme.textLight,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { editFolder(folder) }
                    .size(26.dp)
                    .padding(4.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun DocumentItem(
    document: MenuItemUi.DocumentUi,
    position: Int,
    navigateToEditDocument: (String, String) -> Unit,
    changeIcon: (String, String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val depth = document.depth - 1
    val dropInfo = DropInfo(document, position)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val background =
        if (document.highlighted) {
            Color.Blue
        } else {
            WriteopiaTheme.colorScheme.globalBackground
        }

    DragRowTarget(
        modifier = modifier
            .padding(start = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { navigateToEditDocument(document.id, document.title) }
            .background(background)
            .hoverable(interactionSource)
            .padding(start = 4.dp, bottom = 6.dp, top = 6.dp),
        dataToDrop = dropInfo,
        position = position,
        emptySpaceClick = {
            navigateToEditDocument(document.id, document.title)
        },
        isHoldDraggable = false,
        showIcon = isHovered
    ) {
        Spacer(modifier = Modifier.width(4.dp + 12.dp * depth))

        var showIconsOptions by remember {
            mutableStateOf(false)
        }

        val tint = document.icon?.tint?.let(::Color) ?: WriteopiaTheme.colorScheme.textLight

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = document.icon?.label?.let(WrIcons::fromName) ?: WrIcons.file,
            contentDescription = "File",
            tint = tint,
            modifier = Modifier.size(16.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    showIconsOptions = !showIconsOptions
                }
        )

        DropdownMenu(
            expanded = showIconsOptions,
            onDismissRequest = { showIconsOptions = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            offset = DpOffset(y = 6.dp, x = 6.dp)
        ) {
            IconsPicker(
                iconSelect = { icon, tint -> changeIcon(document.documentId, icon, tint) }
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = document.title,
            modifier = Modifier,
            color = WriteopiaTheme.colorScheme.textLight,
            style = MaterialTheme.typography.bodySmall
                .copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )

        Spacer(modifier = Modifier.weight(1F))
    }
}

@Composable
private fun settingsOptions(
    iconVector: ImageVector?,
    contentDescription: String,
    text: String,
    click: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .let { modifierLet ->
                if (click != null) {
                    modifierLet.clickable(onClick = click)
                } else {
                    modifierLet
                }
            }
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .fillMaxWidth()
    ) {
        iconVector?.let { icon ->
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        if (trailingContent != null) {
            Spacer(modifier = Modifier.weight(1F))

            trailingContent()
        }
    }
}

@Composable
private fun title(
    text: String,
    click: (() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.let { modifierLet ->
            if (click != null) {
                modifierLet.clickable(onClick = click)
            } else {
                modifierLet
            }
        }
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = Modifier.weight(1F)
        )

        if (trailingContent != null) {
            trailingContent()
        }
    }
}
