package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.commonui.workplace.WorkspaceConfigurationDialog
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.controller.OllamaConfigController
import io.writeopia.model.ColorThemeOption
import io.writeopia.notemenu.ui.screen.configuration.molecules.NotesConfigurationMenu
import io.writeopia.notemenu.ui.screen.configuration.molecules.NotesSelectionMenu
import io.writeopia.notemenu.ui.screen.documents.NotesCardsScreenSimple
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.ConfigState
import io.writeopia.notemenu.viewmodel.getPath
import io.writeopia.notemenu.viewmodel.toNumberDesktop

/**
 * NotesMenuScreenContent - A version of NotesMenuScreen designed for Navigation 3.
 * This doesn't require AnimatedVisibilityScope which is not available in Navigation 3's entry pattern.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotesMenuScreenContent(
    isDarkTheme: Boolean,
    folderId: String,
    chooseNoteViewModel: ChooseNoteViewModel,
    ollamaConfigController: OllamaConfigController? = null,
    sharedTransitionScope: SharedTransitionScope,
    onNewNoteClick: () -> Unit,
    onNoteClick: (String, String) -> Unit,
    onAccountClick: () -> Unit,
    onForceGraphSelected: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
    navigateToFolders: (NotesNavigation) -> Unit,
    addFolder: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    nestedScrollConnection: NestedScrollConnection? = null,
    isToolbarVisible: Boolean = true,
    navigationBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(
        key1 = "refresh",
        block = {
            chooseNoteViewModel.requestUser()
            chooseNoteViewModel.syncFolderWithCloud()
        }
    )

    val borderPadding = 8.dp

    Box(modifier = modifier.fillMaxSize().padding(end = 12.dp)) {
        Column(
            modifier = Modifier.padding(top = borderPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 40.dp)
            ) {
                NotesCardsScreenSimple(
                    isDarkTheme = isDarkTheme,
                    documents = chooseNoteViewModel.documentsState.collectAsState().value,
                    showAddMenuState = chooseNoteViewModel.showAddMenuState,
                    loadNote = { id, title ->
                        val handled = chooseNoteViewModel.handleMenuItemTap(id)
                        if (!handled) {
                            onNoteClick(id, title)
                        }
                    },
                    selectionListener = chooseNoteViewModel::onDocumentSelected,
                    folderClick = { id -> navigateToFolders(NotesNavigation.Folder(id)) },
                    moveRequest = chooseNoteViewModel::moveToFolder,
                    onSelection = { id -> chooseNoteViewModel.onDocumentSelected(id, true) },
                    newNote = onNewNoteClick,
                    newFolder = { chooseNoteViewModel.newFolder() },
                    hideShowMenu = chooseNoteViewModel::hideAddMenu,
                    editFolder = editFolder,
                    modifier = Modifier.weight(1F)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.fillMaxHeight()) {
                    NotesConfigurationMenu(
                        showSortingOption = chooseNoteViewModel.showSortMenuState,
                        selectedState = chooseNoteViewModel.notesArrangement.toNumberDesktop(),
                        sortOptionState = chooseNoteViewModel.orderByState,
                        showSortOptionsRequest = chooseNoteViewModel::showSortMenu,
                        hideSortOptionsRequest = chooseNoteViewModel::cancelSortMenu,
                        selectSortOption = chooseNoteViewModel::sortingSelected,
                        staggeredGridSelected = chooseNoteViewModel::staggeredGridArrangementSelected,
                        gridSelected = chooseNoteViewModel::gridArrangementSelected,
                        listSelected = chooseNoteViewModel::listArrangementSelected,
                    )

                    val titlesToDelete by chooseNoteViewModel.titlesToDelete.collectAsState()

                    if (titlesToDelete.isNotEmpty()) {
                        DeleteConfirmationDialog(
                            onConfirmation = chooseNoteViewModel::deleteSelectedNotes,
                            onCancel = chooseNoteViewModel::cancelDeletion
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(horizontal = 40.dp - borderPadding, vertical = 40.dp)
                .testTag("addNote"),
            onClick = {
                chooseNoteViewModel.requestInitFlow(onNewNoteClick)
            },
            content = {
                Icon(
                    imageVector = WrIcons.add,
                    contentDescription = "New note",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            },
            containerColor = MaterialTheme.colorScheme.primary
        )

        val configState = chooseNoteViewModel.showLocalSyncConfigState.collectAsState().value

        if (configState != ConfigState.Idle) {
            WorkspaceConfigurationDialog(
                currentPath = configState.getPath(),
                pathChange = chooseNoteViewModel::pathSelected,
                onDismissRequest = chooseNoteViewModel::hideConfigSyncMenu,
                onConfirmation = chooseNoteViewModel::confirmWorkplacePath
            )
        }

        val hasSelectedNotes by chooseNoteViewModel.hasSelectedNotes.collectAsState()

        NotesSelectionMenu(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                .width(400.dp),
            visibilityState = hasSelectedNotes,
            onDelete = chooseNoteViewModel::requestPermissionToDeleteSelection,
            onCopy = chooseNoteViewModel::copySelectedNotes,
            onFavorite = chooseNoteViewModel::favoriteSelectedNotes,
            onSummary = chooseNoteViewModel::summarizeDocuments,
            onClose = chooseNoteViewModel::clearSelection,
            shape = RoundedCornerShape(CornerSize(16.dp)),
            exitAnimationOffset = 2.3F,
            enterAnimationSpec = spring(dampingRatio = 0.6F)
        )
    }
}
