package io.writeopia.editor.features.editor.ui.screen

// import androidx.compose.ui.tooling.preview.Preview
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import io.writeopia.common.utils.colors.ColorUtils
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.configuration.ui.HeaderEdition
import io.writeopia.editor.configuration.ui.NoteGlobalActionsMenu
import io.writeopia.editor.features.editor.ui.TextEditor
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.editor.features.editor.viewmodel.ShareDocument
import io.writeopia.editor.input.InputScreen
import io.writeopia.editor.model.EditState
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.components.EditionScreen
import io.writeopia.ui.drawer.factory.DefaultDrawersAndroid
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
    modifier: Modifier = Modifier,
) {
    val systemUiController = rememberSystemUiController()
    val systemBarColor = MaterialTheme.colorScheme.background
    val systemBarDefaultColor = MaterialTheme.colorScheme.primary

    DisposableEffect(systemUiController) {
        systemUiController.setStatusBarColor(color = systemBarColor)
        onDispose {}
    }

    BackHandler {
        noteEditorViewModel.handleBackAction(navigateBack = {
            systemUiController.setStatusBarColor(color = systemBarDefaultColor)
            navigateBack()
        })
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

    val context = LocalContext.current
    val document = noteEditorViewModel.documentToShareInfo.collectAsState().value

    if (document != null) {
        LaunchedEffect(document.hashCode()) {
            shareDocument(context, document)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar(
                titleState = noteEditorViewModel.currentTitle,
                editableState = noteEditorViewModel.isEditable,
                navigationClick = {
                    systemUiController.setStatusBarColor(color = systemBarDefaultColor)
                    noteEditorViewModel.handleBackAction(navigateBack = navigateBack)
                },
                shareDocument = noteEditorViewModel::onMoreOptionsClick
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
                    isDarkTheme = isDarkTheme,
                    noteEditorViewModel,
                    DefaultDrawersAndroid,
                    Modifier
                        .weight(1F)
                        .padding(horizontal = 6.dp),
                    keyFn = { drawStory -> drawStory.mobileKey },
                    onDocumentLinkClick = onDocumentLinkClick
                )

                BottomScreen(
                    noteEditorViewModel.isEditState,
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
                    noteEditorViewModel::onAddListItemClick
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
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Center)
                )

                if (!isEditable) {
                    Spacer(modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock",
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
    onListItem: () -> Unit = {}
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
                InputScreen(
                    modifier = containerModifier,
                    onAddSpan = onSpanSelected,
                    onBackPress = unDo,
                    onForwardPress = reDo,
                    canUndoState = canUndo,
                    canRedoState = canRedo,
                )
            }

            EditState.SELECTED_TEXT -> {
                EditionScreen(
                    modifier = containerModifier,
                    onSpanClick = onSpanSelected,
                    onDelete = deleteSelection,
                    onCopy = copySelection,
                    onCut = cutSelection,
                    onClose = onClose,
                    checkboxClick = onCheckItem,
                    listItemClick = onListItem
                )
            }
        }
    }
}

// @Preview
// @Composable
// private fun BottomScreenTextPreview() {
//    BottomScreen(
//        editState = MutableStateFlow(EditState.TEXT),
//        canUndo = MutableStateFlow(true),
//        canRedo = MutableStateFlow(true),
//    )
// }
//
// @Preview
// @Composable
// private fun BottomScreenSelectedPreview() {
//    BottomScreen(
//        editState = MutableStateFlow(EditState.SELECTED_TEXT),
//        canUndo = MutableStateFlow(true),
//        canRedo = MutableStateFlow(true),
//    )
// }
