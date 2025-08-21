package io.writeopia.editor.features.editor.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.SideEditorOptions
import io.writeopia.editor.features.editor.ui.folders.FolderSelectionDialog
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.theme.WriteopiaTheme
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

        val textState by noteEditorViewModel.searchText.collectAsState()
        val showSearch by noteEditorViewModel.showSearchState.collectAsState()
        val shape = MaterialTheme.shapes.medium

        AnimatedVisibility(
            showSearch,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                val focusRequester = remember { FocusRequester() }

                BasicTextField(
                    value = textState,
                    onValueChange = noteEditorViewModel::searchInDocument,
                    modifier = Modifier.defaultMinSize(minWidth = 160.dp)
                        .focusRequester(focusRequester)
                        .padding(12.dp)
                        .background(WriteopiaTheme.colorScheme.cardBg, shape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        .padding(8.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                )

                Icon(
                    imageVector = WrIcons.close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .background(WriteopiaTheme.colorScheme.cardBg, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(4.dp)
                        .clickable {
                            noteEditorViewModel.hideSearch()
                        }
                )

                LaunchedEffect(key1 = Unit) {
                    focusRequester.requestFocus()
                }
            }
        }

        SideEditorOptions(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 40.dp)
                .align(Alignment.TopEnd),
            isDarkTheme,
            fontStyleSelected = { noteEditorViewModel.fontFamily },
            currentModel = noteEditorViewModel.currentModel,
            models = noteEditorViewModel.models,
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
            selectModel = noteEditorViewModel::selectModel,
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
