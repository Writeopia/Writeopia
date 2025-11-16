package io.writeopia.notemenu.ui.screen.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ChooseNoteScreen(
    isDarkTheme: Boolean,
    chooseNoteViewModel: ChooseNoteViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    navigateToNote: (String, String) -> Unit,
    newNote: () -> Unit,
    navigateToAccount: () -> Unit,
    navigateToNotes: (NotesNavigation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelectedNotes by chooseNoteViewModel.hasSelectedNotes.collectAsState()
    val editState by chooseNoteViewModel.editState.collectAsState()

    BackHandler(hasSelectedNotes || editState) {
        when {
            editState -> {
                chooseNoteViewModel.cancelEditMenu()
            }

            hasSelectedNotes -> {
                chooseNoteViewModel.clearSelection()
            }
        }
    }

    MobileChooseNoteScreen(
        isDarkTheme,
        chooseNoteViewModel,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        navigateToNote = navigateToNote,
        newNote = newNote,
        navigateToAccount = navigateToAccount,
        navigateToNotes = navigateToNotes,
        modifier = modifier,
    )
}
