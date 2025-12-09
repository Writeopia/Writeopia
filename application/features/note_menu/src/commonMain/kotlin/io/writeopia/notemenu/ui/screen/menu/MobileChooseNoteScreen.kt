package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.notemenu.ui.screen.configuration.molecules.MobileConfigurationsMenu
import io.writeopia.notemenu.ui.screen.configuration.molecules.NotesSelectionMenu
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.notemenu.ui.screen.documents.ADD_NOTE_TEST_TAG
import io.writeopia.notemenu.ui.screen.documents.NotesCardsScreen
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.UserState
import io.writeopia.notemenu.viewmodel.toNumberDesktop
import io.writeopia.ui.draganddrop.target.DraggableScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MobileChooseNoteScreen(
    isDarkTheme: Boolean,
    chooseNoteViewModel: ChooseNoteViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    navigateToNote: (String, String) -> Unit,
    newNote: () -> Unit,
    navigateToAccount: () -> Unit,
    navigateToNotes: (NotesNavigation) -> Unit,
    navigationBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(key1 = "refresh", block = {
        chooseNoteViewModel.requestUser()
        chooseNoteViewModel.syncFolderWithCloud()
//        chooseNoteViewModel.requestDocuments(false)
    })

    val hasSelectedNotes by chooseNoteViewModel.hasSelectedNotes.collectAsState()
    val editState by chooseNoteViewModel.editState.collectAsState()
    val folderEdit = chooseNoteViewModel.editFolderState.collectAsState().value

    val showFab by derivedStateOf { !editState && !hasSelectedNotes }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopBar(
                    titleState = chooseNoteViewModel.userName,
                    accountClick = navigateToAccount,
                    menuClick = chooseNoteViewModel::showEditMenu
                )
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = chooseNoteViewModel::showAddMenu)
                }
            },
            bottomBar = navigationBar
        ) { paddingValues ->
            DraggableScreen(modifier = Modifier.padding(paddingValues)) {
                Content(
                    isDarkTheme = isDarkTheme,
                    chooseNoteViewModel = chooseNoteViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    loadNote = navigateToNote,
                    selectionListener = chooseNoteViewModel::onDocumentSelected,
                    paddingValues = PaddingValues(),
                    newNote = newNote,
                    navigateToNotes = navigateToNotes,
                )

                val selected = chooseNoteViewModel.notesArrangement.toNumberDesktop()

                MobileConfigurationsMenu(
                    selected = selected,
                    visibilityState = editState,
                    outsideClick = chooseNoteViewModel::cancelEditMenu,
                    staggeredGridOptionClick = chooseNoteViewModel::staggeredGridArrangementSelected,
                    gridOptionClick = chooseNoteViewModel::gridArrangementSelected,
                    listOptionClick = chooseNoteViewModel::listArrangementSelected,
                    sortingSelected = chooseNoteViewModel::sortingSelected,
                    sortingState = chooseNoteViewModel.orderByState
                )

                val titlesToDelete by chooseNoteViewModel.titlesToDelete.collectAsState()

                if (titlesToDelete.isNotEmpty()) {
                    DeleteConfirmationDialog(
                        onConfirmation = chooseNoteViewModel::deleteSelectedNotes,
                        onCancel = chooseNoteViewModel::cancelDeletion,
                    )
                }

                if (folderEdit != null) {
                    EditFileScreen(
                        folderEdit = folderEdit,
                        onDismissRequest = chooseNoteViewModel::stopEditingFolder,
                        deleteFolder = chooseNoteViewModel::deleteFolder,
                        editFolder = chooseNoteViewModel::updateFolder
                    )
                }

                NotesSelectionMenu(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                    visibilityState = hasSelectedNotes,
                    onDelete = chooseNoteViewModel::requestPermissionToDeleteSelection,
                    onCopy = chooseNoteViewModel::copySelectedNotes,
                    onFavorite = chooseNoteViewModel::favoriteSelectedNotes,
                    onSummary = chooseNoteViewModel::summarizeDocuments,
                    onClose = chooseNoteViewModel::clearSelection,
                    shape = MaterialTheme.shapes.large
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// @Preview(backgroundColor = 0xFF000000)
@Composable
private fun TopBar(
    titleState: StateFlow<UserState<String>> = MutableStateFlow(UserState.ConnectedUser("Title")),
    accountClick: () -> Unit = {},
    menuClick: () -> Unit = {}
) {
    val title = titleState.collectAsState().value

    TopAppBar(
        title = {
            Row(
                modifier = Modifier.clickable(onClick = accountClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val userIcon = WrIcons.person

                val fallbackPainter = rememberVectorPainter(
                    defaultWidth = userIcon.defaultWidth,
                    defaultHeight = userIcon.defaultHeight,
                    viewportWidth = userIcon.viewportWidth,
                    viewportHeight = userIcon.viewportHeight,
                    name = userIcon.name,
                    tintColor = MaterialTheme.colorScheme.onPrimary,
                    tintBlendMode = userIcon.tintBlendMode,
                    autoMirror = userIcon.autoMirror,
                ) { _, _ ->
                    RenderVectorGroup(group = userIcon.root)
                }

//                AsyncImage(
//                    modifier = Modifier
//                        .clip(shape = CircleShape)
//                        .background(MaterialTheme.colorScheme.secondary)
//                        .size(48.dp)
//                        .padding(10.dp),
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data("")
//                        .build(),
//                    contentScale = ContentScale.Crop,
//                    contentDescription = "",
//                    placeholder = fallbackPainter,
//                    error = fallbackPainter
//                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    modifier = Modifier,
//                        .let { modifierLet ->
//                        if (title is UserState.Loading) {
//                            modifierLet.shimmer()
//                        } else {
//                            modifierLet
//                        }
//                    },
                    text = getUserName(title),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        actions = {
            Icon(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = menuClick)
                    .padding(10.dp),
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
//                stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    )
}

@Composable
private fun getUserName(userNameState: UserState<String>): String =
    when (userNameState) {
        is UserState.ConnectedUser ->
            "${userNameState.data.takeIf { it.isNotEmpty() } ?: "Unkown"}\'s Workspace"
//            stringResource(id = R.string.name_space, userNameState.data)
        is UserState.DisconnectedUser -> "Offline Workspace"
//            stringResource(id = R.string.offline_workspace)
        is UserState.Idle -> ""
        is UserState.Loading -> ""
        is UserState.UserNotReturned -> "Disconnected"
//            stringResource(id = R.string.disconnected)
    }

@Composable
private fun FloatingActionButton(
    onClick: () -> Unit
) {
    FloatingActionButton(
        modifier = Modifier.semantics {
            testTag = ADD_NOTE_TEST_TAG
        },
        containerColor = MaterialTheme.colorScheme.primary,
        onClick = onClick,
        content = {
            Icon(
                imageVector = WrIcons.add,
                contentDescription = "Add note"
//                stringResource(R.string.add_note)
            )
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Content(
    isDarkTheme: Boolean,
    chooseNoteViewModel: ChooseNoteViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    loadNote: (String, String) -> Unit,
    selectionListener: (String, Boolean) -> Unit,
    newNote: () -> Unit,
    navigateToNotes: (NotesNavigation) -> Unit,
    paddingValues: PaddingValues,
) {
    NotesCardsScreen(
        isDarkTheme = isDarkTheme,
        documents = chooseNoteViewModel.documentsState.collectAsState().value,
        showAddMenuState = chooseNoteViewModel.showAddMenuState,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransitionScope = sharedTransitionScope,
        loadNote = loadNote,
        selectionListener = selectionListener,
        hideShowMenu = chooseNoteViewModel::hideAddMenu,
        folderClick = { id ->
            val handled = chooseNoteViewModel.handleMenuItemTap(id)
            if (!handled) {
                navigateToNotes(NotesNavigation.Folder(id))
            }
        },
        changeIcon = { _, _, _, _ -> },
        moveRequest = chooseNoteViewModel::moveToFolder,
        onSelection = {},
        newNote = newNote,
        newFolder = chooseNoteViewModel::newFolder,
        editFolder = chooseNoteViewModel::editFolder,
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
    )
}
