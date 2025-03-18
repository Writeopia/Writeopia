package io.writeopia.notemenu.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.file.directoryChooserSave
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.common.utils.ui.LocalToastInfo
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.ui.screen.actions.DesktopNoteActionsMenu
import io.writeopia.notemenu.ui.screen.configuration.molecules.NotesConfigurationMenu
import io.writeopia.notemenu.ui.screen.configuration.molecules.NotesSelectionMenu
import io.writeopia.commonui.workplace.WorkspaceConfigurationDialog
import io.writeopia.controller.OllamaConfigController
import io.writeopia.commonui.dialogs.confirmation.DeleteConfirmationDialog
import io.writeopia.notemenu.ui.screen.documents.NotesCards
import io.writeopia.notemenu.ui.screen.file.fileChooserLoad
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.ConfigState
import io.writeopia.notemenu.viewmodel.getPath
import io.writeopia.notemenu.viewmodel.toNumberDesktop
import io.writeopia.onboarding.OnboardingWorkspace

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DesktopNotesMenu(
    folderId: String,
    chooseNoteViewModel: ChooseNoteViewModel,
    ollamaConfigController: OllamaConfigController? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNewNoteClick: () -> Unit,
    onNoteClick: (String, String) -> Unit,
    navigateToNotes: (NotesNavigation) -> Unit,
//    addFolder: () -> Unit,
//    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(
        key1 = "refresh",
        block = {
            chooseNoteViewModel.requestUser()
        }
    )

    val borderPadding = 8.dp

    sharedTransitionScope.run {

        Box(modifier = modifier.fillMaxSize().padding(end = 12.dp)) {
            Column(
                modifier = Modifier.padding(top = borderPadding)
                    .sharedBounds(
                        rememberSharedContentState(key = "folderTransition${folderId}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 40.dp)
                ) {
                    NotesCards(
                        documents = chooseNoteViewModel.documentsState.collectAsState().value,
                        loadNote = { id, title ->
                            val handled = chooseNoteViewModel.handleMenuItemTap(id)
                            if (!handled) {
                                onNoteClick(id, title)
                            }
                        },
                        selectionListener = chooseNoteViewModel::onDocumentSelected,
                        folderClick = { id ->
                            val handled = chooseNoteViewModel.handleMenuItemTap(id)
                            if (!handled) {
                                navigateToNotes(NotesNavigation.Folder(id))
                            }
                        },
                        moveRequest = { item, parentId ->
                            chooseNoteViewModel.moveToFolder(item, parentId)
                        },
                        modifier = Modifier.weight(1F).fillMaxHeight()
                            .padding(end = 10.dp, top = 20.dp),
                        changeIcon = chooseNoteViewModel::changeIcons,
                        onSelection = chooseNoteViewModel::toggleSelection,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        newNote = onNewNoteClick
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    NotesConfigurationMenu(
                        modifier = Modifier.padding(end = borderPadding),
                        showSortingOption = chooseNoteViewModel.showSortMenuState,
                        selectedState = chooseNoteViewModel.notesArrangement.toNumberDesktop(),
                        showSortOptionsRequest = chooseNoteViewModel::showSortMenu,
                        hideSortOptionsRequest = chooseNoteViewModel::cancelSortMenu,
                        staggeredGridSelected = chooseNoteViewModel::staggeredGridArrangementSelected,
                        gridSelected = chooseNoteViewModel::gridArrangementSelected,
                        listSelected = chooseNoteViewModel::listArrangementSelected,
                        selectSortOption = chooseNoteViewModel::sortingSelected,
                        sortOptionState = chooseNoteViewModel.orderByState
                    )
                }
            }

            DesktopNoteActionsMenu(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp),
                showExtraOptions = chooseNoteViewModel.editState,
                showExtraOptionsRequest = chooseNoteViewModel::showEditMenu,
                hideExtraOptionsRequest = chooseNoteViewModel::cancelEditMenu,
                exportAsMarkdownClick = {
                    directoryChooserSave("")?.let(chooseNoteViewModel::directoryFilesAsMarkdown)
                },
                importClick = {
                    chooseNoteViewModel.loadFiles(fileChooserLoad(""))
                },
                syncInProgressState = chooseNoteViewModel.syncInProgress,
                onSyncLocallySelected = chooseNoteViewModel::onSyncLocallySelected,
                onWriteLocallySelected = chooseNoteViewModel::onWriteLocallySelected,
            )

            val showOnboard by chooseNoteViewModel.showOnboardingState.collectAsState()

            LocalToastInfo.current.hideGlobalContent = showOnboard.shouldShow()

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

                    if (ollamaConfigController != null) {
                        DropdownMenu(
                            expanded = showOnboard.shouldShow(),
                            onDismissRequest = chooseNoteViewModel::hideOnboarding,
                            offset = DpOffset(20.dp, 0.dp),
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(
                                1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2F)
                            )
                        ) {
                            OnboardingWorkspace(
                                showOnboard = showOnboard,
                                downloadModelState = ollamaConfigController.downloadModelState,
                                downloadModel = { model ->
                                    ollamaConfigController.modelToDownload(
                                        model,
                                        onComplete = { chooseNoteViewModel.completeOnboarding() }
                                    )
                                },
                                onCloseClick = chooseNoteViewModel::hideOnboarding,
                                onClosePermanentlyClick = chooseNoteViewModel::closeOnboardingPermanently,
                            )
                        }
                    }
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
                onClose = chooseNoteViewModel::unSelectNotes,
                shape = RoundedCornerShape(CornerSize(16.dp)),
                exitAnimationOffset = 2.3F,
                enterAnimationSpec = spring(dampingRatio = 0.6F)
            )

            val titlesToDelete by chooseNoteViewModel.titlesToDelete.collectAsState()

            if (titlesToDelete.isNotEmpty()) {
                DeleteConfirmationDialog(
                    onConfirmation = chooseNoteViewModel::deleteSelectedNotes,
                    onCancel = chooseNoteViewModel::cancelDeletion,
                )
            }
        }
    }
}
