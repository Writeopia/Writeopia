package io.writeopia.notemenu.ui.screen.documents

import io.writeopia.sdk.utils.iterables.Rank
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.common.utils.icons.WrIcons.folder
import io.writeopia.commonui.IconsPicker
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.notemenu.ui.dto.NotesUi
import io.writeopia.notemenu.ui.screen.FolderSharedKeys
import io.writeopia.notemenu.utils.minimalNoteCardWidth
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.AutoScrollLazyVerticalGrid
import io.writeopia.ui.components.AutoScrollLazyVerticalStaggeredGrid
import io.writeopia.ui.components.SwipeBox
import io.writeopia.ui.components.multiselection.SelectableByDrag
import io.writeopia.ui.draganddrop.target.DragCardTarget
import io.writeopia.ui.draganddrop.target.DropTarget
import io.writeopia.ui.draganddrop.target.draggedData
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.preview.AiAnswerPreviewDrawer
import io.writeopia.ui.drawer.preview.CheckItemPreviewDrawer
import io.writeopia.ui.drawer.preview.DocumentLinkPreviewDrawer
import io.writeopia.ui.drawer.preview.LinkCardPreviewDrawer
import io.writeopia.ui.drawer.preview.HeaderPreviewDrawer
import io.writeopia.ui.drawer.preview.ImagePreviewDrawer
import io.writeopia.ui.drawer.preview.TextPreviewDrawer
import io.writeopia.ui.drawer.preview.UnOrderedListItemPreviewDrawer
import io.writeopia.ui.drawer.preview.VideoPreviewDrawer
import io.writeopia.ui.model.DrawInfo
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.date.formatRelativeTime
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

const val DOCUMENT_ITEM_TEST_TAG = "DocumentItem_"
const val ADD_NOTE_TEST_TAG = "addNote"

