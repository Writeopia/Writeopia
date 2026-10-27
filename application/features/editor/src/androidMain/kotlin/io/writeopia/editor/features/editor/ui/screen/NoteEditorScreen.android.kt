package io.writeopia.editor.features.editor.ui.screen

// import androidx.compose.ui.tooling.preview.Preview
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.commonui.folders.MoveSelectionSheet
import io.writeopia.editor.configuration.ui.DocumentIconSheet
import io.writeopia.editor.configuration.ui.HeaderEdition
import io.writeopia.editor.configuration.ui.NoteGlobalActionsMenu
import io.writeopia.editor.features.editor.ui.TextEditor
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.SideEditorOptions
import io.writeopia.editor.features.editor.ui.mobile.MobileAiSheet
import io.writeopia.editor.features.editor.ui.publish.PremiumOnlyDialog
import io.writeopia.editor.features.editor.ui.publish.PublishDialog
import io.writeopia.editor.features.editor.ui.tags.AddDocumentTagSheet
import io.writeopia.editor.features.editor.ui.tags.DocumentTagsDialog
import io.writeopia.editor.features.editor.viewmodel.AiTargetMode
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.editor.viewmodel.ShareDocument
import io.writeopia.editor.input.InputScreen
import io.writeopia.editor.model.EditState
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.Tag
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.components.EditionScreen
import io.writeopia.ui.drawer.factory.DefaultDrawersAndroid
import io.writeopia.ui.model.SelectionMetadata
import io.writeopia.writingagent.actions.WritingAgentUi
import io.writeopia.writingagent.ui.FolderSuggestionDialog
import io.writeopia.writingagent.ui.TagSuggestionDialog
import io.writeopia.writingagent.ui.WritingSuggestionsBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val NAVIGATE_BACK_TEST_TAG = "NoteEditorScreenNavigateBack"
const val NOTE_EDITION_SCREEN_TITLE_TEST_TAG = "noteEditionScreenTitle"

