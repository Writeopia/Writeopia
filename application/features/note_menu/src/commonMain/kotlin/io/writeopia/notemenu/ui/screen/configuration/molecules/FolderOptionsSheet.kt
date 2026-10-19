package io.writeopia.notemenu.ui.screen.configuration.molecules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Bottom sheet with the actions on one folder: editing, moving and deleting it. It's what the
 * "more" button of a folder card opens on touch screens, the same actions the options sheet
 * offers inside the folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderOptionsSheet(
    folderTitle: String,
    onDismissRequest: () -> Unit,
    onEditFolder: () -> Unit,
    onMoveFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = 500.dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Every action opens another sheet or a dialog, so this one slides away first.
    val hideThen = { action: () -> Unit ->
        {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { action() }
            Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            FolderSection(
                folderTitle = folderTitle,
                onEditFolder = hideThen(onEditFolder),
                onMoveFolder = hideThen(onMoveFolder),
                onDeleteFolder = hideThen(onDeleteFolder),
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