/** Lets the list scroll past its last item, so it is not stuck at the bottom edge of the screen. */
private val notesContentPadding =
    PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 240.dp)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotesCardsScreen(
    isDarkTheme: Boolean,
    documents: ResultData<NotesUi>,
    showAddMenuState: StateFlow<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    minimalNoteWidth: Dp = minimalNoteCardWidth(),
    loadNote: (String, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    folderClick: (String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    onSelection: (String) -> Unit,
    newNote: () -> Unit,
    newFolder: () -> Unit,
    hideShowMenu: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
    /** Only true in the root folder: inside a folder or a tag, the new note button is enough. */
    showTapToStart: Boolean = true,
    /** Shown instead of the tap to start button when the screen is empty and it is hidden. */
    emptyMessage: String = "",
    /** Shows a button to create a note below [emptyMessage]. */
    showCreateNoteWhenEmpty: Boolean = false,
) {
    when (documents) {
        is ResultData.Complete -> {
            sharedTransitionScope.run {
                Column(modifier = modifier) {
                    val isEmpty = documents.data.documentUiList.isEmpty()

                    if (isEmpty && !showTapToStart) {
                        EmptyMessage(
                            message = emptyMessage,
                            newNote = newNote.takeIf { showCreateNoteWhenEmpty }
                        )
                    } else if (isEmpty) {
                        TapToStartButton(isEmpty, newNote)
                    } else {
                        val notesUi: NotesUi = documents.data

                        val documentsUiList = notesUi.documentUiList

                        val listModifier = Modifier

                        when (notesUi.notesArrangement) {
                            NotesArrangement.STAGGERED_GRID -> {
                                LazyStaggeredGridNotes(
                                    isDarkTheme = isDarkTheme,
                                    documents = documentsUiList,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionScope = sharedTransitionScope,
                                    minimalNoteWidth = minimalNoteWidth,
                                    selectionListener = selectionListener,
                                    onDocumentClick = loadNote,
                                    folderClick = folderClick,
                                    moveRequest = moveRequest,
                                    changeIcon = changeIcon,
                                    onDragIconClick = onSelection,
                                    newNote = newNote,
                                    editFolder = editFolder,
                                    modifier = listModifier,
                                    showTapToStart = showTapToStart,
                                )
                            }

                            NotesArrangement.GRID -> {
                                LazyGridNotes(
                                    isDarkTheme,
                                    documents = documentsUiList,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionScope = sharedTransitionScope,
                                    minimalNoteWidth = minimalNoteWidth,
                                    selectionListener = selectionListener,
                                    onDocumentClick = loadNote,
                                    folderClick = folderClick,
                                    moveRequest = moveRequest,
                                    changeIcon = changeIcon,
                                    onDragIconClick = onSelection,
                                    newNote = newNote,
                                    editFolder = editFolder,
                                    modifier = listModifier,
                                    showTapToStart = showTapToStart,
                                )
                            }

                            NotesArrangement.LIST -> {
                                LazyColumnNotes(
                                    isDarkTheme = isDarkTheme,
                                    documents = documentsUiList,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionScope = sharedTransitionScope,
                                    selectionListener = selectionListener,
                                    onDocumentClick = loadNote,
                                    folderClick = folderClick,
                                    moveRequest = moveRequest,
                                    changeIcon = changeIcon,
                                    onDragIconClick = onSelection,
                                    newNote = newNote,
                                    editFolder = editFolder,
                                    modifier = listModifier,
                                    showTapToStart = showTapToStart,
                                )
                            }
                        }
                    }
                }
            }
        }

        is ResultData.Error -> {
            LaunchedEffect(key1 = true) {
                documents.exception?.printStackTrace()
            }

            Box(modifier = modifier.fillMaxSize()) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    // stringResource(R.string.error_loading_notes)
                    text = "Error!!"
                )
            }
        }

        // Nothing is shown while the items load, as the screen is often still in its transition.
        is ResultData.Loading, is ResultData.Idle, is ResultData.InProgress -> {
            Box(modifier = modifier.fillMaxSize())
        }
    }

    val showAddMenu by showAddMenuState.collectAsState()

    if (showAddMenu) {
        Dialog(onDismissRequest = hideShowMenu) {
            Card(modifier = Modifier.width(320.dp), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp,
                        top = 20.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val modifier = Modifier.height(50.dp).width(280.dp)

                    Text(
                        WrStrings.add(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    CommonButton(
                        modifier = modifier,
                        text = WrStrings.document(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        clickListener = {
                            hideShowMenu()
                            newNote()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CommonButton(
                        modifier = modifier,
                        text = WrStrings.folder(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        clickListener = {
                            hideShowMenu()
                            newFolder()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyStaggeredGridNotes(
    isDarkTheme: Boolean,
    documents: List<MenuItemUi>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    minimalNoteWidth: Dp,
    onDocumentClick: (String, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    folderClick: (String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    contentPadding: PaddingValues = notesContentPadding,
    onDragIconClick: (String) -> Unit,
    newNote: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
    showTapToStart: Boolean = true,
) {
    val spacing = 6.dp

    AutoScrollLazyVerticalStaggeredGrid(
        modifier = modifier,
        columns = StaggeredGridCells.Adaptive(minSize = minimalNoteWidth),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalItemSpacing = spacing,
        contentPadding = contentPadding,
        content = {
            if (showTapToStart) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    TapToStartButton(
                        isEmpty = false,
                        newNote = newNote,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            itemsIndexed(
                documents,
                key = { _, menuItem -> menuItem.hashCode() }
            ) { i, menuItem ->
                val itemModifier = Modifier.animateItem()

                when (menuItem) {
                    is MenuItemUi.DocumentUi -> {
                        DocumentItem(
                            menuItem,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onDocumentClick,
                            selectionListener,
                            previewDrawers(isDarkTheme),
                            position = i,
                            { onDragIconClick(menuItem.documentId) },
                            modifier = itemModifier,
                        )
                    }

                    is MenuItemUi.FolderUi -> {
                        FolderItem(
                            menuItem,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            folderClick = folderClick,
                            position = i,
                            selectionListener = selectionListener,
                            moveRequest = moveRequest,
                            changeIcon = { id, icon, tint ->
                                changeIcon(id, icon, tint, IconChange.FOLDER)
                            },
                            onDragIconClick = { onDragIconClick(menuItem.documentId) },
                            editFolder = editFolder,
                            modifier = itemModifier
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyGridNotes(
    isDarkTheme: Boolean,
    documents: List<MenuItemUi>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    minimalNoteWidth: Dp,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    contentPadding: PaddingValues = notesContentPadding,
    onDragIconClick: (String) -> Unit,
    newNote: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
    showTapToStart: Boolean = true,
) {
    val spacing = Arrangement.spacedBy(6.dp)

    AutoScrollLazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = minimalNoteWidth),
        horizontalArrangement = spacing,
        verticalArrangement = spacing,
        contentPadding = contentPadding,
        content = {
            if (showTapToStart) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    TapToStartButton(
                        isEmpty = false,
                        newNote = newNote,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            itemsIndexed(
                documents,
                key = { _, menuItem -> menuItem.hashCode() }
            ) { i, menuItem ->
                when (menuItem) {
                    is MenuItemUi.DocumentUi -> {
                        DocumentItem(
                            menuItem,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onDocumentClick,
                            selectionListener,
                            previewDrawers(isDarkTheme),
                            position = i,
                            { onDragIconClick(menuItem.documentId) },
                            modifier = Modifier.animateItem()
                        )
                    }

                    is MenuItemUi.FolderUi -> {
                        FolderItem(
                            menuItem,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            folderClick = folderClick,
                            moveRequest = moveRequest,
                            selectionListener = selectionListener,
                            changeIcon = { id, icon, tint ->
                                changeIcon(id, icon, tint, IconChange.FOLDER)
                            },
                            position = i,
                            editFolder = editFolder,
                            onDragIconClick = { onDragIconClick(menuItem.documentId) },
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyColumnNotes(
    isDarkTheme: Boolean,
    documents: List<MenuItemUi>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    contentPadding: PaddingValues = notesContentPadding,
    onDragIconClick: (String) -> Unit,
    newNote: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
    showTapToStart: Boolean = true,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = contentPadding,
        content = {
            if (showTapToStart) {
                item {
                    TapToStartButton(
                        isEmpty = false,
                        newNote = newNote,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            itemsIndexed(
                documents,
                key = { _, menuItem -> menuItem.hashCode() }
            ) { i, menuItem ->
                when (menuItem) {
                    is MenuItemUi.DocumentUi -> {
                        DocumentItem(
                            menuItem,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onDocumentClick,
                            selectionListener,
                            previewDrawers(isDarkTheme),
                            position = i,
                            onDragIconClick = { onDragIconClick(menuItem.documentId) },
                            modifier = Modifier.animateItem()
                        )
                    }

                    is MenuItemUi.FolderUi -> {
                        FolderItem(
                            menuItem,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            folderClick = folderClick,
                            selectionListener = selectionListener,
                            moveRequest = moveRequest,
                            changeIcon = { id, icon, tint ->
                                changeIcon(id, icon, tint, IconChange.FOLDER)
                            },
                            position = i,
                            editFolder = editFolder,
                            onDragIconClick = { onDragIconClick(menuItem.documentId) },
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FolderItem(
    folderUi: MenuItemUi.FolderUi,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    folderClick: (String) -> Unit,
    position: Int,
    moveRequest: (MenuItemUi, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    changeIcon: (String, String, Int) -> Unit,
    onDragIconClick: () -> Unit,
    modifier: Modifier = Modifier,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
) {
    val folderId = folderUi.documentId

    // The card grows into the folder screen and the title flies into its toolbar.
    val cardModifier = with(sharedTransitionScope) {
        shadowModifier().sharedBounds(
            rememberSharedContentState(key = FolderSharedKeys.card(folderId)),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
    val titleModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = FolderSharedKeys.title(folderId)),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    // Folders are selected by the drag box too, like the documents.
    SelectableByDrag(cardModifier) { isInsideDrag ->
        if (isInsideDrag != null) {
            LaunchedEffect(isInsideDrag) {
                selectionListener(folderUi.documentId, isInsideDrag)
            }
        }

        DropTarget { inBound, data ->
            val menuItemUI = data?.info as? MenuItemUi
            if (inBound && menuItemUI != null) {
                LaunchedEffect(menuItemUI) {
                    moveRequest(menuItemUI, folderUi.documentId)
                }
            }

            // A card held over the folder, other than the folder itself, is about to be moved into it.
            val hoveringItem = draggedData()?.info as? MenuItemUi
            val isDropTarget = inBound && hoveringItem != null && hoveringItem.id != folderUi.id

            // Like the folder cards of the native apps: an accent border and tint, and the icon
            // crossfading into the move icon.
            val accent = MaterialTheme.colorScheme.primary
            val borderColor by animateColorAsState(
                targetValue = if (isDropTarget) accent else accent.copy(alpha = 0F),
                label = "folderDropBorder"
            )

            val bgColor by animateColorAsState(
                targetValue = when {
                    isDropTarget -> accent.copy(alpha = 0.15F)
                        .compositeOver(WriteopiaTheme.colorScheme.cardBg)

                    folderUi.selected -> WriteopiaTheme.colorScheme.selectedBg
                    else -> WriteopiaTheme.colorScheme.cardBg
                },
                label = "folderDropBackground"
            )

            val textColor = WriteopiaTheme.colorScheme.textLight

            SwipeBox(
                modifier = modifier
                    .fillMaxWidth()
                    .background(WriteopiaTheme.colorScheme.cardPlaceHolderBackground)
                    .clip(MaterialTheme.shapes.large)
                    .border(width = 2.dp, color = borderColor, shape = MaterialTheme.shapes.large)
                    .clickable {
                        folderClick(folderUi.documentId)
                    },
                isOnEditState = folderUi.selected,
                swipeListener = { state ->
                    selectionListener(folderUi.documentId, state)
                },
                cornersShape = MaterialTheme.shapes.large,
                defaultColor = WriteopiaTheme.colorScheme.cardBg,
                activeColor = bgColor,
                activeBorderColor = MaterialTheme.colorScheme.primary
            ) {
                DragCardTarget(
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                    position = Rank.at(position),
                    dataToDrop = DropInfo(folderUi, Rank.at(position)),
                    iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                    onIconClick = onDragIconClick,
                    dragOnLongPress = true,
                ) {
                    Column(
                        modifier = Modifier
                            .background(color = bgColor, shape = MaterialTheme.shapes.large)
                            .fillMaxWidth()
                            .padding(bottom = 26.dp, top = 10.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var showIconsOptions by remember { mutableStateOf(false) }
                        val tint = folderUi.icon?.tint?.let(::Color)
                            ?: MaterialTheme.colorScheme.primary

                        Crossfade(
                            targetState = if (isDropTarget) {
                                WrIcons.moveToFolder
                            } else {
                                folderUi.icon?.label?.let(WrIcons::fromName) ?: folder
                            },
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(MaterialTheme.shapes.large)
                                .clickable { showIconsOptions = !showIconsOptions }
                                .size(56.dp)
                                .padding(6.dp),
                            animationSpec = tween(200),
                            label = "folderDropIcon"
                        ) { icon ->
                            Icon(
                                modifier = Modifier.fillMaxSize(),
                                imageVector = icon,
                                contentDescription = "Folder",
                                tint = tint
                            )
                        }

                        DropdownMenu(
                            expanded = showIconsOptions,
                            onDismissRequest = { showIconsOptions = false },
                            modifier = Modifier.background(WriteopiaTheme.colorScheme.cardBg),
                        ) {
                            IconsPicker(
                                initialIconName = folderUi.icon?.label,
                                initialTint = folderUi.icon?.tint,
                                iconSelect = { icon, tint ->
                                    changeIcon(
                                        folderUi.id,
                                        icon,
                                        tint
                                    )
                                }
                            )
                        }

                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp).then(titleModifier),
                            text = folderUi.title,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = formatRelativeTime(folderUi.lastUpdatedAt),
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (folderUi.isFavorite) {
                        Icon(
                            modifier = Modifier.size(40.dp).padding(12.dp),
                            imageVector = WrIcons.favorites,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Icon(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                editFolder(folderUi)
                            }
                            .size(34.dp)
                            .padding(8.dp),
                        imageVector = WrIcons.moreHoriz,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
//    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocumentItem(
    documentUi: MenuItemUi.DocumentUi,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    documentClick: (String, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    drawers: Map<Int, StoryStepDrawer>,
    position: Int,
    onDragIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleFallback = "untitled"
//        stringResource(R.string.untitled)

    val backgroundColor = if (documentUi.selected) {
        WriteopiaTheme.colorScheme.selectedBg
    } else {
        WriteopiaTheme.colorScheme.cardBg
    }

    sharedTransitionScope.run {
        SelectableByDrag(
            shadowModifier().sharedBounds(
                rememberSharedContentState(key = "noteInit${documentUi.documentId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
            )
        ) { isInsideDrag ->
            if (isInsideDrag != null) {
                LaunchedEffect(isInsideDrag) {
                    selectionListener(documentUi.documentId, isInsideDrag)
                }
            }

            SwipeBox(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(WriteopiaTheme.colorScheme.cardPlaceHolderBackground)
                    .clickable {
                        documentClick(
                            documentUi.documentId,
                            documentUi.title.takeIf { it.isNotEmpty() } ?: titleFallback
                        )
                    }
                    .semantics {
                        testTag = "$DOCUMENT_ITEM_TEST_TAG${documentUi.title}"
                    },
                isOnEditState = documentUi.selected,
                swipeListener = { state ->
                    selectionListener(documentUi.documentId, state)
                },
                cornersShape = MaterialTheme.shapes.large,
                defaultColor = WriteopiaTheme.colorScheme.cardBg,
                activeColor = backgroundColor
            ) {
                DragCardTarget(
                    position = Rank.at(position),
                    dataToDrop = DropInfo(documentUi, Rank.at(position)),
                    iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                    onIconClick = onDragIconClick,
                    dragOnLongPress = true
                ) {
                    Column(
                        modifier = Modifier.background(
                            color = backgroundColor,
                            shape = MaterialTheme.shapes.large
                        ).clip(MaterialTheme.shapes.large)
                    ) {
                        documentUi.preview.forEachIndexed { i, storyStep ->
                            val extraData = mutableMapOf<String, Any>()

                            if (storyStep.type == StoryTypes.TITLE.type) {
                                documentUi.icon?.label?.let(WrIcons::fromName)?.let {
                                    extraData["imageVector"] = it
                                }

                                documentUi.icon?.tint?.let {
                                    extraData["imageVectorTint"] = it
                                }
                            }

                            drawers[storyStep.type.number]?.Step(
                                step = storyStep,
                                drawInfo = DrawInfo(
                                    editable = false,
                                    position = Rank.at(i),
                                    selectMode = documentUi.selected,
                                    extraData = extraData
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(modifier = Modifier.align(Alignment.TopEnd).height(40.dp).padding(12.dp)) {
                        if (documentUi.isFavorite) {
                            Icon(
                                imageVector = WrIcons.favorites,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (!documentUi.isSynced) {
                            Icon(
                                imageVector = WrIcons.cloudSync,
                                contentDescription = "Cloud upload",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String, newNote: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (newNote != null) {
            Spacer(modifier = Modifier.height(16.dp))

            // Filled with the accent color, so it stands out from the screen background.
            Button(
                modifier = Modifier.heightIn(min = 44.dp),
                shape = RoundedCornerShape(10.dp),
                onClick = newNote
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = WrIcons.add,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = WrStrings.folderScreenCreateNote(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Preview
@Composable
private fun NoNotesScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.Center),
            text = "You don\'t have notes",
//            stringResource(R.string.you_dont_have_notes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun previewDrawers(isDarkTheme: Boolean): Map<Int, StoryStepDrawer> {
    val unOrderedListItemPreviewDrawer = UnOrderedListItemPreviewDrawer(isDarkTheme = true)

    return mapOf(
        StoryTypes.TITLE.type.number to HeaderPreviewDrawer(
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            isDarkTheme = isDarkTheme,
        ),
        StoryTypes.CHECK_ITEM.type.number to CheckItemPreviewDrawer(),
        StoryTypes.TEXT.type.number to TextPreviewDrawer(isDarkTheme = isDarkTheme),
        StoryTypes.DOCUMENT_LINK.type.number to DocumentLinkPreviewDrawer(),
        StoryTypes.LINK_CARD.type.number to LinkCardPreviewDrawer(),
        StoryTypes.UNORDERED_LIST_ITEM.type.number to unOrderedListItemPreviewDrawer,
        StoryTypes.IMAGE.type.number to ImagePreviewDrawer(),
        StoryTypes.AI_ANSWER.type.number to AiAnswerPreviewDrawer(),
        StoryTypes.VIDEO.type.number to VideoPreviewDrawer()
    )
}

@Composable
private fun shadowModifier(): Modifier =
    Modifier.padding(4.dp)
        .shadow(
            8.dp,
            shape = MaterialTheme.shapes.large,
            spotColor = WriteopiaTheme.colorScheme.cardShadow
        )

@Composable
private fun TapToStartButton(
    isEmpty: Boolean,
    newNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWeb = LocalPlatform.current == PlatformType.WEB

    Box(
        modifier
            .fillMaxWidth()
            .let { modifierLet ->
                if (isEmpty) {
                    modifierLet.fillMaxHeight().padding(bottom = 30.dp)
                } else {
                    if (isWeb) {
                        modifierLet.height(260.dp)
                    } else {
                        modifierLet.height(400.dp)
                    }
                }
            }
            .padding(top = if (isWeb) 0.dp else 10.dp, start = 6.dp, end = 6.dp)
            .background(
                WriteopiaTheme.colorScheme.cardBg,
                MaterialTheme.shapes.large
            )
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = newNote)
    ) {
        val text = WrStrings.tapToStart()

        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center)
                .padding(16.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
    }
}
