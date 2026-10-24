package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import io.writeopia.notemenu.viewmodel.FolderMove
import io.writeopia.resources.WrStrings
import kotlinx.coroutines.flow.StateFlow

/**
 * Tells in [snackbarHostState] what the last drag and drop into a folder moved, with an action
 * to undo it. A new move replaces the snackbar of the previous one.
 */
@Composable
fun FolderMoveSnackbarEffect(
    snackbarHostState: SnackbarHostState,
    lastMoveState: StateFlow<FolderMove?>,
    undoMove: (FolderMove) -> Unit,
    dismissMove: (FolderMove) -> Unit,
) {
    val move = lastMoveState.collectAsState().value ?: return

    val folderTitle = move.folderTitle ?: WrStrings.home()
    val message = if (move.items.size == 1) {
        WrStrings.movedItemToFolder(move.itemTitle.ifBlank { WrStrings.untitled() }, folderTitle)
    } else {
        WrStrings.movedItemsToFolder(move.items.size, folderTitle)
    }
    val undoLabel = WrStrings.undo()

    LaunchedEffect(move) {
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
        )

        when (result) {
            SnackbarResult.ActionPerformed -> undoMove(move)
            SnackbarResult.Dismissed -> dismissMove(move)
        }
    }
}
