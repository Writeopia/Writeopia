package io.writeopia.commonui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import kotlinx.coroutines.CancellationException

/** The folder picker in a bottom sheet, for touch screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSelectionSheet(
    loadFolders: suspend () -> List<Folder>,
    excludedIds: Set<String>,
    currentParentId: String,
    onMove: (parentId: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        MoveSelectionPicker(
            loadFolders = loadFolders,
            excludedIds = excludedIds,
            currentParentId = currentParentId,
            onMove = onMove,
            onCancel = onDismissRequest,
            modifier = Modifier.fillMaxHeight(0.85F),
        )
    }
}

/** The folder picker in a dialog, for the desktop. */
@Composable
fun MoveSelectionDialog(
    loadFolders: suspend () -> List<Folder>,
    excludedIds: Set<String>,
    currentParentId: String,
    onMove: (parentId: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.width(460.dp).height(560.dp),
            shape = MaterialTheme.shapes.large
        ) {
            MoveSelectionPicker(
                loadFolders = loadFolders,
                excludedIds = excludedIds,
                currentParentId = currentParentId,
                onMove = onMove,
                onCancel = onDismissRequest,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Picks the folder to move the selected items to. The tree is browsed from the home, one level
 * at a time, and the items go where the user taps "Move here". The search button lists every
 * folder by name instead; picking one of them asks for a confirmation first. The selected folders
 * and what's inside them aren't offered, and the folder the items are already in can't be picked.
 */
@Composable
private fun MoveSelectionPicker(
    loadFolders: suspend () -> List<Folder>,
    excludedIds: Set<String>,
    currentParentId: String,
    onMove: (parentId: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bumped by the retry button, to load the folders again.
    var loadAttempt by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    val folders by produceState<List<Folder>?>(null, loadAttempt) {
        value = null
        loadFailed = false
        value = try {
            loadFolders()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
            null
        }
    }
    val rootTitle = WrStrings.home()
    val tree = remember(folders, excludedIds, rootTitle) {
        folders?.let { loaded -> MoveFolderTree(loaded, excludedIds, rootTitle) }
    }

    // The folders opened so far: the last one is the level shown.
    var openedFolders by remember { mutableStateOf(emptyList<Folder>()) }
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmTarget by remember { mutableStateOf<Folder?>(null) }

    val level = openedFolders.lastOrNull()
    val levelId = level?.id ?: Folder.ROOT_PATH

    Column(modifier = modifier) {
        PickerHeader(
            isSearching = isSearching,
            query = query,
            onQueryChange = { query = it },
            canGoBack = isSearching || openedFolders.isNotEmpty(),
            onBack = {
                if (isSearching) {
                    isSearching = false
                    query = ""
                } else {
                    openedFolders = openedFolders.dropLast(1)
                }
            },
            onSearch = { isSearching = true },
            onCancel = onCancel,
        )

        HorizontalDivider()

        when {
            tree == null && loadFailed -> Column(
                modifier = Modifier.fillMaxWidth().weight(1F).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = WrStrings.moveErrorLoadingFolders(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { loadAttempt++ }) {
                    Text(WrStrings.retry())
                }
            }

            tree == null -> Box(modifier = Modifier.fillMaxWidth().weight(1F)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            isSearching -> {
                val results = remember(tree, query) { tree.search(query) }

                PickerList(
                    isEmpty = results.isEmpty(),
                    emptyText = WrStrings.moveNoFoldersFound(),
                    modifier = Modifier.weight(1F),
                ) {
                    items(results, key = { result -> result.folder.id }) { result ->
                        val isCurrentParent = result.folder.id == currentParentId

                        FolderRow(
                            title = result.folder.title,
                            subtitle = if (isCurrentParent) WrStrings.moveItemsAreHere() else result.path,
                            icon = folderIcon(result.folder.icon),
                            tint = folderTint(result.folder.icon),
                            enabled = !isCurrentParent,
                            showChevron = false,
                            onClick = { confirmTarget = result.folder },
                        )
                    }
                }
            }

            else -> {
                val children = remember(tree, levelId) { tree.childrenOf(levelId) }
                val isCurrentParent = levelId == currentParentId

                LocationHeader(
                    title = level?.title ?: rootTitle,
                    icon = level?.let { folderIcon(it.icon) } ?: WrIcons.home,
                    tint = level?.let { folderTint(it.icon) } ?: MaterialTheme.colorScheme.primary,
                    subtitle = if (isCurrentParent) {
                        WrStrings.moveLocationCurrent()
                    } else {
                        WrStrings.moveLocationHint()
                    },
                )

                HorizontalDivider()

                PickerList(
                    isEmpty = children.isEmpty(),
                    emptyText = WrStrings.moveNoFoldersHere(),
                    modifier = Modifier.weight(1F),
                ) {
                    items(children, key = { folder -> folder.id }) { folder ->
                        FolderRow(
                            title = folder.title,
                            subtitle = if (folder.id == currentParentId) WrStrings.moveItemsAreHere() else null,
                            icon = folderIcon(folder.icon),
                            tint = folderTint(folder.icon),
                            enabled = true,
                            showChevron = true,
                            onClick = { openedFolders = openedFolders + folder },
                        )
                    }
                }

                HorizontalDivider()

                Button(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 44.dp),
                    enabled = !isCurrentParent,
                    shape = RoundedCornerShape(10.dp),
                    onClick = { onMove(levelId) },
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = WrIcons.moveToFolder,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentParent) WrStrings.moveAlreadyHere() else WrStrings.moveHere(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(WrStrings.moveTo()) },
            text = { Text(WrStrings.moveSelectionConfirmation(target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    onMove(target.id)
                }) {
                    Text(WrStrings.move())
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) {
                    Text(WrStrings.cancel())
                }
            },
        )
    }
}

/** The title with the back, search and close buttons; while searching, the search field. */
@Composable
private fun PickerHeader(
    isSearching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            HeaderButton(WrIcons.backArrowMobile, WrStrings.back(), tint, onBack)
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(modifier = Modifier.weight(1F).padding(horizontal = 4.dp)) {
            if (isSearching) {
                val focusRequester = remember { FocusRequester() }

                BasicTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = tint)),
                    cursorBrush = SolidColor(tint),
                )

                if (query.isEmpty()) {
                    Text(
                        text = WrStrings.searchFolders(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tint.copy(alpha = 0.5F),
                    )
                }

                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(
                    text = WrStrings.moveTo(),
                    style = MaterialTheme.typography.titleMedium,
                    color = tint,
                )
            }
        }

        if (isSearching) {
            if (query.isNotEmpty()) {
                HeaderButton(WrIcons.close, WrStrings.close(), tint) { onQueryChange("") }
            }
        } else {
            HeaderButton(WrIcons.search, WrStrings.searchFolders(), tint, onSearch)
            HeaderButton(WrIcons.close, WrStrings.cancel(), tint, onCancel)
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Icon(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .size(22.dp),
        imageVector = icon,
        contentDescription = description,
        tint = tint,
    )
}

/** The folder being browsed, so the title can stay "Move to...". */
@Composable
private fun LocationHeader(title: String, icon: ImageVector, tint: Color, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint)

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun PickerList(
    isEmpty: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (isEmpty) {
            Text(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                content = content,
            )
        }
    }
}

/** A folder: its icon on a tinted tile, the name, an optional line below and a chevron to open it. */
@Composable
private fun FolderRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    showChevron: Boolean,
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = title,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (showChevron) {
            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = WrIcons.smallArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Icon(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.14F))
            .padding(7.dp),
        imageVector = icon,
        contentDescription = null,
        tint = tint,
    )
}

private fun folderIcon(icon: MenuItem.Icon?): ImageVector =
    icon?.label?.let(WrIcons::fromName) ?: WrIcons.folder

@Composable
private fun folderTint(icon: MenuItem.Icon?): Color =
    icon?.tint?.let(::Color) ?: MaterialTheme.colorScheme.primary
