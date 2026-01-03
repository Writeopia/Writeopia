package io.writeopia.editor.features.editor.ui.desktop

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.colors.ColorUtils
import io.writeopia.editor.configuration.ui.HeaderEditionOptions
import io.writeopia.editor.features.editor.ui.TextEditor
import io.writeopia.editor.features.editor.viewmodel.NoteEditorViewModel
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.ui.drawer.factory.DrawersFactory
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun AppTextEditor(
    isDarkTheme: Boolean,
    manager: WriteopiaStateManager,
    viewModel: NoteEditorViewModel,
    drawersFactory: DrawersFactory,
    loadNoteId: String? = null,
    onDocumentLinkClick: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val coroutine = rememberCoroutineScope()

    coroutine.launch {
        manager.scrollToPosition.collectLatest {
            listState.animateScrollBy(70F)
        }
    }

    if (loadNoteId == null) {
        viewModel.createNewDocument(GenerateId.generate(), "")
    } else {
        viewModel.loadDocument(loadNoteId)
    }

    Box(modifier) {
        TextEditor(
            isDarkTheme = isDarkTheme,
            viewModel,
            drawersFactory,
            onDocumentLinkClick = onDocumentLinkClick,
            listState = listState
        )

        val isEditionHeader by viewModel.editHeader.collectAsState()

        if (isEditionHeader) {
            Dialog(onDismissRequest = viewModel::onHeaderEditionCancel) {
                Card(
                    modifier = Modifier.padding(30.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    HeaderEditionOptions(
                        ColorUtils.headerColors(),
                        viewModel::onHeaderColorSelection
                    )

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}
