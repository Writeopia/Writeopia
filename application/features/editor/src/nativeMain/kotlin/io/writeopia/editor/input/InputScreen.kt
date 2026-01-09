package io.writeopia.editor.input

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.editor.ui.MobileInputScreen
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.model.SelectionMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun InputScreen(
    modifier: Modifier = Modifier,
    metadataState: Flow<Set<SelectionMetadata>>,
    onAddSpan: (Span) -> Unit,
    onBackPress: () -> Unit = {},
    onForwardPress: () -> Unit = {},
    canUndoState: StateFlow<Boolean>,
    canRedoState: StateFlow<Boolean>,
) {
    MobileInputScreen(
        modifier,
        metadataState,
        onAddSpan,
        onBackPress,
        onForwardPress,
        canUndoState,
        canRedoState,
    )
}
