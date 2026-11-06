package io.writeopia.editor.features.editor.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.ui.AiTaskIndicator
import io.writeopia.common.utils.file.fileChooserLoad
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.commonui.folders.MoveSelectionDialog
import io.writeopia.editor.features.editor.ui.ai.AiCommandBar
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.SideEditorOptions
import io.writeopia.editor.features.editor.ui.publish.PremiumOnlyDialog
import io.writeopia.editor.features.editor.ui.search.InDocumentSearchBar
import io.writeopia.editor.features.editor.ui.publish.PublishDialog
import io.writeopia.editor.features.editor.ui.tags.AddDocumentTagDialog
import io.writeopia.editor.features.editor.ui.tags.DocumentTagsDialog
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.editor.viewmodel.SideMenuTab
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.factory.DrawersFactory
import io.writeopia.writingagent.actions.WritingAgentUi
import io.writeopia.writingagent.ui.FolderSuggestionDialog
import io.writeopia.writingagent.ui.TagSuggestionDialog
import io.writeopia.writingagent.ui.WritingSuggestionsBox

@Composable
fun DesktopNoteEditorScreen(
    isDarkTheme: Boolean,
    documentId: String?,
    noteEditorViewModel: NoteEditorViewModel,
    drawersFactory: DrawersFactory,
    onPresentationClick: () -> Unit,
    showPresentation: Boolean = false,
    onDocumentLinkClick: (String) -> Unit,
    onDrawingClick: (StoryStep, String) -> Unit = { _, _ -> },
    onNewDrawingClick: () -> Unit = {},
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
            onClick = {
                noteEditorViewModel.clearSelections()
                noteEditorViewModel.changeSideMenu(SideMenuTab.NONE)
            },
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
                    onDrawingClick = onDrawingClick,
                    modifier = Modifier.padding(start = 30.dp, end = 30.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        )
                )
            }
        )

        var showDeleteConfirmation by remember {
            mutableStateOf(false)
        }

        InDocumentSearchBar(noteEditorViewModel)

        SideEditorOptions(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 40.dp),
            alignment = Alignment.TopEnd,
            isDarkTheme = isDarkTheme,
            fontStyleSelected = { noteEditorViewModel.fontFamily },
            currentModel = noteEditorViewModel.currentModel,
            models = noteEditorViewModel.models,
            isEditableState = noteEditorViewModel.isEditable,
            isFavorite = noteEditorViewModel.notFavorite,
            selectedMetadataState = noteEditorViewModel.selectionMetadataState,
            sideMenuTabState = noteEditorViewModel.sideMenuTabState,
            hasSelectedLinesState = noteEditorViewModel.hasSelectedLines,
            boldClick = noteEditorViewModel::onAddSpanClick,
            setEditable = noteEditorViewModel::toggleEditable,
            checkItemClick = noteEditorViewModel::onAddCheckListClick,
            listItemClick = noteEditorViewModel::onAddListItemClick,
            codeBlockClick = noteEditorViewModel::onAddCodeBlockClick,
            spreadsheetClick = { noteEditorViewModel.onAddSpreadsheetClick(3) },
            highLightBlockClick = noteEditorViewModel::toggleHighLightBlock,
            cardBlockClick = noteEditorViewModel::toggleCardBlock,
            onPresentationClick = onPresentationClick,
            showPresentation = showPresentation,
            changeFontFamily = noteEditorViewModel::changeFontFamily,
            addImage = noteEditorViewModel::addImage,
            exportMarkdown = noteEditorViewModel::exportMarkdown,
            exportJson = noteEditorViewModel::exportJson,
            moveToClick = {
                showFolderSelection = true
            },
            aiCommand = noteEditorViewModel::aiCommand,
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
            changeSideMenuTab = noteEditorViewModel::changeSideMenu,
            titleClick = noteEditorViewModel::titleClick,
            onDrawingClick = onNewDrawingClick,
            onPublishClick = noteEditorViewModel::showPublishDialog,
            documentTagsState = noteEditorViewModel.documentTags,
            onViewTagsClick = noteEditorViewModel::showTagsDialog,
            onAddTagClick = noteEditorViewModel::showAddTagDialog,
            onRemoveTagClick = noteEditorViewModel::removeTagFromDocument,
            documentIconState = noteEditorViewModel.documentIcon,
            onDocumentIconSelect = noteEditorViewModel::changeDocumentIcon,
            onRemoveDocumentIcon = noteEditorViewModel::removeDocumentIcon,
        )

        // The command field comes with the AI options and goes with them.
        val sideMenuTab by noteEditorViewModel.sideMenuTabState.collectAsState()

        AnimatedVisibility(
            visible = isEditable && sideMenuTab == SideMenuTab.AI,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            AiCommandBar(
                onRun = noteEditorViewModel::aiCommand,
                modifier = Modifier.padding(start = 16.dp, end = 72.dp, bottom = 16.dp)
            )
        }

        if (showDeleteConfirmation) {
            DeleteConfirmationDialog(
                onConfirmation = {
                    showDeleteConfirmation = false
                    noteEditorViewModel.deleteDocument(onDeleted = onDocumentDelete)
                },
                onCancel = {
                    showDeleteConfirmation = false
                }
            )
        }

        val isPublished by noteEditorViewModel.isDocumentPublished.collectAsState()
        val notFavorite by noteEditorViewModel.notFavorite.collectAsState()
        val isFavorite = !notFavorite

        if (!isEditable || isFavorite || isPublished) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isEditable) {
                    Icon(
                        imageVector = WrIcons.lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isFavorite) {
                    Icon(
                        imageVector = WrIcons.favorites,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isPublished) {
                    Icon(
                        imageVector = WrIcons.published,
                        contentDescription = "Published",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (showFolderSelection) {
            MoveSelectionDialog(
                loadFolders = noteEditorViewModel::workspaceFolders,
                excludedIds = emptySet(),
                currentParentId = noteEditorViewModel.documentFolderId(),
                onMove = { folderId ->
                    showFolderSelection = false
                    noteEditorViewModel.moveToFolder(folderId)
                },
                onDismissRequest = { showFolderSelection = false }
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

        val showPublishDialog by noteEditorViewModel.showPublishDialog.collectAsState()
        val publishLoading by noteEditorViewModel.publishLoading.collectAsState()

        if (showPublishDialog && documentId != null) {
            PublishDialog(
                documentId = documentId,
                isPublished = isPublished,
                isLoading = publishLoading,
                onDismiss = noteEditorViewModel::hidePublishDialog,
                onPublishAndView = noteEditorViewModel::publishDocument,
                onUnpublish = noteEditorViewModel::unpublishDocument,
                onCopyLink = noteEditorViewModel::copyPublishLink
            )
        }

        val showTagsDialog by noteEditorViewModel.showTagsDialog.collectAsState()
        if (showTagsDialog) {
            val documentTags by noteEditorViewModel.documentTags.collectAsState()
            val isEditableForTags by noteEditorViewModel.isEditable.collectAsState()
            DocumentTagsDialog(
                tags = documentTags,
                isEditable = isEditableForTags,
                onRemove = noteEditorViewModel::removeTagFromDocument,
                onAddTag = {
                    noteEditorViewModel.hideTagsDialog()
                    noteEditorViewModel.showAddTagDialog()
                },
                onDismiss = noteEditorViewModel::hideTagsDialog
            )
        }

        val showAddTagDialog by noteEditorViewModel.showAddTagDialog.collectAsState()
        if (showAddTagDialog) {
            val workspaceTags by noteEditorViewModel.workspaceTags.collectAsState()
            val documentTagIds by noteEditorViewModel.writeopiaManager.documentTagIds.collectAsState()
            val canSuggestWithAi by noteEditorViewModel.canSuggestTagWithAi.collectAsState()
            val aiDraft by noteEditorViewModel.aiTagDraft.collectAsState()
            AddDocumentTagDialog(
                workspaceTags = workspaceTags,
                documentTagIds = documentTagIds,
                canSuggestWithAi = canSuggestWithAi,
                aiDraft = aiDraft,
                onPickTag = noteEditorViewModel::addTagToDocument,
                onCreateTag = noteEditorViewModel::createTag,
                onSuggestWithAi = noteEditorViewModel::suggestTagWithAi,
                onDismiss = noteEditorViewModel::hideAddTagDialog
            )
        }

        val showPremiumDialog by noteEditorViewModel.showPremiumDialog.collectAsState()
        if (showPremiumDialog) {
            PremiumOnlyDialog(onDismiss = noteEditorViewModel::hidePremiumDialog)
        }

        val aiTaskManager = AiTaskManager.singleton()

        AiTaskIndicator(
            tasksFlow = aiTaskManager.tasks,
            onClearFinished = aiTaskManager::clearFinishedTasks,
            onCancelTask = aiTaskManager::cancelTask,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp)
        )

        noteEditorViewModel.writingAgent?.let { agent ->
            val agentUi = remember(onNewDrawingClick) {
                object : WritingAgentUi {
                    override fun pickImage(onPicked: (String) -> Unit) {
                        fileChooserLoad("")?.let(onPicked)
                    }

                    override fun openDrawing() = onNewDrawingClick()
                }
            }

            WritingSuggestionsBox(
                controller = agent,
                ui = agentUi,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 16.dp)
            )

            FolderSuggestionDialog(controller = agent)
            TagSuggestionDialog(controller = agent)
        }
    }
}
