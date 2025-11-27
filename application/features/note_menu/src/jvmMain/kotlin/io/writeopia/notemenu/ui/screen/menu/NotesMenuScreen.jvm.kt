package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.controller.OllamaConfigController
import io.writeopia.model.ColorThemeOption
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.notemenu.ui.screen.DesktopNotesMenu
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.theme.WriteopiaTheme
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun NotesMenuScreen(
    isDarkTheme: Boolean,
    folderId: String,
    chooseNoteViewModel: ChooseNoteViewModel,
    ollamaConfigController: OllamaConfigController?,
    navigationController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNewNoteClick: () -> Unit,
    onNoteClick: (String, String) -> Unit,
    onAccountClick: () -> Unit,
    onForceGraphSelected: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
    navigateToFolders: (NotesNavigation) -> Unit,
    addFolder: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    navigationBar: @Composable () -> Unit,
    modifier: Modifier
) {
    var onDropEvent by remember {
        mutableStateOf(false)
    }

    val highlightBackground = WriteopiaTheme.colorScheme.highlight

    val background by derivedStateOf {
        if (onDropEvent) highlightBackground else Color.Unspecified
    }

    val dragAndDropTarget = documentFilesDropTarget(
        chooseNoteViewModel::loadFiles,
        onStart = { onDropEvent = true },
        onEnd = { onDropEvent = false },
    )

    DesktopNotesMenu(
        isDarkTheme = isDarkTheme,
        folderId = folderId,
        chooseNoteViewModel = chooseNoteViewModel,
        ollamaConfigController = ollamaConfigController,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onNewNoteClick = onNewNoteClick,
        onNoteClick = onNoteClick,
        navigateToNotes = navigateToFolders,
        navigateToForceGraph = onForceGraphSelected,
//        addFolder = addFolder,
//        editFolder = editFolder,
        modifier = modifier.background(background).dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            },
            target = dragAndDropTarget
        ),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun documentFilesDropTarget(
    onFileReceived: (List<ExternalFile>) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) = remember {
    object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            onStart()
        }

        override fun onEnded(event: DragAndDropEvent) {
            onEnd()
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            val files = event.awtTransferable.let { transferable ->
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    val files =
                        transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>

                    if (files.isNotEmpty()) {
                        onFileReceived(
                            files.map { file ->
                                ExternalFile(
                                    file.absolutePath,
                                    file.extension,
                                    file.name
                                )
                            }
                        )
                    }

                    files
                } else {
                    emptyList()
                }
            }

            return files.isNotEmpty()
        }
    }
}
