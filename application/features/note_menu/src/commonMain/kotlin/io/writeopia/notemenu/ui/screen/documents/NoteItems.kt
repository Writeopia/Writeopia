package io.writeopia.notemenu.ui.screen.documents

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.common.utils.icons.IconChange
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.common.utils.icons.WrIcons.folder
import io.writeopia.commonui.IconsPicker
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.notemenu.ui.dto.NotesUi
import io.writeopia.notemenu.utils.minimalNoteCardWidth
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.SwipeBox
import io.writeopia.ui.components.multiselection.SelectableByDrag
import io.writeopia.ui.draganddrop.target.DragCardTarget
import io.writeopia.ui.draganddrop.target.DropTarget
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.preview.AiAnswerPreviewDrawer
import io.writeopia.ui.drawer.preview.CheckItemPreviewDrawer
import io.writeopia.ui.drawer.preview.HeaderPreviewDrawer
import io.writeopia.ui.drawer.preview.ImagePreviewDrawer
import io.writeopia.ui.drawer.preview.TextPreviewDrawer
import io.writeopia.ui.drawer.preview.UnOrderedListItemPreviewDrawer
import io.writeopia.ui.model.DrawInfo
import org.jetbrains.compose.ui.tooling.preview.Preview

const val DOCUMENT_ITEM_TEST_TAG = "DocumentItem_"
const val ADD_NOTE_TEST_TAG = "addNote"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotesCards(
    documents: ResultData<NotesUi>,
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
    modifier: Modifier = Modifier,
) {
    when (documents) {
        is ResultData.Complete -> {
            sharedTransitionScope.run {
                Column(modifier = modifier) {
                    val isEmpty = documents.data.documentUiList.isEmpty()

                    if (isEmpty) {
                        TapToStartButton(isEmpty, animatedVisibilityScope, newNote)
                    } else {
                        val notesUi: NotesUi = documents.data

                        val documentsUiList = notesUi.documentUiList

                        val listModifier = Modifier

                        when (notesUi.notesArrangement) {
                            NotesArrangement.STAGGERED_GRID -> {
                                LazyStaggeredGridNotes(
                                    documentsUiList,
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
                                    modifier = listModifier,
                                )
                            }

                            NotesArrangement.GRID -> {
                                LazyGridNotes(
                                    documentsUiList,
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
                                    modifier = listModifier,
                                )
                            }

                            NotesArrangement.LIST -> {
                                LazyColumnNotes(
                                    documentsUiList,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionScope = sharedTransitionScope,
                                    selectionListener = selectionListener,
                                    onDocumentClick = loadNote,
                                    folderClick = folderClick,
                                    moveRequest = moveRequest,
                                    changeIcon = changeIcon,
                                    onDragIconClick = onSelection,
                                    newNote = newNote,
                                    modifier = listModifier,
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

        is ResultData.Loading, is ResultData.Idle, is ResultData.InProgress -> {
            Box(modifier = modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyStaggeredGridNotes(
    documents: List<MenuItemUi>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    minimalNoteWidth: Dp,
    onDocumentClick: (String, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    folderClick: (String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onDragIconClick: (String) -> Unit,
    newNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 6.dp

    LazyVerticalStaggeredGrid(
        modifier = modifier,
        columns = StaggeredGridCells.Adaptive(minSize = minimalNoteWidth),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalItemSpacing = spacing,
        contentPadding = contentPadding,
        content = {
            item(span = StaggeredGridItemSpan.FullLine) {
                sharedTransitionScope.run {
                    TapToStartButton(
                        isEmpty = false,
                        animatedVisibilityScope = animatedVisibilityScope,
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
                            previewDrawers(),
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
    documents: List<MenuItemUi>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    minimalNoteWidth: Dp,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onDragIconClick: (String) -> Unit,
    newNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Arrangement.spacedBy(6.dp)

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = minimalNoteWidth),
        horizontalArrangement = spacing,
        verticalArrangement = spacing,
        contentPadding = contentPadding,
        content = {
            item(span = { GridItemSpan(this.maxLineSpan) }) {
                sharedTransitionScope.run {
                    TapToStartButton(
                        isEmpty = false,
                        animatedVisibilityScope = animatedVisibilityScope,
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
                            previewDrawers(),
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
    documents: List<MenuItemUi>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    changeIcon: (String, String, Int, IconChange) -> Unit,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onDragIconClick: (String) -> Unit,
    newNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = contentPadding,
        content = {
            item {
                sharedTransitionScope.run {
                    TapToStartButton(
                        isEmpty = false,
                        animatedVisibilityScope = animatedVisibilityScope,
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
                            previewDrawers(),
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
) {
//    sharedTransitionScope.run {
    Box(
        shadowModifier()
//                .sharedBounds(
//                rememberSharedContentState(key = "folderTransition${folderUi.documentId}"),
//                animatedVisibilityScope = animatedVisibilityScope,
//                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
//            )
    ) {
        DropTarget { inBound, data ->
            val menuItemUI = data?.info as? MenuItemUi
            if (inBound && menuItemUI != null) {
                LaunchedEffect(menuItemUI) {
                    moveRequest(menuItemUI, folderUi.documentId)
                }
            }

            val bgColor =
                when {
                    inBound && menuItemUI?.id != folderUi.id -> WriteopiaTheme.colorScheme.highlight
                    folderUi.selected -> WriteopiaTheme.colorScheme.selectedBg
                    else -> WriteopiaTheme.colorScheme.cardBg
                }

            val textColor = WriteopiaTheme.colorScheme.textLight

            SwipeBox(
                modifier = modifier
                    .fillMaxWidth()
                    .background(WriteopiaTheme.colorScheme.cardPlaceHolderBackground)
                    .clip(MaterialTheme.shapes.large)
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
                    modifier = modifier.clip(MaterialTheme.shapes.large),
                    position = position,
                    dataToDrop = DropInfo(folderUi, position),
                    iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                    onIconClick = onDragIconClick,
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

                        Icon(
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(MaterialTheme.shapes.large)
                                .clickable { showIconsOptions = !showIconsOptions }
                                .size(56.dp)
                                .padding(6.dp),
                            imageVector = folderUi.icon?.label?.let(WrIcons::fromName)
                                ?: folder,
                            contentDescription = "Folder",
                            tint = tint
                        )

                        DropdownMenu(
                            expanded = showIconsOptions,
                            onDismissRequest = { showIconsOptions = false },
                            modifier = Modifier.background(WriteopiaTheme.colorScheme.cardBg),
                        ) {
                            IconsPicker(
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
                            modifier = Modifier.padding(horizontal = 12.dp),
                            text = folderUi.title,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${folderUi.itemsCount} items",
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (folderUi.isFavorite) {
                    Icon(
                        modifier = Modifier.align(Alignment.TopEnd).size(40.dp).padding(12.dp),
                        imageVector = WrIcons.favorites,
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
                    position = position,
                    dataToDrop = DropInfo(documentUi, position),
                    iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                    onIconClick = onDragIconClick
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
                                    position = i,
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
private fun previewDrawers(): Map<Int, StoryStepDrawer> {
    val unOrderedListItemPreviewDrawer = UnOrderedListItemPreviewDrawer()

    return mapOf(
        StoryTypes.TITLE.type.number to HeaderPreviewDrawer(
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
            )
        ),
        StoryTypes.CHECK_ITEM.type.number to CheckItemPreviewDrawer(),
        StoryTypes.TEXT.type.number to TextPreviewDrawer(),
        StoryTypes.UNORDERED_LIST_ITEM.type.number to unOrderedListItemPreviewDrawer,
        StoryTypes.IMAGE.type.number to ImagePreviewDrawer(),
        StoryTypes.AI_ANSWER.type.number to AiAnswerPreviewDrawer()
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.TapToStartButton(
    isEmpty: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    newNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .sharedBounds(
                rememberSharedContentState(key = "noteInit"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
            )
            .fillMaxWidth()
            .let { modifierLet ->
                if (isEmpty) {
                    modifierLet.fillMaxHeight().padding(bottom = 30.dp)
                } else {
                    modifierLet.height(400.dp)
                }
            }
            .padding(top = 10.dp, start = 6.dp, end = 6.dp)
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
