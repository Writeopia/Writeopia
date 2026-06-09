package io.writeopia.notemenu.ui.screen.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.notemenu.ui.dto.NotesUi
import io.writeopia.notemenu.utils.minimalNoteCardWidth
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.AutoScrollLazyVerticalGrid
import io.writeopia.ui.components.AutoScrollLazyVerticalStaggeredGrid
import kotlinx.coroutines.flow.StateFlow

/**
 * A simplified version of NotesCardsScreen that doesn't require AnimatedVisibilityScope.
 * Used for Navigation 3 compatibility where AnimatedVisibilityScope is not available
 * in the entry function pattern.
 */
@Composable
fun NotesCardsScreenSimple(
    isDarkTheme: Boolean,
    documents: ResultData<NotesUi>,
    showAddMenuState: StateFlow<Boolean>,
    minimalNoteWidth: Dp = minimalNoteCardWidth(),
    loadNote: (String, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    folderClick: (String) -> Unit,
    moveRequest: (MenuItemUi, String) -> Unit,
    onSelection: (String) -> Unit,
    newNote: () -> Unit,
    newFolder: () -> Unit,
    hideShowMenu: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (documents) {
        is ResultData.Complete -> {
            Column(modifier = modifier) {
                val isEmpty = documents.data.documentUiList.isEmpty()

                if (isEmpty) {
                    TapToStartButtonSimple(newNote)
                } else {
                    val notesUi: NotesUi = documents.data
                    val documentsUiList = notesUi.documentUiList
                    val listModifier = Modifier.fillMaxSize()

                    when (notesUi.notesArrangement) {
                        NotesArrangement.STAGGERED_GRID -> {
                            LazyStaggeredGridNotesSimple(
                                documents = documentsUiList,
                                minimalNoteWidth = minimalNoteWidth,
                                onDocumentClick = loadNote,
                                folderClick = folderClick,
                                editFolder = editFolder,
                                modifier = listModifier,
                            )
                        }

                        NotesArrangement.GRID -> {
                            LazyGridNotesSimple(
                                documents = documentsUiList,
                                minimalNoteWidth = minimalNoteWidth,
                                onDocumentClick = loadNote,
                                folderClick = folderClick,
                                editFolder = editFolder,
                                modifier = listModifier,
                            )
                        }

                        NotesArrangement.LIST -> {
                            LazyColumnNotesSimple(
                                documents = documentsUiList,
                                onDocumentClick = loadNote,
                                folderClick = folderClick,
                                editFolder = editFolder,
                                modifier = listModifier,
                            )
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
                    val buttonModifier = Modifier.height(50.dp).width(280.dp)

                    Text(
                        WrStrings.add(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    CommonButton(
                        modifier = buttonModifier,
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
                        modifier = buttonModifier,
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

@Composable
private fun TapToStartButtonSimple(
    newNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        CommonButton(
            modifier = Modifier.align(Alignment.Center)
                .semantics { testTag = ADD_NOTE_TEST_TAG },
            text = WrStrings.tapToStart(),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            clickListener = newNote
        )
    }
}

@Composable
private fun LazyStaggeredGridNotesSimple(
    documents: List<MenuItemUi>,
    minimalNoteWidth: Dp,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 6.dp

    AutoScrollLazyVerticalStaggeredGrid(
        modifier = modifier,
        columns = StaggeredGridCells.Adaptive(minimalNoteWidth),
        contentPadding = contentPadding,
        verticalItemSpacing = spacing,
    ) {
        itemsIndexed(
            documents,
            key = { _, document -> document.documentId }
        ) { _, document ->
            DocumentItemSimple(
                document = document,
                onDocumentClick = onDocumentClick,
                folderClick = folderClick,
                editFolder = editFolder,
                modifier = Modifier.padding(horizontal = spacing / 2)
            )
        }
    }
}

@Composable
private fun LazyGridNotesSimple(
    documents: List<MenuItemUi>,
    minimalNoteWidth: Dp,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 6.dp

    AutoScrollLazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minimalNoteWidth),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        itemsIndexed(
            documents,
            key = { _, document -> document.documentId }
        ) { _, document ->
            DocumentItemSimple(
                document = document,
                onDocumentClick = onDocumentClick,
                folderClick = folderClick,
                editFolder = editFolder,
                modifier = Modifier.padding(vertical = spacing / 2)
            )
        }
    }
}

@Composable
private fun LazyColumnNotesSimple(
    documents: List<MenuItemUi>,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 6.dp

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        itemsIndexed(
            documents,
            key = { _, document -> document.documentId }
        ) { _, document ->
            DocumentItemSimple(
                document = document,
                onDocumentClick = onDocumentClick,
                folderClick = folderClick,
                editFolder = editFolder,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DocumentItemSimple(
    document: MenuItemUi,
    onDocumentClick: (String, String) -> Unit,
    folderClick: (String) -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier
) {
    when (document) {
        is MenuItemUi.DocumentUi -> {
            DocumentCardSimple(
                document = document,
                onDocumentClick = { onDocumentClick(document.documentId, document.title) },
                modifier = modifier.semantics { testTag = "$DOCUMENT_ITEM_TEST_TAG${document.documentId}" }
            )
        }
        is MenuItemUi.FolderUi -> {
            FolderCardSimple(
                folder = document,
                onClick = { folderClick(document.documentId) },
                onEditClick = { editFolder(document) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun DocumentCardSimple(
    document: MenuItemUi.DocumentUi,
    onDocumentClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (document.selected) {
        WriteopiaTheme.colorScheme.selectedBg
    } else {
        WriteopiaTheme.colorScheme.cardBg
    }

    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onDocumentClick),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .background(backgroundColor)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = document.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = WriteopiaTheme.colorScheme.textLight
            )

            val previewText = getPreviewText(document.preview)
            if (previewText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = WriteopiaTheme.colorScheme.textLight.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (document.isFavorite) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = WrIcons.favorites,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = document.lastEdit,
                    style = MaterialTheme.typography.labelSmall,
                    color = WriteopiaTheme.colorScheme.textLight.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Extract preview text from story steps.
 */
private fun getPreviewText(preview: List<StoryStep>): String {
    return preview
        .mapNotNull { step -> step.text?.takeIf { it.isNotBlank() } }
        .joinToString(" ")
        .take(200)
}

@Composable
private fun FolderCardSimple(
    folder: MenuItemUi.FolderUi,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (folder.selected) {
        WriteopiaTheme.colorScheme.selectedBg
    } else {
        WriteopiaTheme.colorScheme.cardBg
    }

    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .background(backgroundColor)
                .padding(bottom = 26.dp, top = 10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (folder.isFavorite) {
                    Icon(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        imageVector = WrIcons.favorites,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Icon(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(onClick = onEditClick)
                        .size(24.dp)
                        .padding(4.dp),
                    imageVector = WrIcons.moreHoriz,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Icon(
                modifier = Modifier.size(56.dp).padding(6.dp),
                imageVector = WrIcons.folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                modifier = Modifier.padding(horizontal = 12.dp),
                text = folder.title,
                color = WriteopiaTheme.colorScheme.textLight,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${folder.itemsCount} items",
                color = WriteopiaTheme.colorScheme.textLight,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
