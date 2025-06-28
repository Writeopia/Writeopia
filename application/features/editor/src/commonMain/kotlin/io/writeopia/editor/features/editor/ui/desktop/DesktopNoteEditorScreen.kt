package io.writeopia.editor.features.editor.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.SideEditorOptions
import io.writeopia.editor.features.editor.ui.folders.FolderSelectionDialog
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.ui.drawer.factory.DrawersFactory

@Composable
fun DesktopNoteEditorScreen(
    isDarkTheme: Boolean,
    documentId: String?,
    noteEditorViewModel: NoteEditorViewModel,
    drawersFactory: DrawersFactory,
    onPresentationClick: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    onDocumentDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isEditable by noteEditorViewModel.isEditable.collectAsState()
    var showFolderSelection by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier.clickable(
            onClick = noteEditorViewModel::clearSelections,
            interactionSource = interactionSource,
            indication = null
        )
    ) {
        EditorScaffold(
            clickAtBottom = noteEditorViewModel.writeopiaManager::clickAtTheEnd,
            modifier = modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(end = 56.dp),
            content = {
                AppTextEditor(
                    isDarkTheme = isDarkTheme,
                    noteEditorViewModel.writeopiaManager,
                    noteEditorViewModel,
                    drawersFactory = drawersFactory,
                    loadNoteId = documentId,
                    onDocumentLinkClick = onDocumentLinkClick,
                    modifier = Modifier.padding(start = 30.dp, end = 30.dp)
                )
            }
        )

        var showDeleteConfirmation by remember {
            mutableStateOf(false)
        }

        SideEditorOptions(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 40.dp)
                .align(Alignment.TopEnd),
            isDarkTheme,
            fontStyleSelected = { noteEditorViewModel.fontFamily },
            currentModel = noteEditorViewModel.currentModel,
            isEditableState = noteEditorViewModel.isEditable,
            isFavorite = noteEditorViewModel.notFavorite,
            boldClick = noteEditorViewModel::onAddSpanClick,
            setEditable = noteEditorViewModel::toggleEditable,
            checkItemClick = noteEditorViewModel::onAddCheckListClick,
            listItemClick = noteEditorViewModel::onAddListItemClick,
            codeBlockClick = noteEditorViewModel::onAddCodeBlockClick,
            highLightBlockClick = noteEditorViewModel::toggleHighLightBlock,
            onPresentationClick = onPresentationClick,
            changeFontFamily = noteEditorViewModel::changeFontFamily,
            addImage = noteEditorViewModel::addImage,
            exportMarkdown = noteEditorViewModel::exportMarkdown,
            exportJson = noteEditorViewModel::exportJson,
            moveToRoot = noteEditorViewModel::moveToRootFolder,
            moveToClick = {
                showFolderSelection = true
            },
            askAiBySelection = noteEditorViewModel::askAiBySelection,
            addPage = noteEditorViewModel::addPage,
            deleteDocument = {
                showDeleteConfirmation = true
            },
            toggleFavorite = noteEditorViewModel::toggleFavorite,
            aiSummary = noteEditorViewModel::aiSummary,
            aiActionPoints = noteEditorViewModel::aiActionPoints,
            aiFaq = noteEditorViewModel::aiFaq,
            aiTags = noteEditorViewModel::aiTags,
        )

        if (showDeleteConfirmation) {
            DeleteConfirmationDialog(
                onConfirmation = {
                    noteEditorViewModel.deleteDocument()
                    showDeleteConfirmation = false
                    onDocumentDelete()
                },
                onCancel = {
                    showDeleteConfirmation = false
                }
            )
        }

        if (!isEditable) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Lock",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .size(16.dp)
            )
        }

        if (showFolderSelection) {
            FolderSelectionDialog(
                noteEditorViewModel.listenForFolders,
                selectedFolder = { folderId ->
                    showFolderSelection = false
                    noteEditorViewModel.moveToFolder(folderId)
                },
                expandFolder = noteEditorViewModel::expandFolder,
                onDismissRequest = {
                    showFolderSelection = false
                }
            )
        }

        val loading by noteEditorViewModel.loadingState.collectAsState()

        if (loading) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
                    .padding(4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
