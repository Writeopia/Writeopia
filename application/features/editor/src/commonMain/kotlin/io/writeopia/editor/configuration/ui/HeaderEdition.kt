package io.writeopia.editor.configuration.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.colors.ColorUtils
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.theme.WriteopiaTheme

/**
 * The header menu of the phones: a bottom sheet with the colors and the pictures of the header.
 * It opens fully expanded, so the picture the AI draws is in view without dragging the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderEdition(
    noteEditorViewModel: NoteEditorViewModel,
    visibilityState: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!visibilityState) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = WriteopiaTheme.colorScheme.lightBackground,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            HeaderEditionContent(noteEditorViewModel)

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** The header options bound to the editor: what the header has now, and the pictures. */
@Composable
fun HeaderEditionContent(noteEditorViewModel: NoteEditorViewModel) {
    val currentStory by noteEditorViewModel.writeopiaManager.currentStory.collectAsState()
    val headerImages by noteEditorViewModel.headerImages.collectAsState()
    val header = currentStory.stories[0L]

    HeaderEditionOptions(
        availableColors = ColorUtils.headerColors(),
        selectedColor = header?.decoration?.backgroundColor,
        onColorSelection = noteEditorViewModel::onHeaderColorSelection,
        headerImages = headerImages,
        selectedImageUrl = header?.url,
        onImageSelection = noteEditorViewModel::onHeaderImageSelection,
        onGenerateImage = noteEditorViewModel::generateHeaderImage,
        onApproveImage = noteEditorViewModel::approveHeaderImage,
        onDiscardImage = noteEditorViewModel::discardHeaderImage,
    )
}
