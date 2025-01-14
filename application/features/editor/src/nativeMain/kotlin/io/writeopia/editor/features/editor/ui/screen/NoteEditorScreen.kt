package io.writeopia.editor.features.editor.ui.screen

// import androidx.compose.ui.tooling.preview.Preview
// import io.writeopia.appresourcers.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.common.utils.ColorUtils
import io.writeopia.editor.configuration.ui.HeaderEdition
import io.writeopia.editor.configuration.ui.NoteGlobalActionsMenu
import io.writeopia.editor.features.editor.ui.TextEditor
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.editor.viewmodel.ShareDocument
import io.writeopia.editor.model.EditState
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.ui.components.EditionScreen
import io.writeopia.ui.drawer.factory.DefaultDrawersNative
import kotlinx.coroutines.flow.StateFlow

const val NAVIGATE_BACK_TEST_TAG = "NoteEditorScreenNavigateBack"
const val NOTE_EDITION_SCREEN_TITLE_TEST_TAG = "noteEditionScreenTitle"

@Composable
internal fun NoteEditorScreen(
    documentId: String?,
    title: String?,
    noteEditorViewModel: NoteEditorViewModel,
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemBarDefaultColor = MaterialTheme.colorScheme.primary

    if (documentId != null) {
        noteEditorViewModel.loadDocument(documentId)
    } else {
        noteEditorViewModel.createNewDocument(
            GenerateId.generate(),
            "Untitled",
//            stringResource(R.string.untitled)
        )
    }

    val document = noteEditorViewModel.documentToShareInfo.collectAsState().value

    if (document != null) {
        LaunchedEffect(document.hashCode()) {
            shareDocument(document)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar(
                titleState = noteEditorViewModel.currentTitle,
                editableState = noteEditorViewModel.isEditable,
                navigationClick = {
                    noteEditorViewModel.handleBackAction(navigateBack = navigateBack)
                },
                shareDocument = noteEditorViewModel::onMoreOptionsClick,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxSize()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TextEditor(
                    noteEditorViewModel,
                    DefaultDrawersNative,
                    Modifier.weight(1F).padding(horizontal = 6.dp),
                    keyFn = { index, drawStory -> drawStory.mobileKey + index }
                )

                BottomScreen(
                    noteEditorViewModel.isEditState,
                    noteEditorViewModel::undo,
                    noteEditorViewModel::redo,
                    noteEditorViewModel.canUndo,
                    noteEditorViewModel.canRedo,
                    noteEditorViewModel::deleteSelection,
                    noteEditorViewModel::clearSelections
                )
            }

            val headerEdition by noteEditorViewModel.editHeader.collectAsState()

            HeaderEdition(
                modifier = Modifier.fillMaxWidth(),
                availableColors = ColorUtils.headerColors(),
                onColorSelection = noteEditorViewModel::onHeaderColorSelection,
                outsideClick = noteEditorViewModel::onHeaderEditionCancel,
                visibilityState = headerEdition
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
                    onShareJson = { noteEditorViewModel.shareDocumentInJson() },
                    onShareMd = { noteEditorViewModel.shareDocumentInMarkdown() },
                    changeFontFamily = noteEditorViewModel::changeFontFamily,
                    selectedState = noteEditorViewModel.fontFamily
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
    modifier: Modifier = Modifier,
    navigationClick: () -> Unit = {},
    shareDocument: () -> Unit
) {
    val title by titleState.collectAsState()
    val isEditable by editableState.collectAsState()

    TopAppBar(
        modifier = modifier.height(44.dp),
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
                    overflow = TextOverflow.Ellipsis
                )

                if (!isEditable) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.onBackground
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
                        .semantics {
                            testTag = NAVIGATE_BACK_TEST_TAG
                        }
                        .clip(CircleShape)
                        .clickable(onClick = navigationClick)
                        .padding(10.dp),
                    imageVector = WrIcons.backArrowAndroid,
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
                imageVector = Icons.Default.MoreVert,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onBackground
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

private fun shareDocument(shareDocument: ShareDocument) {}

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
    unDo: () -> Unit = {},
    reDo: () -> Unit = {},
    canUndo: StateFlow<Boolean>,
    canRedo: StateFlow<Boolean>,
    deleteSelection: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val edit by editState.collectAsState()

    val containerModifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
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
//                InputScreen(
//                    modifier = containerModifier,
//                    onBackPress = unDo,
//                    onForwardPress = reDo,
//                    canUndoState = canUndo,
//                    canRedoState = canRedo,
//                )
            }

            EditState.SELECTED_TEXT -> {
                EditionScreen(
                    modifier = containerModifier,
                    onDelete = deleteSelection,
                    onClose = onClose
                )
            }
        }
    }
}