@Composable
internal fun NoteEditorScreen(
    isDarkTheme: Boolean,
    documentId: String?,
    title: String?,
    noteEditorViewModel: NoteEditorViewModel,
    navigateBack: () -> Unit,
    onDocumentLinkClick: (String) -> Unit,
    onNewDrawingClick: () -> Unit = {},
    onNewImageClick: () -> Unit = {},
    onDrawingClick: (StoryStep, String) -> Unit = { _, _ -> },
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    isWideLayout: Boolean = false,
    onPresentationClick: () -> Unit = {},
    showPresentation: Boolean = false,
    onDocumentDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler {
        noteEditorViewModel.handleBackAction(navigateBack = {
            navigateBack()
        })
    }

    val context = LocalContext.current

    // Set by the writing agent when it asks for an image, so the picked one lands at its paragraph.
    var onAgentImagePicked by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val agentCallback = onAgentImagePicked
        onAgentImagePicked = null

        uri?.let {
            // Copy the image to app storage for persistence
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val destinationFile = java.io.File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (agentCallback != null) {
                agentCallback(destinationFile.absolutePath)
            } else {
                noteEditorViewModel.addImage(destinationFile.absolutePath)
            }
        }
    }

    if (documentId != null) {
        noteEditorViewModel.loadDocument(documentId)
    } else {
        noteEditorViewModel.createNewDocument(
            GenerateId.generate(),
            "Untitled",
//            stringResource(R.string.untitled)
        )
    }

    var showAiDialog by remember { mutableStateOf(false) }
    var showSelectedLinesAiDialog by remember { mutableStateOf(false) }
    var showFolderSelection by remember { mutableStateOf(false) }
    var showIconSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val document = noteEditorViewModel.documentToShareInfo.collectAsState().value

    if (document != null) {
        LaunchedEffect(document.hashCode()) {
            shareDocument(context, document)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
                TopBar(
                    titleState = noteEditorViewModel.currentTitle,
                    editableState = noteEditorViewModel.isEditable,
                    publishedState = noteEditorViewModel.isDocumentPublished,
                    notFavoriteState = noteEditorViewModel.notFavorite,
                    navigationClick = {
                        noteEditorViewModel.handleBackAction(navigateBack = navigateBack)
                    },
                    shareDocument = noteEditorViewModel::onMoreOptionsClick
                )
            }
        },
    ) { paddingValues ->
        val bottomPadding by animateDpAsState(
            targetValue = if (isToolbarVisible) 80.dp else 8.dp,
            label = "bottomPadding"
        )
        val scrollModifier = if (nestedScrollConnection != null) {
            Modifier
                .padding(paddingValues)
                .padding(bottom = bottomPadding)
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .imePadding()
        } else {
            Modifier
                .padding(paddingValues)
                .padding(bottom = bottomPadding)
                .fillMaxSize()
                .imePadding()
        }
        Box(modifier = scrollModifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextEditor(
                    isDarkTheme = isDarkTheme,
                    noteEditorViewModel,
                    DefaultDrawersAndroid,
                    Modifier
                        .weight(1F)
                        .padding(horizontal = 6.dp),
                    keyFn = { drawStory -> drawStory.mobileKey },
                    onDocumentLinkClick = onDocumentLinkClick,
                    onDrawingClick = onDrawingClick
                )

                if (!isWideLayout) {
                    BottomScreen(
                        noteEditorViewModel.isEditState,
                        metadataState = noteEditorViewModel.writeopiaManager.selectionMetadataState,
                        isDarkTheme = isDarkTheme,
                        noteEditorViewModel::undo,
                        noteEditorViewModel::redo,
                        noteEditorViewModel.canUndo,
                        noteEditorViewModel.canRedo,
                        noteEditorViewModel::onAddSpanClick,
                        noteEditorViewModel::deleteSelection,
                        noteEditorViewModel::copySelection,
                        noteEditorViewModel::cutSelection,
                        noteEditorViewModel::clearSelections,
                        noteEditorViewModel::onAddCheckListClick,
                        noteEditorViewModel::onAddListItemClick,
                        noteEditorViewModel::onAddCodeBlockClick,
                        noteEditorViewModel::addPage,
                        noteEditorViewModel::titleClick,
                        onDrawingClick = onNewDrawingClick,
                        onImageClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSpreadsheetClick = { noteEditorViewModel.onAddSpreadsheetClick(3) },
                        onBoxClick = noteEditorViewModel::toggleHighLightBlock,
                        onCardClick = noteEditorViewModel::toggleCardBlock,
                        onAiClick = { showAiDialog = true },
                        onSelectedLinesAiClick = { showSelectedLinesAiDialog = true },
                        isWorkspaceOfflineState = noteEditorViewModel.isWorkspaceOffline
                    )
                }
            }

            if (isWideLayout) {
                SideEditorOptions(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 40.dp),
                    alignment = Alignment.CenterEnd,
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
                    onImagePickRequest = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    exportMarkdown = noteEditorViewModel::exportMarkdown,
                    exportJson = noteEditorViewModel::exportJson,
                    moveToClick = { showFolderSelection = true },
                    aiCommand = noteEditorViewModel::aiCommand,
                    addPage = noteEditorViewModel::addPage,
                    deleteDocument = { showDeleteConfirmation = true },
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
            }

            // Opened by the side menu on wide layouts and by the global actions menu on phones.
            if (showDeleteConfirmation) {
                DeleteConfirmationDialog(
                    onConfirmation = {
                        showDeleteConfirmation = false
                        noteEditorViewModel.deleteDocument(onDeleted = onDocumentDelete)
                    },
                    onCancel = { showDeleteConfirmation = false }
                )
            }

            if (showIconSheet) {
                DocumentIconSheet(
                    documentIconState = noteEditorViewModel.documentIcon,
                    onIconSelect = noteEditorViewModel::changeDocumentIcon,
                    onRemoveIcon = noteEditorViewModel::removeDocumentIcon,
                    onDismissRequest = { showIconSheet = false }
                )
            }

            if (showFolderSelection) {
                MoveSelectionSheet(
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

            noteEditorViewModel.writingAgent?.let { agent ->
                val agentUi = remember(onNewDrawingClick) {
                    object : WritingAgentUi {
                        override fun pickImage(onPicked: (String) -> Unit) {
                            onAgentImagePicked = onPicked
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }

                        override fun openDrawing() = onNewDrawingClick()
                    }
                }

                WritingSuggestionsBox(
                    controller = agent,
                    ui = agentUi,
                    mobile = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = if (isWideLayout) 16.dp else 72.dp)
                )

                FolderSuggestionDialog(controller = agent)
                TagSuggestionDialog(controller = agent)
            }

            val headerEdition by noteEditorViewModel.editHeader.collectAsState()

            HeaderEdition(
                noteEditorViewModel = noteEditorViewModel,
                visibilityState = headerEdition,
                onDismissRequest = noteEditorViewModel::onHeaderEditionCancel
            )

            val showGlobalMenu by noteEditorViewModel.showGlobalMenu.collectAsState()

            AnimatedVisibility(
                visible = showGlobalMenu,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.8F,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset.VisibilityThreshold
                    ),
                    initialOffsetY = { fullHeight -> fullHeight }
                ),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight }
                )
            ) {
                NoteGlobalActionsMenu(
                    isEditableState = noteEditorViewModel.isEditable,
                    setEditable = noteEditorViewModel::toggleEditable,
                    isFavoriteState = noteEditorViewModel.notFavorite,
                    toggleFavorite = noteEditorViewModel::toggleFavorite,
                    onMoveToClick = { showFolderSelection = true },
                    onDeleteClick = { showDeleteConfirmation = true },
                    onShareJson = { noteEditorViewModel.shareDocumentInJson() },
                    onShareMd = { noteEditorViewModel.shareDocumentInMarkdown() },
                    changeFontFamily = noteEditorViewModel::changeFontFamily,
                    selectedState = noteEditorViewModel.fontFamily,
                    showPresentation = showPresentation,
                    onPresentationClick = onPresentationClick,
                    onPublishClick = noteEditorViewModel::showPublishDialog,
                    documentTagsState = noteEditorViewModel.documentTags,
                    onViewTagsClick = noteEditorViewModel::showTagsDialog,
                    onAddTagClick = noteEditorViewModel::showAddTagDialog,
                    onRemoveTagClick = noteEditorViewModel::removeTagFromDocument,
                    onIconClick = { showIconSheet = true },
                )
            }

            val showPublishDialog by noteEditorViewModel.showPublishDialog.collectAsState()
            val isDocumentPublished by noteEditorViewModel.isDocumentPublished.collectAsState()
            val publishLoading by noteEditorViewModel.publishLoading.collectAsState()

            if (showPublishDialog && documentId != null) {
                PublishDialog(
                    documentId = documentId,
                    isPublished = isDocumentPublished,
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
                AddDocumentTagSheet(
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

            if (showAiDialog) {
                MobileAiSheet(
                    onDismissRequest = { showAiDialog = false },
                    hasSelectedLinesState = noteEditorViewModel.hasSelectedLines,
                    aiSummary = noteEditorViewModel::aiSummary,
                    aiActionPoints = noteEditorViewModel::aiActionPoints,
                    aiFaq = noteEditorViewModel::aiFaq,
                    aiTags = noteEditorViewModel::aiTags,
                    aiCommand = noteEditorViewModel::aiCommand,
                )
            }

            if (showSelectedLinesAiDialog) {
                MobileAiSheet(
                    onDismissRequest = { showSelectedLinesAiDialog = false },
                    hasSelectedLinesState = noteEditorViewModel.hasSelectedLines,
                    aiSummary = noteEditorViewModel::aiSummary,
                    aiActionPoints = noteEditorViewModel::aiActionPoints,
                    aiFaq = noteEditorViewModel::aiFaq,
                    aiTags = noteEditorViewModel::aiTags,
                    aiCommand = noteEditorViewModel::aiCommand,
                    fixedTargetMode = AiTargetMode.SELECTED_LINES,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    titleState: StateFlow<String>,
    editableState: StateFlow<Boolean>,
    publishedState: StateFlow<Boolean>,
    notFavoriteState: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
    navigationClick: () -> Unit = {},
    shareDocument: () -> Unit
) {
    val title by titleState.collectAsState()
    val isEditable by editableState.collectAsState()
    val isPublished by publishedState.collectAsState()
    val notFavorite by notFavoriteState.collectAsState()
    val isFavorite = !notFavorite

    TopAppBar(
        modifier = modifier.height(110.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.semantics {
                        testTag = NOTE_EDITION_SCREEN_TITLE_TEST_TAG
                    },
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Center)
                )

                if (!isEditable || isFavorite || isPublished) {
                    Spacer(modifier.width(4.dp))
                }

                if (!isEditable) {
                    Icon(
                        imageVector = WrIcons.lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (isFavorite) {
                    if (!isEditable) {
                        Spacer(modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = WrIcons.favorites,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (isPublished) {
                    if (!isEditable || isFavorite) {
                        Spacer(modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = WrIcons.published,
                        contentDescription = "Published",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        },
        navigationIcon = {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier
                        .semantics { testTag = NAVIGATE_BACK_TEST_TAG }
                        .clip(CircleShape)
                        .clickable(onClick = navigationClick)
                        .padding(10.dp),
                    imageVector = WrIcons.backArrowMobile,
                    contentDescription = "",
//                    stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        actions = {
            Icon(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = shareDocument)
                    .padding(9.dp),
                imageVector = WrIcons.moreVert,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onBackground
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

private fun shareDocument(context: Context, shareDocument: ShareDocument) {
    val intent = Intent(Intent.ACTION_SEND)
        .apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TEXT, shareDocument.content)
            putExtra(Intent.EXTRA_TITLE, shareDocument.title)
            action = Intent.ACTION_SEND
            this.type = shareDocument.type
        }

    context.startActivity(
        Intent.createChooser(intent, "Export Document")
    )
}

// @Preview
// @Composable
// private fun TopBar_Preview() {
//    Box(modifier = Modifier.background(Color.LightGray)) {
//        TopBar(titleState = MutableStateFlow("Title"), shareDocument = {})
//    }
// }

@Composable
private fun BottomScreen(
    editState: StateFlow<EditState>,
    metadataState: Flow<Set<SelectionMetadata>>,
    isDarkTheme: Boolean,
    unDo: () -> Unit = {},
    reDo: () -> Unit = {},
    canUndo: StateFlow<Boolean>,
    canRedo: StateFlow<Boolean>,
    onSpanSelected: (Span) -> Unit = {},
    deleteSelection: () -> Unit = {},
    copySelection: () -> Unit = {},
    cutSelection: () -> Unit = {},
    onClose: () -> Unit = {},
    onCheckItem: () -> Unit = {},
    onListItem: () -> Unit = {},
    onCodeBlock: () -> Unit = {},
    onAddPage: () -> Unit = {},
    titleClick: (Tag) -> Unit,
    onDrawingClick: () -> Unit = {},
    onImageClick: () -> Unit = {},
    onSpreadsheetClick: () -> Unit = {},
    onBoxClick: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
    onSelectedLinesAiClick: () -> Unit = {},
    isWorkspaceOfflineState: StateFlow<Boolean> = MutableStateFlow(false)
) {
    val edit by editState.collectAsState()

    val containerModifier = Modifier
        .fillMaxWidth()
        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.primary)

    AnimatedContent(
        targetState = edit,
        label = "bottomSheetAnimation",
        transitionSpec = {
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.65F),
                initialOffsetY = { fullHeight -> fullHeight }
            ) togetherWith slideOutVertically(
                animationSpec = tween(durationMillis = 130),
                targetOffsetY = { fullHeight -> fullHeight }
            )
        }
    ) { editStateAnimated ->
        when (editStateAnimated) {
            EditState.TEXT -> {
                InputScreen(
                    modifier = containerModifier,
                    isDarkTheme = isDarkTheme,
                    metadataState = metadataState,
                    onAddSpan = onSpanSelected,
                    onBackPress = unDo,
                    onForwardPress = reDo,
                    canUndoState = canUndo,
                    canRedoState = canRedo,
                    onDrawingClick = onDrawingClick,
                    onImageClick = onImageClick,
                    onSpreadsheetClick = onSpreadsheetClick,
                    onAiClick = onAiClick,
                    isWorkspaceOfflineState = isWorkspaceOfflineState
                )
            }

            EditState.SELECTED_TEXT -> {
                EditionScreen(
                    modifier = containerModifier,
                    metadataState = metadataState,
                    highlightButtonColor = WriteopiaTheme.colorScheme.optionsSelector,
                    onSpanClick = onSpanSelected,
                    onDelete = deleteSelection,
                    onCopy = copySelection,
                    onCut = cutSelection,
                    onClose = onClose,
                    checkboxClick = onCheckItem,
                    listItemClick = onListItem,
                    codeBlockClick = onCodeBlock,
                    onBoxClick = onBoxClick,
                    onCardClick = onCardClick,
                    onAiClick = onSelectedLinesAiClick,
                    onAddPage = onAddPage,
                    titleClick = titleClick,
                    isWorkspaceOfflineState = isWorkspaceOfflineState
                )
            }
        }
    }
}
