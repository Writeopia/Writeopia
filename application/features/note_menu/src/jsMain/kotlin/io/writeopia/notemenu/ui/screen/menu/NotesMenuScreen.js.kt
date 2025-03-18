package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.controller.OllamaConfigController
import io.writeopia.model.ColorThemeOption
import io.writeopia.notemenu.data.model.NotesNavigation
import io.writeopia.notemenu.ui.screen.DesktopNotesMenu
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
actual fun NotesMenuScreen(
    folderId: String,
    chooseNoteViewModel: ChooseNoteViewModel,
    ollamaConfigController: OllamaConfigController?,
    navigationController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNewNoteClick: () -> Unit,
    onNoteClick: (String, String) -> Unit,
    onAccountClick: () -> Unit,
    selectColorTheme: (ColorThemeOption) -> Unit,
    navigateToFolders: (NotesNavigation) -> Unit,
    addFolder: () -> Unit,
    editFolder: (MenuItemUi.FolderUi) -> Unit,
    modifier: Modifier
) {
    DesktopNotesMenu(
        chooseNoteViewModel = chooseNoteViewModel,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onNewNoteClick = onNewNoteClick,
        onNoteClick = onNoteClick,
        navigateToNotes = navigateToFolders,
//        addFolder = addFolder,
//        editFolder = editFolder,
        modifier = modifier,
    )
}
